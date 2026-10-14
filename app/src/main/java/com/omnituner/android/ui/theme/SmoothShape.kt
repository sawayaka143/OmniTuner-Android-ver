package com.omnituner.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

// Roughly the iOS continuous-corner look (Figma's "iOS" preset is 60% corner smoothing).
const val DefaultCornerSmoothing = 0.6f

/**
 * Rounded rectangle with continuous ("squircle") corners: the curve eases out of the straight edge
 * instead of a circular arc starting abruptly, which is what makes stock rounded corners look
 * stamped-out next to iOS.
 *
 * [radius] is clamped to half the shorter side, so [Dp.Infinity] gives a fully rounded pill. When a
 * side is too short to blend into, graphics-shapes gives up smoothing before radius, so a nearly
 * square fully-rounded shape ends up as a superellipse rather than a circle.
 */
@Immutable
class SmoothShape(
    private val radius: Dp,
    private val smoothing: Float = DefaultCornerSmoothing,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radiusPx = with(density) { radius.toPx() }.coerceAtMost(size.minDimension / 2f)
        if (radiusPx <= 0f) return Outline.Rectangle(size.toRect())
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            rounding = CornerRounding(radiusPx, smoothing),
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }

    override fun equals(other: Any?): Boolean =
        other is SmoothShape && other.radius == radius && other.smoothing == smoothing

    override fun hashCode(): Int = 31 * radius.hashCode() + smoothing.hashCode()

    override fun toString(): String = "SmoothShape(radius=$radius, smoothing=$smoothing)"

    companion object {
        /** Fully rounded ends, still with continuous curvature where the ends meet the long edges. */
        val Full = SmoothShape(Dp.Infinity)
    }
}
