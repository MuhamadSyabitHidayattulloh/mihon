package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.NetworkHelper

class MLKitTranslatorEngine(
    private val networkHelper: NetworkHelper,
) : TranslatorEngine {

    private val fallbackEngine = GoogleTranslatorEngine(networkHelper)

    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return text
        return try {
            fallbackEngine.translate(text, from, to)
        } catch (e: Exception) {
            text
        }
    }
}
