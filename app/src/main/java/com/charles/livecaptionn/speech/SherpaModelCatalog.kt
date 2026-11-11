package com.charles.livecaptionn.speech

enum class SherpaModelType { TRANSDUCER, PARAFORMER, TONE_CTC }

data class SherpaFileSpec(
    /** File name inside the HuggingFace repo (also the local file name). */
    val name: String,
    /** Exact size in bytes — a smaller local file counts as incomplete. */
    val sizeBytes: Long
)

data class SherpaModel(
    val id: String,
    val displayName: String,
    /** Base language codes this model can recognize (e.g. "zh", "en"). */
    val languages: Set<String>,
    val type: SherpaModelType,
    /** Value for OnlineModelConfig.modelType. Always "" (auto-detect from the
     *  model's own metadata): hardcoding "zipformer" makes the native runtime
     *  abort on zipformer2 models — that was the Start force-close bug. */
    val modelType: String,
    val hfRepo: String,
    val files: List<SherpaFileSpec>,
    /** Optional sentencepiece model for cjkchar+bpe models. */
    val bpeFile: String? = null,
    /** Short user-facing description of the model's strengths. */
    val description: String = ""
) {
    val totalSizeBytes: Long get() = files.sumOf { it.sizeBytes }
    val totalSizeMb: Int get() = (totalSizeBytes / 1_000_000).toInt()
    fun fileUrl(fileName: String): String =
        "https://huggingface.co/$hfRepo/resolve/main/$fileName"
}

// Verified streaming sherpa-onnx models, downloadable file-by-file from
// HuggingFace. Languages without a streaming model (de, es, pt, it, hi, tr)
// are not listed — the remote/built-in engine covers those.
object SherpaModelCatalog {

    val ENGLISH_2023 = SherpaModel(
        id = "en-2023",
        displayName = "English (Fast Streaming)",
        languages = setOf("en"),
        type = SherpaModelType.TRANSDUCER,
        modelType = "",
        hfRepo = "csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26",
        files = listOf(
            SherpaFileSpec("encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx", 71_083_163),
            SherpaFileSpec("decoder-epoch-99-avg-1-chunk-16-left-128.onnx", 2_092_621),
            SherpaFileSpec("joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx", 259_335),
            SherpaFileSpec("tokens.txt", 5_048)
        ),
        description = "Compact dedicated English streaming Zipformer with low memory and fast response."
    )

    val BILINGUAL_ZH_EN = SherpaModel(
        id = "bilingual-zh-en",
        displayName = "Chinese + English (bilingual)",
        languages = setOf("zh", "en"),
        type = SherpaModelType.TRANSDUCER,
        modelType = "",
        hfRepo = "csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20",
        files = listOf(
            SherpaFileSpec("encoder-epoch-99-avg-1.int8.onnx", 181_895_032),
            SherpaFileSpec("decoder-epoch-99-avg-1.onnx", 13_876_452),
            SherpaFileSpec("joiner-epoch-99-avg-1.int8.onnx", 3_228_404),
            SherpaFileSpec("tokens.txt", 56_317)
        ),
        description = "Bilingual Mandarin Chinese and English model; ideal for mixed-language streams."
    )

    val CHINESE_2025 = SherpaModel(
        id = "zh-2025",
        displayName = "Chinese (2025, Chinese only)",
        languages = setOf("zh"),
        type = SherpaModelType.TRANSDUCER,
        modelType = "",
        hfRepo = "csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30",
        files = listOf(
            SherpaFileSpec("encoder.int8.onnx", 161_141_793),
            SherpaFileSpec("decoder.onnx", 5_165_083),
            SherpaFileSpec("joiner.int8.onnx", 1_033_416),
            SherpaFileSpec("tokens.txt", 20_628)
        ),
        description = "High-accuracy 2025 Mandarin Zipformer model optimized for pure Chinese media."
    )

    val KOREAN = SherpaModel(
        id = "ko-2024",
        displayName = "Korean",
        languages = setOf("ko"),
        type = SherpaModelType.TRANSDUCER,
        modelType = "",
        hfRepo = "k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16",
        files = listOf(
            SherpaFileSpec("encoder-epoch-99-avg-1.int8.onnx", 126_968_852),
            SherpaFileSpec("decoder-epoch-99-avg-1.int8.onnx", 2_844_692),
            SherpaFileSpec("joiner-epoch-99-avg-1.int8.onnx", 2_581_421),
            SherpaFileSpec("tokens.txt", 60_246)
        ),
        description = "Dedicated Korean streaming Zipformer tuned for broadcasts, dramas, and talks."
    )

