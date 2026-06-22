package com.opentouch.sensorapp.presentation.fragment

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
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
import com.opentouch.sensorapp.databinding.FragmentCameraPreviewBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
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

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentCameraPreviewBinding.inflate(inflater, container, false)
        binding.reconnectButton.setOnClickListener { onReconnectClicked() }
        return binding.root
    }

    // scaleY = -1f flips only the camera image vertically (so the bottom of
    // the physical sensor maps to the bottom of the screen). This affects
    // just the camera preview surface — text and other UI on screen are
    // untouched.
    override fun getCameraView(): IAspectRatio = FillTextureView(requireContext()).apply {
        scaleY = -1f
    }

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
        val isQuarterTurn = SENSOR_ROTATE_TYPE == RotateType.ANGLE_90 ||
            SENSOR_ROTATE_TYPE == RotateType.ANGLE_270
        val width = if (isQuarterTurn) 240 else 320
        val height = if (isQuarterTurn) 320 else 240

        return CameraRequest.Builder()
            .setPreviewWidth(width)
            .setPreviewHeight(height)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(SENSOR_ROTATE_TYPE)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setAspectRatioShow(true)
            .setCaptureRawImage(false)
            .setRawPreviewData(false)
            .create()
    }

    override fun initData() {
        super.initData()
        _binding?.statusText?.text = getString(R.string.camera_waiting_for_device)
        startPermissionOpenRetry(initialDelayMs = 600)
    }

    override fun onResume() {
        super.onResume()
        startPermissionOpenRetry(initialDelayMs = 250)
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        val statusView = view?.findViewById<TextView>(R.id.statusText) ?: return
        when (code) {
            ICameraStateCallBack.State.OPENED -> {
                statusView.visibility = View.GONE
                _binding?.reconnectButton?.visibility = View.GONE
                applyRgb(pendingRed, pendingGreen, pendingBlue)
                permissionRetryJob?.cancel()

                // The system "Allow Open Touch to access ...?" dialog (shown
                // automatically when the USB device was attached) has now
                // been resolved and the camera is streaming. Show the
                // "is this sensor supported?" popup now, so it appears AFTER
                // the system dialog instead of at the same time as it.
                val device = getDeviceList()?.firstOrNull()
                if (device != null) {
                    val key = "${device.vendorId}:${device.productId}"
                    if (key != lastDetectedDeviceKey) {
                        lastDetectedDeviceKey = key
                        detectionSequence++
                        _connectDecision = ConnectDecision.NONE
                        _detectedDevice.value = DetectedDevice(
                            name = device.productName?.takeIf { it.isNotBlank() }
                                ?: "Unknown USB device",
                            vendorId = device.vendorId,
                            productId = device.productId,
                            sequence = detectionSequence
                        )
                    }
                }
            }
            ICameraStateCallBack.State.CLOSED -> {
                statusView.visibility = View.VISIBLE
                // Forget the device so the "sensor connected" popup shows
                // again the next time a camera opens for it.
                lastDetectedDeviceKey = null
                if (declinedClose) {
                    // The user tapped "Cancel" on the sensor popup, which is
                    // what closed the camera. Show a clear "disconnected"
                    // message with a way to reconnect, instead of the
                    // misleading "requesting permission" polling status.
                    declinedClose = false
                    statusView.text = getString(R.string.sensor_disconnected_declined)
                    _binding?.reconnectButton?.visibility = View.VISIBLE
                } else {
                    // The sensor was unplugged (or the camera otherwise
                    // closed for some other reason). Resume looking for a
                    // device.
                    statusView.text = getString(R.string.camera_disconnected)
                    _binding?.reconnectButton?.visibility = View.GONE
                    startPermissionOpenRetry(initialDelayMs = 500)
                }
            }
            ICameraStateCallBack.State.ERROR -> {
                statusView.visibility = View.VISIBLE
                statusView.text = getString(R.string.camera_error, msg ?: "unknown")
            }
        }
    }

    override fun onDestroyView() {
        permissionRetryJob?.cancel()
        permissionRetryJob = null
        if (activeInstance === this) activeInstance = null
        _binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        activeInstance = this
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
     * Called when the user taps the "Reconnect" button shown after declining
     * the sensor popup. Re-requests permission for the still-attached
     * device, which Android typically grants instantly (no system dialog)
     * since it was already approved once, re-opening the camera and showing
     * the sensor popup again.
     */
    private fun onReconnectClicked() {
        _binding?.reconnectButton?.visibility = View.GONE
        val device = getDeviceList()?.firstOrNull()
        if (device != null) {
            _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
            requestPermission(device)
        } else {
            _binding?.statusText?.text = getString(R.string.camera_waiting_for_device)
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
     * True only when the fragment is attached AND the USB camera is open and streaming.
     */
    val isCameraReady: Boolean
        get() = isAdded && _binding != null && isCameraOpened()

    /**
     * Generates the photo filename in the format: DIGIT_001_2026-05-29_18-30-45.jpg
     * The number is based on how many DIGIT photos already exist so it's always correct
     * even after the app restarts.
     */
    private fun generateFileName(): String {
        val existingCount = try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("DIGIT_%.jpg")
            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) { 0 }

        val nextNumber = String.format("%03d", existingCount + 1)
        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val timePart = SimpleDateFormat("HH-mm-ss", Locale.US).format(Date())
        return "DIGIT_${nextNumber}_${datePart}_${timePart}.jpg"
    }

    /**
     * Returns a temporary path inside the app's private folder.
     * This works on ALL Android versions with no permissions needed.
     * We save here first, then move to the public Pictures/DIGIT/ or Videos/DIGIT/ folder.
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
     * Moves the photo from the temp private folder to the public Pictures/DIGIT/ folder.
     * Works on ALL Android versions:
     *   - Android 10+ (API 29+): uses MediaStore API — no extra permission needed.
     *   - Android 9 and below: uses direct file copy — needs WRITE_EXTERNAL_STORAGE permission.
     *
     * Returns the final path/URI string, or null if something went wrong.
     */
    private fun moveToPublicStorage(tempPath: String, fileName: String): String? {
        val context = requireContext()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore. No storage permission needed.
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DIGIT")
                    // IS_PENDING = 1 means "I'm still writing this file, don't show it yet."
                    // We set it to 0 after the copy is done so the gallery shows it properly.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                ) ?: return null

                // Copy temp file into the MediaStore entry
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    File(tempPath).inputStream().use { input -> input.copyTo(output) }
                }

                // Mark file as complete — gallery will now show it
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                uri.toString()
            } else {
                // Android 9 and below — direct file copy.
                // WRITE_EXTERNAL_STORAGE permission is declared in the manifest for these versions.
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "DIGIT"
                )
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Logger.e("CameraPreviewFragment", "moveToPublicStorage: could not create DIGIT dir")
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
     * Takes a single photo from the USB camera and saves it to Pictures/DIGIT/.
     * Works on all Android versions (7 through 14+).
     *
     * Flow:
     *  1. Check storage permission on Android 9 and below
     *  2. Save to a temp private file (always accessible, no permission needed)
     *  3. Write EXIF metadata to the temp file
     *  4. Move temp file to Pictures/DIGIT/ using the correct method for the Android version
     *  5. Delete the temp file
     *
     * [onDone] is called on the main thread with:
     *   success = true  and the final file path/URI
     *   success = false and an error message
     */
    fun capturePhoto(onDone: (success: Boolean, path: String?) -> Unit) {
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

        captureImage(object : ICaptureCallBack {
            override fun onBegin() {}

            override fun onError(error: String?) {
                activity?.runOnUiThread { onDone(false, error ?: "Unknown capture error") }
            }

            override fun onComplete(path: String?) {
                activity?.runOnUiThread {
                    if (path == null) {
                        onDone(false, "Photo was not saved (null path)")
                        return@runOnUiThread
                    }
                    // Step 1: write metadata into the temp file
                    writeMetadata(path)
                    // Step 2: move to public Pictures/DIGIT/ folder
                    val fileName = generateFileName()
                    val finalPath = moveToPublicStorage(path, fileName)
                    // Step 3: delete the temp file regardless of outcome
                    try { File(path).delete() } catch (_: Exception) {}

                    if (finalPath != null) {
                        onDone(true, finalPath)
                    } else {
                        // moveToPublicStorage failed — photo was saved in temp but we couldn't move it.
                        // Still report success since the image data exists.
                        Logger.e("CameraPreviewFragment", "Could not move photo to Pictures/DIGIT/")
                        onDone(true, path)
                    }
                }
            }
        }, tempPath)
    }

    // ─── Video recording ──────────────────────────────────────────────────────

    /**
     * Generates a video filename: DIGIT_VID_001_2026-05-29_18-30-45.mp4
     * Counter is based on existing DIGIT_VID_ files in the Videos collection.
     */
    private fun generateVideoFileName(): String {
        val existingCount = try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("DIGIT_VID_%.mp4")
            requireContext().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) { 0 }

        val nextNumber = String.format("%03d", existingCount + 1)
        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val timePart = SimpleDateFormat("HH-mm-ss", Locale.US).format(Date())
        return "DIGIT_VID_${nextNumber}_${datePart}_${timePart}.mp4"
    }

    /**
     * Moves a finished video from the temp private folder to Videos/DIGIT/.
     * Android 10+: MediaStore API (no permission needed).
     * Android 9-: direct file copy (needs WRITE_EXTERNAL_STORAGE).
     */
    private fun moveVideoToPublicStorage(tempPath: String, fileName: String): String? {
        val context = requireContext()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DIGIT")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
                ) ?: return null

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    File(tempPath).inputStream().use { input -> input.copyTo(output) }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                uri.toString()
            } else {
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "DIGIT"
                )
                if (!destDir.exists() && !destDir.mkdirs()) {
                    Logger.e("CameraPreviewFragment", "moveVideoToPublicStorage: could not create DIGIT dir")
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
                    val fileName = generateVideoFileName()
                    val finalPath = moveVideoToPublicStorage(path, fileName)
                    try { File(path).delete() } catch (_: Exception) {}

                    if (finalPath != null) {
                        onDone(true, finalPath)
                    } else {
                        Logger.e("CameraPreviewFragment", "Could not move video to Movies/DIGIT/")
                        onDone(true, path) // temp path — video still exists
                    }
                }
            }
        }, tempPath)
    }

    /**
     * Stops an in-progress recording. onDone from [startVideoRecording] will be called
     * once the file is finalised and moved to Videos/DIGIT/.
     */
    fun stopVideoRecording() {
        captureVideoStop()
    }

    private fun startPermissionOpenRetry(initialDelayMs: Long) {
        permissionRetryJob?.cancel()
        permissionRetryJob = lifecycleScope.launch {
            delay(initialDelayMs)
            var permissionRequested = false
            repeat(30) {
                if (!isAdded || _binding == null) return@launch
                if (isCameraOpened()) return@launch
                val firstDevice = getDeviceList()?.firstOrNull()
                if (firstDevice != null) {
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
                        _binding?.statusText?.text = getString(R.string.camera_waiting_for_device)
                        lastDetectedDeviceKey = null
                        delay(500)
                        return@repeat
                    }
                    ftdiBootloaderPollCount = 0

                    if (!permissionRequested) {
                        // First time we see the device — request permission,
                        // which shows the "Allow Open Touch to access …?" dialog.
                        _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
                        requestPermission(firstDevice)
                        permissionRequested = true
                    } else {
                        // Permission was already requested (user either tapped OK
                        // and the camera is still opening, or tapped Cancel and
                        // we're re-requesting on the next poll). Show a message
                        // that reflects the actual state — not "requesting" again.
                        _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
                        requestPermission(firstDevice)
                    }
                } else {
                    // No device connected — reset so the next attach starts fresh.
                    permissionRequested = false
                    lastDetectedDeviceKey = null
                }
                delay(500)
            }
        }
    }

    /** The user's response to the "Connect/Cancel" sensor popup. */
    enum class ConnectDecision { NONE, CONNECT, CANCEL }

    /** A USB device that was just detected, for the "is this sensor supported?" popup. */
    data class DetectedDevice(
        val name: String,
        val vendorId: Int,
        val productId: Int,
        // Increments each time a device is (re)detected. Without this, plugging
        // the same sensor back in would produce an identical DetectedDevice
        // (data classes compare by value), so Compose state wouldn't register
        // a change and the popup wouldn't reappear.
        val sequence: Int
    )

    companion object {
        private const val REQUEST_WRITE_PERMISSION = 1001

        // FTDI's vendor ID, and the product ID the FT900 chip on the sensor
        // reports while it's still in its bootloader (DFU) mode during boot —
        // before it re-enumerates as the real "DIGIT" sensor.
        private const val FTDI_BOOTLOADER_VENDOR_ID = 0x0403
        private const val FT900_DFU_PRODUCT_ID = 0x0FDE

        // Change this to ANGLE_270 / ANGLE_0 / ANGLE_180 / FLIP_UP_DOWN /
        // FLIP_LEFT_RIGHT to correct the sensor's orientation on screen —
        // see getCameraRequest() above for details.
        private val SENSOR_ROTATE_TYPE = RotateType.ANGLE_90

        @Volatile
        private var activeInstance: CameraPreviewFragment? = null

        // The most recently detected USB device, and whether the UI has already
        // shown a popup for it. DemoScreen reads [detectedDevice] and shows a
        // dialog whenever it changes to a new, non-null value.
        private val _detectedDevice = mutableStateOf<DetectedDevice?>(null)
        val detectedDevice: State<DetectedDevice?> get() = _detectedDevice

        // VID:PID of the last device we reported, so we don't spam the popup
        // every poll cycle while waiting for permission/connection.
        private var lastDetectedDeviceKey: String? = null

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
