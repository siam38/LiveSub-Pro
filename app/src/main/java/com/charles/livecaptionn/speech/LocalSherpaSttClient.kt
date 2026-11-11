package com.charles.livecaptionn.speech

import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// How fast a spoken segment is finalized into a subtitle cue. A cue is only
// translated once its segment ends, so these two values are the subtitle
// latency: PAUSE_SECONDS of trailing silence ends a segment at a natural
// phrase pause, and MAX_SEGMENT_SECONDS caps continuous speech (rule3's
// min utterance length — sherpa's own default there is 20s, which made
// unbroken dialogue wait up to 20s for its first subtitle).
internal object SherpaEndpointTuning {
    const val PAUSE_SECONDS = 0.5f
    const val MAX_SEGMENT_SECONDS = 7.0f
}

// Streaming recognition via sherpa-onnx. Picks the best installed model for
// the requested language from SherpaModelCatalog.
class LocalSherpaSttClient(private val store: SherpaModelStore) : StreamingSttClient {

    private var recognizer: OnlineRecognizer? = null
    private var recognizerModelId: String? = null
    private val recognizerMutex = Mutex()

    override suspend fun openSession(languageCode: String, sampleRate: Int): StreamingSttSession? {
        val model = store.installedModelFor(languageCode) ?: return null
        val rec = recognizerFor(model) ?: return null
        return try {
            SherpaStreamingSession(rec)
        } catch (t: Throwable) {
            Log.w(TAG, "openSession failed", t)
            null
        }
    }

    private suspend fun recognizerFor(model: SherpaModel): OnlineRecognizer? {
        if (recognizer != null && recognizerModelId == model.id) return recognizer
        return recognizerMutex.withLock {
            if (recognizer != null && recognizerModelId == model.id) return@withLock recognizer
            withContext(Dispatchers.IO) {
                try {
                    val dir = store.modelDir(model)
                    fun path(prefix: String): String =
                        File(dir, model.files.first { it.name.startsWith(prefix) }.name).absolutePath
                    val modelConfig = when (model.type) {
                        SherpaModelType.TRANSDUCER -> OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = path("encoder"),
                                decoder = path("decoder"),
                                joiner = path("joiner")
                            ),
                            tokens = File(dir, "tokens.txt").absolutePath,
                            numThreads = 2,
                            modelType = model.modelType,
                            modelingUnit = if (model.bpeFile != null) "cjkchar+bpe" else "",
                            bpeVocab = model.bpeFile?.let { File(dir, it).absolutePath } ?: ""
                        )
                        SherpaModelType.PARAFORMER -> OnlineModelConfig(
                            paraformer = OnlineParaformerModelConfig(
                                encoder = path("encoder"),
                                decoder = path("decoder")
                            ),
                            tokens = File(dir, "tokens.txt").absolutePath,
                            numThreads = 2,
                            modelType = model.modelType
                        )
                        SherpaModelType.TONE_CTC -> OnlineModelConfig(
                            toneCtc = OnlineToneCtcModelConfig(
                                model = File(dir, "model.onnx").absolutePath
                            ),
                            tokens = File(dir, "tokens.txt").absolutePath,
                            numThreads = 2,
                            modelType = model.modelType
                        )
                    }
                    val config = OnlineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                        modelConfig = modelConfig,
                        endpointConfig = EndpointConfig(
                            rule1 = EndpointRule(false, 2.4f, 0.0f),
                            rule2 = EndpointRule(true, SherpaEndpointTuning.PAUSE_SECONDS, 0.0f),
                            rule3 = EndpointRule(false, 0.0f, SherpaEndpointTuning.MAX_SEGMENT_SECONDS)
                        ),
                        enableEndpoint = true,
                        decodingMethod = "greedy_search"
                    )
                    try { recognizer?.release() } catch (_: Throwable) {}
                    OnlineRecognizer(config = config).also {
                        recognizer = it
                        recognizerModelId = model.id
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to create Sherpa recognizer for ${model.id}", t)
                    null
                }
            }
        }
    }

    companion object {
        private const val TAG = "LocalSherpaSttClient"
    }
}

// Live Sherpa session. FeedResult is shared with the streaming engine:
// accepted=true means an endpoint (pause) finalized the current segment.
// Not thread-safe — call from a single worker coroutine.
class SherpaStreamingSession internal constructor(
    private val recognizer: OnlineRecognizer
) : StreamingSttSession {

    private val stream: OnlineStream = recognizer.createStream()
    @Volatile private var closed = false

    override fun feed(pcm: ByteArray, length: Int): SttFeedResult {
        if (closed) return SttFeedResult("", accepted = false)
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
                SttFeedResult(text, accepted = true)
            } else {
                SttFeedResult(text, accepted = false)
            }
        } catch (t: Throwable) {
            Log.w("SherpaStreamingSession", "feed failed", t)
            SttFeedResult("", accepted = false)
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
