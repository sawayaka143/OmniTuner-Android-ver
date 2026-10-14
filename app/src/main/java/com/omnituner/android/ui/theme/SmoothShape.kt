package com.omnituner.android.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposePath
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
 * It is a [CornerBasedShape], so it can stand in for every slot of Material's `Shapes` and survives
 * the `copy(...)` calls components make (e.g. a bottom sheet squaring off its bottom corners).
 * Corner sizes are clamped like `RoundedCornerShape`'s, so 50% gives a fully rounded pill. When a
 * side is too short to blend into, graphics-shapes gives up smoothing before radius, so a nearly
 * square fully-rounded shape ends up as a superellipse rather than a circle.
 */
@Immutable
class SmoothShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val smoothing: Float = DefaultCornerSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(corner: CornerSize, smoothing: Float = DefaultCornerSmoothing) :
        this(corner, corner, corner, corner, smoothing)

    constructor(radius: Dp, smoothing: Float = DefaultCornerSmoothing) :
        this(CornerSize(radius), smoothing)

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): SmoothShape = SmoothShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            // rectangle() lists its vertices bottom-right, bottom-left, top-left, top-right.
            perVertexRounding = listOf(bottomRight, bottomLeft, topLeft, topRight)
                .map { CornerRounding(it, smoothing) },
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }

    override fun equals(other: Any?): Boolean =
        other is SmoothShape &&
            other.topStart == topStart && other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd && other.bottomStart == bottomStart &&
            other.smoothing == smoothing

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return 31 * result + smoothing.hashCode()
    }

    override fun toString(): String =
        "SmoothShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothing=$smoothing)"

    companion object {
        /** Fully rounded ends, still with continuous curvature where the ends meet the long edges. */
        val Full = SmoothShape(CornerSize(50))
    }
}
