package com.opentouch.sensorapp.ml

import com.jiangdg.ausbc.callback.IPreviewDataCallBack.DataFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class LiveFrameSourceTest {
    @Test
    fun slowConsumerDoesNotBlockCaptureOrBuildABacklog() {
        val source = LiveFrameSource { 0L }
        val capture = Executors.newSingleThreadExecutor()
        try {
            // No consumer runs during this burst, as if inference were very slow.
            capture.submit {
                repeat(10_000) { index ->
                    source.onPreviewData(byteArrayOf((index % 256).toByte(), 0, 0, -1), 1, 1, DataFormat.RGBA)
                }
            }.get(5, TimeUnit.SECONDS)
            val newest = source.frames.tryReceive().getOrThrow()
            assertEquals((9_999 % 256).toByte(), newest.rgba[0])
            assertTrue(source.frames.tryReceive().isFailure)
        } finally {
            source.close()
            capture.shutdownNow()
        }
    }

    @Test
    fun samplingCapsReadbackAtFiveFramesPerSecond() {
        var time = 0L
        LiveFrameSource { time }.use { source ->
            assertTrue(source.shouldReadFrame())
            for (tick in 1L..199L) {
                time = tick
                assertFalse(source.shouldReadFrame())
            }
            time = 200L
            assertTrue(source.shouldReadFrame())
            time = 1_000L
            assertTrue(source.shouldReadFrame())
            assertFalse(source.shouldReadFrame())
        }
    }

    @Test
    fun closingDropsPendingFramesAndRejectsLateCameraCallbacks() {
        val source = LiveFrameSource { 0L }
        source.onPreviewData(ByteArray(4), 1, 1, DataFormat.RGBA)
        source.close()
        source.onPreviewData(ByteArray(4), 1, 1, DataFormat.RGBA)
        assertTrue(source.frames.tryReceive().isClosed)
        assertFalse(source.shouldReadFrame())
    }

    @Test
    fun rejectsUnexpectedFormatsAndIncompleteBuffers() {
        LiveFrameSource { 0L }.use { source ->
            source.onPreviewData(ByteArray(4), 1, 1, DataFormat.NV21)
            source.onPreviewData(ByteArray(3), 1, 1, DataFormat.RGBA)
            source.onPreviewData(null, 1, 1, DataFormat.RGBA)
            source.onPreviewData(ByteArray(4), 0, 1, DataFormat.RGBA)
            assertTrue(source.frames.tryReceive().isFailure)
        }
    }
}