    val FRENCH = SherpaModel(
        id = "fr-2023",
        displayName = "French",
        languages = setOf("fr"),
        type = SherpaModelType.TRANSDUCER,
        modelType = "",
        hfRepo = "shaojieli/sherpa-onnx-streaming-zipformer-fr-2023-04-14",
        files = listOf(
            SherpaFileSpec("encoder-epoch-29-avg-9-with-averaged-model.int8.onnx", 126_655_903),
            SherpaFileSpec("decoder-epoch-29-avg-9-with-averaged-model.int8.onnx", 1_307_157),
            SherpaFileSpec("joiner-epoch-29-avg-9-with-averaged-model.int8.onnx", 259_572),
            SherpaFileSpec("tokens.txt", 4_819)
        ),
        description = "Dedicated French streaming model for videos, podcasts, and conversation."
    )

    val MULTILINGUAL = SherpaModel(
        id = "multi-2025",
        displayName = "Multilingual (ja · ar · id · th · vi · ru)",
        languages = setOf("ja", "ar", "id", "th", "vi", "ru", "en", "zh"),
        type = SherpaModelType.TRANSDUCER,
        modelType = "",
        hfRepo = "csukuangfj/sherpa-onnx-streaming-zipformer-ar_en_id_ja_ru_th_vi_zh-2025-02-10",
        files = listOf(
            SherpaFileSpec("encoder-epoch-75-avg-11-chunk-16-left-128.int8.onnx", 296_583_597),
            SherpaFileSpec("decoder-epoch-75-avg-11-chunk-16-left-128.onnx", 33_837_085),
            SherpaFileSpec("joiner-epoch-75-avg-11-chunk-16-left-128.int8.onnx", 8_257_421),
            SherpaFileSpec("tokens.txt", 195_244),
            SherpaFileSpec("bpe.model", 476_049)
        ),
        bpeFile = "bpe.model",
        description = "8-language streaming pack covering Japanese, Arabic, Indonesian, Thai, Vietnamese, Russian, Chinese, and English."
    )

    val RUSSIAN_TONE = SherpaModel(
        id = "ru-tone",
        displayName = "Russian",
        languages = setOf("ru"),
        type = SherpaModelType.TONE_CTC,
        modelType = "",
        hfRepo = "csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08",
        files = listOf(
            SherpaFileSpec("model.onnx", 144_193_702),
            SherpaFileSpec("tokens.txt", 202)
        ),
        description = "Dedicated T-One CTC streaming model for Russian."
    )

    val CANTONESE = SherpaModel(
        id = "yue-paraformer",
        displayName = "Cantonese + Chinese + English",
        languages = setOf("yue", "zh", "en"),
        type = SherpaModelType.PARAFORMER,
        modelType = "",
        hfRepo = "csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en",
        files = listOf(
            SherpaFileSpec("encoder.int8.onnx", 166_362_800),
            SherpaFileSpec("decoder.int8.onnx", 72_062_549),
            SherpaFileSpec("tokens.txt", 81_289)
        ),
        description = "Trilingual Paraformer model supporting Cantonese, Mandarin, and English."
    )

    val ALL: List<SherpaModel> = listOf(
        ENGLISH_2023, BILINGUAL_ZH_EN, CHINESE_2025, KOREAN, FRENCH, RUSSIAN_TONE, CANTONESE, MULTILINGUAL
    )

    fun byId(id: String): SherpaModel? = ALL.firstOrNull { it.id == id }

    /** Models that can serve [languageCode], best first (dedicated before multilingual). */
    fun modelsFor(languageCode: String): List<SherpaModel> {
        val base = languageCode.lowercase().substringBefore('-').substringBefore('_')
        fun rank(model: SherpaModel): Int = when {
            model.id == MULTILINGUAL.id -> 2
            model.id == CANTONESE.id && base != "yue" -> 1
            else -> 0
        }
        return ALL.filter { base in it.languages }
            .sortedWith(compareBy({ rank(it) }, { it.languages.size }))
    }

    /** The recommended model for a language (the one setup offers first). */
    fun defaultFor(languageCode: String): SherpaModel? = modelsFor(languageCode).firstOrNull()

    /** Every language with an on-device model: code -> display name. */
    val LANGUAGES: List<Pair<String, String>> = listOf(
        "zh" to "Chinese",
        "en" to "English",
        "ko" to "Korean",
        "ja" to "Japanese",
        "fr" to "French",
        "ru" to "Russian",
        "yue" to "Cantonese",
        "ar" to "Arabic",
        "id" to "Indonesian",
        "th" to "Thai",
        "vi" to "Vietnamese"
    )
}
