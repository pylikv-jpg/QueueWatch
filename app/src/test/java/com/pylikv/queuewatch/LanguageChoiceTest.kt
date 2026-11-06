package com.pylikv.queuewatch

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChoiceTest {
    @Test fun firstLaunchUsesEachSupportedDeviceLanguage() {
        for (language in LanguageChoice.supported) assertEquals(language, LanguageChoice.resolve(null, language))
    }
    @Test fun regionalVariantsUseTheirSupportedBaseLanguage() {
        assertEquals("en", LanguageChoice.resolve("", "en-GB"))
        assertEquals("de", LanguageChoice.resolve("", "de-DE"))
        assertEquals("uk", LanguageChoice.resolve("", "uk-UA"))
        assertEquals("be", LanguageChoice.resolve("", "be-BY"))
        assertEquals("lt", LanguageChoice.resolve("", "lt_LT"))
    }
    @Test fun unsupportedDeviceLanguageFallsBackToEnglish() {
        for (language in listOf("fr-FR", "es-ES", "zh-CN", "", "und"))
            assertEquals("en", LanguageChoice.resolve(null, language))
    }
    @Test fun explicitChoiceIsRetainedInsteadOfFollowingDevice() {
        assertEquals("pl", LanguageChoice.resolve("pl", "de-DE"))
        assertEquals("ru", LanguageChoice.resolve("ru", "en-US"))
    }
    @Test fun returningToDeviceModeResolvesAgain() {
        assertEquals("lv", LanguageChoice.resolve("", "lv-LV"))
        assertEquals("en", LanguageChoice.resolve("", "fr-FR"))
    }
}
