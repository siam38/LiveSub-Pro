package com.charles.livecaptionn

import com.charles.livecaptionn.translation.MlKitLanguages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGatingTest {

    @Test
    fun indonesianTranslation_requiresPro() {
        assertTrue(MlKitLanguages.requiresPro("id"))
        assertTrue(MlKitLanguages.requiresPro("ID"))
    }

    @Test
    fun freeTierTranslationLanguages_doNotRequirePro() {
        for (code in MlKitLanguages.FREE_TIER) {
            assertFalse("$code is in the free tier", MlKitLanguages.requiresPro(code))
        }
    }

    @Test
    fun freeTier_isSubsetOfMlKitList() {
        val all = MlKitLanguages.LIST.map { it.code.lowercase() }.toSet()
        for (code in MlKitLanguages.FREE_TIER) {
            assertTrue("$code must be an ML Kit language", code.lowercase() in all)
        }
    }

    @Test
    fun everyProTranslationLanguage_isAnMlKitLanguage() {
        for (lang in MlKitLanguages.LIST) {

            val free = lang.code.lowercase() in MlKitLanguages.FREE_TIER
            assertTrue("${lang.code}", free == !MlKitLanguages.requiresPro(lang.code))
        }
    }

}
