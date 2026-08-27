package com.opentouch.sensorapp.presentation.fragment

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.opentouch.sensorapp.R
import com.opentouch.sensorapp.data.SupportedSensors
import com.opentouch.sensorapp.databinding.FragmentCameraPreviewBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.widget.IAspectRatio
import com.opentouch.sensorapp.presentation.component.FillTextureView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ─── Recording state shared with DemoScreen ──────────────────────────────────
// DemoScreen observes this to update the UI (red button, timer, toast).
typealias RecordingCallback = (success: Boolean, path: String?) -> Unit

class CameraPreviewFragment : CameraFragment() {
    private var pendingRed = 0
    private var pendingGreen = 0
    private var pendingBlue = 0

    // Identifies which camera this fragment represents.
    // Default is "Camera 1" — when multi-camera is implemented, each fragment
    // will be assigned a unique ID (e.g. "Camera 2", "Camera 3").
    var cameraId: String = "Camera 1"

    private var _binding: FragmentCameraPreviewBinding? = null
    private val binding: FragmentCameraPreviewBinding
        get() = _binding!!
    private var permissionRetryJob: Job? = null

    // True while the camera is being closed because the user tapped "Cancel"
    // on the "is this sensor supported?" popup (as opposed to the sensor
    // being physically unplugged). Read once by onCameraState(CLOSED) to
    // decide whether to show the "Sensor disconnected" + Reconnect UI.
    private var declinedClose = false

    // True while the camera is closed because the user dragged the FPS
    // slider to 0 ("pause"), as opposed to a real disconnect. Read by
    // onCameraState(CLOSED) so the auto-reconnect loop does not immediately
    // reopen the camera while paused.
    private var pausedForZeroFps = false

    // True while the app is backgrounded (between onPause and onResume).
    // Guards against onCameraState(CLOSED) restarting the permission-retry
    // loop as a side effect of the unregister we do in onPause() below.
    private var isPausedForBackground = false

    // True after the user explicitly taps Cancel/Deny on the system
    // "Allow OpenTouch to access <device>?" dialog, until they tap
    // Reconnect (or the device is unplugged). While true, onResume() must
    // NOT re-register the camera monitor: showing the system dialog itself
    // triggers onPause()/onResume() on this fragment (it's a new foreground
    // window, same as pressing Home), and re-registering there replays the
    // library's onAttachDev auto-permission-request for the still-attached
    // device - popping the exact same dialog right back up and making
    // Cancel look like it does nothing.
    private var awaitingManualReconnect = false

    // TEMP DIAGNOSTIC — true only while a capturePhoto() call is in flight.
    // Included in the onCameraState(CLOSED) trace log so we can see whether
    // a close during capture is really happening, or just a coincidence of
    // timing. Safe to remove once the capture-disconnect bug is root-caused.
    private var isCapturingDiag = false

    // ─── FPS measurement (read-only display in Settings) ──────────────────────
    // Counts preview frames between samples. Incremented on the camera thread,
    // read+reset once per second on the main thread — a single Int write/read is
    // atomic enough for a display counter, no lock needed.
    @Volatile
    private var frameTick = 0
    private var fpsJob: Job? = null

    // Lightweight frame callback: it does NOT process the bytes, it only counts
    // frames so we can derive a real, measured FPS for the read-only display.
    private val fpsCounter = object : IPreviewDataCallBack {
        override fun onPreviewData(
            data: ByteArray?,
            width: Int,
            height: Int,
            format: IPreviewDataCallBack.DataFormat
        ) {
            frameTick++
        }
    }

    private fun startFpsMeasurement() {
        fpsJob?.cancel()
        frameTick = 0
        fpsJob = lifecycleScope.launch {
            // delay(1_000) is not an exact stopwatch - coroutine dispatch
            // overhead means the real gap between samples can be a little
            // more or less than 1000ms, which used to make a steady 60fps
            // stream occasionally read as 61 or 62. Measuring the ACTUAL
            // elapsed time and dividing by it (frames / real seconds passed)
            // instead of assuming exactly 1.000s keeps the reading accurate.
            var lastSampleTime = SystemClock.elapsedRealtime()
            while (isAdded) {
                delay(1_000)
                val now = SystemClock.elapsedRealtime()
                val elapsedSeconds = (now - lastSampleTime) / 1000f
                lastSampleTime = now
                val frames = frameTick
                frameTick = 0
                _currentFps.value = if (elapsedSeconds > 0f) {
                    (frames / elapsedSeconds).roundToInt()
                } else {
                    frames
                }
            }
        }
    }

    private fun stopFpsMeasurement() {
        fpsJob?.cancel()
        fpsJob = null
        _currentFps.value = 0
    }

    /**
     * Shows the "Waiting for Touch Sensor" status text along with a list of
     * the sensor models this app recognizes (per Roberto's feedback), so
     * someone with an unsupported/unrecognized device knows what's expected
     * before they even plug one in.
     */
    private fun showWaitingForSensor() {
        _binding?.statusText?.text = getString(R.string.camera_waiting_for_device)
        _binding?.supportedModelsText?.apply {
            text = "Supported Sensors:\n" +
                SupportedSensors.list.joinToString("\n") { "•  ${it.shortName}" }
            visibility = View.VISIBLE
        }
    }

