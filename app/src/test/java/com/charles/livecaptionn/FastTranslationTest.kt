package com.charles.livecaptionn

import com.charles.livecaptionn.translation.fast.EngineBatchResult
import com.charles.livecaptionn.translation.fast.FastLang
import com.charles.livecaptionn.translation.fast.FastTranslateEngine
import com.charles.livecaptionn.translation.fast.FastTranslationManager
import com.charles.livecaptionn.translation.fast.GoogleFastEngine
import com.charles.livecaptionn.translation.fast.MicrosoftFastEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class FastTranslationTest {

    @Test
    fun langMapping_google() {
        assertEquals("zh-CN", FastLang.google("zh"))
        assertEquals("zh-CN", FastLang.google("zh-Hans"))
        assertEquals("zh-TW", FastLang.google("zh-TW"))
        assertEquals("yue", FastLang.google("yue"))
        assertEquals("en", FastLang.google("en"))
        assertEquals("auto", FastLang.google("auto"))
    }

    @Test
    fun langMapping_microsoftAndYandex() {
        assertEquals("zh-Hans", FastLang.microsoft("zh"))
        assertEquals("zh-Hant", FastLang.microsoft("zh-Hant"))
        assertEquals("", FastLang.microsoft("auto"))
        assertEquals("zh", FastLang.yandex("zh"))
        assertEquals("zh", FastLang.yandex("yue"))
        assertEquals("en", FastLang.yandex("en"))
    }

    @Test
    fun googleSplit_joinedBatch() {
        val joined = "Hello\n<<<BTE_SPLIT_TOKEN>>>\nWorld"
        assertEquals(listOf("Hello", "World"), GoogleFastEngine.splitJoined(joined, 2))
        assertNull(GoogleFastEngine.splitJoined("Hello World", 2))
    }

    @Test
    fun googleEncodedSize_cjkIsNineBytesPerChar() {
        assertTrue(GoogleFastEngine.encodedSize("你好") > 2)
    }

    @Test
    fun microsoftJwtExpiry_decodesExp() {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"exp\":2000000000}".toByteArray())
        val expMs = MicrosoftFastEngine.decodeJwtExpiryMs("$header.$payload.sig")
        assertEquals(2_000_000_000_000L, expMs)
    }

    @Test
    fun manager_translatesAndCaches() = runTest {
        val engine = FakeEngine("fake") { texts -> texts.map { "T:$it" } }
        val manager = FastTranslationManager(listOf(engine), this)
        val first = manager.translateMany(listOf("你好", "谢谢"), "zh", "en", urgent = false)
        assertEquals(listOf("T:你好", "T:谢谢"), first)
        assertEquals(1, engine.calls)
        val second = manager.translateMany(listOf("你好", "谢谢"), "zh", "en", urgent = false)
        assertEquals(first, second)
        assertEquals(1, engine.calls)
    }

    @Test
    fun manager_fallsBackWhenPrimaryThrows() = runTest {
        val broken = FakeEngine("broken") { throw RuntimeException("boom") }
        val working = FakeEngine("working") { texts -> texts.map { "W:$it" } }
        val manager = FastTranslationManager(listOf(broken, working), this)
        val result = manager.translateMany(listOf("你好"), "zh", "en", urgent = false)
        assertEquals(listOf("W:你好"), result)
    }

    @Test
    fun manager_singleCallsCoalesce() = runTest {
        val engine = FakeEngine("fake") { texts -> texts.map { "T:$it" } }
        val manager = FastTranslationManager(listOf(engine), this)
        val a = manager.translate("你好", "zh", "en")
        val b = manager.translate("谢谢", "zh", "en")
        assertEquals("T:你好", a)
        assertEquals("T:谢谢", b)
        assertTrue(engine.batchSizes.isNotEmpty())
    }

    @Test
    fun manager_urgentTranslates() = runTest {
        val engine = FakeEngine("fake") { texts -> texts.map { "T:$it" } }
        val manager = FastTranslationManager(listOf(engine), this)
        val result = manager.translate("你好", "zh", "en", urgent = true)
        assertEquals("T:你好", result)
    }

    @Test
    fun manager_sameAsSourceIsNegativeCached() = runTest {
        val engine = FakeEngine("fake") { texts -> texts.map { it } }
        val manager = FastTranslationManager(listOf(engine), this)
        manager.translateMany(listOf("hello"), "en", "en", urgent = false)
        val callsAfterFirst = engine.calls
        manager.translateMany(listOf("hello"), "en", "en", urgent = false)
        assertEquals(callsAfterFirst, engine.calls)
    }
}

private class FakeEngine(
    override val name: String,
    private val behavior: (List<String>) -> List<String?>
) : FastTranslateEngine {
    override val maxItemsPerRequest = 25
    override val maxCharsPerRequest = 4000
    var calls = 0
    val batchSizes = mutableListOf<Int>()

    override suspend fun translateBatch(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): EngineBatchResult {
        calls++
        batchSizes.add(texts.size)
        return EngineBatchResult(behavior(texts))
    }
}
