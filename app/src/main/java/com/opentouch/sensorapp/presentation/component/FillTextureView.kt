package com.opentouch.sensorapp.presentation.component

import android.content.Context
import android.view.Surface
import android.view.TextureView
import com.jiangdg.ausbc.widget.IAspectRatio

/**
 * A TextureView that always fills its parent container completely, with no
 * letterboxing or pillarboxing.
 *
 * The standard [AspectRatioTextureView] from libausbc overrides [onMeasure]
 * to shrink itself so the camera's native resolution fits exactly inside the
 * container while keeping its aspect ratio — leaving black bars on screens
 * where the preview area has a different shape.
 *
 * This view intentionally does NOT override [onMeasure]. Because
 * [com.jiangdg.ausbc.base.CameraFragment] adds it with MATCH_PARENT ×
 * MATCH_PARENT layout params, it always fills the full sensor-shaped arch
 * on any screen size or orientation.
 *
 * The camera image is stretched slightly to fill the arch. Any distortion
 * is imperceptible because the arch shape itself was designed to match the
 * sensor's physical form factor.
 */
class FillTextureView(context: Context) : TextureView(context), IAspectRatio {

    // Called by the library when it knows the camera's native resolution.
    // We intentionally ignore it — fill mode means we don't constrain size.
    override fun setAspectRatio(width: Int, height: Int) { /* fill mode — no-op */ }

    override fun getSurfaceWidth(): Int = measuredWidth
    override fun getSurfaceHeight(): Int = measuredHeight

    override fun getSurface(): Surface? = try {
        Surface(surfaceTexture)
    } catch (e: Exception) {
        null
    }

    override fun postUITask(task: () -> Unit) { post { task() } }
}
