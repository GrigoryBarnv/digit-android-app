package com.opentouch.sensorapp.presentation.screen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.opentouch.sensorapp.R
import com.opentouch.sensorapp.data.ResolutionFpsOption
import com.opentouch.sensorapp.data.SupportedSensors
import com.opentouch.sensorapp.ml.ModelPrediction
import com.opentouch.sensorapp.ml.LiveFrameSource
import com.opentouch.sensorapp.ml.LiveModelAnalyzer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.opentouch.sensorapp.ml.ModelRepository
import com.opentouch.sensorapp.ml.ModelRuntimeLoader
import com.opentouch.sensorapp.ml.MlRuntimeInstaller
import com.opentouch.sensorapp.ml.MlRuntimeState
import com.opentouch.sensorapp.ml.StoredModel
import com.opentouch.sensorapp.presentation.component.RgbControls
import com.opentouch.sensorapp.presentation.component.SensorPreviewShape
import com.opentouch.sensorapp.presentation.fragment.CameraPreviewFragment
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GalleryApp(val label: String, val intent: Intent, val icon: Bitmap?)

/**
 * Circular bottom-bar button: lilac fill / white icon at rest, and when
 * [selected] flips to a white fill / lilac icon while growing slightly
 * (56dp -> 60dp) with a stronger shadow - so the active mode or a chosen
 * AI model is unmistakable at a glance. Press feedback is a spring
 * scale-down, matching the shutter button's feel.
 *
 * The icon circle is drawn inside a fixed-size [selectedSize] box (not
 * measured at its animated size directly), and the label sits in a
 * fixed-width box too. Both exist so the "grow when selected" animation and
 * varying label lengths ("AI" vs "Model 1" vs "Photo"/"Video") happen
 * entirely *within* reserved space instead of changing this button's own
 * measured size - which previously reflowed the whole nav row/bar (and, via
 * its weight(1f) sibling, the camera preview) by a few pixels every time a
 * mode or AI model was selected. See Roberto's "dimensions change between
 * modes" feedback (v1.1.8).
 */
@Composable
private fun CircularNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Sized relative to the available width by the caller (see the
    // BoxWithConstraints wrapping the bottom bar) so the buttons scale up on
    // wider screens (tablets) instead of staying a fixed phone-tuned size.
    restSize: Dp = 48.dp,
    selectedSize: Dp = 52.dp,
    iconSize: Dp = 19.dp,
    labelWidth: Dp = 66.dp,
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
        targetValue = if (selected) selectedSize else restSize,
        animationSpec = tween(280),
        label = "navSize"
    )
    val elevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 10.dp else 4.dp,
        animationSpec = tween(280),
        label = "navElevation"
    )
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF4A4A4A)
            selected -> Color.White
            else -> lilac
        },
        animationSpec = tween(280),
        label = "navBg"
    )
    val iconColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF8A8A8A)
            selected -> lilac
            else -> Color.White
        },
        animationSpec = tween(280),
        label = "navIcon"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Fixed-size reserved space - always selectedSize, the largest the
        // circle ever gets - so the animated inner circle grows/shrinks
        // in place without changing this Box's (and therefore the Row's)
        // measured size.
        Box(modifier = Modifier.size(selectedSize), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                    .shadow(elevation, CircleShape)
                    .background(bgColor, CircleShape)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(iconSize))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (enabled) Color(0xFFC9C9CC) else Color(0xFF77777C),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(labelWidth)
        )
    }
}

/**
 * A stepped FPS slider that only lands on [stops] - the sensor's actually
 * verified fps values (e.g. DIGIT: 15/30/60, from its VGA/QVGA specs) -
 * rather than an arbitrary continuous 0..max range. Shows a floating value
 * bubble above the thumb while it's being dragged, tracking the thumb's
 * horizontal position - similar to Android's native brightness/volume
 * sliders.
 *
 * [stops] must be non-empty; a single stop (e.g. GelSight Mini's one
 * datasheet combo) is shown as a plain fixed-rate label instead of an
 * interactive slider, since there's nothing to actually pick between.
 */
