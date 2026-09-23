package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.translation.engine.GeminiTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleWebTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.MlKitTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngine
import eu.kanade.tachiyomi.data.translation.model.TranslationTask
import eu.kanade.tachiyomi.data.translation.pipeline.TranslationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.translation.service.TranslationPreferences

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val translationStorage: TranslationStorage,
    private val translationPreferences: TranslationPreferences,
    private val modelManager: TranslationModelManager,
    private val downloadManager: DownloadManager,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pipeline = TranslationPipeline(modelManager)

    private val _queue = MutableStateFlow<List<TranslationTask>>(emptyList())
    val queue: StateFlow<List<TranslationTask>> = _queue.asStateFlow()

    private var isRunning = false

    fun isChapterTranslated(chapter: Chapter, manga: Manga, source: Source): Boolean {
        return translationStorage.isChapterTranslated(chapter, manga, source)
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source): Boolean {
        val result = translationStorage.deleteTranslation(chapter, manga, source)
        removeFromQueue(chapter)
        return result
    }

    fun enqueue(source: Source, manga: Manga, chapter: Chapter) {
        if (_queue.value.any { it.chapter.id == chapter.id }) return

        val task = TranslationTask(
            source = source,
            manga = manga,
            chapter = chapter,
            state = TranslationTask.State.QUEUE,
        )
        _queue.value = _queue.value + task
        startQueueIfNeeded()
    }

    fun removeFromQueue(chapter: Chapter) {
        _queue.value = _queue.value.filter { it.chapter.id != chapter.id }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    fun pauseQueue() {
        isRunning = false
    }

    fun resumeQueue() {
        if (!isRunning) {
            startQueueIfNeeded()
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val current = _queue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _queue.value = current
        }
    }

    private fun startQueueIfNeeded() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            while (isRunning && _queue.value.any { it.state == TranslationTask.State.QUEUE }) {
                val task = _queue.value.firstOrNull { it.state == TranslationTask.State.QUEUE } ?: break
                processTask(task)
            }
            isRunning = false
        }
    }

    private suspend fun processTask(task: TranslationTask) {
        task.state = TranslationTask.State.TRANSLATING
        task.progress = 0f
        updateQueueState()

        try {
            val engine = createEngine()
            val fromLang = translationPreferences.translateFrom().get()
            val toLang = translationPreferences.translateTo().get()

            // Retrieve downloaded chapter images
            val pageFiles = downloadManager.buildPageList(task.source, task.manga, task.chapter)
            if (pageFiles.isEmpty()) {
                task.state = TranslationTask.State.ERROR
                task.error = "No downloaded pages found"
                updateQueueState()
                return
            }

            val translatedBitmaps = mutableListOf<Bitmap>()
            val total = pageFiles.size

            for ((index, page) in pageFiles.withIndex()) {
                val bitmap = if (page.stream != null) {
                    BitmapFactory.decodeStream(page.stream!!())
                } else null

                if (bitmap != null) {
                    val translated = pipeline.processImage(bitmap, engine, fromLang, toLang)
                    translatedBitmaps.add(translated)
                }

                task.progress = (index + 1).toFloat() / total.toFloat()
                updateQueueState()
            }

            val success = translationStorage.saveTranslatedPages(
                task.chapter,
                task.manga,
                task.source,
                translatedBitmaps,
            )

            if (success) {
                task.state = TranslationTask.State.TRANSLATED
            } else {
                task.state = TranslationTask.State.ERROR
                task.error = "Failed to save translated pages"
            }
        } catch (e: Exception) {
            task.state = TranslationTask.State.ERROR
            task.error = e.localizedMessage ?: "Translation failed"
        } finally {
            updateQueueState()
        }
    }

    private fun createEngine(): TranslationEngine {
        return when (translationPreferences.translatorEngine().get()) {
            TranslationPreferences.ENGINE_MLKIT -> MlKitTranslationEngine()
            TranslationPreferences.ENGINE_GOOGLE_WEB -> GoogleWebTranslationEngine(okHttpClient)
            TranslationPreferences.ENGINE_GEMINI -> GeminiTranslationEngine(
                okHttpClient = okHttpClient,
                apiKey = translationPreferences.geminiApiKey().get(),
                model = translationPreferences.geminiModel().get(),
            )
            TranslationPreferences.ENGINE_OPENROUTER -> OpenRouterTranslationEngine(
                okHttpClient = okHttpClient,
                apiKey = translationPreferences.openRouterApiKey().get(),
                model = translationPreferences.openRouterModel().get(),
            )
            else -> MlKitTranslationEngine()
        }
    }

    private fun updateQueueState() {
        _queue.value = _queue.value.toList()
    }
}
