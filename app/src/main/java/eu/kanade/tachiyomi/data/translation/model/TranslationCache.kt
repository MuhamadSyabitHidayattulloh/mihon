
package eu.kanade.tachiyomi.data.translation.model

import android.content.Context
import java.io.File

class TranslationCache(
    private val context: Context,
) {

    private val cacheDir = File(context.cacheDir, "translations")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun get(pageId: Long): String? {
        val file = File(cacheDir, pageId.toString())
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    fun put(pageId: Long, translation: String) {
        val file = File(cacheDir, pageId.toString())
        file.writeText(translation)
    }
}
