package com.opentouch.sensorapp.data

/**
 * One verified (resolution, frame rate) combination a sensor can actually
 * stream at. The Settings FPS slider snaps to 0 / half / max of a sensor's
 * rated max fps; [ResolutionFpsOption.fps] values are matched against
 * whichever of those the slider is at to decide which resolution(s) to
 * offer - see SupportedSensor.resolutionOptionsForFps(). This is a plain
 * data list rather than logic branching on literal fps values so that
 * adding a sensor, or adding a newly-verified combo to an existing one,
 * never requires touching UI code - see the 3 July 2026 research diary
 * entry on this requirement.
 */
data class ResolutionFpsOption(
    val width: Int,
    val height: Int,
    val fps: Int,
)

/**
 * A sensor model the app recognizes, identified by USB Vendor ID and one or
 * more Product IDs. A sensor can report different Product IDs across hardware
 * revisions (e.g. GelSight Mini "R0B" vs a later board), so each model holds a
 * SET of product IDs plus a name fragment used to recognize future revisions
 * automatically.
 *
 * [maxFps] and [nativeWidth]/[nativeHeight] are the manufacturer-published
 * specs, shown read-only in Settings. These sensors are fixed-format UVC
 * devices (one streaming size + a fixed frame rate), so the specs are for
 * display/diagnostics — the actual live preview sizes still come from the
 * device via getAllPreviewSizes().
 *
 * Verified specs:
 *   DIGIT        : QVGA 320x240 @ 60/30 fps, VGA 640x480 @ 30/15 fps
 *                  (Meta's official digit-interface DigitDefaults)
 *   GelSight Mini: ~320x240 @ 25 fps  (datasheet: 8MP cam, 25 FPS; streams a
 *                                      downsampled image — exact live size is
 *                                      best read from the device at runtime)
 */
data class SupportedSensor(
    val displayName: String,
    /** Short device name for compact UI (e.g. the Settings sensor-info row). */
    val shortName: String,
    val vendorId: Int,
    val productIds: Set<Int>,
    val nameFragment: String,
    val maxFps: Int,
    val nativeWidth: Int,
    val nativeHeight: Int,
    /** Folder-safe name used for this sensor's subfolder under Pictures/Open_Touch/ and Movies/Open_Touch/. */
    val folderName: String,
    /**
     * Every verified (resolution, fps) combination this sensor can stream.
     * Kept separate from [maxFps]/[nativeWidth]/[nativeHeight] (which stay as
     * the single manufacturer-rated "native" spec used elsewhere) so this list
     * can grow independently as more combos get verified on real hardware.
     */
    val resolutionFpsOptions: List<ResolutionFpsOption> = emptyList(),
) {
    /** Native streaming resolution as "WxH" for display. */
    val nativeResolution: String get() = "${nativeWidth}x${nativeHeight}"

    /**
     * Resolutions valid at exactly [fps]. Falls back to the single native
     * resolution when no combo has been verified yet at that rate (e.g. a
     * sensor whose only confirmed spec is its max fps) so the picker always
     * has something sane to show instead of coming up empty.
     */
    fun resolutionOptionsForFps(fps: Int): List<ResolutionFpsOption> {
        val matches = resolutionFpsOptions.filter { it.fps == fps }
        return matches.ifEmpty { listOf(ResolutionFpsOption(nativeWidth, nativeHeight, fps)) }
    }
}

enum class SensorMatchType { KNOWN, PROBABLE, UNKNOWN }

data class SensorMatch(
    val type: SensorMatchType,
    val sensor: SupportedSensor?,
)

