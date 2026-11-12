package com.charles.livecaptionn.translation.fast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// The orchestration layer ported from BTE's TranslationManager: LRU cache,
// in-flight dedup, failure backoff, 16 ms single-call coalescing into
// batches, health-scored engine fallback, adaptive batch concurrency, and
// hedged requests for urgent (final) captions — first engine answer wins.
class FastTranslationManager(
    private val engines: List<FastTranslateEngine>,
    private val scope: CoroutineScope
) {
    private val health: Map<String, EngineHealth> = engines.associate { it.name to EngineHealth() }

    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > CACHE_MAX
    }
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap<String, Deferred<String?>>()
    private val failureBackoff = ConcurrentHashMap<String, Backoff>()
    private val latencySamples = Collections.synchronizedList(mutableListOf<Long>())

    private data class Backoff(val untilMs: Long, val delayMs: Long)

    private data class PendingSingle(
        val text: String,
        val sourceCode: String,
        val targetCode: String,
        val deferred: kotlinx.coroutines.CompletableDeferred<String?>
    )

    private val queueLock = Any()
    private val singleQueue = mutableListOf<PendingSingle>()
    private var queueFlushScheduled = false

    suspend fun warmUp(sourceCode: String, targetCode: String) {
        engines.forEach { engine ->
            runCatching { engine.warmUp() }
        }
    }

    suspend fun translate(text: String, sourceCode: String, targetCode: String): String? =
        translate(text, sourceCode, targetCode, urgent = false)

    suspend fun translate(text: String, sourceCode: String, targetCode: String, urgent: Boolean): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        if (urgent) {
            return translateMany(listOf(clean), sourceCode, targetCode, urgent = true).firstOrNull()
        }
        // Non-urgent singles are coalesced over a 16 ms window so a burst of
        // preview lines rides in one batched request, like BTE's queue.
        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        synchronized(queueLock) {
            singleQueue.add(PendingSingle(clean, sourceCode, targetCode, deferred))
            if (!queueFlushScheduled) {
                queueFlushScheduled = true
                scope.async { flushSingleQueueDelayed() }
            }
        }
        return deferred.await()
    }

    private suspend fun flushSingleQueueDelayed() {
        delay(SINGLE_QUEUE_DELAY_MS)
        val batch: List<PendingSingle>
        synchronized(queueLock) {
            batch = singleQueue.toList()
            singleQueue.clear()
            queueFlushScheduled = false
        }
        if (batch.isEmpty()) return
        val groups = batch.groupBy { it.sourceCode to it.targetCode }
        coroutineScope {
            groups.values.map { group ->
                async {
                    val results = runCatching {
                        translateMany(group.map { it.text }, group.first().sourceCode, group.first().targetCode, urgent = false)
                    }.getOrElse { List<String?>(group.size) { null } }
                    group.forEachIndexed { i, item -> item.deferred.complete(results.getOrNull(i)) }
                }
            }.forEach { it.await() }
        }
    }

    suspend fun translateMany(
        texts: List<String>,
        sourceCode: String,
        targetCode: String,
        urgent: Boolean
    ): List<String?> {
        if (texts.isEmpty()) return emptyList()
        val chain = resolveChain()
        if (chain.isEmpty()) return List(texts.size) { null }
        val results = arrayOfNulls<String>(texts.size)
        val waits = mutableListOf<Deferred<Unit>>()
        val newItems = LinkedHashMap<String, Item>()

        for (index in texts.indices) {
            val prepared = texts[index].trim()
            if (prepared.isEmpty()) continue
            val key = buildKey(sourceCode, targetCode, prepared)
            lookupCache(key)?.let { cached ->
                results[index] = cached
                continue
            }
            if (isNegativeCached(key)) continue
            if (isBackingOff(key)) continue
            val existing = pending[key]
            if (existing != null) {
                waits.add(scope.async {
                    val value = runCatching { existing.await() }.getOrNull()
                    if (value != null) results[index] = value
                })
                continue
            }
            val item = newItems.getOrPut(key) { Item(key, prepared) }
            item.indexes.add(index)
        }

        if (newItems.isNotEmpty()) {
            val items = newItems.values.toList()
            val deferredByKey = HashMap<String, kotlinx.coroutines.CompletableDeferred<String?>>()
            for (item in items) {
                val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
                deferredByKey[item.key] = deferred
                pending[item.key] = deferred
                deferred.invokeOnCompletion { pending.remove(item.key, deferred) }
                item.indexes.forEach { index ->
                    waits.add(scope.async {
                        val value = runCatching { deferred.await() }.getOrNull()
                        if (value != null) results[index] = value
                    })
                }
            }

            val primary = chain.first()
            val batches = buildBatches(items, primary)
            val gate = Semaphore(MAX_CONCURRENT_BATCHES)
            coroutineScope {
                batches.map { batch ->
                    async {
                        gate.withPermit { runBatch(batch, chain, sourceCode, targetCode, urgent, deferredByKey) }
                    }
                }.forEach { runCatching { it.await() } }
            }
        }

        waits.forEach { runCatching { it.await() } }
        return results.toList()
    }

    private suspend fun runBatch(
        batch: List<Item>,
        chain: List<FastTranslateEngine>,
        sourceCode: String,
        targetCode: String,
        urgent: Boolean,
        deferredByKey: Map<String, kotlinx.coroutines.CompletableDeferred<String?>>
    ) {
        val batchTexts = batch.map { it.text }
        val startedAt = System.currentTimeMillis()
        val outcome = if (urgent && chain.size > 1) {
            hedgedBatch(chain, batchTexts, sourceCode, targetCode)
        } else {
            translateBatchWithFallback(chain, batchTexts, sourceCode, targetCode)
        }
        noteBatchDuration(System.currentTimeMillis() - startedAt)
        batch.forEachIndexed { i, item ->
            val value = outcome.values.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() && it != item.text }
            if (value != null) {
                storeCache(item.key, value)
                failureBackoff.remove(item.key)
            } else if (!outcome.threw) {
                storeNegativeCache(item.key)
            } else {
                noteFailure(item.key)
            }
            deferredByKey[item.key]?.complete(value)
        }
    }

    // Race the primary engine; if it has not answered within the hedge delay
    // (2x the median batch latency, clamped 500..3000 ms like BTE), ask the
    // next engine too. First full answer wins, the loser is cancelled.
    private suspend fun hedgedBatch(
        chain: List<FastTranslateEngine>,
        batchTexts: List<String>,
        sourceCode: String,
        targetCode: String
    ): BatchOutcome = coroutineScope {
        val main = async { translateBatchWithFallback(chain, batchTexts, sourceCode, targetCode) }
        val hedge = async {
            delay(hedgeDelayMs())
            if (main.isCompleted) return@async null
            // Main still running: fire the second engine on the whole batch.
            val second = chain[1]
            val result = runCatching {
                val t0 = System.currentTimeMillis()
                val r = second.translateBatch(batchTexts, sourceCode, targetCode)
                health[second.name]?.note(r.outputs.any { !it.isNullOrBlank() }, System.currentTimeMillis() - t0)
                BatchOutcome(r.outputs, threw = false)
            }.getOrNull()
            if (result != null && result.values.any { !it.isNullOrBlank() }) result else null
        }
        val mainResult = runCatching { main.await() }.getOrNull()
        val hedgeResult = if (mainResult != null && mainResult.values.any { !it.isNullOrBlank() }) {
            hedge.cancel()
            null
        } else {
            runCatching { hedge.await() }.getOrNull()
        }
        when {
            hedgeResult != null && (mainResult == null || mainResult.values.all { it.isNullOrBlank() }) -> {
                main.cancel()
                hedgeResult
            }
            mainResult != null -> mainResult
            hedgeResult != null -> hedgeResult
            else -> BatchOutcome(List(batchTexts.size) { null }, threw = true)
        }
    }

    private suspend fun translateBatchWithFallback(
        chain: List<FastTranslateEngine>,
        batchTexts: List<String>,
        sourceCode: String,
        targetCode: String
    ): BatchOutcome {
        val values = arrayOfNulls<String>(batchTexts.size)
        var unresolved = batchTexts.indices.toList()
        var threw = false
        for (engine in chain) {
            if (unresolved.isEmpty()) break
            val subset = unresolved.map { batchTexts[it] }
            var subsetOut: List<String?> = List(subset.size) { null }
            var threwThis = false
            val t0 = System.currentTimeMillis()
            try {
                val result = engine.translateBatch(subset, sourceCode, targetCode)
                subsetOut = result.outputs
                val producedAny = subsetOut.any { !it.isNullOrBlank() }
                if (producedAny || result.partialFailure) {
                    health[engine.name]?.note(producedAny, System.currentTimeMillis() - t0)
                }
                if (result.partialFailure) threwThis = true
            } catch (t: Throwable) {
                health[engine.name]?.note(false, System.currentTimeMillis() - t0)
                threwThis = true
            }
            if (threwThis) threw = true
            val next = mutableListOf<Int>()
            unresolved.forEachIndexed { subsetIndex, originalIndex ->
                val translated = subsetOut.getOrNull(subsetIndex)
                if (!translated.isNullOrBlank()) {
                    values[originalIndex] = translated
                } else {
                    next.add(originalIndex)
                }
            }
            unresolved = next
            if (!threwThis && engine.reportsErrors) {
                // This engine's nulls mean "nothing to translate", not errors.
                unresolved = emptyList()
                break
            }
            if (!threwThis && values.any { it != null }) {
                unresolved = emptyList()
                break
            }
        }
        return BatchOutcome(values.toList(), threw)
    }

    private fun hedgeDelayMs(): Long {
        val samples = synchronized(latencySamples) { latencySamples.toList() }
        val median = if (samples.isEmpty()) 400L else samples.sorted()[samples.size / 2]
        return (median * 2).coerceIn(HEDGE_MIN_MS, HEDGE_MAX_MS)
    }

    fun noteBatchDuration(ms: Long) {
        if (ms <= 40) return
        synchronized(latencySamples) {
            latencySamples.add(ms)
            while (latencySamples.size > 9) latencySamples.removeAt(0)
        }
    }

    private fun resolveChain(): List<FastTranslateEngine> =
        engines.sortedBy { health[it.name]?.score() ?: 0.0 }

    private fun buildBatches(items: List<Item>, engine: FastTranslateEngine): List<List<Item>> {
        val batches = mutableListOf<List<Item>>()
        var current = mutableListOf<Item>()
        var chars = 0
        for (item in items) {
            if (current.isNotEmpty() &&
                (current.size >= engine.maxItemsPerRequest || chars + item.text.length > engine.maxCharsPerRequest)
            ) {
                batches.add(current)
                current = mutableListOf()
                chars = 0
            }
            current.add(item)
            chars += item.text.length
        }
        if (current.isNotEmpty()) batches.add(current)
        return batches
    }

    private fun buildKey(sourceCode: String, targetCode: String, text: String) =
        "${FastLang.base(sourceCode).ifBlank { "auto" }}|${FastLang.base(targetCode)}|$text"

    private fun lookupCache(key: String): String? = synchronized(cache) { cache[key] }

    private fun storeCache(key: String, value: String) {
        synchronized(cache) { cache[key] = value }
        negativeCache.remove(key)
    }

    private fun isNegativeCached(key: String): Boolean {
        val until = negativeCache[key] ?: return false
        if (System.currentTimeMillis() > until) {
            negativeCache.remove(key)
            return false
        }
        return true
    }

    private fun storeNegativeCache(key: String) {
        negativeCache[key] = System.currentTimeMillis() + NEGATIVE_TTL_MS
    }

    private fun isBackingOff(key: String): Boolean {
        val entry = failureBackoff[key] ?: return false
        return System.currentTimeMillis() < entry.untilMs
    }

    private fun noteFailure(key: String) {
        val prev = failureBackoff[key]
        val delayMs = if (prev == null) BACKOFF_MIN_MS else minOf(BACKOFF_MAX_MS, prev.delayMs * 2)
        failureBackoff[key] = Backoff(System.currentTimeMillis() + delayMs, delayMs)
        if (failureBackoff.size > 5000) failureBackoff.clear()
    }

    private class Item(val key: String, val text: String) {
        val indexes = mutableListOf<Int>()
    }

    private data class BatchOutcome(val values: List<String?>, val threw: Boolean)

    companion object {
        private const val CACHE_MAX = 2000
        private const val NEGATIVE_TTL_MS = 2 * 60_000L
        private const val BACKOFF_MIN_MS = 5_000L
        private const val BACKOFF_MAX_MS = 60_000L
        private const val SINGLE_QUEUE_DELAY_MS = 16L
        private const val MAX_CONCURRENT_BATCHES = 3
        private const val HEDGE_MIN_MS = 500L
        private const val HEDGE_MAX_MS = 3000L
    }
}
