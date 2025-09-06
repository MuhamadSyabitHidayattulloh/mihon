package eu.kanade.domain.translation.service

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(private val preferenceStore: PreferenceStore) {

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("autoTranslateAfterDownload", false)

    fun translateFrom() = preferenceStore.getString("translateFrom", "la")

    fun translateTo() = preferenceStore.getString("translateTo", "en")

    fun translationModel() = preferenceStore.getString("translationModel", "google")

    fun backgroundColor() = preferenceStore.getLong("backgroundColor", 0xFF000000)

    fun fontFamily() = preferenceStore.getString("fontFamily", "sans")

    fun fontSize() = preferenceStore.getInt("fontSize", 16)

    fun showTranslation() = preferenceStore.getBoolean("showTranslation", true)

    fun offsetX() = preferenceStore.getInt("offsetX", 0)

    fun offsetY() = preferenceStore.getInt("offsetY", 0)

    fun offsetZ() = preferenceStore.getInt("offsetZ", 0)
}
