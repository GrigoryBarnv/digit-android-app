package com.example.digit_app.presentation.fragment

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.digit_app.R
import com.example.digit_app.databinding.FragmentCameraPreviewBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CameraPreviewFragment : CameraFragment() {
    private var pendingRed = 0
    private var pendingGreen = 0
    private var pendingBlue = 0

    private var _binding: FragmentCameraPreviewBinding? = null
    private val binding: FragmentCameraPreviewBinding
        get() = _binding!!
    private var permissionRetryJob: Job? = null

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        // Inflate XML container where CameraFragment injects its preview view.
        _binding = FragmentCameraPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getCameraView(): IAspectRatio = AspectRatioTextureView(requireContext())

    override fun getCameraViewContainer(): ViewGroup = binding.cameraViewContainer

    override fun getGravity(): Int = Gravity.CENTER

    override fun initData() {
        super.initData()
        _binding?.statusText?.text = getString(R.string.camera_waiting_for_device)
        startPermissionOpenRetry(initialDelayMs = 600)
    }

    override fun onResume() {
        super.onResume()
        // After permission dialog returns, keep retrying briefly so camera opens without app restart.
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
                // Apply last RGB values once camera is opened.
                applyRgb(pendingRed, pendingGreen, pendingBlue)
            }
            ICameraStateCallBack.State.CLOSED -> {
                statusView.visibility = View.VISIBLE
                statusView.text = getString(R.string.camera_disconnected)
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
        if (activeInstance === this) {
            activeInstance = null
        }
        _binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        activeInstance = this
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
        // Keep parity with old app behavior; this also confirms command path.
        val zoomEcho = camera.getZoom()
        Logger.i("CameraPreviewFragment", "applyRgb r=$red g=$green b=$blue intensity=$intensity zoomEcho=$zoomEcho")
    }

    private fun startPermissionOpenRetry(initialDelayMs: Long) {
        permissionRetryJob?.cancel()
        permissionRetryJob = lifecycleScope.launch {
            delay(initialDelayMs)
            repeat(30) {
                if (!isAdded || _binding == null) return@launch
                if (isCameraOpened()) return@launch
                val firstDevice = getDeviceList()?.firstOrNull()
                if (firstDevice != null) {
                    _binding?.statusText?.text = getString(R.string.camera_detected_requesting_permission)
                    requestPermission(firstDevice)
                }
                delay(500)
            }
        }
    }

    companion object {
        @Volatile
        private var activeInstance: CameraPreviewFragment? = null

        fun pushRgb(red: Int, green: Int, blue: Int) {
            activeInstance?.applyRgb(red, green, blue)
        }
    }
}
