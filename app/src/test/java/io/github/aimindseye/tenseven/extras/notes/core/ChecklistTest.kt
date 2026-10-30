// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/notes/src/test),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.notes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTest {
    @Test
    fun parsesOpenAndDoneItems() {
        assertEquals(Checklist.Item("", "milk", false), Checklist.parse("- [ ] milk"))
        assertEquals(Checklist.Item("  ", "eggs", true), Checklist.parse("  - [x] eggs"))
        assertEquals(true, Checklist.parse("- [X] bread")?.done)
        assertNull(Checklist.parse("milk"))
        assertNull(Checklist.parse("- milk"))
    }

    @Test
    fun countsProgress() {
        assertEquals(Checklist.Progress(1, 3), Checklist.progress("Shop\n- [ ] a\n- [x] b\n- [ ] c"))
        assertEquals(Checklist.Progress(0, 0), Checklist.progress("plain"))
    }

    @Test
    fun togglesTheLineUnderTheCursor() {
        val body = "Shop\n- [ ] milk\n- [x] eggs"
        val edit = Checklist.toggleAt(body, body.indexOf("milk"))!!
        assertEquals("Shop\n- [x] milk\n- [x] eggs", edit.text)
        assertEquals("Shop\n- [ ] milk\n- [ ] eggs", Checklist.toggleLine(body, 2))
        assertNull(Checklist.toggleAt(body, 1))
    }

    @Test
    fun convertsALineToAnItemAndBack() {
        val made = Checklist.convertAt("Shop\nmilk", 7)
        assertEquals("Shop\n- [ ] milk", made.text)
        assertEquals(13, made.cursor)
        val back = Checklist.convertAt(made.text, made.cursor)
        assertEquals("Shop\nmilk", back.text)
        assertEquals(7, back.cursor)
        assertEquals("- [ ] ", Checklist.convertAt("", 0).text)
    }

    @Test
    fun enterContinuesAListAndAnEmptyItemEndsIt() {
        val body = "- [ ] milk"
        val next = Checklist.newline(body, body.length)!!
        assertEquals("- [ ] milk\n- [ ] ", next.text)
        assertEquals(next.text.length, next.cursor)
        val end = Checklist.newline(next.text, next.cursor)!!
        assertEquals("- [ ] milk\n", end.text)
        assertNull(Checklist.newline("plain", 5))
    }
}
