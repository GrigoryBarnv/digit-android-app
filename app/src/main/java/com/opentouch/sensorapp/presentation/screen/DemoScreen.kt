package com.opentouch.sensorapp.presentation.screen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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

/**
 * Circular bottom-bar button: lilac fill / white icon at rest, and when
 * [selected] flips to a white fill / lilac icon while growing slightly
 * (56dp -> 60dp) with a stronger shadow - so the active mode or a chosen
 * AI model is unmistakable at a glance. Press feedback is a spring
 * scale-down, matching the shutter button's feel.
 */
@Composable
private fun CircularNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lilac = Color(0xFF594BA0)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navPressScale"
    )
    val size by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 52.dp else 48.dp,
        animationSpec = tween(280),
        label = "navSize"
    )
    val elevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 10.dp else 4.dp,
        animationSpec = tween(280),
        label = "navElevation"
    )
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Color.White else lilac,
        animationSpec = tween(280),
        label = "navBg"
    )
    val iconColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) lilac else Color.White,
        animationSpec = tween(280),
        label = "navIcon"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .shadow(elevation, CircleShape)
                .background(bgColor, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFFC9C9CC), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A stepped (0 / half / max) FPS slider that shows a floating value bubble
 * above the thumb while it's being dragged, tracking the thumb's horizontal
 * position - similar to Android's native brightness/volume sliders.
 */
