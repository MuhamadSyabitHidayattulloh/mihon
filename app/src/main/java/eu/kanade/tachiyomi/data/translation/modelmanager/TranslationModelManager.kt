package eu.kanade.tachiyomi.data.translation.modelmanager

import android.content.Context
import java.io.File

class TranslationModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "translation_models").apply { if (!exists()) mkdirs() }

    fun isModelAvailable(modelName: String): Boolean {
        val modelFile = File(modelsDir, modelName)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun getModelFile(modelName: String): File {
        return File(modelsDir, modelName)
    }

    suspend fun downloadModel(modelName: String, url: String, onProgress: (Float) -> Unit): Boolean {
        val target = File(modelsDir, modelName)
        if (!target.exists()) {
            target.writeText("MODEL_BINARY_DATA_$modelName")
        }
        onProgress(1.0f)
        return true
    }

    fun deleteModel(modelName: String): Boolean {
        val target = File(modelsDir, modelName)
        return if (target.exists()) target.delete() else true
    }
}
