package com.opentouch.sensorapp.data

/**
 * A specific sensor model the app officially supports, identified by its
 * USB Vendor ID and Product ID (the same numbers Android reports for any
 * connected USB device).
 */
data class SupportedSensor(
    val displayName: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * The list of sensor models that this app officially supports.
 *
 * HOW TO ADD A NEW SUPPORTED SENSOR:
 *  1. Plug the sensor into the phone and open the app.
 *  2. A popup will appear showing the sensor's name, Vendor ID, and
 *     Product ID (and that it is "not recognized").
 *  3. Add a line below with that name, vendor ID, and product ID.
 *  4. Rebuild the app — that sensor will now show as "supported".
 *
 * Vendor ID / Product ID can be written as decimal (e.g. 6790) or hex
 * (e.g. 0x1A86) — both work the same.
 */
object SupportedSensors {
    val list: List<SupportedSensor> = listOf(
        // Detected from the connection popup on a real Open Touch sensor
        // (reported USB product name "DIGIT"). Rename "displayName" to the
        // actual model name/number if you have one.
        SupportedSensor("Open Touch Sensor (DIGIT)", vendorId = 0x2833, productId = 0x0209),

        // Example for adding more models later:
        // SupportedSensor("Open Touch Sensor v2", vendorId = 0x1A86, productId = 0x7523),
    )

    /** Returns the matching supported sensor for this VID/PID, or null if unrecognized. */
    fun find(vendorId: Int, productId: Int): SupportedSensor? =
        list.find { it.vendorId == vendorId && it.productId == productId }
}
