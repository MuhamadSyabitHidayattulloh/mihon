package eu.kanade.tachiyomi.data.translation.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class TextDetector(
    private val modelFile: File,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    init {
        if (modelFile.exists()) {
            session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        }
    }

    data class BoundingBox(
        val rect: RectF,
        val score: Float,
    )

    fun detect(bitmap: Bitmap, scoreThreshold: Float = 0.3f): List<BoundingBox> {
        val sess = session ?: return emptyList()

        val inputSize = 640
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val inputBuffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)
        val intValues = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // CHW format, normalized [0, 1]
        for (c in 0..2) {
            for (i in 0 until inputSize * inputSize) {
                val pixel = intValues[i]
                val v = when (c) {
                    0 -> (pixel shr 16 and 0xFF) / 255.0f
                    1 -> (pixel shr 8 and 0xFF) / 255.0f
                    else -> (pixel and 0xFF) / 255.0f
                }
                inputBuffer.put(v)
            }
        }
        inputBuffer.rewind()

        val inputNamesList = sess.inputNames.toList()
        val imgInputName = inputNamesList.getOrElse(0) { "images" }
        val imgTensor = OnnxTensor.createTensor(
            env,
            inputBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
        )

        val inputMap = mutableMapOf<String, OnnxTensor>(imgInputName to imgTensor)
        var sizeTensor: OnnxTensor? = null

        if (inputNamesList.size > 1) {
            val sizeInputName = inputNamesList[1]
            val origSizes = java.nio.LongBuffer.wrap(longArrayOf(bitmap.height.toLong(), bitmap.width.toLong()))
            sizeTensor = OnnxTensor.createTensor(env, origSizes, longArrayOf(1, 2))
            inputMap[sizeInputName] = sizeTensor
        }

        val boxes = mutableListOf<BoundingBox>()
        try {
            sess.run(inputMap).use { result ->
                val output = result.get(0).value
                if (output is Array<*>) {
                    val arr = output as Array<Array<FloatArray>>
                    val origWidth = bitmap.width.toFloat()
                    val origHeight = bitmap.height.toFloat()

                    for (box in arr[0]) {
                        if (box.size >= 5) {
                            val score = box[4]
                            if (score >= scoreThreshold) {
                                val x1 = box[0] / inputSize * origWidth
                                val y1 = box[1] / inputSize * origHeight
                                val x2 = box[2] / inputSize * origWidth
                                val y2 = box[3] / inputSize * origHeight

                                val rect = RectF(
                                    max(0f, min(x1, x2)),
                                    max(0f, min(y1, y2)),
                                    min(origWidth, max(x1, x2)),
                                    min(origHeight, max(y1, y2)),
                                )
                                boxes.add(BoundingBox(rect, score))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imgTensor.close()
            sizeTensor?.close()
        }

        return boxes
    }

    fun close() {
        session?.close()
    }
}
