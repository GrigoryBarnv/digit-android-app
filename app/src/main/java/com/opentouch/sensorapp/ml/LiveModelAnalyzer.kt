package com.opentouch.sensorapp.ml

import android.graphics.Bitmap
import android.os.SystemClock
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The capture thread offers frames without waiting for inference. No disk/JPEG capture. */
class LiveFrameSource(
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) : IPreviewDataCallBack, Closeable {
    data class Frame(val rgba: ByteArray, val width: Int, val height: Int)

    // Exactly one waiting frame; a slow model skips old frames automatically.
    internal val frames = Channel<Frame>(Channel.CONFLATED)
    @Volatile private var closed = false
    private var lastCaptureMs = -200L // Only accessed on the GL capture thread.

    override fun shouldReadFrame(): Boolean {
        val now = nowMs()
        if (closed || now - lastCaptureMs < 200L) return false
        lastCaptureMs = now
        return true
    }

    override fun onPreviewData(
        data: ByteArray?, width: Int, height: Int, format: IPreviewDataCallBack.DataFormat
    ) {
        if (closed || data == null || format != IPreviewDataCallBack.DataFormat.RGBA) return
        if (width <= 0 || height <= 0 || data.size.toLong() != width.toLong() * height * 4) return
        // RenderManager supplies an owned, read-only array for each sampled frame.
        frames.trySend(Frame(data, width, height))
    }

    override fun close() {
        closed = true
        frames.cancel()
    }
}

/** One processing thread owns model creation, every inference, and session disposal. */
class LiveModelAnalyzer : Closeable {
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "OpenTouch-inference").apply { priority = Thread.NORM_PRIORITY - 1 }
    }.asCoroutineDispatcher()

    suspend fun analyze(
        model: StoredModel,
        source: LiveFrameSource,
        onReady: () -> Unit,
        onPrediction: (ModelPrediction, Long) -> Unit
    ) = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        // Keeping creation and use inside the same context prevents cancellation
        // during model loading from leaking a native session.
        ModelRuntimeLoader.create(model.modelFile, model.configFile).use { runner ->
            withContext(Dispatchers.Main) { onReady() }
            for (frame in source.frames) {
                currentCoroutineContext().ensureActive()
                val started = SystemClock.elapsedRealtime()
                val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                val prediction = try {
                    // CaptureRender already applies the sensor rotation and GL row flip,
                    // exactly as the existing saved-photo path does.
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame.rgba))
                    runner.run(bitmap)
                } finally {
                    bitmap.recycle()
                }
                // Cancellation discards results from a previous model, disconnect,
                // or backgrounded screen before publishing anything to Compose.
                withContext(Dispatchers.Main) {
                    onPrediction(prediction, SystemClock.elapsedRealtime() - started)
                }
            }
        }
    }

    override fun close() {
        dispatcher.close()
    }
}
