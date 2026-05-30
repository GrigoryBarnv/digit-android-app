package com.example.digit_app.presentation.screen

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.example.digit_app.presentation.component.RgbControls
import com.example.digit_app.presentation.fragment.CameraPreviewFragment
import kotlin.math.roundToInt

@Composable
fun DemoScreen() {
    val red = remember { mutableFloatStateOf(0f) }
    val green = remember { mutableFloatStateOf(0f) }
    val blue = remember { mutableFloatStateOf(0f) }
    var showRgbControls by remember { mutableStateOf(false) }
    var cameraFragment by remember { mutableStateOf<CameraPreviewFragment?>(null) }
    val cameraContainerId = remember { View.generateViewId() }
    val cameraFragmentTag = "camera_preview_fragment"
    val context = LocalContext.current

    // Controls the white "flash" effect when a photo is taken.
    // true = flash is visible, false = fading out.
    var showFlash by remember { mutableStateOf(false) }
    // animateFloatAsState smoothly changes the alpha (opacity) from 0.7 → 0 over 400ms
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlash) 0.7f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "flash"
    )
    // After the flash peaks, wait briefly then set showFlash = false so it fades back out.
    // Without this, showFlash stays true and the white overlay never disappears.
    LaunchedEffect(showFlash) {
        if (showFlash) {
            kotlinx.coroutines.delay(150)
            showFlash = false
        }
    }

    // Bug fix #5: prevents the user from triggering multiple captures by tapping rapidly.
    // While a capture is in progress, the button is locked and visually dimmed.
    var isCapturing by remember { mutableStateOf(false) }

    fun onCaptureClicked() {
        // If a capture is already running, ignore the tap entirely.
        if (isCapturing) return
        isCapturing = true

        val cameraReady = CameraPreviewFragment.requestCapture { success, path ->
            // This runs when the capture finishes (success or failure).
            // Always unlock the button here — whether it worked or not.
            isCapturing = false
            if (success) {
                showFlash = true
                Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Capture failed: $path", Toast.LENGTH_SHORT).show()
            }
        }
        if (!cameraReady) {
            // Camera not connected or not open yet — unlock immediately.
            isCapturing = false
            Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
        }
    }

    fun uiToLed(value: Float): Int {
        val normalized = ((value + 50f) / 100f).coerceIn(0f, 1f)
        return (normalized * 15f).roundToInt().coerceIn(0, 15) //Convert to LED Scale
    }

    fun applyRgbToCamera(r: Float, g: Float, b: Float) {
        CameraPreviewFragment.pushRgb(uiToLed(r), uiToLed(g), uiToLed(b))
    }

    // Start from balanced RGB (centered sliders => mid LED values), not all zero.
    LaunchedEffect(Unit) {
        applyRgbToCamera(red.floatValue, green.floatValue, blue.floatValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DIGIT", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D))) { Text("FPS") }
                    Button(
                        onClick = { showRgbControls = !showRgbControls },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showRgbControls) Color(0xFF4A4A4A) else Color(0xFF2D2D2D)
                        )
                    ) { Text("RGB") }
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D))) { Text("MODEL") }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .background(Color.Black, RoundedCornerShape(12.dp))
            ) {
                // Bridge: embed Fragment-based USB camera preview inside Compose.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val container = FragmentContainerView(context).apply {
                            id = cameraContainerId
                        }
                        val activity = context as FragmentActivity
                        val existing = activity.supportFragmentManager.findFragmentByTag(cameraFragmentTag)
                        if (existing == null) {
                            val created = CameraPreviewFragment()
                            activity.supportFragmentManager.commit {
                                replace(container.id, created, cameraFragmentTag)
                            }
                            cameraFragment = created
                        } else {
                            cameraFragment = existing as? CameraPreviewFragment
                        }
                        container
                    },
                    update = { _ ->
                        val activity = context as? FragmentActivity ?: return@AndroidView
                        cameraFragment =
                            activity.supportFragmentManager.findFragmentByTag(cameraFragmentTag) as? CameraPreviewFragment
                    }
                )

                // White flash overlay — appears briefly when a photo is taken.
                // alpha() makes it invisible when flashAlpha is 0, so it doesn't block touches.
                if (flashAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(flashAlpha)
                            .background(Color.White, RoundedCornerShape(12.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp)) //Like an invisible gap

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button — opens the phone's built-in photo gallery.
                // Gallery button — always shows a picker so the user can choose which gallery app to open.
                Button(
                    onClick = {
                        // CATEGORY_APP_GALLERY filters to only gallery/photo apps (no banking, scanners, etc.)
                        // createChooser ensures the picker always appears — user can select "Just once" or "Always".
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_GALLERY)
                        }
                        val chooser = Intent.createChooser(intent, "Open gallery with...").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(chooser)
                            } else {
                                // No gallery app found on this device.
                                Toast.makeText(context, "No gallery app found on this device", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            // Catch any unexpected error during launch.
                            Toast.makeText(context, "Could not open gallery", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))
                ) { Text("Gallery") }
                // Capture button — tap to take a photo.
                // Dims to 40% opacity while a capture is already in progress (isCapturing = true),
                // giving the user clear visual feedback that the button is temporarily locked.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .alpha(if (isCapturing) 0.4f else 1f)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color(0xFF303030), CircleShape)
                        .clickable(enabled = !isCapturing) { onCaptureClicked() }
                )
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))) { Text("Mode") }
            }
        }

        if (showRgbControls) {
            // RGB controls are shown as an overlay panel, not in main layout flow.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 96.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                RgbControls(
                    red = red.floatValue,
                    green = green.floatValue,
                    blue = blue.floatValue,
                    onRedChange = {
                        red.floatValue = it //it = new slider value.
                        applyRgbToCamera(red.floatValue, green.floatValue, blue.floatValue)
                    },
                    onGreenChange = {
                        green.floatValue = it
                        applyRgbToCamera(red.floatValue, green.floatValue, blue.floatValue)
                    },
                    onBlueChange = {
                        blue.floatValue = it
                        applyRgbToCamera(red.floatValue, green.floatValue, blue.floatValue)
                    }
                )
            }
        }
    }
}

