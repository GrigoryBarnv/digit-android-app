package com.opentouch.sensorapp.presentation.screen

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.opentouch.sensorapp.data.SupportedSensors
import com.opentouch.sensorapp.presentation.component.RgbControls
import com.opentouch.sensorapp.presentation.component.SensorPreviewShape
import com.opentouch.sensorapp.presentation.fragment.CameraPreviewFragment
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

    // ── Mode: "photo" or "video" ──────────────────────────────────────────────
    var isVideoMode by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }

    // ── Photo: flash overlay ──────────────────────────────────────────────────
    var showFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlash) 0.7f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "flash"
    )
    LaunchedEffect(showFlash) {
        if (showFlash) {
            kotlinx.coroutines.delay(150)
            showFlash = false
        }
    }

    // ── Photo: capture lock (prevents double-tap) ─────────────────────────────
    var isCapturing by remember { mutableStateOf(false) }

    // ── USB sensor connection popup ────────────────────────────────────────────
    // Shows the detected sensor's name/IDs and whether it's a supported model,
    // whenever a new USB device is detected by CameraPreviewFragment.
    val detectedDevice = CameraPreviewFragment.detectedDevice.value
    var dismissedSequence by remember { mutableIntStateOf(0) }

    // ── Video: recording state + elapsed-time counter ─────────────────────────
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Tick the timer up every second while recording.
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1_000)
                recordingSeconds++
            }
        } else {
            recordingSeconds = 0
        }
    }

    // Format seconds → "MM:SS"
    fun formatTimer(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    // ── Capture button handler ────────────────────────────────────────────────
    fun onCaptureClicked() {
        if (isVideoMode) {
            if (!isRecording) {
                // Start recording
                val started = CameraPreviewFragment.requestStartRecording(
                    onStarted = { isRecording = true },
                    onDone = { success, path ->
                        isRecording = false
                        if (success) {
                            Toast.makeText(context, "Video saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Recording failed: $path", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (!started) {
                    Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Stop recording — onDone above fires once the file is finalised
                CameraPreviewFragment.requestStopRecording()
            }
        } else {
            // Photo mode
            if (isCapturing) return
            isCapturing = true

            val cameraReady = CameraPreviewFragment.requestCapture { success, path ->
                isCapturing = false
                if (success) {
                    showFlash = true
                    Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Capture failed: $path", Toast.LENGTH_SHORT).show()
                }
            }
            if (!cameraReady) {
                isCapturing = false
                Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
            }
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
                Text("Open Touch", color = Color.White)
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

            val sensorShape = remember { SensorPreviewShape() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(sensorShape)
                    .border(1.dp, Color(0xFF3D3D3D), sensorShape)
                    .background(Color.Black, sensorShape)
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
                if (flashAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(flashAlpha)
                            .background(Color.White, RoundedCornerShape(12.dp))
                    )
                }

                // Recording indicator — red dot + timer shown in the dome's
                // empty space at the top-center of the preview.
                // (TopStart/TopEnd corners are clipped away by the dome
                // shape, so the indicator must sit near the top-center.)
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color(0x99000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Red, CircleShape)
                        )
                        Text(
                            text = formatTimer(recordingSeconds),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row of 4 primary actions: Gallery, Photo/Video, AI, Settings.
            // Each button shares the row equally so labels never get squeezed
            // into a multi-line wrap.
            val actionButtonPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
            val actionButtonTextStyle = TextStyle(fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button — opens the phone's built-in photo gallery.
                Button(
                    onClick = {
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
                                Toast.makeText(context, "No gallery app found on this device", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open gallery", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = actionButtonPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))
                ) {
                    Text("🖼️ Gallery", style = actionButtonTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Photo/Video button — opens a dropdown to choose Photo or Video.
                // Ignored while recording (so the user can't switch mid-clip),
                // but kept visually "enabled" so the label doesn't fade out.
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { if (!isRecording) showModeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = actionButtonPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVideoMode) Color(0xFF8B0000) else Color(0xFF303030)
                        )
                    ) {
                        Text(
                            if (isVideoMode) "🎥 Video" else "📷 Photo",
                            style = actionButtonTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = showModeMenu,
                        onDismissRequest = { showModeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📷  Photo") },
                            onClick = {
                                isVideoMode = false
                                showModeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🎥  Video") },
                            onClick = {
                                isVideoMode = true
                                showModeMenu = false
                            }
                        )
                    }
                }

                // AI button — placeholder for future on-device ML features.
                Button(
                    onClick = {
                        Toast.makeText(context, "AI features coming soon", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = actionButtonPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))
                ) {
                    Text("🤖 AI", style = actionButtonTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Settings button — placeholder, no functionality yet.
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    contentPadding = actionButtonPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))
                ) {
                    Text("⚙️ Settings", style = actionButtonTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Capture / Record button, centered below the action row.
            // Photo mode: white circle, dims while capturing.
            // Video mode: white circle normally, red circle while recording.
            val captureButtonColor = when {
                isVideoMode && isRecording -> Color.Red
                else -> Color.White
            }
            val captureButtonAlpha = if (!isVideoMode && isCapturing) 0.4f else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .alpha(captureButtonAlpha)
                        .background(captureButtonColor, CircleShape)
                        .border(4.dp, Color(0xFF303030), CircleShape)
                        .clickable(enabled = !isCapturing) { onCaptureClicked() }
                )
            }
        }

        if (showRgbControls) {
            // RGB controls are shown as an overlay panel, not in main layout flow.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 144.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    RgbControls(
                        red = red.floatValue,
                        green = green.floatValue,
                        blue = blue.floatValue,
                        onRedChange = {
                            red.floatValue = it
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Apply button — confirms the current RGB values and
                    // closes the panel, so the user doesn't need to tap the
                    // RGB button again separately to dismiss it.
                    Button(
                        onClick = {
                            applyRgbToCamera(red.floatValue, green.floatValue, blue.floatValue)
                            showRgbControls = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4A4A))
                    ) { Text("Apply") }
                }
            }
        }

        // ── "Sensor supported?" popup ───────────────────────────────────────
        // Appears once whenever a new USB device is detected. Lists which
        // sensors the app supports, shows the detected sensor and whether it
        // is on that list, and lets the user Connect (proceed to the USB
        // permission prompt) or Cancel (skip it for this device).
        if (detectedDevice != null && detectedDevice.sequence != dismissedSequence) {
            val supported = SupportedSensors.find(detectedDevice.vendorId, detectedDevice.productId)
            AlertDialog(
                onDismissRequest = {
                    dismissedSequence = detectedDevice.sequence
                    CameraPreviewFragment.declineConnect()
                },
                title = { Text(if (supported != null) "Sensor supported" else "Sensor not supported") },
                text = {
                    Column {
                        Text("Supported sensors:")
                        SupportedSensors.list.forEach { sensor ->
                            Text("• ${sensor.displayName}")
                        }
                        if (SupportedSensors.list.isEmpty()) {
                            Text("• (none added yet)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Detected sensor: ${detectedDevice.name}")
                        Text("Vendor ID: 0x%04X (%d)".format(detectedDevice.vendorId, detectedDevice.vendorId))
                        Text("Product ID: 0x%04X (%d)".format(detectedDevice.productId, detectedDevice.productId))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (supported != null) {
                            Text("✓ This sensor (${supported.displayName}) is supported.")
                        } else {
                            Text("✗ This sensor is not supported by the app.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dismissedSequence = detectedDevice.sequence
                        CameraPreviewFragment.confirmConnect()
                    }) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dismissedSequence = detectedDevice.sequence
                        CameraPreviewFragment.declineConnect()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
