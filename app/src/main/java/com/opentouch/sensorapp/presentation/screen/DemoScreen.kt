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
import com.opentouch.sensorapp.data.SensorMatchType
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

    // ── Settings menu (FPS / RGB / Resolution) ────────────────────────────────
    var showSettingsMenu by remember { mutableStateOf(false) }
    // Live-measured FPS coming from the camera fragment (read-only display).
    val currentFps = CameraPreviewFragment.currentFps.value

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
    val detectedDevice = CameraPreviewFragment.detectedDevice.value
    var dismissedSequence by remember { mutableIntStateOf(0) }

    // ── Video: recording state + elapsed-time counter ─────────────────────────
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

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

    fun formatTimer(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    // ── Capture button handler ────────────────────────────────────────────────
    fun onCaptureClicked() {
        if (isVideoMode) {
            if (!isRecording) {
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
                CameraPreviewFragment.requestStopRecording()
            }
        } else {
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
        return (normalized * 15f).roundToInt().coerceIn(0, 15)
    }

    fun applyRgbToCamera(r: Float, g: Float, b: Float) {
        CameraPreviewFragment.pushRgb(uiToLed(r), uiToLed(g), uiToLed(b))
    }

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
            // ── Top bar: centered "Open Touch" title only ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Open Touch",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

                if (flashAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(flashAlpha)
                            .background(Color.White, RoundedCornerShape(12.dp))
                    )
                }

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

            val actionButtonPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
            val actionButtonTextStyle = TextStyle(fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
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

                // Photo/Video button
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

                // AI button
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

                // Settings button — hosts FPS (read-only), RGB controls, and
                // Resolution (read-only spec + live device-reported sizes).
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { showSettingsMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = actionButtonPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))
                    ) {
                        Text("⚙️ Settings", style = actionButtonTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false }
                    ) {
                        // Resolve the connected sensor's spec (if recognized).
                        val matchedSensor = detectedDevice?.let {
                            SupportedSensors.classify(it.vendorId, it.productId, it.name).sensor
                        }

                        // ── FPS — read-only: live measured vs rated spec. ──────
                        val ratedFps = matchedSensor?.maxFps
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        when {
                                            ratedFps != null && currentFps > 0 -> "FPS: $currentFps / $ratedFps max"
                                            ratedFps != null                   -> "FPS: — / $ratedFps max"
                                            currentFps > 0                     -> "FPS: $currentFps"
                                            else                               -> "FPS: —"
                                        }
                                    )
                                    // Warn if measured rate is well below spec
                                    // (<70% of rated) — usually USB/host limited.
                                    if (ratedFps != null && currentFps in 1 until (ratedFps * 7 / 10)) {
                                        Text(
                                            "below rated — check USB/host",
                                            fontSize = 11.sp,
                                            color = Color(0xFFE0A030),
                                        )
                                    }
                                }
                            },
                            enabled = false,
                            onClick = { }
                        )

                        HorizontalDivider()

                        // ── RGB — opens the existing slider overlay panel. ─────
                        DropdownMenuItem(
                            text = { Text("RGB controls") },
                            onClick = {
                                showSettingsMenu = false
                                showRgbControls = true
                            }
                        )

                        HorizontalDivider()

                        // ── Resolution — read-only: spec + live device sizes. ──
                        // These sensors are fixed-format, so this is info, not a
                        // selector. The spec comes from SupportedSensors; the
                        // live list is what the device actually advertises.
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (matchedSensor != null)
                                        "Resolution: ${matchedSensor.nativeResolution} (native)"
                                    else
                                        "Resolution"
                                )
                            },
                            enabled = false,
                            onClick = { }
                        )

                        // Live device-reported sizes, listed beneath.
                        val liveSizes = remember(showSettingsMenu) {
                            CameraPreviewFragment.supportedSizes()
                        }
                        if (liveSizes.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "   (no sizes reported — connect a sensor)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF9A9A9A),
                                    )
                                },
                                enabled = false,
                                onClick = { }
                            )
                        } else {
                            liveSizes.forEach { size ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "   • ${size.width}x${size.height}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF9A9A9A),
                                        )
                                    },
                                    enabled = false,
                                    onClick = { }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

        // ── Sensor detection popup ──────────────────────────────────────────
        if (detectedDevice != null && detectedDevice.sequence != dismissedSequence) {
            val match = SupportedSensors.classify(
                detectedDevice.vendorId,
                detectedDevice.productId,
                detectedDevice.name,
            )
            val titleText = when (match.type) {
                SensorMatchType.KNOWN    -> "Sensor supported"
                SensorMatchType.PROBABLE -> "Sensor recognized"
                SensorMatchType.UNKNOWN  -> "Unrecognized sensor"
            }
            AlertDialog(
                onDismissRequest = {
                    dismissedSequence = detectedDevice.sequence
                    CameraPreviewFragment.declineConnect()
                },
                title = { Text(titleText) },
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
                        when (match.type) {
                            SensorMatchType.KNOWN ->
                                Text("✓ This sensor (${match.sensor?.displayName}) is supported.")
                            SensorMatchType.PROBABLE ->
                                Text("≈ This looks like a ${match.sensor?.displayName} " +
                                        "(new hardware revision). It should work — tap Connect.")
                            SensorMatchType.UNKNOWN ->
                                Text("This sensor isn't in the recognized list, but it may " +
                                        "still work. Tap Connect to try it.")
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