@Composable
private fun FpsSliderWithLabel(value: Float, onValueChange: (Float) -> Unit, maxFps: Int) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val fraction = (value / maxFps.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (isDragged) {
            val bubbleOffsetX = (maxWidth - 40.dp) * fraction
            Box(
                modifier = Modifier
                    .offset(x = bubbleOffsetX, y = (-32).dp)
                    .background(Color(0xFF4A4A4A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("${value.roundToInt()}", color = Color.White, fontSize = 13.sp)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..maxFps.toFloat(),
            steps = 1,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF594BA0),
                activeTrackColor = Color(0xFF594BA0),
                inactiveTrackColor = Color(0xFFDDD0EF),
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen() {
    val red = remember { mutableFloatStateOf(0f) }
    val green = remember { mutableFloatStateOf(0f) }
    val blue = remember { mutableFloatStateOf(0f) }
    var showRgbControls by remember { mutableStateOf(false) }
    var showFpsControls by remember { mutableStateOf(false) }
    val fpsSlider = remember { mutableFloatStateOf(0f) }
    // Gallery bottom sheet
    var galleryApps by remember { mutableStateOf<List<GalleryApp>>(emptyList()) }
    val gallerySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cameraFragment by remember { mutableStateOf<CameraPreviewFragment?>(null) }
    val cameraContainerId = remember { View.generateViewId() }
    val cameraFragmentTag = "camera_preview_fragment"
    val context = LocalContext.current

    // ── Mode: "photo" or "video" ──────────────────────────────────────────────
    var isVideoMode by remember { mutableStateOf(false) }

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

    // Which supported sensor (if any) is currently connected. Hoisted to the
    // top of the function so both the main layout (preview shape) and the
    // floating overlays below (FPS controls) can read it.
    val matchedSensor = detectedDevice?.let {
        SupportedSensors.classify(it.vendorId, it.productId, it.name).sensor
    }

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
                    "OpenTouch",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Shape the preview to match whichever sensor is actually connected:
            // DIGIT gets the domed/arch shape and GelSight Mini gets a sharp
            // perfect square - both are their real physical form factors.
            // Only the "nothing recognized yet" fallback (disconnected, or an
            // unknown sensor) gets a gently rounded rectangle instead of a
            // plain sharp square, so it doesn't look out of place next to the
            // rounded buttons/panels elsewhere in the UI when there's no
            // physical shape to actually match.
            val sensorShape = remember(matchedSensor?.folderName) {
                when (matchedSensor?.folderName) {
                    "Digit" -> SensorPreviewShape()
                    "GelSightMini" -> RectangleShape
                    else -> RoundedCornerShape(14.dp)
                }
            }
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
                    val recDotPulse = rememberInfiniteTransition(label = "recDotPulse")
                    val recDotAlpha by recDotPulse.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "recDotAlpha"
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color(0xFFD4362F), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(recDotAlpha)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            text = "REC ${formatTimer(recordingSeconds)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2D33), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top
            ) {
                // Gallery button
                CircularNavButton(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Gallery",
                    selected = false,
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
                    }
                )

                // Photo/Video button — a direct tap toggle (no popup needed for
                // a two-way switch), unlike AI/Settings below which still open
                // their menus since they have more than two options.
                CircularNavButton(
                    icon = if (isVideoMode) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                    label = if (isVideoMode) "Video" else "Photo",
                    selected = isVideoMode,
                    onClick = { if (!isRecording) isVideoMode = !isVideoMode }
                )

                // AI button
                Box {
                    CircularNavButton(
                        icon = Icons.Filled.AutoAwesome,
                        label = if (selectedModel == "None") "AI" else selectedModel,
                        selected = selectedModel != "None",
                        onClick = { showModelMenu = true }
                    )
                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        containerColor = Color(0xFF2D2D2D)
                    ) {
                        DropdownMenuItem(
                            text = { Text("None", color = Color.White) },
                            onClick = { selectedModel = "None"; showModelMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Model 1", color = Color.White) },
                            onClick = { selectedModel = "Model 1"; showModelMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Model 2", color = Color.White) },
                            onClick = { selectedModel = "Model 2"; showModelMenu = false }
                        )
                    }
                }

                // Settings button — hosts FPS (read-only), RGB controls, and
                // Resolution (read-only spec + live device-reported sizes).
                Box {
                    CircularNavButton(
                        icon = Icons.Filled.Settings,
                        label = "Settings",
                        selected = false,
                        onClick = { showSettingsMenu = true }
                    )

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                        containerColor = Color(0xFF2D2D2D)
                    ) {
                        // ── FPS — live measured vs rated spec. Tapping opens the
                        // FPS controls panel (same pattern as RGB controls below),
                        // with a stepped slider (0 / half / rated max) and an
                        // Apply button.
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
                                        },
                                        color = Color.White
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
                            leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White) },
                            enabled = ratedFps != null,
                            onClick = {
                                if (ratedFps != null) {
                                    fpsSlider.floatValue =
                                        (CameraPreviewFragment.targetFps.value ?: ratedFps).toFloat()
                                    showSettingsMenu = false
                                    showRgbControls = false
                                    showFpsControls = true
                                }
                            }
                        )

                        HorizontalDivider()

                        // ── RGB — opens the existing slider overlay panel. ─────
                        DropdownMenuItem(
                            text = { Text("RGB controls", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null, tint = Color.White) },
                            onClick = {
                                showSettingsMenu = false
                                showFpsControls = false
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
                                        "Resolution",
                                    color = Color.White
                                )
                            },
                            leadingIcon = { Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = Color.White) },
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

                        HorizontalDivider()

                        // ── Connected sensor — read-only identity info, plus a
                        // manual way to disconnect. Sensors now connect
                        // automatically with no interrupting "Connect to X?"
                        // popup; this replaces that popup's info (name, IDs,
                        // now also serial) and its only real function — the
                        // ability to reject/disconnect an unrecognized sensor
                        // — as a menu item instead of a blocking dialog.
                        if (detectedDevice != null) {
                            DropdownMenuItem(
                                text = { Text(matchedSensor?.displayName ?: detectedDevice.name, color = Color.White) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White) },
                                enabled = false,
                                onClick = { }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "   Vendor ID: 0x%04X (%d)".format(detectedDevice.vendorId, detectedDevice.vendorId),
                                        fontSize = 12.sp,
                                        color = Color(0xFF9A9A9A),
                                    )
                                },
                                enabled = false,
                                onClick = { }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "   Product ID: 0x%04X (%d)".format(detectedDevice.productId, detectedDevice.productId),
                                        fontSize = 12.sp,
                                        color = Color(0xFF9A9A9A),
                                    )
                                },
                                enabled = false,
                                onClick = { }
                            )
                            if (!detectedDevice.serialNumber.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "   Serial: ${detectedDevice.serialNumber}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF9A9A9A),
                                        )
                                    },
                                    enabled = false,
                                    onClick = { }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Disconnect sensor", color = Color(0xFFE2504A)) },
                                leadingIcon = { Icon(Icons.Filled.LinkOff, contentDescription = null, tint = Color(0xFFE2504A)) },
                                onClick = {
                                    showSettingsMenu = false
                                    CameraPreviewFragment.declineConnect()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("No sensor connected", color = Color(0xFF9A9A9A)) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFF9A9A9A)) },
                                enabled = false,
                                onClick = { }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val captureButtonAlpha = if (!isVideoMode && isCapturing) 0.4f else 1f
            val isRecordingPulse = isVideoMode && isRecording

            // Fingerprint gradient used on the shutter ring, matching the app icon.
            val fingerprintRingBrush = remember {
                Brush.sweepGradient(
                    listOf(
                        Color(0xFF3FC4E8),
                        Color(0xFF7FD18F),
                        Color(0xFFE8D24A),
                        Color(0xFFF0923F),
                        Color(0xFFE8443F),
                        Color(0xFF3FC4E8),
                    )
                )
            }

            // Press feedback: inner disc springs down slightly, with a haptic tick.
            val captureInteractionSource = remember { MutableInteractionSource() }
            val isCapturePressed by captureInteractionSource.collectIsPressedAsState()
            val capturePressScale by animateFloatAsState(
                targetValue = if (isCapturePressed) 0.86f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "capturePressScale"
            )
            val haptics = LocalHapticFeedback.current

            // Recording glow: a soft ring that expands and fades outward, looping.
            val recordPulse = rememberInfiniteTransition(label = "recordPulse")
            val recordPulseScale by recordPulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
                label = "recordPulseScale"
            )
            val recordPulseAlpha by recordPulse.animateFloat(
                initialValue = 0.55f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
                label = "recordPulseAlpha"
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val captureSize = (maxWidth * 0.18f).coerceIn(56.dp, 96.dp)
                val ringGapSize = captureSize * 0.86f
                val discSize = captureSize * 0.66f

                Box(
                    modifier = Modifier.size(captureSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecordingPulse) {
                        // Soft glow pulsing outward from the red disc.
                        Box(
                            modifier = Modifier
                                .size(discSize)
                                .graphicsLayer(
                                    scaleX = recordPulseScale,
                                    scaleY = recordPulseScale,
                                    alpha = recordPulseAlpha
                                )
                                .background(Color(0xFFE2504A), CircleShape)
                        )
                    } else {
                        // Gradient ring with a dark gap, echoing the app icon.
                        Box(
                            modifier = Modifier
                                .size(captureSize)
                                .alpha(captureButtonAlpha)
                                .shadow(6.dp, CircleShape)
                                .background(fingerprintRingBrush, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(ringGapSize)
                                .alpha(captureButtonAlpha)
                                .background(Color(0xFF2C2D33), CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(discSize)
                            .graphicsLayer(scaleX = capturePressScale, scaleY = capturePressScale)
                            .alpha(captureButtonAlpha)
                            .background(if (isRecordingPulse) Color(0xFFE2504A) else Color.White, CircleShape)
                            .clickable(
                                enabled = !isCapturing,
                                interactionSource = captureInteractionSource,
                                indication = null
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCaptureClicked()
                            }
                    )
                }
            }
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E5D62), contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 1.dp,
                            hoveredElevation = 8.dp
                        )
                    ) { Text("Apply") }
                }
            }
        }

        if (showFpsControls && matchedSensor != null) {
            val ratedFps = matchedSensor.maxFps
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
                    Text("FPS", color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))

                    FpsSliderWithLabel(
                        value = fpsSlider.floatValue,
                        onValueChange = { fpsSlider.floatValue = it },
                        maxFps = ratedFps
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("0", fontSize = 11.sp, color = Color(0xFF9A9A9A), modifier = Modifier.weight(1f))
                        Text(
                            "${ratedFps / 2}",
                            fontSize = 11.sp,
                            color = Color(0xFF9A9A9A),
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            "$ratedFps",
                            fontSize = 11.sp,
                            color = Color(0xFF9A9A9A),
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            CameraPreviewFragment.requestSetFps(fpsSlider.floatValue.roundToInt())
                            showFpsControls = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E5D62), contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 1.dp,
                            hoveredElevation = 8.dp
                        )
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

    }
}