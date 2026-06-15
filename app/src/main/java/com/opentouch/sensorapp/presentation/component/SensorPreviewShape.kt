package com.opentouch.sensorapp.presentation.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Outline that mimics the physical shape of the Open Touch sensor: a full
 * semicircular ("domed") top edge, straight sides, and a flat bottom edge
 * with slightly rounded corners. Used to clip the camera preview so the
 * on-screen image matches the sensor's silhouette.
 *
 * @param bottomCornerRadiusFraction how rounded the two bottom corners are,
 *   as a fraction of the preview's width. 0f = sharp corners.
 */
class SensorPreviewShape(
    private val bottomCornerRadiusFraction: Float = 0.08f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height

        // The dome is a true semicircle spanning the full width.
        val domeRadius = min(width / 2f, height)

        // Bottom corners get a small rounding, capped so they don't collide
        // with the dome or with each other.
        val bottomCornerRadius = (width * bottomCornerRadiusFraction)
            .coerceAtMost(height - domeRadius)
            .coerceAtMost(width / 2f)
            .coerceAtLeast(0f)

        val path = Path().apply {
            // Left edge, starting where the dome's left side meets it.
            moveTo(0f, domeRadius)

            // Semicircular dome across the top.
            arcTo(
                rect = Rect(0f, 0f, width, domeRadius * 2f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // Right edge down to the bottom-right corner.
            lineTo(width, height - bottomCornerRadius)

            if (bottomCornerRadius > 0f) {
                // Bottom-right rounded corner.
                arcTo(
                    rect = Rect(
                        width - bottomCornerRadius * 2f,
                        height - bottomCornerRadius * 2f,
                        width,
                        height
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
            }

            // Bottom edge.
            lineTo(bottomCornerRadius, height)

            if (bottomCornerRadius > 0f) {
                // Bottom-left rounded corner.
                arcTo(
                    rect = Rect(
                        0f,
                        height - bottomCornerRadius * 2f,
                        bottomCornerRadius * 2f,
                        height
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
            }

            // Left edge back up to the start — closed automatically.
            close()
        }
        return Outline.Generic(path)
    }
}
