package com.opentouch.sensorapp.data

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
 *   DIGIT        : 320x240 @ 60 fps   (Meta)
 *   GelSight Mini: ~320x240 @ 25 fps  (datasheet: 8MP cam, 25 FPS; streams a
 *                                      downsampled image — exact live size is
 *                                      best read from the device at runtime)
 */
data class SupportedSensor(
    val displayName: String,
    val vendorId: Int,
    val productIds: Set<Int>,
    val nameFragment: String,
    val maxFps: Int,
    val nativeWidth: Int,
    val nativeHeight: Int,
) {
    /** Native streaming resolution as "WxH" for display. */
    val nativeResolution: String get() = "${nativeWidth}x${nativeHeight}"
}

enum class SensorMatchType { KNOWN, PROBABLE, UNKNOWN }

data class SensorMatch(
    val type: SensorMatchType,
    val sensor: SupportedSensor?,
)

object SupportedSensors {
    val list: List<SupportedSensor> = listOf(
        SupportedSensor(
            displayName = "Open Touch Sensor (DIGIT)",
            vendorId = 0x2833,
            productIds = setOf(0x0209),
            nameFragment = "digit",
            maxFps = 60,           // DIGIT: 60 Hz (Meta spec)
            nativeWidth = 320,     // DIGIT streams 320x240
            nativeHeight = 240,
        ),
        SupportedSensor(
            displayName = "GelSight Mini",
            vendorId = 0x0C45,
            productIds = setOf(0x636D),   // R0B (28BJ-5HLX). Add future revision PIDs here.
            nameFragment = "gelsight",
            maxFps = 25,           // GelSight Mini: 25 FPS (datasheet)
            nativeWidth = 320,     // streams a downsampled image (~320x240);
            nativeHeight = 240,    // verify against getAllPreviewSizes() at runtime
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