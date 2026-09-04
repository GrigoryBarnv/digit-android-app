package com.opentouch.sensorapp.ml

import java.io.Closeable
import java.io.File

data class ModelPrediction(
    val label: String,
    val confidence: Float,
    val probabilities: Map<String, Float>
)

/** Implemented by the on-demand ML feature module. */
interface ModelRunner : Closeable {
    /** Performs inference off the Android main thread. */
    fun run(bitmap: android.graphics.Bitmap): ModelPrediction
}

/** Creates the implementation only after Google Play has installed the feature module. */
object ModelRuntimeLoader {
    private val IMPLEMENTATION_CLASSES = listOf(
        // Present only in local debug builds.
        "com.opentouch.sensorapp.ml.DebugOnnxModelRunner",
        // Supplied by the Play on-demand feature in release builds.
        "com.opentouch.sensorapp.ml.OnnxModelRunner"
    )

    fun isDebugRuntimeBundled(): Boolean =
        runCatching { Class.forName(IMPLEMENTATION_CLASSES.first()) }.isSuccess

    fun create(modelFile: File, configFile: File): ModelRunner {
        var lastError: Throwable? = null
        for (implementationClass in IMPLEMENTATION_CLASSES) {
            try {
                val factory = Class.forName(implementationClass).getMethod(
                    "create",
                    File::class.java,
                    File::class.java
                )
                return factory.invoke(null, modelFile, configFile) as ModelRunner
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("AI runtime is not installed", lastError)
    }
}
