package com.opentouch.sensorapp.ml

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.exp

data class ModelPrediction(
    val label: String,
    val confidence: Float,
    val probabilities: Map<String, Float>
)

/** Runs the bundled key/finger ONNX classifier. Call [run] off the main thread. */
class ModelRunner(
    context: Context,
    private val modelAssetPath: String = "models/key_finger.onnx",
    private val configAssetPath: String = "models/model_config.json"
) : Closeable {

    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val labels: List<String>
    private val inputWidth: Int
    private val inputHeight: Int
    private val mean: FloatArray
    private val std: FloatArray

    init {
        val config = JSONObject(
            appContext.assets.open(configAssetPath).bufferedReader().use { it.readText() }
        )
        labels = config.getJSONArray("labels").let { array ->
            List(array.length()) { index -> array.getString(index) }
        }
        inputWidth = config.getInt("input_width")
        inputHeight = config.getInt("input_height")
        mean = config.getJSONArray("mean").toFloatArray()
        std = config.getJSONArray("std").toFloatArray()
        require(labels.isNotEmpty()) { "Model must define at least one class label" }
        require(mean.size == 3 && std.size == 3) { "Model normalization must contain 3 RGB values" }

        val sessionOptions = OrtSession.SessionOptions()
        session = appContext.assets.open(modelAssetPath).use { modelStream ->
            environment.createSession(modelStream.readBytes(), sessionOptions)
        }
        inputName = session.inputNames.firstOrNull()
            ?: error("ONNX model does not define an input")
    }

    /**
     * Classifies one image. This method performs CPU work and must not run on
     * the Android main thread.
     */
    fun run(bitmap: Bitmap): ModelPrediction {
        val input = bitmapToInput(bitmap)
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val logits = readLogits(results[0].value)
                require(logits.size == labels.size) {
                    "Model returned ${logits.size} classes, but metadata defines ${labels.size}"
                }
                val probabilities = softmax(logits)
                val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] }
                    ?: error("Model returned no predictions")
                return ModelPrediction(
                    label = labels[bestIndex],
                    confidence = probabilities[bestIndex],
                    probabilities = labels.indices.associate { labels[it] to probabilities[it] }
                )
            }
        }
    }

    override fun close() {
        session.close()
    }

    private fun bitmapToInput(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (resized !== bitmap) resized.recycle()

        val input = FloatArray(3 * inputWidth * inputHeight)
        val planeSize = inputWidth * inputHeight
        for (pixelIndex in pixels.indices) {
            val pixel = pixels[pixelIndex]
            val red = ((pixel shr 16) and 0xff) / 255f
            val green = ((pixel shr 8) and 0xff) / 255f
            val blue = (pixel and 0xff) / 255f
            input[pixelIndex] = (red - mean[0]) / std[0]
            input[planeSize + pixelIndex] = (green - mean[1]) / std[1]
            input[2 * planeSize + pixelIndex] = (blue - mean[2]) / std[2]
        }
        return input
    }

    private fun readLogits(value: Any?): FloatArray {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                require(value.size == 1) { "Expected one batch of model output" }
                value[0] as? FloatArray
                    ?: error("Unsupported ONNX output type: ${value[0]?.javaClass}")
            }
            else -> error("Unsupported ONNX output type: ${value?.javaClass}")
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: return FloatArray(0)
        val exponentials = FloatArray(logits.size) { index -> exp((logits[index] - maxLogit).toDouble()).toFloat() }
        val total = exponentials.sum()
        return FloatArray(exponentials.size) { index -> exponentials[index] / total }
    }

    private fun org.json.JSONArray.toFloatArray(): FloatArray =
        FloatArray(length()) { index -> getDouble(index).toFloat() }
}
