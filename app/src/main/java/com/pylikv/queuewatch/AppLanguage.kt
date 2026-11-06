package com.pylikv.queuewatch

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

class QueueWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        L.initialize(this)
        AppLanguage.migrateLegacySelection(this)
    }
}

object AppLanguage {
    private const val PREFS = "queuewatch_language"
    private const val KEY = "selected_language"

    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun migrateLegacySelection(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        LanguageChoice.legacyChoiceToMigrate(manager.applicationLocales.toLanguageTags(),
            preferences.getString(KEY, null))?.let { manager.applicationLocales = LocaleList.forLanguageTags(it) }
        preferences.edit().remove(KEY).commit()
    }

    fun locale(context: Context): Locale {
        val device = Resources.getSystem().configuration.locales[0]?.toLanguageTag()
        return Locale.forLanguageTag(LanguageChoice.resolve(selected(context), device))
    }

    fun wrap(context: Context): Context = forLocale(context, locale(context))

    fun forLocale(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    fun select(context: Context, tag: String) {
        require(tag.isEmpty() || tag in LanguageChoice.supported)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
    }
}

/** Application context only; the locale is resolved afresh, including in services. */
object L {
    private lateinit var app: Context
    fun initialize(context: Context) { app = context.applicationContext }
    fun text(id: Int, vararg args: Any): String = AppLanguage.wrap(app).getString(id, *args)
    fun locale(): Locale = AppLanguage.locale(app)

    private val persistedIds by lazy { LocalizedMessageIds.values }
    private val storedMessages by lazy {
        buildMap<String, Int> {
            for (tag in LanguageChoice.supported) {
                val localized = AppLanguage.forLocale(app, Locale.forLanguageTag(tag))
                persistedIds.forEach { id ->
                    val template = localized.getString(id)
                    if (!template.contains("%")) put(template, id)
                }
            }
        }
    }
    fun stored(value: String): String {
        storedMessages[value]?.let { return text(it) }
        // A position warning saved before changing the language keeps its threshold.
        for (tag in LanguageChoice.supported) {
            val template = AppLanguage.forLocale(app, Locale.forLanguageTag(tag))
                .getString(R.string.position_reached)
            val pattern = template.split("%1\$d").joinToString("(\\d+)") { Regex.escape(it) }
            Regex(pattern).matchEntire(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.let { return text(R.string.position_reached, it) }
        }
        return value
    }

    fun checkpoint(canonical: String): String = when (canonical) {
        "Бенякони" -> text(R.string.cp_benyakoni)
        "Берестовица" -> text(R.string.cp_berestovitsa)
        "Брест" -> text(R.string.cp_brest)
        "Брузги" -> text(R.string.cp_bruzgi)
        "Григоровщина" -> text(R.string.cp_grigorovshchina)
        "Каменный Лог" -> text(R.string.cp_kamenny_log)
        "Козловичи" -> text(R.string.cp_kozlovichi)
        else -> canonical
    }
}
