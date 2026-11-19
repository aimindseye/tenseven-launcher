package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubPeekGestureTest {
    @Test
    fun inwardEdgeDragOpensAfterSlop() {
        assertTrue(HubPeekGesture.startsAtEdge(EDGE_START, EDGE_WIDTH))
        assertTrue(HubPeekGesture.opens(DRAG, 0f, SLOP))
        assertFalse(HubPeekGesture.opens(SLOP, 0f, SLOP))
    }

    @Test
    fun nonEdgeAndOutwardDragsDoNotOpenPeek() {
        assertFalse(HubPeekGesture.startsAtEdge(-1f, EDGE_WIDTH))
        assertFalse(HubPeekGesture.startsAtEdge(EDGE_WIDTH + 1f, EDGE_WIDTH))
        assertFalse(HubPeekGesture.opens(-DRAG, 0f, SLOP))
    }

    @Test
    fun verticalScrollingCancelsInsteadOfOpeningPeek() {
        assertFalse(HubPeekGesture.opens(SLOP, DRAG, SLOP))
        assertTrue(HubPeekGesture.cancels(SLOP, DRAG, SLOP))
        assertTrue(HubPeekGesture.cancels(SLOP, -DRAG, SLOP))
    }

    @Test
    fun horizontalIntentDoesNotCancelThePagerProtectedDrag() {
        assertTrue(HubPeekGesture.opens(DRAG, SLOP, SLOP))
        assertFalse(HubPeekGesture.cancels(DRAG, SLOP, SLOP))
    }

    @Test
    fun unmeasuredSlopDoesNotCreateAnAccidentalPeek() {
        assertFalse(HubPeekGesture.opens(DRAG, 0f, 0f))
    }

    private companion object {
        const val EDGE_START = 24f
        const val EDGE_WIDTH = 32f
        const val DRAG = 40f
        const val SLOP = 8f
    }
}
