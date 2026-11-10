package com.charles.livecaptionn.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class SherpaModelState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)

// Sherpa models are far too big to bundle in the APK, so each one downloads
// in-app on demand — one shared store for the whole catalog.
class SherpaModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /** First installed model that can serve [languageCode], best first. */
    fun installedModelFor(languageCode: String): SherpaModel? =
        SherpaModelCatalog.modelsFor(languageCode).firstOrNull { isInstalled(it) }

    fun download(model: SherpaModel) {
        if (mutableState.value[model.id]?.downloading == true || isInstalled(model)) return
        scope.launch {
            update(model.id, SherpaModelState(downloading = true))
            try {
                val dir = modelDir(model).apply { mkdirs() }
                var doneBytes = 0L
                val totalBytes = model.totalSizeBytes
                for (spec in model.files) {
                    val target = File(dir, spec.name)
                    if (target.exists() && target.length() >= spec.sizeBytes) {
                        doneBytes += target.length()
                        continue
                    }
                    val part = File(dir, "${spec.name}.part")
                    val request = Request.Builder().url(model.fileUrl(spec.name)).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code} for ${spec.name}")
                        }
                        val body = response.body
                            ?: throw IllegalStateException("Empty body for ${spec.name}")
                        body.byteStream().use { input ->
                            FileOutputStream(part).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    doneBytes += read
                                    update(
                                        model.id,
                                        SherpaModelState(
                                            downloading = true,
                                            progress = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (!part.renameTo(target)) {
                        part.copyTo(target, overwrite = true)
                        part.delete()
                    }
                }
                if (isInstalled(model)) {
                    update(model.id, SherpaModelState(installed = true))
                } else {
                    update(model.id, SherpaModelState(error = "Download finished but files look incomplete. Try again."))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Download failed for ${model.id}", t)
                update(model.id, SherpaModelState(error = "Download failed: ${t.message ?: t::class.java.simpleName}"))
            }
        }
    }

    fun delete(model: SherpaModel) {
        scope.launch {
            try { modelDir(model).deleteRecursively() } catch (_: Throwable) {}
            update(model.id, SherpaModelState(installed = false))
        }
    }

    private fun update(id: String, state: SherpaModelState) {
        mutableState.value = mutableState.value + (id to state)
    }

    companion object {
        private const val TAG = "SherpaModelStore"
    }
}
