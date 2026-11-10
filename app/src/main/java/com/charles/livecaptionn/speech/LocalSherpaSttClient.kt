package com.charles.livecaptionn.speech

import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Streaming zh-en recognition via sherpa-onnx (Zipformer transducer).
// One bilingual model covers Chinese and English, including mixed speech.
class LocalSherpaSttClient(private val store: SherpaModelStore) : StreamingSttClient {

    private var recognizer: OnlineRecognizer? = null
    private val recognizerMutex = Mutex()

    override suspend fun openSession(languageCode: String, sampleRate: Int): StreamingSttSession? {
        val base = languageCode.lowercase().substringBefore('-').substringBefore('_')
        if (base != "zh" && base != "en") return null
        if (!store.isInstalled()) return null
        val rec = recognizer() ?: return null
        return try {
            SherpaStreamingSession(rec)
        } catch (t: Throwable) {
            Log.w(TAG, "openSession failed", t)
            null
        }
    }

    private suspend fun recognizer(): OnlineRecognizer? {
        recognizer?.let { return it }
        return recognizerMutex.withLock {
            recognizer?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                try {
                    val dir = store.modelDir()
                    val config = OnlineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = File(dir, "encoder-epoch-99-avg-1.int8.onnx").absolutePath,
                                decoder = File(dir, "decoder-epoch-99-avg-1.onnx").absolutePath,
                                joiner = File(dir, "joiner-epoch-99-avg-1.int8.onnx").absolutePath
                            ),
                            tokens = File(dir, "tokens.txt").absolutePath,
                            numThreads = 2,
                            modelType = "zipformer"
                        ),
                        endpointConfig = EndpointConfig(
                            rule1 = EndpointRule(false, 2.4f, 0.0f),
                            rule2 = EndpointRule(true, 1.0f, 0.0f),
                            rule3 = EndpointRule(false, 0.0f, 20.0f)
                        ),
                        enableEndpoint = true,
                        decodingMethod = "greedy_search"
                    )
                    OnlineRecognizer(config = config).also { recognizer = it }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to create Sherpa recognizer", t)
                    null
                }
            }
        }
    }

    companion object {
        private const val TAG = "LocalSherpaSttClient"
    }
}

// Live Sherpa session. Not thread-safe — call from a single worker coroutine,
// same contract as VoskStreamingSession.
class SherpaStreamingSession internal constructor(
    private val recognizer: OnlineRecognizer
) : StreamingSttSession {

    private val stream: OnlineStream = recognizer.createStream()
    @Volatile private var closed = false

    override fun feed(pcm: ByteArray, length: Int): VoskStreamingSession.FeedResult {
        if (closed) return VoskStreamingSession.FeedResult("", accepted = false)
        return try {
            val count = (length.coerceAtMost(pcm.size)) / 2
            val samples = FloatArray(count)
            var j = 0
            for (i in 0 until count) {
                val low = pcm[j].toInt() and 0xFF
                val high = pcm[j + 1].toInt() shl 8
                samples[i] = (high or low) / 32768f
                j += 2
            }
            stream.acceptWaveform(samples, 16_000)
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val text = recognizer.getResult(stream).text.trim()
            if (recognizer.isEndpoint(stream)) {
                recognizer.reset(stream)
                VoskStreamingSession.FeedResult(text, accepted = true)
            } else {
                VoskStreamingSession.FeedResult(text, accepted = false)
            }
        } catch (t: Throwable) {
            Log.w("SherpaStreamingSession", "feed failed", t)
            VoskStreamingSession.FeedResult("", accepted = false)
        }
    }

    override fun finish(): String {
        if (closed) return ""
        return try {
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } catch (t: Throwable) {
            Log.w("SherpaStreamingSession", "finish failed", t)
            ""
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { stream.release() } catch (_: Throwable) {}
    }
}