    /** Hides the supported-models list for any status other than "waiting". */
    private fun hideSupportedModelsList() {
        _binding?.supportedModelsText?.visibility = View.GONE
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentCameraPreviewBinding.inflate(inflater, container, false)
        binding.reconnectButton.setOnClickListener { onReconnectClicked() }

        // Soft pulsing dot next to the status pill (waiting/disconnected/error
        // messages) - runs continuously; only visible while the pill itself is.
        ObjectAnimator.ofFloat(binding.statusDot, "alpha", 1f, 0.25f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        return binding.root
    }

    // No scaleX/scaleY flip here - deliberately. Combining ANY single-axis
    // flip (scaleX=-1 or scaleY=-1) with SENSOR_ROTATE_TYPE's 90/270 degree
    // GL rotation below always produces a mirror image, regardless of which
    // axis is picked (confirmed with matrix math: a 90-degree rotation has
    // determinant +1, any single-axis flip has determinant -1, and +1 * -1
    // is always -1 - i.e. always a mirror, no matter which axis). Two
    // earlier attempts (scaleY, then scaleX) both produced a mirrored image
    // on-device (confirmed with a coin pressed on the sensor - readable
    // text came out backwards both times). Since the old Digit app applied
    // no rotation AND no flip at all and displayed correctly (confirmed via
    // git history), the sensor doesn't need flipping - it only needs
    // rotating to fit portrait, which SENSOR_ROTATE_TYPE alone (a pure
    // rotation, no mirroring) already does correctly.
    override fun getCameraView(): IAspectRatio = FillTextureView(requireContext())

    private fun isSensorRotationQuarterTurn() =
        SENSOR_ROTATE_TYPE == RotateType.ANGLE_90 || SENSOR_ROTATE_TYPE == RotateType.ANGLE_270

    override fun getCameraViewContainer(): ViewGroup = binding.cameraViewContainer

    override fun getGravity(): Int = Gravity.CENTER

    /**
     * The raw image coming off the USB sensor comes out sideways — the sensor
     * streams a landscape (320x240) image, but we want it to fill the screen
     * upright (portrait), with the sensor's flat edge at the bottom of the
     * screen and its rounded edge at the top.
     *
     * Two things work together to do this:
     *  1. [SENSOR_ROTATE_TYPE] rotates the actual image content.
     *  2. When that rotation is 90 or 270 degrees, we swap the width/height we
     *     report below (320x240 -> 240x320) so the preview BOX is portrait-
     *     shaped too — otherwise the rotated image would be squeezed into a
     *     landscape-shaped box with black bars.
     *
     * [SENSOR_ROTATE_TYPE] is the only thing you need to change: with the
     * sensor plugged in and the preview showing, try ANGLE_90, ANGLE_270,
     * ANGLE_0 or ANGLE_180 (and FLIP_UP_DOWN / FLIP_LEFT_RIGHT if a rotation
     * alone doesn't fix it — some sensors are mirrored) until the image fills
     * the box upright with the flat edge at the bottom.
     */
    override fun getCameraRequest(): CameraRequest {
        // Swap dimensions for 90/270 degree rotations so the preview box
        // becomes portrait-shaped to match the rotated image.
        val isQuarterTurn = isSensorRotationQuarterTurn()
        // Base (unrotated) size the user picked via the Settings resolution
        // row - see changePreviewResolution(). Falls back to the 320x240
        // default when nothing has been explicitly requested yet.
        val (baseWidth, baseHeight) = _targetResolution.value ?: (320 to 240)
        val width = if (isQuarterTurn) baseHeight else baseWidth
        val height = if (isQuarterTurn) baseWidth else baseHeight

        return CameraRequest.Builder()
            .setPreviewWidth(width)
            .setPreviewHeight(height)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(SENSOR_ROTATE_TYPE)

            // DIGIT/GelSight Mini are small raw-sensor UVC devices with no
            // onboard JPEG encoder - they reject a FORMAT_MJPEG request
            // outright (confirmed via real-device logs: "setPreviewSize
            // failed(format is 1)", where 1 = FRAME_FORMAT_MJPEG). The
            // vendored library then falls back to FORMAT_YUYV internally,
            // but that fallback path has a native bug (a SIGABRT inside
            // UVCPreview::stopPreview(), joining a preview thread that was
            // never started - see CameraUVC.kt's catch block). Requesting
            // YUYV directly here skips the failing MJPEG attempt entirely,
            // so the crashy fallback path never runs.
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
            .setAspectRatioShow(true)
            .setCaptureRawImage(false)
            .setRawPreviewData(false)
            .create()
    }

    override fun initData() {
        super.initData()
        // Reset all "attempt in progress" tracking for a genuinely fresh
        // start. These are companion-level (so they survive across
        // onPause/onResume restarts during a single attempt, on purpose -
        // see their individual comments), but that also means a stale
        // value left over from an earlier attempt in this same app
        // process could otherwise immediately make a brand new plug-in
        // look like it's already been waiting for 6+ seconds, skipping
        // straight to "Access denied" without ever actually asking.
        firstUnconnectedSeenAt = null
        lastPermissionRequestKey = null
        permissionRequestPending = false
        permissionRequestedAt = null
        awaitingManualReconnect = false
        isPausedForBackground = false
        showWaitingForSensor()
        startPermissionOpenRetry(initialDelayMs = 600)
    }

    override fun onResume() {
        super.onResume()
        if (permissionRequestPending) {
            // A request survived this pause/resume cycle. Showing the
            // system "Allow app to access <device>?" dialog is what
            // triggers this fragment's onPause() in the first place
            // (confirmed repeatedly via on-device tracing) - so onResume()
            // firing again while a request is STILL pending means the
            // dialog has almost certainly just been dismissed by some tap.
            // This is a far more precise "the dialog is no longer sitting
            // open on screen" signal than counting elapsed time from when
            // the request was originally sent, which could fire while the
            // user is still legitimately reading a dialog they haven't
            // tapped yet - see the give-up check in
            // startPermissionOpenRetry() for how this timestamp is used.
            permissionRequestedAt = System.currentTimeMillis()
        }
        if (awaitingManualReconnect) {
            // Stay fully idle - don't re-register the camera monitor or
            // restart the retry loop - until the user explicitly taps
            // Reconnect. See the comment on awaitingManualReconnect above
            // for why this guard exists. (isPausedForBackground is left
            // alone here on purpose: registerMultiCamera() was already torn
            // down by onPermissionDenied(), same as a real onPause() would
            // have done, so this flag still correctly means "not currently
            // registered".)
            return
        }
        if (isPausedForBackground) {
            // Undo the onPause() teardown below: re-register so the camera
            // can be detected/opened again now that the app is visible.
            isPausedForBackground = false
            registerMultiCamera()
        }
        startPermissionOpenRetry(initialDelayMs = 250)
    }

    override fun onPause() {
        super.onPause()
        // Stop polling for USB permission while the app isn't in the
        // foreground. Without this, the loop started in onResume() keeps
        // running in the background (the Fragment's view survives a Home
        // press, only onPause/onStop fire) and keeps calling
        // requestPermission() every ~500ms, which pops the system "Allow
        // Open Touch to access DIGIT?" dialog over whatever app the user is
        // actually using. onResume() restarts the loop when the app is
        // reopened, so normal in-app behavior is unaffected.
        permissionRetryJob?.cancel()

        // The library's own USB-attach listener (registered in
        // registerMultiCamera(), which runs when this screen is created) is
        // a SEPARATE mechanism from the polling loop above: it auto-requests
        // permission the instant a matching USB device is (re)detected, and
        // is normally only torn down when the screen is destroyed - not
        // merely backgrounded. So it kept popping the same system dialog in
        // the background even after the fix above. Unregistering it here,
        // and re-registering in onResume(), closes that gap too.
        //
        // EXCEPT while a permission request is genuinely pending
        // (permissionRequestPending). Root cause found via on-device
        // tracing: showing the system "Allow app to access <device>?"
        // dialog triggers THIS onPause() within milliseconds on more than
        // one phone, and unRegisterMultiCamera() tears down the exact
        // broadcast receiver that's supposed to catch the user's eventual
        // Cancel/OK answer (USBMonitor scopes that receiver to an action
        // string unique to its own instance - see USBMonitor.java). Tearing
        // it down here orphaned every single permission request, which is
        // why onCancelDev() never fired no matter what was tapped. So:
        // leave the camera client alone while an answer is still pending -
        // only cancel our own polling loop, same as always.
        if (permissionRequestPending) {
            return
        }
        isPausedForBackground = true
        unRegisterMultiCamera()
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        val statusView = view?.findViewById<TextView>(R.id.statusText) ?: return
        val statusPillView = view?.findViewById<View>(R.id.statusPill)
        // Covers the stale last-rendered frame with black whenever the sensor
        // isn't actively streaming, so a disconnect doesn't leave a frozen
        // frame showing behind the status message.
        val disconnectedOverlay = view?.findViewById<View>(R.id.disconnectedOverlay)
        // The supported-models list only belongs next to the "waiting for
        // sensor" message (shown via showWaitingForSensor()) - hide it by
        // default for every other status, below.
        hideSupportedModelsList()
        when (code) {
            ICameraStateCallBack.State.OPENED -> {
                statusPillView?.visibility = View.GONE
                disconnectedOverlay?.visibility = View.GONE
                _binding?.reconnectButton?.visibility = View.GONE
                applyRgb(pendingRed, pendingGreen, pendingBlue)
                permissionRetryJob?.cancel()
                firstUnconnectedSeenAt = null
                permissionRequestPending = false
                permissionRequestedAt = null

                // Start measuring FPS: attach the lightweight frame counter and
                // begin the once-per-second sampler.
                getCurrentCamera()?.addPreviewDataCallBack(fpsCounter)
                startFpsMeasurement()

                // Show the "is this sensor supported?" popup only once per
                // physical connection. lastDetectedDeviceKey holds the VID:PID
                // we've already shown a popup for; it is cleared ONLY when the
                // device list becomes empty (a real unplug), not on the
                // close/reopen that happens during an app resume. So a resume
                // with the same sensor still attached finds a matching key and
                // shows nothing.
                val device = getDeviceList()?.firstOrNull()
                if (device != null) {
                    val key = "${device.vendorId}:${device.productId}"
                    if (key != lastDetectedDeviceKey) {
                        lastDetectedDeviceKey = key
                        detectionSequence++
                        _connectDecision = ConnectDecision.NONE
                        // New physical sensor - forget any FPS/resolution
                        // choice made for a previously connected sensor.
                        _targetFps.value = null
                        _targetResolution.value = null
                    }
                    // Always refresh detectedDevice while a sensor is attached
                    // and the camera is open - even on an internal reopen with
                    // the SAME device (e.g. after an FPS change), where the key
                    // above is unchanged. Other UI (preview shape, FPS panel)
                    // depends on this being non-null whenever a sensor is
                    // connected. detectionSequence only increments in the
                    // branch above, so the "is this sensor supported?" popup
                    // still fires only once per physical connection.
                    val serial = try {
                        device.serialNumber
                    } catch (e: SecurityException) {
                        null
                    }
                    _detectedDevice.value = DetectedDevice(
                        name = device.productName?.takeIf { it.isNotBlank() }
                            ?: "Unknown USB device",
                        vendorId = device.vendorId,
                        productId = device.productId,
                        serialNumber = serial,
                        sequence = detectionSequence
                    )
                }
            }
            ICameraStateCallBack.State.CLOSED -> {
                // TEMP DIAGNOSTIC — logs a fake stack trace so we can see
                // WHAT called closeCamera() the next time the "camera
                // disconnected" message shows up unexpectedly (e.g. right
                // after taking a photo). Safe to remove once the capture-
                // disconnect bug is root-caused.
                Logger.w(
                    "CameraPreviewFragment",
                    "onCameraState CLOSED msg=$msg declinedClose=$declinedClose pausedForZeroFps=$pausedForZeroFps isCapturingNow=$isCapturingDiag",
                    Exception("CLOSED-trace")
                )
                statusPillView?.visibility = View.VISIBLE
                disconnectedOverlay?.visibility = View.VISIBLE
                stopFpsMeasurement()
                // Dismiss any visible popup, but DO NOT clear
                // lastDetectedDeviceKey here — otherwise an app-resume reopen
                // would treat the same sensor as new and re-show the popup.
                // The key is only reset on a genuine unplug (device list empty),
                // handled in startPermissionOpenRetry's no-device branch.
                _detectedDevice.value = null
                if (declinedClose) {
                    // The user tapped "Cancel" on the sensor popup, which is
                    // what closed the camera. Show a clear "disconnected"
                    // message with a way to reconnect, instead of the
                    // misleading "requesting permission" polling status.
                    declinedClose = false
                    statusView.text = getString(R.string.sensor_disconnected_declined)
                    _binding?.reconnectButton?.visibility = View.VISIBLE
                } else if (pausedForZeroFps) {
                    // Closed on purpose because the FPS slider was set to 0.
                    // Do NOT auto-reopen — show a paused state with a
                    // Reconnect button the user can tap to resume (moving
                    // the slider off 0 also resumes, via changePreviewFps).
                    statusView.text = "Preview paused (FPS set to 0)"
                    _binding?.reconnectButton?.visibility = View.VISIBLE
                } else {
                    // The sensor was unplugged (or the camera otherwise
                    // closed for some other reason). Resume looking for a
                    // device.
                    statusView.text = getString(R.string.camera_disconnected)
                    _binding?.reconnectButton?.visibility = View.GONE
                    // Don't restart the retry loop if this close was a side
                    // effect of onPause()'s unregister (app backgrounded) -
                    // onResume() will restart it when the app comes back.
                    if (!isPausedForBackground) {
                        startPermissionOpenRetry(initialDelayMs = 500)
                    }
                }
            }
            ICameraStateCallBack.State.ERROR -> {
                statusPillView?.visibility = View.VISIBLE
                disconnectedOverlay?.visibility = View.VISIBLE
                stopFpsMeasurement()
                statusView.text = getString(R.string.camera_error, msg ?: "unknown")
            }
        }
    }

    override fun onDestroyView() {
        permissionRetryJob?.cancel()
        permissionRetryJob = null
        stopFpsMeasurement()
        // Leaving the screen — dismiss any visible popup. Keep
        // lastDetectedDeviceKey so returning with the same sensor still
        // attached does NOT re-show the popup.
        _detectedDevice.value = null
        if (activeInstance === this) activeInstance = null
        _binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        activeInstance = this
    }

    // Breaks the repeated-dialog cycle: registerMultiCamera() gets called
    // again on every onPause()/onResume(), and showing the system "Allow
    // app to access <device>?" dialog itself triggers that pause/resume on
    // several phones we've tested (Vivo AND Samsung, so it isn't just one
    // OEM) - so re-registering can re-fire onAttachDev for a device that
    // never actually detached, which used to call requestPermission()
    // again and pop a brand new copy of the same dialog every couple of
    // seconds. This vetoes a repeat request for the same device within a
    // short window, so only the first request in a burst actually shows a
    // dialog - the user gets one dialog, not an endless series of them.
    override fun shouldRequestPermission(device: UsbDevice): Boolean {
        val key = "${device.vendorId}:${device.productId}:${device.deviceName}"
        val now = System.currentTimeMillis()
        // Covers the onPause/onResume flicker some phones show the instant
        // the system dialog appears: without this, that flicker can re-fire
        // onAttachDev() for the same still-attached device and pop a SECOND
        // copy of the same dialog on top of the first, forcing the user to
        // dismiss it twice.
        if (key == lastPermissionRequestKey && now - lastPermissionRequestAt < 9_000) {
            return false
        }
        lastPermissionRequestKey = key
        lastPermissionRequestAt = now
        permissionRequestPending = true
        // NOTE: permissionRequestedAt (the give-up clock) is NOT set here.
        // It's only set in onResume() once we know the dialog this request
        // triggered has actually closed - see that comment for why. Setting
        // it here instead would start the clock the instant we ASK, which
        // could fire while the user is still legitimately reading a dialog
        // they haven't tapped yet.
        // A request reaching this point (about to actually show the system
        // dialog) can come from the library's OWN attach-detection path
        // (onAttachDev's fallback/default branches in CameraFragment.kt),
        // NOT just our own startPermissionOpenRetry() loop. That library
        // path never touches our status text/reconnect button - only our
        // loop's own request branch did. So if the screen was last showing
        // "Access denied. Want to reconnect?" (or any other leftover
        // state) from a previous attempt, and a fresh attach then fires a
        // request through the library's path instead of our loop's, the
        // dialog would pop up right over that stale text - looking like
        // Access Denied was showing at the same moment as the dialog.
        // Clearing it here covers every path that can trigger a real
        // permission request, not just our own.
        hideSupportedModelsList()
        _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
        _binding?.reconnectButton?.visibility = View.GONE
        return true
    }

    /**
     * Called by the library when the user taps Cancel/Deny on the system
     * "Allow OpenTouch to access <device>?" dialog. Without this override,
     * the permission retry loop in [startPermissionOpenRetry] would just
     * call requestPermission() again on its next 500ms tick, popping the
     * exact same system dialog straight back up - so an explicit Cancel
     * needs to stop that loop and hand control back to the user via the
     * Reconnect button instead of silently re-prompting.
     */
    override fun onPermissionDenied(device: UsbDevice?) {
        permissionRetryJob?.cancel()
        hideSupportedModelsList()
        _binding?.statusText?.text = getString(R.string.permission_denied_retry)
        _binding?.reconnectButton?.visibility = View.VISIBLE
        // Stop everything until the user explicitly taps Reconnect. See the
        // comment on awaitingManualReconnect for why: showing this very
        // dialog already triggered onPause() on this fragment (a new
        // foreground window does that, same as pressing Home), so without
        // this guard, onResume() (which fires right after the dialog
        // closes) would immediately re-register the camera monitor and
        // replay the auto permission request for the still-attached
        // device - popping the same dialog straight back up.
        awaitingManualReconnect = true
        isPausedForBackground = true
        permissionRequestPending = false
        permissionRequestedAt = null
        unRegisterMultiCamera()
    }

    /**
     * Stops the camera preview/stream. Called when the user taps "Cancel" on
     * the "is this sensor supported?" popup — by that point the library has
     * already requested USB permission and opened the camera automatically
     * (that's what triggers the popup), so declining needs to explicitly
     * close it again rather than just dismissing the dialog.
     */
    fun stopCameraForDeclinedSensor() {
        declinedClose = true
        closeCamera()
    }

    /**
     * Called when the user taps the "Reconnect" button - shown either after
     * declining the sensor popup, or after the permission/attach retry loop
     * in [startPermissionOpenRetry] stalls without ever connecting. Re-runs
     * that same retry loop from a clean state rather than firing a single
     * one-shot request, so a repeat stall still leaves this button visible
     * instead of going silent again.
     */
    private fun onReconnectClicked() {
        _binding?.reconnectButton?.visibility = View.GONE
        pausedForZeroFps = false
        firstUnconnectedSeenAt = null
        if (awaitingManualReconnect) {
            // awaitingManualReconnect becomes true via onPermissionDenied()
            // (an explicit Cancel) or via the give-up check in
            // startPermissionOpenRetry() (confirmed via hasUsbPermission()
            // once the dialog is closed) - both already unregister the
            // camera monitor themselves once they've confirmed a real
            // outcome, so this call is normally a harmless no-op. Kept for
            // safety/clarity and to cover the post-loop "stuck in bootloader
            // the whole 30s" safety net, which also sets this flag.
            // unRegisterMultiCamera() is safe to call even if already
            // unregistered.
            unRegisterMultiCamera()
            permissionRequestPending = false
            permissionRequestedAt = null
            // Undo the teardown from onPermissionDenied() so the camera
            // monitor is watching for the device again.
            awaitingManualReconnect = false
            isPausedForBackground = false
            registerMultiCamera()
        } else {
            permissionRequestPending = false
            permissionRequestedAt = null
        }
        // See firstRecognizedDevice() - ignores unrecognized USB devices
        // (e.g. an OTG adapter with nothing attached yet) so tapping
        // Reconnect with only one of those present correctly shows "Waiting
        // for Touch Sensor" instead of falsely claiming a device was found.
        val device = firstRecognizedDevice()
        if (device != null) {
            // Explicit Reconnect tap — allow the popup to show again for this
            // device by forgetting the previously-shown key.
            lastDetectedDeviceKey = null
            hideSupportedModelsList()
            _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
            startPermissionOpenRetry(initialDelayMs = 0)
        } else {
            showWaitingForSensor()
            startPermissionOpenRetry(initialDelayMs = 500)
        }
    }

    fun applyRgb(red: Int, green: Int, blue: Int) {
        pendingRed = red
        pendingGreen = green
        pendingBlue = blue
        val intensity = ((red and 0xF) shl 8) or ((green and 0xF) shl 4) or (blue and 0xF)
        val camera = getCurrentCamera() as? CameraUVC
        if (camera == null) {
            Logger.i("CameraPreviewFragment", "applyRgb skipped: camera is null, pending=$intensity")
            return
        }
        camera.setZoom(intensity)
        val zoomEcho = camera.getZoom()
        Logger.i("CameraPreviewFragment", "applyRgb r=$red g=$green b=$blue intensity=$intensity zoomEcho=$zoomEcho")
    }

    /**
     * Changes the requested preview frame rate. The underlying camera
     * library only applies a new FPS value the next time the stream is
     * opened, not while it's already running — so this closes the camera
     * and lets the existing auto-reconnect flow (the same one already used
     * when the sensor is unplugged/replugged, or the app resumes) reopen it
     * at the new rate. A [fps] of 0 pauses the preview instead of reopening.
     */
    fun changePreviewFps(fps: Int) {
        _targetFps.value = fps
        if (fps <= 0) {
            pausedForZeroFps = true
            if (isCameraOpened()) closeCamera()
            return
        }
        pausedForZeroFps = false
        setFps(fps)
        if (isCameraOpened()) {
            // Triggers onCameraState(CLOSED) -> pausedForZeroFps is false,
            // so the normal auto-reconnect path reopens at the new fps.
            closeCamera()
        }
        // If the camera isn't open yet, nothing more to do — setFps()
        // already updated the value the next natural open will use.
    }

    /**
     * Changes the requested preview resolution. Like [changePreviewFps], the
     * underlying camera library only reads getCameraRequest() the next time
     * the stream opens, so this stores the new size and closes the camera to
     * let the existing auto-reconnect flow reopen it at the new resolution.
     * [width]/[height] are the sensor's native (unrotated) dimensions - the
     * portrait swap for the on-screen box happens inside getCameraRequest().
     */
    fun changePreviewResolution(width: Int, height: Int) {
        _targetResolution.value = width to height
        if (isCameraOpened()) {
            closeCamera()
        }
    }

    /**
     * Changes both the requested fps and resolution together, as a single
     * close/reopen. [changePreviewFps] and [changePreviewResolution] each
     * independently check isCameraOpened() and call closeCamera() - calling
     * them back-to-back (as the FPS panel's Apply button used to) is racy:
     * the fps change's closeCamera() can kick off the auto-reconnect flow
     * and have it call getCameraRequest() again before the very next line
     * sets the new target resolution, so the reopen silently uses the OLD
     * resolution with the NEW fps. On DIGIT that combination can be a
     * genuinely invalid one per the sensor's spec (e.g. 320x240 has no
     * 15fps mode - only 640x480 does), so the open fails outright and shows
     * "Camera disconnected" even though 640x480@15fps itself is fine.
     *
     * Setting both target values first and closing exactly once removes the
     * race entirely - the reopen (whenever it happens) always sees a
     * consistent, already-valid fps+resolution pair.
     */
    fun changePreviewFpsAndResolution(fps: Int, width: Int, height: Int) {
        _targetFps.value = fps
        _targetResolution.value = width to height
        if (fps <= 0) {
            pausedForZeroFps = true
            if (isCameraOpened()) closeCamera()
            return
        }
        pausedForZeroFps = false
        setFps(fps)
        if (isCameraOpened()) {
            closeCamera()
        }
    }

    // ─── Resolution + supported-size helpers (Settings) ───────────────────────

    /**
     * The preview sizes the CONNECTED sensor actually advertises. For fixed-
     * format sensors (DIGIT / GelSight Mini) this is typically a single entry.
     * Returns an empty list if the camera isn't open yet — callers must handle
     * that.
     */
    fun getSupportedSizes(): List<PreviewSize> {
        if (!isCameraOpened()) return emptyList()
        return try {
            // aspectRatio = null → return every advertised size, unfiltered.
            getCurrentCamera()?.getAllPreviewSizes(null) ?: emptyList()
        } catch (e: Exception) {
            Logger.e("CameraPreviewFragment", "getAllPreviewSizes failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * True only when the fragment is attached AND the USB camera is open and streaming.
     */
    val isCameraReady: Boolean
        get() = isAdded && _binding != null && isCameraOpened()

    /**
     * Identifies which supported sensor is currently connected, so captures can
     * be routed into a matching folder (Pictures/OpenTouch_Digit,
     * Pictures/OpenTouch_GelSightMini, ...). Falls back to "Other" if the
     * connected device doesn't match a known sensor.
     */
    private fun currentSensorFolderName(): String {
        val device = getDeviceList()?.firstOrNull() ?: return "Other"
        val match = SupportedSensors.classify(device.vendorId, device.productId, device.productName)
        return match.sensor?.folderName ?: "Other"
    }

    /**
     * Like getDeviceList()?.firstOrNull(), but ignores any USB device that
     * isn't a recognized sensor (or its known transient bootloader
     * identity). getDeviceList() returns EVERY USB device Android currently
     * sees - including an OTG adapter/hub that's plugged into the tablet
     * with nothing on its far end yet, or any other unrelated accessory.
     * Using the unfiltered list to decide "a sensor was detected" meant the
     * permission-request/stall-timer flow could fire for a device that was
     * never going to be our sensor, eventually showing "Access denied"
     * after ~30s even though no dialog was ever shown and the user hadn't
     * plugged the sensor in yet - confirmed on-device via video where the
     * device list clearly reported something before the sensor's own USB
     * connection request dialog ever appeared.
     */
    private fun firstRecognizedDevice(): UsbDevice? {
        return getDeviceList()?.firstOrNull { device ->
            val isBootloader = device.vendorId == FTDI_BOOTLOADER_VENDOR_ID &&
                    device.productId == FT900_DFU_PRODUCT_ID
            isBootloader || SupportedSensors.classify(
                device.vendorId, device.productId, device.productName
            ).sensor != null
        }
    }

    /**
     * Asks Android directly whether USB permission for this device is
     * currently granted - the exact same check the vendored library's own
     * requestPermission()/schedulePermissionGrantCheck() trust internally
     * (see USBMonitor.java). We call this ourselves in
     * startPermissionOpenRetry()'s give-up check: once onResume() has
     * confirmed a request's dialog actually closed, Android's permission
     * state for that device is final, not "still pending" - so this
     * distinguishes a real grant whose broadcast got lost (hasPermission()
     * true - fixes "Reconnect skips straight to the live view", since
     * permission really was already granted) from a real denial (false -
     * shows "Access denied" instead of the vaguer "No response").
     */
    private fun hasUsbPermission(device: UsbDevice): Boolean {
        return try {
            (context?.getSystemService(Context.USB_SERVICE) as? UsbManager)
                ?.hasPermission(device) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates the photo filename in the format:
     * OpenTouch_Digit_001_2026-05-29_18-30-45.jpg
     * The number is based on how many photos already exist in this sensor's
     * folder, so numbering is independent per sensor and always correct even
     * after the app restarts. The sensor name is baked into both the folder
     * AND the filename itself, since the folder structure is lost if a photo
     * gets shared or exported elsewhere - the filename should stay self-
     * describing on its own.
     */
    private fun generateFileName(sensorFolder: String): String {
        val existingCount = try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection =
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("Pictures/OpenTouch_$sensorFolder/", "OpenTouch_${sensorFolder}_%.jpg")
            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) { 0 }

        val nextNumber = String.format("%03d", existingCount + 1)
        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val timePart = SimpleDateFormat("HH-mm-ss", Locale.US).format(Date())
        return "OpenTouch_${sensorFolder}_${nextNumber}_${datePart}_${timePart}.jpg"
    }

    /**
     * Returns a temporary path inside the app's private folder.
     * This works on ALL Android versions with no permissions needed.
     * We save here first, then move to the public Pictures/OpenTouch_<sensor>/
     * or Movies/OpenTouch_<sensor>/ folder.
     */
    private fun getTempPath(extension: String = "jpg"): String? {
        return try {
            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: requireContext().filesDir
            if (!dir.exists()) dir.mkdirs()
            "${dir.absolutePath}/temp_capture.$extension"
        } catch (e: Exception) {
            Logger.e("CameraPreviewFragment", "getTempPath failed: ${e.message}")
            null
        }
    }

    /**
     * Writes EXIF metadata (date/time + camera ID) into the photo file.
     * Called on the temp file before moving it to public storage.
     * If this fails, the photo is still saved — metadata failure is never fatal.
     */
    private fun writeMetadata(path: String) {
        try {
            val exif = ExifInterface(path)
            val exifDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
            // Which camera took this photo — important for future multi-camera support.
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Camera: $cameraId")
            exif.saveAttributes()
            Logger.i("CameraPreviewFragment", "writeMetadata: saved for $path, camera=$cameraId")
        } catch (e: Exception) {
            Logger.e("CameraPreviewFragment", "writeMetadata failed: ${e.message}")
        }
    }

    /**
     * Moves the photo from the temp private folder to the public
     * Pictures/OpenTouch_<sensor>/ folder.
     * Works on ALL Android versions:
     *   - Android 10+ (API 29+): uses MediaStore API — no extra permission needed.
     *   - Android 9 and below: uses direct file copy — needs WRITE_EXTERNAL_STORAGE permission.
     *
     * Returns the final path/URI string, or null if something went wrong.
     */
    private fun moveToPublicStorage(tempPath: String, fileName: String, sensorFolder: String): String? {
        val context = requireContext()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore. No storage permission needed.
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenTouch_$sensorFolder")
                    // IS_PENDING = 1 means "I'm still writing this file, don't show it yet."
                    // We set it to 0 after the copy is done so the gallery shows it properly.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                ) ?: return null

                // The capture library sometimes hands back a content:// URI instead
                // of a plain file path (it can save directly via MediaStore itself,
                // e.g. into DCIM/Camera with an auto-generated name). Handle both
                // sources so the photo always ends up copied into our own entry.
                val isSourceContentUri = tempPath.startsWith("content://")
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    if (isSourceContentUri) {
                        context.contentResolver.openInputStream(android.net.Uri.parse(tempPath))
                            ?.use { input -> input.copyTo(output) }
                    } else {
                        File(tempPath).inputStream().use { input -> input.copyTo(output) }
                    }
                }

                // Mark file as complete — gallery will now show it
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                // If the source was the library's own stray MediaStore entry, delete
                // it now that we've copied it into the correctly named/located one -
                // otherwise the photo would show up twice in the gallery.
                if (isSourceContentUri) {
                    try {
                        context.contentResolver.delete(android.net.Uri.parse(tempPath), null, null)
                    } catch (e: Exception) {
                        Logger.e("CameraPreviewFragment", "moveToPublicStorage: could not delete stray source: ${e.message}")
                    }
                }

                uri.toString()
            } else {
                // Android 9 and below — direct file copy.
                // WRITE_EXTERNAL_STORAGE permission is declared in the manifest for these versions.
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "OpenTouch_$sensorFolder"
                )
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Logger.e("CameraPreviewFragment", "moveToPublicStorage: could not create OpenTouch dir")
                    return null
                }
                val destFile = File(destDir, fileName)
                File(tempPath).copyTo(destFile, overwrite = true)
                // Tell the gallery app to scan and show this new file.
                MediaScannerConnection.scanFile(
                    context, arrayOf(destFile.absolutePath), arrayOf("image/jpeg"), null
                )
                destFile.absolutePath
            }
        } catch (e: Exception) {
            Logger.e("CameraPreviewFragment", "moveToPublicStorage failed: ${e.message}")
            null
        }
    }

