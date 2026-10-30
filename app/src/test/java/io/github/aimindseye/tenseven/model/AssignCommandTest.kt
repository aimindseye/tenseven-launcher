package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssignCommandTest {
    @Test
    fun assignTakesALetterAndAnOptionalApp() {
        assertEquals(AssignRequest.Assign('k', ""), AssignCommand.parse("assign k"))
        assertEquals(AssignRequest.Assign('k', "camera app"), AssignCommand.parse("  Assign K camera app "))
    }

    @Test
    fun unassignClearsALetter() {
        assertEquals(AssignRequest.Clear('m'), AssignCommand.parse("unassign m"))
        assertEquals(AssignRequest.Clear('m'), AssignCommand.parse("UNASSIGN M ignored"))
    }

    @Test
    fun anythingElseIsNotACommand() {
        assertNull(AssignCommand.parse("assign"))
        assertNull(AssignCommand.parse("assign 5"))
        assertNull(AssignCommand.parse("assign kk"))
        assertNull(AssignCommand.parse("assigned k"))
        assertNull(AssignCommand.parse("camera"))
    }
}
