package com.opentouch.sensorapp.presentation.screen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
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

private data class GalleryApp(val label: String, val intent: Intent, val icon: Bitmap?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen() {
    val red = remember { mutableFloatStateOf(0f) }
    val green = remember { mutableFloatStateOf(0f) }
    val blue = remember { mutableFloatStateOf(0f) }
    var showRgbControls by remember { mutableStateOf(false) }
    // Gallery bottom sheet
    var galleryApps by remember { mutableStateOf<List<GalleryApp>>(emptyList()) }
    val gallerySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cameraFragment by remember { mutableStateOf<CameraPreviewFragment?>(null) }
    val cameraContainerId = remember { View.generateViewId() }
    val cameraFragmentTag = "camera_preview_fragment"
    val context = LocalContext.current

    // ── Mode: "photo" or "video" ──────────────────────────────────────────────
    var isVideoMode by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }

    // ── AI model selection ────────────────────────────────────────────────────
    var selectedModel by remember { mutableStateOf("None") }
    var showModelMenu by remember { mutableStateOf(false) }

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
            .windowInsetsPadding(WindowInsets.systemBars)
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
                // Gallery button — always shows ALL installed gallery apps so the
                // user can pick. Two sources are combined:
                //
                //  1. CATEGORY_APP_GALLERY: the standard Android category that
                //     manufacturer gallery apps (Samsung Gallery, MIUI Gallery,
                //     OnePlus Gallery, etc.) declare. Each matching app gets its
                //     own targeted intent so the chooser shows them individually.
                //
                //  2. Google Photos: never declares CATEGORY_APP_GALLERY, so we
                //     add it explicitly via its package name if it's installed.
                //
                // Result:
                //  - Samsung tablet with both apps → chooser lists both
                //  - Pixel with only Google Photos  → opens Google Photos directly
                //  - Nothing installed              → toast
                Button(
                    onClick = {
                        val pm = context.packageManager
                        val seen = mutableSetOf<String>()
                        val found = mutableListOf<GalleryApp>()

                        fun tryAdd(pkg: String, intent: Intent) {
                            if (seen.add(pkg)) {
                                val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return }
                                val label = pm.getApplicationLabel(info).toString()
                                val icon = try {
                                    val drawable = pm.getApplicationIcon(info)
                                    if (drawable is BitmapDrawable) {
                                        drawable.bitmap
                                    } else {
                                        val bmp = Bitmap.createBitmap(
                                            drawable.intrinsicWidth.coerceAtLeast(1),
                                            drawable.intrinsicHeight.coerceAtLeast(1),
                                            Bitmap.Config.ARGB_8888
                                        )
                                        val canvas = Canvas(bmp)
                                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                                        drawable.draw(canvas)
                                        bmp
                                    }
                                } catch (_: Exception) { null }
                                found.add(GalleryApp(label, intent, icon))
                            }
                        }

                        // All known gallery packages — check each one directly.
                        listOf(
                            "com.sec.android.gallery3d",
                            "com.samsung.android.app.gallery",
                            "com.google.android.apps.photos",
                            "com.google.android.apps.photosgo",
                            "com.miui.gallery",
                            "com.asus.gallery",
                            "com.oneplus.gallery",
                            "com.oppo.gallery3d",
                            "com.vivo.gallery",
                        ).forEach { pkg ->
                            pm.getLaunchIntentForPackage(pkg)?.let { tryAdd(pkg, it) }
                        }

                        when {
                            found.isEmpty() ->
                                Toast.makeText(context, "No gallery app found", Toast.LENGTH_SHORT).show()
                            found.size == 1 ->
                                context.startActivity(found[0].intent)
                            else ->
                                galleryApps = found
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

                // AI button — opens a dropdown to select an ML model.
                // "None" means no model is active. More models will be added
                // once the professor specifies which algorithms to run.
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { showModelMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = actionButtonPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedModel == "None") Color(0xFF303030) else Color(0xFF1A3D1A)
                        )
                    ) {
                        Text(
                            text = if (selectedModel == "None") "🤖 AI" else "🤖 ${selectedModel}",
                            style = actionButtonTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = { selectedModel = "None"; showModelMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Model 1") },
                            onClick = { selectedModel = "Model 1"; showModelMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Model 2") },
                            onClick = { selectedModel = "Model 2"; showModelMenu = false }
                        )
                    }
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Button size: 18% of screen width, clamped between 56dp and 96dp
                // so it looks right on both phones and tablets.
                val captureSize = (maxWidth * 0.18f).coerceIn(56.dp, 96.dp)
                Box(
                    modifier = Modifier
                        .size(captureSize)
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

        // ── Gallery app bottom sheet ────────────────────────────────────────
        if (galleryApps.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { galleryApps = emptyList() },
                sheetState = gallerySheetState,
                containerColor = Color(0xFF2D2D2D),
            ) {
                Text(
                    text = "Open gallery with…",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = Color(0xFF3D3D3D))
                galleryApps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                galleryApps = emptyList()
                                context.startActivity(app.intent)
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        app.icon?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Text(text = app.label, color = Color.White, fontSize = 16.sp)
                    }
                    HorizontalDivider(color = Color(0xFF3D3D3D))
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
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
