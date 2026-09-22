package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.ai.ImageTranslatorPipeline
import eu.kanade.tachiyomi.data.translation.ai.ModelManager
import eu.kanade.tachiyomi.data.translation.engine.GeminiTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.MLKitTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.TranslatorEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.InputStream

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val downloadProvider: DownloadProvider,
    private val networkHelper: NetworkHelper,
    private val sourceManager: SourceManager,
    private val translationPreferences: TranslationPreferences,
    private val modelManager: ModelManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pipeline = ImageTranslatorPipeline(modelManager, translationPreferences)

    private val _stateMap = MutableStateFlow<Map<Long, TranslationProgressState>>(emptyMap())
    val stateMap: StateFlow<Map<Long, TranslationProgressState>> = _stateMap.asStateFlow()

    fun getTranslationState(chapterId: Long): TranslationProgressState {
        return _stateMap.value[chapterId] ?: TranslationProgressState(chapterId)
    }

    fun hasTranslation(manga: Manga, chapter: Chapter): Boolean {
        val dir = getTranslationDir(manga, chapter)
        return dir != null && dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
    }

    fun getTranslationDir(manga: Manga, chapter: Chapter): UniFile? {
        val source = sourceManager.get(manga.source) ?: return null
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        ) ?: return null

        return if (chapterDir.isDirectory) {
            chapterDir.createDirectory("translations")
        } else {
            val parent = chapterDir.parent ?: return null
            parent.createDirectory("${chapterDir.nameWithoutExtension}_translations")
        }
    }

    fun getTranslatedPageFile(manga: Manga, chapter: Chapter, pageFileName: String): UniFile? {
        val dir = getTranslationDir(manga, chapter) ?: return null
        return dir.findFile(pageFileName) ?: dir.findFile("${pageFileName.substringBeforeLast(".")}.jpg")
    }

    fun startTranslation(manga: Manga, chapter: Chapter) {
        val chapterId = chapter.id
        val source = sourceManager.get(manga.source) ?: return

        updateState(chapterId) {
            it.copy(
                status = TranslationStatus.QUEUE,
                errorLogs = emptyList(),
            )
        }

        scope.launch {
            processChapterTranslation(manga, chapter, source)
        }
    }

    private suspend fun processChapterTranslation(manga: Manga, chapter: Chapter, source: Source) {
        val chapterId = chapter.id
        val translationDir = getTranslationDir(manga, chapter)
        if (translationDir == null) {
            updateState(chapterId) {
                it.copy(
                    status = TranslationStatus.ERROR,
                    errorLogs = it.errorLogs + "Failed to create translation directory",
                )
            }
            return
        }

        updateState(chapterId) {
            it.copy(status = TranslationStatus.TRANSLATING)
        }

        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        )

        if (chapterDir == null) {
            updateState(chapterId) {
                it.copy(
                    status = TranslationStatus.ERROR,
                    errorLogs = it.errorLogs + "Downloaded chapter not found",
                )
            }
            return
        }

        val engine = getActiveEngine()
        val fromLang = translationPreferences.sourceLanguage.get()
        val toLang = translationPreferences.targetLanguage.get()

        var done = 0
        var failed = 0

        if (chapterDir.isDirectory) {
            val filesToProcess = chapterDir.listFiles()?.filter { file ->
                file.isFile && !file.name.isNullOrBlank() &&
                    !file.name.equals("translations", ignoreCase = true) &&
                    (
                        file.name.endsWith(".jpg", true) || file.name.endsWith(".png", true) ||
                            file.name.endsWith(".webp", true)
                        )
            } ?: emptyList()

            val total = filesToProcess.size
            updateState(chapterId) {
                it.copy(
                    totalCount = total,
                    queueCount = total,
                    doneCount = 0,
                    failedCount = 0,
                )
            }

            if (total == 0) {
                updateState(chapterId) {
                    it.copy(
                        status = TranslationStatus.TRANSLATED,
                        currentStage = "Done",
                    )
                }
                return
            }

            for (file in filesToProcess) {
                val fileName = file.name ?: continue
                try {
                    val inputStream: InputStream? = file.openInputStream()
                    if (inputStream != null) {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        if (bitmap != null) {
                            val translatedBitmap = pipeline.processImage(
                                originalBitmap = bitmap,
                                engine = engine,
                                fromLang = fromLang,
                                toLang = toLang,
                                onStageChanged = { stageName ->
                                    updateState(chapterId) { state ->
                                        state.copy(currentStage = stageName)
                                    }
                                },
                            )

                            val outFile = translationDir.createFile(fileName)
                            if (outFile != null) {
                                val outStream = outFile.openOutputStream()
                                translatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outStream)
                                outStream.flush()
                                outStream.close()
                            }
                            done++
                        } else {
                            failed++
                        }
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed translating image $fileName" }
                    failed++
                    updateState(chapterId) { state ->
                        state.copy(errorLogs = state.errorLogs + "Failed $fileName: ${e.message}")
                    }
                }

                updateState(chapterId) { state ->
                    state.copy(
                        doneCount = done,
                        failedCount = failed,
                        queueCount = (total - done - failed).coerceAtLeast(0),
                    )
                }
            }
        } else if (chapterDir.isFile) {
            // Process CBZ archive file
            val entries = mutableListOf<Pair<String, ByteArray>>()
            try {
                val zipInputStream = java.util.zip.ZipInputStream(chapterDir.openInputStream())
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory &&
                        (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".webp", true))
                    ) {
                        val bytes = zipInputStream.readBytes()
                        entries.add(name to bytes)
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed reading CBZ archive $chapterDir" }
            }

            val total = entries.size
            updateState(chapterId) {
                it.copy(
                    totalCount = total,
                    queueCount = total,
                    doneCount = 0,
                    failedCount = 0,
                )
            }

            entries.forEachIndexed { index, (name, bytes) ->
                val fileName = "${index + 1}.jpg"
                try {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val translatedBitmap = pipeline.processImage(
                            originalBitmap = bitmap,
                            engine = engine,
                            fromLang = fromLang,
                            toLang = toLang,
                            onStageChanged = { stageName ->
                                updateState(chapterId) { state ->
                                    state.copy(currentStage = stageName)
                                }
                            },
                        )

                        val outFile = translationDir.createFile(fileName)
                        if (outFile != null) {
                            val outStream = outFile.openOutputStream()
                            translatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outStream)
                            outStream.flush()
                            outStream.close()
                        }
                        done++
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed translating archive image $name" }
                    failed++
                    updateState(chapterId) { state ->
                        state.copy(errorLogs = state.errorLogs + "Failed $name: ${e.message}")
                    }
                }

                updateState(chapterId) { state ->
                    state.copy(
                        doneCount = done,
                        failedCount = failed,
                        queueCount = (total - done - failed).coerceAtLeast(0),
                    )
                }
            }
        }

        updateState(chapterId) { state ->
            state.copy(
                status = if (done > 0) TranslationStatus.TRANSLATED else TranslationStatus.ERROR,
                currentStage = if (done > 0) "Completed" else "Failed",
            )
        }
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter) {
        val dir = getTranslationDir(manga, chapter)
        dir?.delete()
        _stateMap.value = _stateMap.value - chapter.id
    }

    private fun getActiveEngine(): TranslatorEngine {
        return when (translationPreferences.engineType.get()) {
            TranslationPreferences.ENGINE_GEMINI -> GeminiTranslatorEngine(
                networkHelper = networkHelper,
                apiKeyProvider = { translationPreferences.geminiApiKey.get() },
                modelProvider = { translationPreferences.geminiModel.get() },
            )
            TranslationPreferences.ENGINE_OPENROUTER -> OpenRouterTranslatorEngine(
                networkHelper = networkHelper,
                apiKeyProvider = { translationPreferences.openRouterApiKey.get() },
                modelProvider = { translationPreferences.openRouterModel.get() },
            )
            TranslationPreferences.ENGINE_MLKIT -> MLKitTranslatorEngine(
                networkHelper = networkHelper,
            )
            else -> GoogleTranslatorEngine(networkHelper)
        }
    }

    private fun updateState(chapterId: Long, transform: (TranslationProgressState) -> TranslationProgressState) {
        val current = getTranslationState(chapterId)
        _stateMap.value = _stateMap.value + (chapterId to transform(current))
    }
}
