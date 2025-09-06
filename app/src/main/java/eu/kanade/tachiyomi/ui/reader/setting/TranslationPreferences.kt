package eu.kanade.tachiyomi.ui.reader.setting

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun autoTranslateOnDownload() = preferenceStore.getBoolean("pref_auto_translate_on_download", false)

    fun translateFrom() = preferenceStore.getString("pref_translate_from", "latin")

    fun translateTo() = preferenceStore.getString("pref_translate_to", "en")

    fun translationModel() = preferenceStore.getString("pref_translation_model", "google")

    fun fontFamily() = preferenceStore.getString("pref_font_family", "Default")

    fun fontSize() = preferenceStore.getFloat("pref_font_size", 16f)

    fun fontColor() = preferenceStore.getInt("pref_font_color", 0xFF000000.toInt())

    fun backgroundColor() = preferenceStore.getInt("pref_background_color", 0xFFFFFFFF.toInt())
}
