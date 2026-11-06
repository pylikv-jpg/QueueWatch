package com.pylikv.queuewatch

import java.util.Locale

object LanguageChoice {
    val supported = listOf("en", "de", "pl", "uk", "be", "lt", "lv", "et", "ru")
    fun legacyChoiceToMigrate(systemChoice: String, legacyChoice: String?): String? =
        legacyChoice?.takeIf { systemChoice.isEmpty() && it in supported }

    fun resolve(selectedTag: String?, deviceTag: String?): String {
        val tag = selectedTag?.takeIf { it.isNotBlank() } ?: deviceTag.orEmpty()
        val language = Locale.forLanguageTag(tag.replace('_', '-')).language
        return language.takeIf { it in supported } ?: "en"
    }
}
