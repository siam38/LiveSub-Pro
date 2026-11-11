package com.charles.livecaptionn.speech

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class SherpaModelState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)

class SherpaModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val mutableState = MutableStateFlow(
        SherpaModelCatalog.ALL.associate { it.id to SherpaModelState(installed = isInstalled(it)) }
    )
    val state: StateFlow<Map<String, SherpaModelState>> = mutableState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun modelDir(model: SherpaModel): File = File(appContext.filesDir, "sherpa/${model.id}")

    fun isInstalled(model: SherpaModel): Boolean {
        val dir = modelDir(model)
        return model.files.all { spec ->
            val f = File(dir, spec.name)
            f.exists() && f.length() >= spec.sizeBytes
        }
    }

    fun installedModelFor(languageCode: String): SherpaModel? =
        SherpaModelCatalog.modelsFor(languageCode).firstOrNull { isInstalled(it) }

    fun installedBytes(model: SherpaModel): Long {
        val dir = modelDir(model)
        if (!dir.exists()) return 0L
        return dir.listFiles()?.filter { !it.name.endsWith(".part") }?.sumOf { it.length() } ?: 0L
    }

    fun totalInstalledBytes(): Long =
        SherpaModelCatalog.ALL.sumOf { installedBytes(it) }

    fun download(model: SherpaModel) {
        if (mutableState.value[model.id]?.downloading == true || isInstalled(model)) return
        val job = scope.launch {
            update(model.id, SherpaModelState(downloading = true, progress = 0f, error = null))
            val dir = modelDir(model).apply { mkdirs() }
            try {
                val missingBytes = model.files
                    .filter { spec ->
                        val f = File(dir, spec.name)
                        !f.exists() || f.length() < spec.sizeBytes
                    }
                    .sumOf { it.sizeBytes }

                if (!hasEnoughDiskSpace(dir, missingBytes + STORAGE_SAFETY_MARGIN_BYTES)) {
                    update(
                        model.id,
                        SherpaModelState(
                            error = "Not enough free storage space (${model.totalSizeMb} MB required)."
                        )
                    )
                    return@launch
                }

                var doneBytes = 0L
                val totalBytes = model.totalSizeBytes.coerceAtLeast(1L)
                for (spec in model.files) {
                    coroutineContext.ensureActive()
                    val target = File(dir, spec.name)
                    if (target.exists() && target.length() >= spec.sizeBytes) {
                        doneBytes += target.length()
                        update(
                            model.id,
                            SherpaModelState(
                                downloading = true,
                                progress = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            )
                        )
                        continue
                    }

                    val part = File(dir, "${spec.name}.part")
                    val existingPartBytes = if (part.exists()) part.length() else 0L
                    val reqBuilder = Request.Builder()
                        .url(model.fileUrl(spec.name))
                        .header("User-Agent", "LiveSubPro-Android/1.0")
                    if (existingPartBytes in 1 until spec.sizeBytes) {
                        reqBuilder.header("Range", "bytes=$existingPartBytes-")
                    }

                    val call = client.newCall(reqBuilder.build())
                    activeCalls[model.id] = call

                    call.execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            throw IllegalStateException("HTTP ${response.code} for ${spec.name}")
                        }
                        val isResuming = response.code == 206 && existingPartBytes > 0L
                        if (isResuming) {
                            doneBytes += existingPartBytes
                        } else if (part.exists()) {
                            part.delete()
                        }

                        val body = response.body
                            ?: throw IllegalStateException("Empty body for ${spec.name}")
                        body.byteStream().use { input ->
                            FileOutputStream(part, isResuming).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var lastReportedPct = -1
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    doneBytes += read
                                    val progress = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    val pctInt = (progress * 200).toInt()
                                    if (pctInt != lastReportedPct) {
                                        lastReportedPct = pctInt
                                        update(
                                            model.id,
                                            SherpaModelState(
                                                downloading = true,
                                                progress = progress
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    activeCalls.remove(model.id)

                    if (part.length() < spec.sizeBytes) {
                        val actualKb = part.length() / 1024
                        val expectedKb = spec.sizeBytes / 1024
                        part.delete()
                        throw IllegalStateException(
                            "Incomplete download for ${spec.name} ($actualKb KB / $expectedKb KB)"
                        )
                    }

                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) {
                        part.copyTo(target, overwrite = true)
                        part.delete()
                    }
                }
                if (isInstalled(model)) {
                    update(model.id, SherpaModelState(installed = true))
                } else {
                    update(
                        model.id,
                        SherpaModelState(error = "Download finished but files look incomplete. Try again.")
                    )
                }
            } catch (ce: CancellationException) {
                Log.i(TAG, "Download cancelled for ${model.id}")
                update(model.id, SherpaModelState(installed = isInstalled(model)))
                throw ce
            } catch (t: Throwable) {
                if (activeJobs[model.id]?.isCancelled == true) {
                    update(model.id, SherpaModelState(installed = isInstalled(model)))
                } else {
                    Log.w(TAG, "Download failed for ${model.id}", t)
                    update(
                        model.id,
                        SherpaModelState(
                            error = "Download failed: ${t.message ?: t::class.java.simpleName}"
                        )
                    )
                }
            } finally {
                activeCalls.remove(model.id)
                activeJobs.remove(model.id)
            }
        }
        activeJobs[model.id] = job
    }

    fun cancelDownload(model: SherpaModel) {
        activeCalls.remove(model.id)?.cancel()
        activeJobs.remove(model.id)?.cancel()
        scope.launch {
            try {
                val dir = modelDir(model)
                dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            } catch (_: Throwable) {}
            update(model.id, SherpaModelState(installed = isInstalled(model)))
        }
    }

    fun delete(model: SherpaModel) {
        activeCalls.remove(model.id)?.cancel()
        activeJobs.remove(model.id)?.cancel()
        scope.launch {
            try { modelDir(model).deleteRecursively() } catch (_: Throwable) {}
            update(model.id, SherpaModelState(installed = false))
        }
    }

    private fun hasEnoughDiskSpace(dir: File, requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes >= requiredBytes
        } catch (_: Throwable) {
            true
        }
    }

    private fun update(id: String, state: SherpaModelState) {
        mutableState.value = mutableState.value + (id to state)
    }

    companion object {
        private const val TAG = "SherpaModelStore"
        private const val STORAGE_SAFETY_MARGIN_BYTES = 45L * 1024L * 1024L
    }
}