@Composable
private fun FpsSliderWithLabel(stops: List<Int>, value: Float, onValueChange: (Float) -> Unit) {
    if (stops.size < 2) {
        Text(
            stops.firstOrNull()?.let { "Fixed at $it fps" } ?: "—",
            color = Color(0xFF9A9A9A),
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val maxIndex = stops.size - 1
    val currentIndex = stops.indexOf(value.roundToInt()).let { if (it >= 0) it else 0 }
    val fraction = currentIndex / maxIndex.toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (isDragged) {
            val bubbleOffsetX = (maxWidth - 40.dp) * fraction
            Box(
                modifier = Modifier
                    .offset(x = bubbleOffsetX, y = (-32).dp)
                    .background(Color(0xFF4A4A4A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("${stops[currentIndex]}", color = Color.White, fontSize = 13.sp)
            }
        }
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { newIndex ->
                onValueChange(stops[newIndex.roundToInt().coerceIn(stops.indices)].toFloat())
            },
            valueRange = 0f..maxIndex.toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
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

/**
 * Row of resolution chips valid at the FPS panel's current slider position.
 * Only ever shows resolutions [options] actually lists — the caller is
 * responsible for narrowing that list to the current fps (see
 * SupportedSensor.resolutionOptionsForFps()), so this composable has no
 * hardcoded knowledge of which sensor or fps is involved.
 */
@Composable
private fun ResolutionChipRow(
    options: List<ResolutionFpsOption>,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = selected == (option.width to option.height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF3C3489) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFF7F77DD) else Color(0xFF4A4A4A),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(option.width to option.height) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${option.width} x ${option.height}",
                    color = if (isSelected) Color(0xFFEEEDFE) else Color(0xFFB4B2A9),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(
    incomingModelUri: Uri? = null,
    onIncomingModelHandled: () -> Unit = {}
) {
    val red = remember { mutableFloatStateOf(0f) }
    val green = remember { mutableFloatStateOf(0f) }
    val blue = remember { mutableFloatStateOf(0f) }
    var showRgbControls by remember { mutableStateOf(false) }
    var showFpsControls by remember { mutableStateOf(false) }
    var showDeviceInfoPanel by remember { mutableStateOf(false) }
    var showModelControls by remember { mutableStateOf(false) }
    var addModelOutputToRecording by remember { mutableStateOf(false) }
    // Actual measured height of the bottom nav bar, so the RGB/FPS overlay
    // panels can sit just above it on any screen size instead of guessing a
    // fixed dp clearance - the bar's own size is responsive (scales with
    // screen width), so a hardcoded offset would overlap it on a tablet or
    // leave a gap on a small phone.
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val fpsSlider = remember { mutableFloatStateOf(0f) }
    // Resolution chosen in the FPS panel's resolution row - null means "no
    // explicit choice yet this session", in which case the panel falls back
    // to the first option valid for the current slider position.
    val selectedResolution = remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
    var availableModels by remember { mutableStateOf<List<StoredModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<StoredModel?>(null) }
    var showModelMenu by remember { mutableStateOf(false) }
    var modelPendingDeletion by remember { mutableStateOf<StoredModel?>(null) }
    var analysisResult by remember { mutableStateOf<ModelPrediction?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var analysisStatus by remember { mutableStateOf("Waiting for sensor") }
    var analysisDurationMs by remember { mutableStateOf<Long?>(null) }
    var analysisUpdateSequence by remember { mutableIntStateOf(0) }
    var modelSelectionMessage by remember { mutableStateOf<String?>(null) }
    var modelSelectionMessageId by remember { mutableIntStateOf(0) }
    val modelSelectionTone = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    }
    val liveAnalyzer = remember { LiveModelAnalyzer() }
    val activeCamera by CameraPreviewFragment.activeInstanceState
    val cameraStreaming by CameraPreviewFragment.isStreaming
    val lifecycle = (context as FragmentActivity).lifecycle
    var isResumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val debugRuntimeBundled = remember { ModelRuntimeLoader.isDebugRuntimeBundled() }
    var mlRuntimeState by remember {
        mutableStateOf(
            if (debugRuntimeBundled) MlRuntimeState.READY else MlRuntimeState.DOWNLOADING
        )
    }
    val analysisScope = rememberCoroutineScope()

    DisposableEffect(context) {
        if (debugRuntimeBundled) {
            onDispose { }
        } else {
            val installer = MlRuntimeInstaller(context) { state -> mlRuntimeState = state }
            installer.start()
            onDispose { installer.close() }
        }
    }
    LaunchedEffect(Unit) {
        availableModels = withContext(Dispatchers.IO) { ModelRepository.listModels(context) }
    }
    LaunchedEffect(incomingModelUri) {
        val uri = incomingModelUri ?: return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                ModelRepository.importFile(context, uri)
            }
            availableModels = withContext(Dispatchers.IO) {
                ModelRepository.listModels(context)
            }
            Toast.makeText(context, "ONNX model imported", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(
                context,
                error.message ?: "Could not import ONNX model",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            onIncomingModelHandled()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(liveAnalyzer) {
        onDispose { liveAnalyzer.close() }
    }
    DisposableEffect(modelSelectionTone) {
        onDispose { modelSelectionTone.release() }
    }
    LaunchedEffect(modelSelectionMessageId) {
        if (modelSelectionMessage != null) {
            delay(1_000L)
            modelSelectionMessage = null
        }
    }
    LaunchedEffect(selectedModel, mlRuntimeState, activeCamera, cameraFragment, cameraStreaming, isResumed) {
        analysisResult = null
        analysisError = null
        analysisDurationMs = null
        analysisUpdateSequence = 0
        val model = selectedModel ?: return@LaunchedEffect
        analysisStatus = when {
            mlRuntimeState == MlRuntimeState.DOWNLOADING -> "Downloading AI runtime..."
            mlRuntimeState != MlRuntimeState.READY -> "AI runtime unavailable"
            !isResumed -> "Live analysis paused"
            !cameraStreaming -> "Waiting for sensor"
            else -> "Loading model..."
        }
        // Prefer the observable active instance. The AndroidView callback may
        // still hold null (or an old fragment) while FragmentManager completes
        // the transaction that opens the sensor.
        val camera = activeCamera ?: cameraFragment
        if (camera == null) {
            analysisStatus = "Preparing camera..."
            return@LaunchedEffect
        }
        if (mlRuntimeState != MlRuntimeState.READY || !cameraStreaming || !isResumed) return@LaunchedEffect
        val source = LiveFrameSource()
        camera.liveFrameListener = source
        try {
            liveAnalyzer.analyze(
                model,
                source,
                onLoading = { analysisStatus = "Creating model session..." },
                onReady = { analysisStatus = "Waiting for first result..." },
                onPrediction = { result, durationMs ->
                    analysisResult = result
                    analysisDurationMs = durationMs
                    analysisUpdateSequence += 1
                    analysisStatus = "Live"
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            analysisResult = null
            analysisError = error.cause?.message ?: error.message ?: "Live analysis failed"
        } finally {
            source.close()
            if (camera.liveFrameListener === source) camera.liveFrameListener = null
        }
    }

    val importModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            analysisScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        uris.forEach { ModelRepository.importFile(context, it) }
                    }
                    availableModels = withContext(Dispatchers.IO) {
                        ModelRepository.listModels(context)
                    }
                    Toast.makeText(context, "Model files imported", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        error.message ?: "Could not import model files",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun requestModelDeletion(model: StoredModel) {
        showModelMenu = false
        modelPendingDeletion = model
    }

    fun deletePendingModel() {
        val model = modelPendingDeletion ?: return
        modelPendingDeletion = null
        if (selectedModel?.modelFile?.canonicalPath == model.modelFile.canonicalPath) {
            selectedModel = null
            analysisResult = null
            analysisError = null
        }
        analysisScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ModelRepository.deleteModel(context, model)
                }
                availableModels = withContext(Dispatchers.IO) {
                    ModelRepository.listModels(context)
                }
                Toast.makeText(context, "${model.displayName} deleted", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not delete model",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    modelPendingDeletion?.let { model ->
        AlertDialog(
            onDismissRequest = { modelPendingDeletion = null },
            title = { Text("Delete model?") },
            text = {
                Text("Delete ${model.displayName} and its configuration file from this device?")
            },
            confirmButton = {
                TextButton(onClick = { deletePendingModel() }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelPendingDeletion = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Settings menu (FPS / RGB / Resolution) ────────────────────────────────
    var showSettingsMenu by remember { mutableStateOf(false) }

    // ── Credits dialog — app version + who built it (professor's request) ─────
    var showCreditsDialog by remember { mutableStateOf(false) }
    val appVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }
    // Live-measured FPS coming from the camera fragment (read-only display).
    val currentFps = CameraPreviewFragment.currentFps.value
    val currentPreviewSize = CameraPreviewFragment.currentPreviewSize.value

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

    fun formatRgbaFrameSize(width: Int, height: Int): String {
        val bytes = width.toLong() * height.toLong() * 4L
        val mib = bytes.toDouble() / (1024.0 * 1024.0)
        return "%.1f MiB RGBA".format(mib)
    }

    fun latestAnalysisMetadata(): String? {
        val model = selectedModel ?: return null
        val result = analysisResult ?: return "OpenTouch AI model: ${model.displayName}; result: pending"
        val confidence = (result.confidence * 100f).roundToInt()
        val duration = analysisDurationMs?.let { "; inferenceMs: $it" } ?: ""
        return "OpenTouch AI model: ${model.displayName}; result: ${result.label}; confidence: $confidence%$duration; capturedAtMs: ${System.currentTimeMillis()}"
    }

    fun latestAnalysisOverlayText(): String? {
        val result = analysisResult ?: return null
        val confidence = (result.confidence * 100f).roundToInt()
        return "${result.label.replaceFirstChar { it.uppercase() }} $confidence%"
    }

    // ── Capture button handler ────────────────────────────────────────────────
    fun onCaptureClicked() {
        if (isVideoMode) {
            if (!isRecording) {
                val started = CameraPreviewFragment.requestStartRecording(
                    onStarted = { isRecording = true },
                    onDone = { success, path ->
                        isRecording = false
                        CameraPreviewFragment.requestVideoOverlayText(null)
                        if (success) {
                            Toast.makeText(context, "Video saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Recording failed: $path", Toast.LENGTH_SHORT).show()
                        }
                    },
                    analysisMetadata = latestAnalysisMetadata(),
                    analysisOverlayText = latestAnalysisOverlayText().takeIf { addModelOutputToRecording }
                )
                if (!started) {
                    Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                }
            } else {
                CameraPreviewFragment.requestVideoOverlayText(null)
                CameraPreviewFragment.requestStopRecording()
            }
        } else {
            if (isCapturing) return
            isCapturing = true

            val cameraReady = CameraPreviewFragment.requestCapture(
                onDone = { success, path ->
                    isCapturing = false
                    if (success) {
                        showFlash = true
                        Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Capture failed: $path", Toast.LENGTH_SHORT).show()
                    }
                },
                analysisMetadata = latestAnalysisMetadata(),
                analysisOverlayText = latestAnalysisOverlayText().takeIf { addModelOutputToRecording }
            )
            if (!cameraReady) {
                isCapturing = false
                Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(
        isRecording,
        addModelOutputToRecording,
        analysisResult?.label,
        analysisResult?.confidence
    ) {
        val overlayText = if (isRecording && addModelOutputToRecording) {
            latestAnalysisOverlayText()
        } else {
            null
        }
        CameraPreviewFragment.requestVideoOverlayText(overlayText)
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
            // ── Top bar: centered "OpenTouch" title, Credits button top-right ──
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
                // "Info" reads more clearly as "tap for details about this
                // app" than the previous people/group icon, which looked
                // more like a contacts or multi-user feature. The subtle
                // translucent circle behind it gives the button a visible
                // tap target/affordance instead of a bare icon floating in
                // the corner.
                IconButton(
                    onClick = { showCreditsDialog = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "About OpenTouch and credits",
                        tint = Color.White
                    )
                }
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

                if (selectedModel != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xDD202126),
                        border = BorderStroke(1.dp, Color(0xFF594BA0))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Active model indicator - which model is actually
                            // producing the result/status shown below. Kept
                            // small/muted since the result itself is the main
                            // point of this overlay.
                            selectedModel?.displayName?.let { activeModelName ->
                                Text(
                                    activeModelName.uppercase(),
                                    color = Color(0xFFB9A6FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            when {
                                analysisResult != null -> {
                                    val result = analysisResult!!
                                    key(analysisUpdateSequence) {
                                        Text(
                                            "${result.label.replaceFirstChar { it.uppercase() }} ${(result.confidence * 100f).roundToInt()}%",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                analysisError != null -> Text(
                                    analysisError!!,
                                    color = Color(0xFFFFB4AB),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                else -> Text(analysisStatus, color = Color.White, fontSize = 14.sp)
                            }
                            if (analysisResult != null && analysisError == null) {
                                Text(
                                    "Live В· ${analysisDurationMs ?: 0} ms",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Sized off the card's own width so the nav buttons and popup
            // offsets scale up on wider screens (tablets) instead of staying
            // fixed at phone-tuned dp values, which looked tiny/misaligned
            // on a tablet.
            val navRestSize = (maxWidth * 0.09f).coerceIn(48.dp, 72.dp)
            // Keep the navigation geometry identical in every state. The
            // selected color/elevation still communicates state, but changing
            // the reserved size makes the whole bottom control area reflow.
            val navSelectedSize = navRestSize
            val navIconSize = navRestSize * 0.4f
            // Label text boxes scale the same way as the circles above, so a
            // label isn't clipped on a narrow phone or left looking cramped
            // on a tablet. The "wide" variant is for labels longer than the
            // usual single word (e.g. "New image").
            val navLabelWidth = (maxWidth * 0.185f).coerceIn(58.dp, 92.dp)
            val navLabelWidthWide = (maxWidth * 0.24f).coerceIn(76.dp, 120.dp)
            // The AI menu contains None, installed models, and the import action,
            // gets its own, narrower scaled width rather than reusing the
            // wider Settings one. Giving the menu a known, exact width (not
            // just a max) lets the x offset below be calculated precisely -
            // half the difference between the button's width and the menu's
            // width - instead of a hand-tuned constant that only happened to
            // look right on one screen size.
            val navAiMenuWidth = (maxWidth * 0.52f).coerceIn(190.dp, 240.dp)
            val navAiMenuOffsetX = (navLabelWidth - navAiMenuWidth) / 2
            // The Settings menu is anchored to the Settings button itself
            // (the last, rightmost button), so it always opens leftward
            // from that button's right edge. If the menu's content is wider
            // than the space between the button and the screen's left edge,
            // Compose falls back to pinning the menu flush against the
            // screen edge instead - no longer aligned with the button at
            // all, which is worse the narrower the screen is. Capping the
            // menu's width to a scaled fraction of the available width
            // keeps it comfortably inside that space on any screen size, so
            // it reliably opens above the button rather than the fallback.
            val navMenuMaxWidth = (maxWidth * 0.62f).coerceIn(200.dp, 260.dp)
            val captureSize = (maxWidth * 0.18f).coerceIn(56.dp, 96.dp)
            // Reserve enough height for either the shutter or the circular AI
            // actions. This keeps the panel and weighted preview identical in
            // every AI state, including while an analysis result changes.
            val captureRowHeight = maxOf(captureSize, navSelectedSize + 30.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2D33), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .onGloballyPositioned { coords ->
                        bottomBarHeight = with(density) { coords.size.height.toDp() }
                    }
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top
            ) {
                // Gallery button
                CircularNavButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Gallery",
                    selected = false,
                    restSize = navRestSize,
                    selectedSize = navSelectedSize,
                    iconSize = navIconSize,
                    labelWidth = navLabelWidth,
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
                    modifier = Modifier.weight(1f),
                    icon = if (isVideoMode) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                    label = if (isVideoMode) "Video" else "Photo",
                    selected = isVideoMode,
                    restSize = navRestSize,
                    selectedSize = navSelectedSize,
                    iconSize = navIconSize,
                    labelWidth = navLabelWidth,
                    onClick = {
                        if (!isRecording) {
                            isVideoMode = !isVideoMode
                        }
                    }
                )

                // AI button
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Wrap the button and its menu together, sized to just the
                    // button's own footprint (not the full quarter-width slot
                    // this sits in). The DropdownMenu anchors to whatever Box
                    // directly contains it, so anchoring it here - already
                    // centered in the slot by the outer Box above - keeps the
                    // popup aligned under the actual button on any screen
                    // width, instead of a fixed offset that only worked for
                    // one specific slot width.
                    Box {
                    CircularNavButton(
                        icon = Icons.Filled.Memory,
                        // The model has two output classes, but it is one
                        // installed model. Keep the button label stable so the
                        // bottom navigation never changes width.
                        label = "AI",
                        selected = selectedModel != null,
                        enabled = !isRecording,
                        restSize = navRestSize,
                        selectedSize = navSelectedSize,
                        iconSize = navIconSize,
                        labelWidth = navLabelWidth,
                        onClick = { showModelMenu = true }
                    )
                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        modifier = Modifier.width(navAiMenuWidth),
                        containerColor = Color(0xFF2D2D2D),
                        offset = DpOffset(x = navAiMenuOffsetX, y = (-5).dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("None", color = Color.White) },
                            onClick = {
                                selectedModel = null
                                analysisResult = null
                                analysisError = null
                                showModelMenu = false
                            }
                        )
                        if (mlRuntimeState != MlRuntimeState.READY) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (mlRuntimeState) {
                                            MlRuntimeState.DOWNLOADING -> "Downloading AI runtime..."
                                            MlRuntimeState.UNAVAILABLE -> "AI runtime unavailable"
                                            MlRuntimeState.FAILED -> "AI runtime download failed"
                                            MlRuntimeState.READY -> "AI runtime ready"
                                        },
                                        color = Color.LightGray
                                    )
                                },
                                enabled = false,
                                onClick = {}
                            )
                        }
                        // Scrollable + height-capped so a handful of imported
                        // models doesn't push the "Import model files" row
                        // (or the whole menu) off-screen - None, the runtime
                        // status row, and Import stay pinned outside this
                        // scrollable section instead of scrolling away with
                        // the list.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            availableModels.forEach { model ->
                                // Currently active model - same identity check
                                // used elsewhere (StoredModel isn't a data
                                // class, so compare by the underlying file).
                                val isActiveModel = selectedModel?.modelFile?.canonicalPath ==
                                    model.modelFile.canonicalPath
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            model.displayName,
                                            color = when {
                                                mlRuntimeState != MlRuntimeState.READY -> Color.LightGray
                                                isActiveModel -> Color(0xFFB9A6FF)
                                                else -> Color.White
                                            },
                                            fontWeight = if (isActiveModel) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    // Active-model checkmark - the one persistent,
                                    // always-visible indicator of which model is
                                    // currently selected/running, right where the
                                    // user picks it.
                                    leadingIcon = if (isActiveModel) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "Currently active model",
                                                tint = Color(0xFFB9A6FF)
                                            )
                                        }
                                    } else null,
                                    trailingIcon = {
                                        IconButton(onClick = { requestModelDeletion(model) }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete ${model.displayName}",
                                                tint = Color(0xFFFFB4AB)
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (mlRuntimeState == MlRuntimeState.READY) {
                                            selectedModel = model
                                            // Do NOT force isVideoMode = false here.
                                            // This used to reset the camera to photo
                                            // mode any time a model was picked, but
                                            // AI + video are a fully supported
                                            // combination now (see
                                            // addModelOutputToRecording and
                                            // analysisOverlayText/recording burn-in
                                            // above) - selecting a model while
                                            // already in video mode shouldn't silently
                                            // kick the user back to photo mode.
                                            analysisResult = null
                                            analysisError = null
                                            analysisUpdateSequence = 0
                                            analysisDurationMs = null
                                            modelSelectionMessage = "${model.displayName} selected"
                                            modelSelectionMessageId += 1
                                            modelSelectionTone.startTone(
                                                ToneGenerator.TONE_PROP_ACK,
                                                120
                                            )
                                            showModelMenu = false
                                        }
                                    }
                                )
                            }
                        }
                        if (availableModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No models installed", color = Color.LightGray) },
                                enabled = false,
                                onClick = {}
                            )
                        }
                        HorizontalDivider(color = Color(0xFF3D3D3D))
                        DropdownMenuItem(
                            text = { Text("Import model files", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White) },
                            onClick = {
                                showModelMenu = false
                                importModelLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                    }
                }

                // Settings button — hosts FPS (read-only), RGB controls, and
                // Resolution (read-only spec + live device-reported sizes).
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Same pattern as the AI button above: wrap the button
                    // and its menu together, sized to just the button's own
                    // footprint (not the full quarter-width slot this sits
                    // in). The DropdownMenu anchors to whatever Box directly
                    // contains it, so anchoring it here - already centered
                    // in the slot by the outer Box above - keeps the popup
                    // aligned directly above the button on any screen width.
                    Box {
                    CircularNavButton(
                        icon = Icons.Filled.Settings,
                        label = "Settings",
                        selected = false,
                        restSize = navRestSize,
                        selectedSize = navSelectedSize,
                        iconSize = navIconSize,
                        labelWidth = navLabelWidth,
                        onClick = { showSettingsMenu = true }
                    )

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                        modifier = Modifier.widthIn(max = navMenuMaxWidth),
                        containerColor = Color(0xFF2D2D2D),
                        offset = DpOffset(x = 15.dp, y = (-5).dp)
                    ) {
                        // ── FPS — live measured vs rated spec. Tapping opens the
                        // FPS controls panel (same pattern as RGB controls below),
                        // with a stepped slider (0 / half / rated max) and an
                        // Apply button.
                        // Show the active mode as the denominator. The
                        // sensor's max capability is not the active FPS on
                        // first connection (DIGIT starts at 30 FPS).
                        val ratedFps = CameraPreviewFragment.targetFps.value
                            ?: matchedSensor?.maxFps
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        when {
                                            ratedFps != null && currentFps > 0 -> "FPS: $currentFps / $ratedFps"
                                            ratedFps != null                   -> "FPS: — / $ratedFps"
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
                                if (ratedFps != null && matchedSensor != null) {
                                    // Snap to the nearest actually-valid stop -
                                    // a leftover target fps from before this
                                    // sensor's stops were tightened up (or a
                                    // value from a different sensor entirely)
                                    // could otherwise land the slider
                                    // somewhere that isn't one of its stops.
                                    val initialFps = CameraPreviewFragment.targetFps.value ?: ratedFps
                                    fpsSlider.floatValue = matchedSensor.fpsStops
                                        .minByOrNull { kotlin.math.abs(it - initialFps) }
                                        ?.toFloat() ?: initialFps.toFloat()
                                    selectedResolution.value = CameraPreviewFragment.targetResolution.value
                                    showSettingsMenu = false
                                    showRgbControls = false
                                    showDeviceInfoPanel = false
                                    showModelControls = false
                                    showFpsControls = true
                                }
                            }
                        )

                        // GelSight Mini has no controllable RGB illumination
                        // (unlike DIGIT), so the option is hidden entirely
                        // rather than shown disabled - there's nothing for it
                        // to ever do on this sensor.
                        if (matchedSensor?.folderName != "GelSightMini") {
                            HorizontalDivider()

                            // ── RGB — opens the existing slider overlay panel. ──
                            DropdownMenuItem(
                                text = { Text("RGB controls", color = Color.White) },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showSettingsMenu = false
                                    showFpsControls = false
                                    showDeviceInfoPanel = false
                                    showModelControls = false
                                    showRgbControls = true
                                }
                            )
                        }

                        HorizontalDivider()

                        // ── Connected sensor — read-only identity info, plus a
                        // manual way to disconnect. Sensors now connect
                        // automatically with no interrupting "Connect to X?"
                        // popup; this replaces that popup's info (name, IDs,
                        // now also serial) and its only real function — the
                        // ability to reject/disconnect an unrecognized sensor
                        // — as a menu item instead of a blocking dialog.
                        DropdownMenuItem(
                            text = { Text("Model controls", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.Memory, contentDescription = null, tint = Color.White) },
                            onClick = {
                                showSettingsMenu = false
                                showRgbControls = false
                                showFpsControls = false
                                showDeviceInfoPanel = false
                                showModelControls = true
                            }
                        )

                        HorizontalDivider()

                        if (detectedDevice != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Device: ${matchedSensor?.shortName ?: detectedDevice.name}",
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showSettingsMenu = false
                                    showRgbControls = false
                                    showFpsControls = false
                                    showModelControls = false
                                    showDeviceInfoPanel = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("No sensor connected", color = Color(0xFF9A9A9A)) },
                                leadingIcon = { Icon(Icons.Filled.LinkOff, contentDescription = null, tint = Color(0xFF9A9A9A)) },
                                enabled = false,
                                onClick = { }
                            )
                        }
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
                    .height(captureRowHeight + 4.dp)
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
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
                            .background(if (isVideoMode) Color(0xFFE2504A) else Color.White, CircleShape)
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

                if (false && selectedModel != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = -(captureSize * 0.78f))
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xDD202126),
                        border = BorderStroke(1.dp, Color(0xFF594BA0))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when {
                                analysisResult != null -> {
                                    val result = analysisResult!!
                                    key(analysisUpdateSequence) {
                                        Text(
                                            "${result.label.replaceFirstChar { it.uppercase() }} ${(result.confidence * 100f).roundToInt()}%",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                analysisError != null -> Text(
                                    analysisError!!,
                                    color = Color(0xFFFFB4AB),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                else -> Text(analysisStatus, color = Color.White, fontSize = 14.sp)
                            }
                            if (analysisResult != null && analysisError == null) {
                                Text(
                                    "Live · ${analysisDurationMs ?: 0} ms",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            }
            }
        }

        // Also guard the panel itself, not just the menu entry that opens it -
        // covers the case where RGB controls were already open and the user
        // then hot-swaps to a GelSight Mini without closing the panel first.
        if (showRgbControls && matchedSensor?.folderName != "GelSightMini") {
            // Invisible full-screen scrim placed behind the panel below -
            // same outside-tap-to-dismiss behavior as the nav DropdownMenus.
            // No ripple/indication since it covers the whole screen.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showRgbControls = false }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomBarHeight + 20.dp)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                red.floatValue = 0f
                                green.floatValue = 0f
                                blue.floatValue = 0f
                                applyRgbToCamera(0f, 0f, 0f)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E5D62), contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 1.dp,
                                hoveredElevation = 8.dp
                            )
                        ) { Text("Reset") }

                        Button(
                            onClick = {
                                applyRgbToCamera(red.floatValue, green.floatValue, blue.floatValue)
                                showRgbControls = false
                            },
                            modifier = Modifier.weight(1f),
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
        }

        if (showDeviceInfoPanel && detectedDevice != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showDeviceInfoPanel = false }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomBarHeight + 20.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Device: ${matchedSensor?.shortName ?: detectedDevice.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Vendor ID: 0x%04X (%d)".format(detectedDevice.vendorId, detectedDevice.vendorId),
                        fontSize = 12.sp,
                        color = Color(0xFFBDBDBD)
                    )
                    Text(
                        "Product ID: 0x%04X (%d)".format(detectedDevice.productId, detectedDevice.productId),
                        fontSize = 12.sp,
                        color = Color(0xFFBDBDBD),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    currentPreviewSize?.let { (width, height) ->
                        Text(
                            "Stream: ${width}x${height} (${formatRgbaFrameSize(width, height)})",
                            fontSize = 12.sp,
                            color = Color(0xFFBDBDBD),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (!detectedDevice.serialNumber.isNullOrBlank()) {
                        Text(
                            "Serial: ${detectedDevice.serialNumber}",
                            fontSize = 12.sp,
                            color = Color(0xFFBDBDBD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // The explicit "Close" button was removed - this panel is
                    // dismissed by tapping outside it instead (see the
                    // full-screen transparent Box above that sets
                    // showDeviceInfoPanel = false on click). Disconnect
                    // remains as the one explicit action here.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDeviceInfoPanel = false
                                CameraPreviewFragment.declineConnect()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2504A), contentColor = Color.White)
                        ) {
                            Icon(
                                Icons.Filled.LinkOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect Sensor", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (showModelControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showModelControls = false }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomBarHeight + 20.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Model controls", color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Add model output to recording", color = Color.White)
                            Text(
                                if (addModelOutputToRecording) {
                                    "On: saved photos include the visible model result; videos keep metadata."
                                } else {
                                    "Off: saved media keeps model output only as metadata."
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF9A9A9A),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = addModelOutputToRecording,
                            onCheckedChange = { addModelOutputToRecording = it }
                        )
                    }
                }
            }
        }

        if (showFpsControls && matchedSensor != null) {
            val ratedFps = matchedSensor.maxFps
            // Same outside-tap-to-dismiss scrim as the RGB panel above.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showFpsControls = false }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomBarHeight + 20.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("FPS", color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))

                    val fpsStops = matchedSensor.fpsStops

                    FpsSliderWithLabel(
                        stops = fpsStops,
                        value = fpsSlider.floatValue,
                        onValueChange = { fpsSlider.floatValue = it }
                    )

                    if (fpsStops.size >= 2) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            fpsStops.forEachIndexed { index, fps ->
                                Text(
                                    "$fps",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9A9A9A),
                                    modifier = Modifier.weight(1f),
                                    textAlign = when (index) {
                                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                                        fpsStops.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                                        else -> androidx.compose.ui.text.style.TextAlign.Center
                                    }
                                )
                            }
                        }
                    }

                    // ── Resolution — narrowed to whatever's actually valid at
                    // the slider's current (snapped) fps. See
                    // SupportedSensor.resolutionOptionsForFps().
                    val snappedFps = fpsSlider.floatValue.roundToInt()
                    val verifiedAtFps = matchedSensor.resolutionFpsOptions.filter { it.fps == snappedFps }
                    val resolutionOptions = matchedSensor.resolutionOptionsForFps(snappedFps)
                    val effectiveResolution = selectedResolution.value
                        ?.takeIf { sel -> resolutionOptions.any { it.width == sel.first && it.height == sel.second } }
                        ?: resolutionOptions.firstOrNull()?.let { it.width to it.height }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Resolution", color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    ResolutionChipRow(
                        options = resolutionOptions,
                        selected = effectiveResolution,
                        onSelect = { selectedResolution.value = it }
                    )
                    when {
                        verifiedAtFps.isEmpty() -> Text(
                            "No verified resolution data at $snappedFps fps yet — using native ${matchedSensor.nativeResolution}",
                            fontSize = 11.sp,
                            color = Color(0xFF9A9A9A),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        verifiedAtFps.size == 1 -> Text(
                            "Only this resolution is verified at $snappedFps fps",
                            fontSize = 11.sp,
                            color = Color(0xFF9A9A9A),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        else -> {}
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            // Set fps and resolution together (single close/
                            // reopen) rather than two separate calls - see
                            // CameraPreviewFragment.changePreviewFpsAndResolution
                            // for why calling them back-to-back is racy.
                            effectiveResolution?.let { (w, h) ->
                                CameraPreviewFragment.requestSetFpsAndResolution(snappedFps, w, h)
                            } ?: CameraPreviewFragment.requestSetFps(snappedFps)
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
        modelSelectionMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE594BA0),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    shadowElevation = 12.dp
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

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
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = Color(0xFF3D3D3D))
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        // ── Credits dialog — app version + who built it ────────────────────
        if (showCreditsDialog) {
            val lilac = Color(0xFF594BA0)
            Dialog(onDismissRequest = { showCreditsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF2C2D33),
                    border = BorderStroke(1.dp, Color(0xFF3D3D3D)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // App logo (the actual launcher artwork, not just a
                        // generic icon) - it's a plain RGB PNG with a white
                        // backing (no alpha), so it's shown on a white circle
                        // rather than tinted lilac, which would otherwise
                        // leave an odd white square poking out from behind it.
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "OpenTouch logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("OpenTouch Mobile", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (appVersionName != null) "Version $appVersionName" else "Version —",
                                color = Color(0xFF9A9A9A),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))
                        HorizontalDivider(color = Color(0xFF3D3D3D))
                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            "DEVELOPED BY",
                            color = Color(0xFF9A9A9A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        )

                        // Name + LinkedIn URL pairs. Tapping a row opens that
                        // person's profile - the trailing "open in new" icon
                        // signals it's a link rather than a plain label.
                        listOf(
                            "Nasima Mallick" to "https://www.linkedin.com/in/nasima-mallick-110351211/",
                            "Grigory Baranov" to "https://www.linkedin.com/in/grigory-baranov/",
                            "Gayathri Kakarla" to "https://www.linkedin.com/in/gayathri-kakarla/",
                            "Roberto Calandra" to "https://www.linkedin.com/in/rcalandra/"
                        ).forEach { (name, linkedInUrl) ->
                            val initials = name.split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .joinToString("")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Explicitly force this into an external browser tab
                                        // rather than letting it resolve to the LinkedIn app
                                        // (if installed) or reuse this app's own task/window -
                                        // CATEGORY_BROWSABLE + FLAG_ACTIVITY_NEW_TASK opens a
                                        // fresh browser task outside OpenTouch itself.
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(linkedInUrl)).apply {
                                                addCategory(Intent.CATEGORY_BROWSABLE)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        )
                                    }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(lilac.copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        initials,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    contentDescription = "Opens LinkedIn profile",
                                    tint = Color(0xFF9A9A9A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFF3D3D3D))
                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Project website ─────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Same as the LinkedIn rows above: force this into an
                                    // external browser tab rather than staying tied to
                                    // OpenTouch's own task/window.
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://lasr-lab.github.io/opentouch.org/mobile/")
                                        ).apply {
                                            addCategory(Intent.CATEGORY_BROWSABLE)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    )
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = null,
                                tint = lilac,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "OpenTouch Mobile Website",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = "Opens project website",
                                tint = Color(0xFF9A9A9A),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Underlined to read clearly as a link, not just a
                        // muted caption, now that it opens lasr.org.
                        Text(
                            "LASR Lab · TU Dresden",
                            color = Color(0xFF9A9A9A),
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://lasr.org/"))
                                    )
                                }
                                .padding(vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "© 2026 OpenTouch",
                            color = Color(0xFF6E6E6E),
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        Button(
                            onClick = { showCreditsDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = lilac, contentColor = Color.White)
                        ) { Text("Close") }
                    }
                }
            }
        }
    }
}
