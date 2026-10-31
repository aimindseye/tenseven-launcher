package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GridColumnsTest {
    @Test
    fun unknownValuesFallBackToAuto() {
        assertEquals(GridColumns.AUTO, GridColumns.normalize(7))
        assertEquals(4, GridColumns.normalize(4))
    }

    @Test
    fun autoUsesWhatFitsAndAFixedNumberIsCappedByTheWidth() {
        assertEquals(5, GridColumns.resolve(GridColumns.AUTO, 360, 5))
        assertEquals(4, GridColumns.resolve(4, 360, 5))
        assertEquals(2, GridColumns.resolve(6, 100, 5))
    }
}