object SupportedSensors {
    val list: List<SupportedSensor> = listOf(
        SupportedSensor(
            displayName = "OpenTouch Sensor (DIGIT)",
            shortName = "DIGIT",
            vendorId = 0x2833,
            productIds = setOf(0x0209),
            nameFragment = "digit",
            maxFps = 60,           // DIGIT: 60 Hz (Meta spec)
            nativeWidth = 320,     // DIGIT streams 320x240
            nativeHeight = 240,
            folderName = "Digit",
            // Per Meta's official digit-interface driver (DigitDefaults):
            //   VGA  (640x480): 30fps (default), 15fps
            //   QVGA (320x240): 60fps (default), 30fps
            //
            // 640x480 @ 30fps was previously removed from this list (research
            // diary, 04.06.2026 meeting notes) after real-device testing hit
            // a native crash - the camera library logged "setPreviewSize
            // failed(format is 1), try to use other format..." (format 1 =
            // MJPEG) and then crashed (native SIGABRT inside
            // UVCPreview::stopPreview(), cleaning up after a preview that
            // never successfully started). That failure required an MJPEG
            // attempt to happen FIRST - CameraPreviewFragment.getCameraRequest()
            // now hardcodes FORMAT_YUYV specifically so MJPEG is never tried,
            // which should prevent this exact crash path, but 640x480 hasn't
            // been re-verified against a real DIGIT unit since that change -
            // test it on hardware before relying on it.
            resolutionFpsOptions = listOf(
                ResolutionFpsOption(320, 240, 30),
                ResolutionFpsOption(320, 240, 60),
                ResolutionFpsOption(640, 480, 15),
                ResolutionFpsOption(640, 480, 30),
            ),
        ),
        SupportedSensor(
            displayName = "GelSight Mini",
            shortName = "GelSight Mini",
            vendorId = 0x0C45,
            productIds = setOf(0x636D),   // R0B (28BJ-5HLX). Add future revision PIDs here.
            nameFragment = "gelsight",
            maxFps = 25,           // GelSight Mini: 25 FPS (datasheet)
            nativeWidth = 320,     // streams a downsampled image (~320x240);
            nativeHeight = 240,    // verify against getAllPreviewSizes() at runtime
            folderName = "GelSightMini",
            // Only the datasheet max-fps combo is verified so far - no unit to
            // test against yet. resolutionOptionsForFps() falls back to this
            // native size at other fps steps until real combos are measured.
            resolutionFpsOptions = listOf(
                ResolutionFpsOption(320, 240, 25),
            ),
        ),
        SupportedSensor(
            displayName = "DIGIT 360",
            shortName = "DIGIT 360",
            // TODO: placeholder values - not yet verified against real hardware.
            // DIGIT 360 (Meta/GelSight's newer multimodal sensor) doesn't have a
            // published VID/PID or a simple native resolution/fps like DIGIT and
            // GelSight Mini do - it's a multimodal USB-C 3.1 device (18+ sensing
            // channels, ~8.3M taxels) normally accessed via Meta's own
            // python/ROS2 interface (github.com/facebookresearch/digit360), so it
            // may not even present as a single plain UVC camera the way the
            // other two sensors do. vendorId/productId are left at 0x0000 (not a
            // real, assignable USB-IF vendor ID) so this entry can't accidentally
            // match a real connected device until it's actually plugged in and
            // read from Logcat/USB descriptor - update once a unit or datasheet
            // is available.
            vendorId = 0x0000,
            productIds = setOf(0x0000),
            nameFragment = "digit360",
            maxFps = 30,           // TODO: unverified guess
            nativeWidth = 320,     // TODO: unverified guess
            nativeHeight = 240,    // TODO: unverified guess
            folderName = "Digit360",
            resolutionFpsOptions = emptyList(),
        ),
    )

    /** Confident match: vendor + a known product ID. */
    fun find(vendorId: Int, productId: Int): SupportedSensor? =
        list.find { it.vendorId == vendorId && productId in it.productIds }

    /** Probable match: same vendor and the product name looks right, but the
     *  product ID isn't listed yet (likely a new hardware revision). */
    fun findProbable(vendorId: Int, productName: String?): SupportedSensor? {
        val name = productName ?: return null
        return list.find {
            it.vendorId == vendorId && name.contains(it.nameFragment, ignoreCase = true)
        }
    }

    /** Classify a detected device into KNOWN / PROBABLE / UNKNOWN. */
    fun classify(vendorId: Int, productId: Int, productName: String?): SensorMatch {
        find(vendorId, productId)?.let {
            return SensorMatch(SensorMatchType.KNOWN, it)
        }
        findProbable(vendorId, productName)?.let {
            return SensorMatch(SensorMatchType.PROBABLE, it)
        }
        return SensorMatch(SensorMatchType.UNKNOWN, null)
    }
}