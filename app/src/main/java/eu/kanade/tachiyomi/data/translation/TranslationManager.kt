
package eu.kanade.tachiyomi.data.translation

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.tachiyomi.data.translation.model.TranslationCache
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File

class TranslationManager(
    private val context: Context,
) {

    private val cache = TranslationCache(context)

    fun getTranslator(from: String, to: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(to)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                logcat { "Translator model downloaded" }
            }
            .addOnFailureListener {
                logcat(it) { "Failed to download translator model" }
            }
    }

    fun translate(text: String, from: String, to: String, onResult: (String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(to)
            .build()
        val translator = Translation.getClient(options)
        translator.translate(text)
            .addOnSuccessListener {
                onResult(it)
            }
            .addOnFailureListener {
                logcat(it) { "Failed to translate text" }
            }
    }

    fun getFromCache(pageId: Long): String? {
        return cache.get(pageId)
    }

    fun putInCache(pageId: Long, translation: String) {
        cache.put(pageId, translation)
    }
}
