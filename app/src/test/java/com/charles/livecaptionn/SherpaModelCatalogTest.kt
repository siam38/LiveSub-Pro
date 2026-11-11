package com.charles.livecaptionn

import com.charles.livecaptionn.speech.SherpaModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaModelCatalogTest {

    @Test
    fun defaultFor_english_returnsDedicatedCompactModel() {
        val model = SherpaModelCatalog.defaultFor("en")
        assertNotNull(model)
        assertEquals("en-2023", model?.id)
        assertTrue("Expected dedicated English model < 90 MB", (model?.totalSizeMb ?: 999) < 90)
    }

    @Test
    fun modelsFor_english_includesDedicatedAndBilingualFallbacks() {
        val models = SherpaModelCatalog.modelsFor("en")
        val ids = models.map { it.id }
        assertEquals("en-2023", ids.first())
        assertTrue(ids.contains("bilingual-zh-en"))
        assertTrue(ids.contains("multi-2025"))
    }

    @Test
    fun defaultFor_chinese_returnsBilingualOrDedicatedChineseModel() {
        val model = SherpaModelCatalog.defaultFor("zh")
        assertNotNull(model)
        assertTrue(model!!.id == "zh-2025" || model.id == "bilingual-zh-en")
    }

    @Test
    fun allModels_haveValidHuggingFaceUrlsAndPositiveSizes() {
        SherpaModelCatalog.ALL.forEach { model ->
            assertTrue(model.files.isNotEmpty())
            assertTrue(model.totalSizeBytes > 1_000_000L)
            model.files.forEach { spec ->
                val url = model.fileUrl(spec.name)
                assertTrue(url.startsWith("https://huggingface.co/"))
                assertTrue(spec.sizeBytes > 0L)
            }
        }
    }
}