    /**
     * Takes a single photo from the USB camera and saves it to
     * Pictures/OpenTouch_<sensor>/. Works on all Android versions (7 through 14+).
     *
     * Flow:
     *  1. Check storage permission on Android 9 and below
     *  2. Save to a temp private file (always accessible, no permission needed)
     *  3. Write EXIF metadata to the temp file
     *  4. Move temp file to Pictures/OpenTouch_<sensor>/ using the correct method for the Android version
     *  5. Delete the temp file
     *
     * [onDone] is called on the main thread with:
     *   success = true  and the final file path/URI
     *   success = false and an error message
     */
    fun capturePhoto(onDone: (success: Boolean, path: String?) -> Unit) {
        // TEMP DIAGNOSTIC — see isCapturingDiag's comment.
        Logger.w("CameraPreviewFragment", "capturePhoto() called, isCameraReady=$isCameraReady")
        if (!isCameraReady) {
            onDone(false, "Camera is not ready yet")
            return
        }

        // On Android 9 and below, we need WRITE_EXTERNAL_STORAGE permission to save to Pictures/.
        // On Android 10+, MediaStore API handles it without any permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_PERMISSION
                )
                onDone(false, "Storage permission needed — please accept the permission and try again")
                return
            }
        }

        val tempPath = getTempPath()
        if (tempPath == null) {
            onDone(false, "Could not prepare temporary storage")
            return
        }

        isCapturingDiag = true
        captureImage(object : ICaptureCallBack {
            override fun onBegin() {
                Logger.w("CameraPreviewFragment", "capturePhoto onBegin()")
            }

            override fun onError(error: String?) {
                Logger.w("CameraPreviewFragment", "capturePhoto onError: $error")
                isCapturingDiag = false
                activity?.runOnUiThread { onDone(false, error ?: "Unknown capture error") }
            }

            override fun onComplete(path: String?) {
                Logger.w("CameraPreviewFragment", "capturePhoto onComplete: path=$path")
                isCapturingDiag = false
                activity?.runOnUiThread {
                    if (path == null) {
                        onDone(false, "Photo was not saved (null path)")
                        return@runOnUiThread
                    }
                    // No flip step here - deliberately. getCameraView() no
                    // longer applies any scaleX/scaleY flip to the live
                    // preview (see that function's comment - a flip
                    // combined with SENSOR_ROTATE_TYPE's rotation always
                    // produced a mirror image), so the raw captured bitmap
                    // now already matches the live preview as-is, with no
                    // extra transform needed here to keep them in sync.
                    // Step 2: write metadata into the temp file
                    writeMetadata(path)
                    // Step 2: move to public Pictures/OpenTouch_<sensor>/ folder
                    val sensorFolder = currentSensorFolderName()
                    val fileName = generateFileName(sensorFolder)
                    val finalPath = moveToPublicStorage(path, fileName, sensorFolder)
                    // Step 3: delete the temp file regardless of outcome
                    try { File(path).delete() } catch (_: Exception) {}

                    if (finalPath != null) {
                        onDone(true, finalPath)
                    } else {
                        // moveToPublicStorage failed — photo was saved in temp but we couldn't move it.
                        // Still report success since the image data exists.
                        Logger.e("CameraPreviewFragment", "Could not move photo to Pictures/OpenTouch_")
                        onDone(true, path)
                    }
                }
            }
        }, tempPath)
    }

    // ─── Video recording ──────────────────────────────────────────────────────

    /**
     * Generates a video filename:
     * OpenTouch_Digit_VID_001_2026-05-29_18-30-45.mp4
     * Counter is based on existing videos in this sensor's Movies folder, so
     * numbering is independent per sensor. The sensor name is baked into both
     * the folder AND the filename, since folder structure is lost if a video
     * gets shared or exported elsewhere.
     */
    private fun generateVideoFileName(sensorFolder: String): String {
        val existingCount = try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection =
                "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("Movies/OpenTouch_$sensorFolder/", "OpenTouch_${sensorFolder}_VID_%.mp4")
            requireContext().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) { 0 }

        val nextNumber = String.format("%03d", existingCount + 1)
        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val timePart = SimpleDateFormat("HH-mm-ss", Locale.US).format(Date())
        return "OpenTouch_${sensorFolder}_VID_${nextNumber}_${datePart}_${timePart}.mp4"
    }

    /**
     * Moves a finished video from the temp private folder to
     * Movies/OpenTouch_<sensor>/. "Movies" is Android's fixed system directory
     * name for video content (like "Pictures" is for photos) - it can't be
     * renamed, but the folder name underneath it is fully our own.
     * Android 10+: MediaStore API (no permission needed).
     * Android 9-: direct file copy (needs WRITE_EXTERNAL_STORAGE).
     */
    private fun moveVideoToPublicStorage(tempPath: String, fileName: String, sensorFolder: String): String? {
        val context = requireContext()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OpenTouch_$sensorFolder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
                ) ?: return null

                // Same content:// vs plain-file-path handling as moveToPublicStorage.
                val isSourceContentUri = tempPath.startsWith("content://")
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    if (isSourceContentUri) {
                        context.contentResolver.openInputStream(android.net.Uri.parse(tempPath))
                            ?.use { input -> input.copyTo(output) }
                    } else {
                        File(tempPath).inputStream().use { input -> input.copyTo(output) }
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                if (isSourceContentUri) {
                    try {
                        context.contentResolver.delete(android.net.Uri.parse(tempPath), null, null)
                    } catch (e: Exception) {
                        Logger.e("CameraPreviewFragment", "moveVideoToPublicStorage: could not delete stray source: ${e.message}")
                    }
                }

                uri.toString()
            } else {
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "OpenTouch_$sensorFolder"
                )
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Logger.e("CameraPreviewFragment", "moveVideoToPublicStorage: could not create OpenTouch dir")
                    return null
                }
                val destFile = File(destDir, fileName)
                File(tempPath).copyTo(destFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null
                )
                destFile.absolutePath
            }
        } catch (e: Exception) {
            Logger.e("CameraPreviewFragment", "moveVideoToPublicStorage failed: ${e.message}")
            null
        }
    }

    /**
     * Starts video recording.
     * [onStarted] is called immediately on the main thread so the UI can show the recording state.
     * [onDone] is called when recording is stopped and the file is saved.
     */
    fun startVideoRecording(
        onStarted: () -> Unit,
        onDone: RecordingCallback
    ) {
        if (!isCameraReady) {
            onDone(false, "Camera is not ready yet")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_PERMISSION
                )
                onDone(false, "Storage permission needed — please accept and try again")
                return
            }
        }

        val tempPath = getTempPath("mp4")
        if (tempPath == null) {
            onDone(false, "Could not prepare temporary storage")
            return
        }

        captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() {
                // onBegin fires on the camera thread — bounce to main thread for UI.
                activity?.runOnUiThread { onStarted() }
            }

            override fun onError(error: String?) {
                activity?.runOnUiThread { onDone(false, error ?: "Unknown recording error") }
            }

            override fun onComplete(path: String?) {
                activity?.runOnUiThread {
                    if (path == null) {
                        onDone(false, "Video was not saved (null path)")
                        return@runOnUiThread
                    }
                    val sensorFolder = currentSensorFolderName()
                    val fileName = generateVideoFileName(sensorFolder)
                    val finalPath = moveVideoToPublicStorage(path, fileName, sensorFolder)
                    try { File(path).delete() } catch (_: Exception) {}

                    if (finalPath != null) {
                        onDone(true, finalPath)
                    } else {
                        Logger.e("CameraPreviewFragment", "Could not move video to Movies/OpenTouch_")
                        onDone(true, path) // temp path — video still exists
                    }
                }
            }
        }, tempPath)
    }

    /**
     * Stops an in-progress recording. onDone from [startVideoRecording] will be called
     * once the file is finalised and moved to Movies/OpenTouch_<sensor>/.
     */
    fun stopVideoRecording() {
        captureVideoStop()
    }

    private fun startPermissionOpenRetry(initialDelayMs: Long) {
        permissionRetryJob?.cancel()
        permissionRetryJob = lifecycleScope.launch {
            delay(initialDelayMs)
            // Seed from the companion flag, not just false: this loop gets
            // relaunched fresh on every onResume(), but if a request from
            // a PREVIOUS run of this loop is still genuinely awaiting an
            // answer, we must not re-request just because this particular
            // run is new - see permissionRequestPending's comment.
            var permissionRequested = permissionRequestPending
            // Tracks whether ANY device showed up at all during this run.
            // Only used below to tell "a sensor sat here stuck in its
            // bootloader identity the whole 30s, never even getting to a
            // real request" (worth surfacing) apart from "nothing has been
            // plugged in this whole time" (the normal waiting state).
            var everSawDevice = false
            // Counts consecutive polls (500ms apart) where no recognized
            // device was found. See its use below: a SINGLE miss is treated
            // as a momentary USB re-enumeration gap, not a real unplug.
            var consecutiveMisses = 0
            // "Access denied. Want to reconnect?" and "No response. Want to
            // reconnect?" are now resolved with real evidence, not a guess:
            // once onResume() confirms a pending request's dialog has
            // actually closed (permissionRequestedAt gets set there - see
            // that comment), Android's own hasUsbPermission() check below
            // gives a FINAL, authoritative answer for that device - the
            // same check the vendored library itself trusts internally
            // (see USBMonitor.requestPermission()'s hasPermission() check
            // and its schedulePermissionGrantCheck() polling). So:
            // - hasUsbPermission() true  -> it really WAS granted; the
            //   broadcast telling us just never arrived. Silently
            //   re-request (the library detects the existing grant and
            //   opens the camera directly, no dialog shown again) instead
            //   of showing anything at all.
            // - hasUsbPermission() false -> since the dialog is confirmed
            //   closed, this genuinely means denied (a real Cancel whose
            //   broadcast got lost, same underlying issue as a lost grant)
            //   - so THIS shows "Access denied", not a vague "No response".
            // "No response" is now only a last-resort safety net for the
            // case where firstDevice is somehow null right at this instant
            // (so hasUsbPermission() can't even be checked) - a "No
            // response. Want to reconnect?" string was kept for that.
            repeat(60) {
                if (!isAdded || _binding == null) return@launch
                if (isCameraOpened()) return@launch
                // See firstRecognizedDevice() - ignores unrecognized USB
                // devices (e.g. an OTG adapter with nothing attached yet)
                // so they can't trigger the permission/stall flow. Read
                // once per iteration and reused below by the give-up check,
                // which needs to know which device a pending request was
                // actually for.
                val firstDevice = firstRecognizedDevice()
                // permissionRequestedAt is set in onResume() - see that
                // comment - ONLY once we know this request's dialog has
                // actually closed (onResume() firing again while a request
                // is still pending). So this is "give up ~5s after we know
                // the dialog closed with no answer following", NOT "~5s
                // since we sent the request" - it deliberately can't fire
                // while the dialog is still legitimately open on screen,
                // no matter how long that takes.
                val requestedAt = permissionRequestedAt
                if (permissionRequested && requestedAt != null &&
                    System.currentTimeMillis() - requestedAt >= 5_000
                ) {
                    if (firstDevice != null && hasUsbPermission(firstDevice)) {
                        permissionRequestedAt = null
                        requestPermission(firstDevice)
                        return@repeat
                    }
                    hideSupportedModelsList()
                    _binding?.statusText?.text = getString(
                        if (firstDevice != null) R.string.permission_denied_retry
                        else R.string.permission_no_response_retry
                    )
                    _binding?.reconnectButton?.visibility = View.VISIBLE
                    awaitingManualReconnect = true
                    isPausedForBackground = true
                    // Confirmed denial (or the rare case firstDevice was
                    // null right here) - safe to actually clear the pending
                    // state and tear the monitor down now, unlike before:
                    // hasUsbPermission() just gave us a FINAL answer, so
                    // there's nothing left in flight to orphan.
                    permissionRequestPending = false
                    permissionRequestedAt = null
                    unRegisterMultiCamera()
                    return@launch
                }
                if (firstDevice != null) {
                    everSawDevice = true
                    consecutiveMisses = 0
                    // The sensor's FTDI FT900 chip briefly shows up as its own
                    // bootloader ("FT900 DFU Mode") for a second or two while it
                    // boots, before re-enumerating as the real "DIGIT" sensor.
                    // If we show the popup for that transient identity, the user
                    // sees "not supported" for their actual (supported) sensor.
                    // So: ignore this identity for a few seconds and wait for the
                    // real device to show up. If it's STILL stuck like this after
                    // ~6 seconds, fall through as normal (so a genuinely
                    // stuck-in-DFU sensor doesn't hang forever).
                    val isTransientBootloader = firstDevice.vendorId == FTDI_BOOTLOADER_VENDOR_ID &&
                            firstDevice.productId == FT900_DFU_PRODUCT_ID
                    if (isTransientBootloader && ftdiBootloaderPollCount < 12) {
                        ftdiBootloaderPollCount++
                        showWaitingForSensor()
                        lastDetectedDeviceKey = null
                        delay(500)
                        return@repeat
                    }
                    ftdiBootloaderPollCount = 0

                    // See the comment on firstUnconnectedSeenAt: this survives
                    // the loop itself being cancelled/restarted (e.g. by the
                    // onPause/onResume flicker some phones show while the
                    // permission dialog is up).
                    //
                    // No fast-fail timer here anymore - an explicit Cancel
                    // is handled instantly via onPermissionDenied() below,
                    // and otherwise we simply wait, no matter how long the
                    // dialog rendering, the user reading/tapping it, the
                    // grant round-trip, or the camera actually opening
                    // takes.
                    if (!permissionRequested) {
                        // First time we see the device — request permission,
                        // which shows the "Allow OpenTouch to access …?" dialog.
                        hideSupportedModelsList()
                        _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
                        // A Reconnect button left over from an earlier stall/
                        // denial contradicts "requesting permission..." being
                        // shown at the same time — clear it now that we're
                        // actively back in a request attempt.
                        _binding?.reconnectButton?.visibility = View.GONE
                        permissionRequestPending = true
                        // NOTE: permissionRequestedAt is NOT set here - see
                        // the NOTE in shouldRequestPermission() for why; it's
                        // only set once onResume() confirms this dialog has
                        // actually closed.
                        requestPermission(firstDevice)
                        permissionRequested = true
                    } else {
                        // Permission was already requested and we're just
                        // waiting for the user to answer the system dialog
                        // (or for the camera to finish opening after they hit
                        // OK). Do NOT call requestPermission() again here -
                        // that used to happen on every 500ms tick as a
                        // workaround, but it re-issues a brand new request
                        // while the first one is still pending. That new
                        // request pops the same system dialog right back up
                        // moments after the user taps Cancel, racing against
                        // onPermissionDenied()'s job cancellation - which is
                        // exactly why Cancel looked like it did nothing.
                        // Just leave the state as-is and let onConnectDev
                        // (granted) or onPermissionDenied (denied) decide
                        // what happens next. No early "still waiting" UI
                        // here - an explicit Cancel already shows "Access
                        // denied..." instantly via onPermissionDenied(), and
                        // the give-up check above (once onResume() confirms
                        // the dialog closed) covers the OS silently dropping
                        // the answer entirely.
                    }
                } else {
                    consecutiveMisses++
                    if (consecutiveMisses < 2) {
                        // Closing the camera to apply a new FPS/resolution
                        // (see changePreviewFps/changePreviewFpsAndResolution)
                        // can make the still-attached sensor briefly vanish
                        // from getDeviceList() during USB re-enumeration -
                        // the "[USBMonitor] get permission failed in
                        // mUsbReceiver" / processCancel hiccup seen in
                        // Logcat right before the camera silently reopens on
                        // its own. Treating that single missed poll as a
                        // genuine unplug used to wipe lastDetectedDeviceKey
                        // here, which then made onCameraState(OPENED) think
                        // a "new" sensor had just connected and reset the
                        // user's just-chosen _targetFps/_targetResolution
                        // back to null right as the camera reopened - so an
                        // Apply of e.g. 640x480@15fps silently reopened at
                        // the old/default 320x240 instead, which isn't a
                        // valid combo at 15fps and failed with "Camera
                        // disconnected". Waiting for a second consecutive
                        // miss (~1s of continuous absence) before declaring
                        // an unplug rides out that gap while still reacting
                        // quickly to a real unplug.
                        delay(500)
                        return@repeat
                    }
                    // No device connected (genuine unplug) — reset so the next
                    // physical connection shows the popup again, and clear the
                    // popup state so it can't show with nothing attached.
                    permissionRequested = false
                    lastDetectedDeviceKey = null
                    _detectedDevice.value = null
                    firstUnconnectedSeenAt = null
                    lastPermissionRequestKey = null
                    permissionRequestPending = false
                    permissionRequestedAt = null
                    ftdiBootloaderPollCount = 0
                    // A Reconnect button left over from an earlier stall/
                    // denial doesn't make sense once there's genuinely
                    // nothing plugged in - hide it so the passive "waiting"
                    // state doesn't show a button with nothing to reconnect
                    // to.
                    _binding?.reconnectButton?.visibility = View.GONE
                }
                delay(500)
            }
            // Reached once all 60 attempts (~30s) of this run pass without
            // the camera opening. If a request is still pending, the give-up
            // check above will already have handled it once its dialog
            // closes (or will on a future run) - this is only reached extra
            // for the case where a device
            // sat here the WHOLE 30s without a request ever being made at
            // all (e.g. stuck in the FTDI bootloader identity way longer
            // than its usual ~6s). No dialog was ever shown here either, so
            // same "No response" wording, not "Access denied".
            if (isAdded && _binding != null && !isCameraOpened()) {
                if (everSawDevice && !permissionRequested) {
                    hideSupportedModelsList()
                    _binding?.statusText?.text = getString(R.string.permission_no_response_retry)
                    _binding?.reconnectButton?.visibility = View.VISIBLE
                    awaitingManualReconnect = true
                    isPausedForBackground = true
                    unRegisterMultiCamera()
                } else {
                    startPermissionOpenRetry(initialDelayMs = 0)
                }
            }
        }
    }

    /** The user's response to the "Connect/Cancel" sensor popup. */
    enum class ConnectDecision { NONE, CONNECT, CANCEL }

    /** A USB device that was just detected, for the "is this sensor supported?" popup. */
    /**
     * A USB device that was just detected. Sensors connect automatically now —
     * this is read by the Settings menu to show identity info (and offer a
     * manual disconnect) rather than by an interrupting connect/cancel popup.
     */
    data class DetectedDevice(
        val name: String,
        val vendorId: Int,
        val productId: Int,
        // Null if the device's USB descriptor doesn't report one, or if
        // reading it throws (some devices/API levels refuse this even with
        // permission already granted).
        val serialNumber: String?,
        // Increments each time a device is (re)detected. Without this, plugging
        // the same sensor back in would produce an identical DetectedDevice
        // (data classes compare by value), so Compose state wouldn't register
        // a change.
        val sequence: Int
    )

    companion object {
        private const val REQUEST_WRITE_PERMISSION = 1001

        // FTDI's vendor ID, and the product ID the FT900 chip on the sensor
        // reports while it's still in its bootloader (DFU) mode during boot —
        // before it re-enumerates as the real "DIGIT" sensor.
        private const val FTDI_BOOTLOADER_VENDOR_ID = 0x0403
        private const val FT900_DFU_PRODUCT_ID = 0x0FDE

        // Change this to ANGLE_90 / ANGLE_0 / ANGLE_180 / FLIP_UP_DOWN /
        // FLIP_LEFT_RIGHT to correct the sensor's orientation on screen —
        // see getCameraRequest() above for details. ANGLE_90 alone (a pure
        // rotation, no flip - see getCameraView()) showed the image upside
        // down but correctly NOT mirrored (confirmed on-device: "RUPEES"
        // and "5" read correctly once rotated another 180 degrees) -
        // ANGLE_270 is exactly ANGLE_90 plus that missing 180 degrees.
        private val SENSOR_ROTATE_TYPE = RotateType.ANGLE_270

        @Volatile
        private var activeInstance: CameraPreviewFragment? = null

        // The most recently detected USB device, and whether the UI has already
        // shown a popup for it. DemoScreen reads [detectedDevice] and shows a
        // dialog whenever it changes to a new, non-null value.
        private val _detectedDevice = mutableStateOf<DetectedDevice?>(null)
        val detectedDevice: State<DetectedDevice?> get() = _detectedDevice

        // Live-measured preview frame rate, sampled once per second by the
        // active fragment. DemoScreen reads this for the read-only FPS display.
        private val _currentFps = mutableStateOf(0)
        val currentFps: State<Int> get() = _currentFps

        // The FPS the user has requested via the Settings slider. Null means
        // "no explicit choice yet" - DemoScreen falls back to the connected
        // sensor's rated max in that case. Reset to null whenever a new
        // sensor is detected so a leftover value from a previous sensor
        // (e.g. 60 from a DIGIT) can't be carried over to a different one.
        private val _targetFps = mutableStateOf<Int?>(null)
        val targetFps: State<Int?> get() = _targetFps

        // The (width, height) the user has requested via the Settings
        // resolution row. Null means "no explicit choice yet" - DemoScreen
        // falls back to the connected sensor's native resolution in that
        // case. Reset to null on a new sensor detection, same as
        // [_targetFps], so a leftover size from a previous sensor can't leak
        // into a different one's request.
        private val _targetResolution = mutableStateOf<Pair<Int, Int>?>(null)
        val targetResolution: State<Pair<Int, Int>?> get() = _targetResolution

        // VID:PID of the last device we showed the popup for. Reset only on a
        // genuine unplug (device list empty) — NOT on the close/reopen of an
        // app resume — so the popup shows once per physical connection.
        private var lastDetectedDeviceKey: String? = null

        // Wall-clock timestamp of the first time we saw a device present but
        // not yet connected. This is companion-level (survives across
        // startPermissionOpenRetry() being cancelled and relaunched) on
        // purpose: on some phones, showing the system permission dialog
        // causes this fragment's onPause()/onResume() to fire repeatedly
        // (confirmed via on-device tracing - a phone/OEM quirk, not
        // something this app controls), which cancels and restarts the
        // 30-iteration retry loop before it ever completes and before its
        // own "stalled" fallback can fire. Tracking elapsed real time here
        // instead of a per-loop-run iteration count means a real stall is
        // still tracked correctly regardless of how many times the loop
        // itself got restarted in between.
        //
        // The 1.5s fast-fail in startPermissionOpenRetry() only applies
        // BEFORE permission has been requested - see the comment there for
        // why it must not keep counting through the dialog-answer/camera-
        // open phase.
        private var firstUnconnectedSeenAt: Long? = null

        // See shouldRequestPermission() below - de-dupes repeat permission
        // requests for the same still-attached device within a short
        // window, so the onPause/onResume flicker some phones show while
        // the system dialog is up can't pop a fresh copy of that dialog
        // every cycle.
        private var lastPermissionRequestKey: String? = null
        private var lastPermissionRequestAt: Long = 0L

        // True from the moment we call requestPermission() until we know
        // the outcome (camera opens, we give up after the 6s stall check,
        // or the device is unplugged). ROOT CAUSE FOUND: USBMonitor's
        // permission broadcast receiver is registered under an action
        // string unique to that USBMonitor INSTANCE
        // ("com.serenegiant.USB_PERMISSION." + instance hashCode - see
        // USBMonitor.java). unRegisterMultiCamera() tears that receiver
        // down. Showing the system "Allow app to access <device>?" dialog
        // itself triggers this fragment's onPause() within milliseconds
        // (confirmed via on-device tracing, on more than one phone) - and
        // onPause() used to unconditionally call unRegisterMultiCamera(),
        // which destroyed the exact receiver that would have caught the
        // user's eventual Cancel/OK answer. That's the real reason
        // onCancelDev() never fired in ANY test, regardless of phone: the
        // broadcast had nowhere to land by the time the user answered.
        // While this is true, onPause() must NOT tear down the camera
        // client - it just cancels our own polling loop instead, and
        // resumes it (without re-registering) once we're foreground again.
        private var permissionRequestPending = false

        // Wall-clock time onResume() confirmed a pending request's dialog
        // has actually closed (set in onResume(), NOT when the request was
        // originally sent - see that comment for why). Drives the "give up
        // after ~5s of no answer FOLLOWING the dialog closing" check in
        // startPermissionOpenRetry() - a safety net for when the OS
        // silently drops the dialog's answer entirely (confirmed on-device:
        // 77s of total silence with no callback at all in one test),
        // separate from the instant, real Cancel handled by
        // onPermissionDenied(). Once this fires, hasUsbPermission() gives a
        // final, authoritative answer for what actually happened: silently
        // re-request and open the camera if it was really granted (broadcast
        // just got lost), or show "Access denied. Want to reconnect?" if it
        // wasn't - see the give-up check in startPermissionOpenRetry() for
        // both branches. Deliberately can't fire while the dialog is still
        // open on screen, no matter how long that takes - only once it's
        // confirmed closed does this clock even start. Null whenever no
        // request is currently pending, or its dialog hasn't closed yet.
        private var permissionRequestedAt: Long? = null

        // Bumped every time a device is (re)detected — see DetectedDevice.sequence.
        private var detectionSequence = 0

        // How many consecutive polls we've seen the FT900 bootloader identity —
        // see the "isTransientBootloader" check in startPermissionOpenRetry().
        private var ftdiBootloaderPollCount = 0

        // The user's response to the "Connect/Cancel" popup for the most
        // recently detected device. The polling loop waits on this before
        // requesting USB permission.
        @Volatile
        private var _connectDecision: ConnectDecision = ConnectDecision.NONE

        /** Call when the user taps "Connect" on the sensor popup. */
        fun confirmConnect() {
            _connectDecision = ConnectDecision.CONNECT
        }

        /** Call when the user taps "Cancel" on the sensor popup. */
        fun declineConnect() {
            _connectDecision = ConnectDecision.CANCEL
            // The camera was already opened automatically (that's what
            // triggered this popup) — stop streaming since the user declined.
            activeInstance?.stopCameraForDeclinedSensor()
        }

        fun pushRgb(red: Int, green: Int, blue: Int) {
            activeInstance?.applyRgb(red, green, blue)
        }

        /** Requests a new preview FPS (0 pauses the preview). See [changePreviewFps]. */
        fun requestSetFps(fps: Int) {
            activeInstance?.changePreviewFps(fps)
        }

        /** Requests a new preview resolution. See [changePreviewResolution]. */
        fun requestSetResolution(width: Int, height: Int) {
            activeInstance?.changePreviewResolution(width, height)
        }

        /**
         * Requests a new fps + resolution together as a single close/reopen.
         * Prefer this over calling [requestSetFps] and [requestSetResolution]
         * back-to-back - see [changePreviewFpsAndResolution] for why that's
         * racy.
         */
        fun requestSetFpsAndResolution(fps: Int, width: Int, height: Int) {
            activeInstance?.changePreviewFpsAndResolution(fps, width, height)
        }

        /** Sizes the connected sensor supports. Empty if no camera is open. */
        fun supportedSizes(): List<PreviewSize> =
            activeInstance?.getSupportedSizes() ?: emptyList()

        fun requestCapture(onDone: (success: Boolean, path: String?) -> Unit): Boolean {
            val instance = activeInstance ?: return false
            if (!instance.isCameraReady) return false
            instance.capturePhoto(onDone)
            return true
        }

        /**
         * Starts video recording. Returns false if the camera is not ready.
         * [onStarted] fires as soon as the encoder begins (use it to flip the UI to "recording").
         * [onDone] fires when the file is saved after [requestStopRecording] is called.
         */
        fun requestStartRecording(
            onStarted: () -> Unit,
            onDone: RecordingCallback
        ): Boolean {
            val instance = activeInstance ?: return false
            if (!instance.isCameraReady) return false
            instance.startVideoRecording(onStarted, onDone)
            return true
        }

        /** Stops the current recording. Does nothing if no recording is active. */
        fun requestStopRecording() {
            activeInstance?.stopVideoRecording()
        }
    }
}