package io.github.aimindseye.tenseven.model

import kotlin.math.abs

/** Only an inward horizontal drag at Home's left edge belongs to Peek. */
internal object HubPeekGesture {
    const val EDGE_DP = 32
    const val EXCLUSION_DP = 200

    fun startsAtEdge(
        x: Float,
        edge: Float,
    ): Boolean = x >= 0f && x <= edge

    fun opens(
        dx: Float,
        dy: Float,
        slop: Float,
    ): Boolean = slop > 0f && dx > slop && dx > abs(dy)

    fun cancels(
        dx: Float,
        dy: Float,
        slop: Float,
    ): Boolean = abs(dy) > slop && abs(dy) > abs(dx)
}
