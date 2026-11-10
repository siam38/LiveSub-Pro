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

// The Sherpa bilingual (zh-en) Zipformer model is too big to bundle in the
// APK, so it downloads once in-app — same pattern as the Vosk models.
class SherpaModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutableState = MutableStateFlow(SherpaModelState(installed = isInstalled()))
    val state: StateFlow<SherpaModelState> = mutableState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun modelDir(): File = File(appContext.filesDir, "sherpa/bilingual-zh-en")

    fun isInstalled(): Boolean {
        val dir = modelDir()
        return FILES.all { (name, minBytes) ->
            val f = File(dir, name)
            f.exists() && f.length() >= minBytes
        }
    }

    fun download() {
        if (mutableState.value.downloading || isInstalled()) return
        scope.launch {
            mutableState.value = SherpaModelState(downloading = true)
            try {
                val dir = modelDir().apply { mkdirs() }
                val totalBytes = FILES.sumOf { it.second }
                var doneBytes = 0L
                for ((name, minBytes) in FILES) {
                    val target = File(dir, name)
                    if (target.exists() && target.length() >= minBytes) {
                        doneBytes += target.length()
                        continue
                    }
                    val part = File(dir, "$name.part")
                    val request = Request.Builder().url(BASE_URL + name).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code} for $name")
                        }
                        val body = response.body ?: throw IllegalStateException("Empty body for $name")
                        body.byteStream().use { input ->
                            FileOutputStream(part).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    doneBytes += read
                                    mutableState.value = SherpaModelState(
                                        downloading = true,
                                        progress = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
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
                mutableState.value = SherpaModelState(installed = isInstalled())
                if (!mutableState.value.installed) {
                    mutableState.value = SherpaModelState(error = "Download finished but files look incomplete. Try again.")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Sherpa model download failed", t)
                mutableState.value = SherpaModelState(
                    error = "Download failed: ${t.message ?: t::class.java.simpleName}"
                )
            }
        }
    }

    companion object {
        private const val TAG = "SherpaModelStore"
        private const val BASE_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/"

        // File name -> expected size in bytes (download is incomplete below this).
        val FILES: List<Pair<String, Long>> = listOf(
            "encoder-epoch-99-avg-1.int8.onnx" to 181_895_032L,
            "decoder-epoch-99-avg-1.onnx" to 13_876_452L,
            "joiner-epoch-99-avg-1.int8.onnx" to 3_228_404L,
            "tokens.txt" to 56_317L
        )
        const val TOTAL_SIZE_MB = 190
    }
}
