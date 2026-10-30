package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickActionsTest {
    @Test
    fun dashPlusAndSlashAreTodoEventAndNote() {
        assertEquals(QuickAction(QuickActionKind.TODO, "Buy milk"), QuickActions.parse("-Buy milk"))
        assertEquals(QuickAction(QuickActionKind.EVENT, "Dinner Friday at 7"), QuickActions.parse("  +Dinner Friday at 7 "))
        assertEquals(QuickAction(QuickActionKind.NOTE, "Draft the release"), QuickActions.parse("/Draft the release"))
    }

    @Test
    fun aPrefixAloneOrPlainTextIsNotACommand() {
        assertNull(QuickActions.parse("-"))
        assertNull(QuickActions.parse("+   "))
        assertNull(QuickActions.parse("/"))
        assertNull(QuickActions.parse("camera"))
        assertNull(QuickActions.parse(""))
    }

    @Test
    fun scopeWordsKeepTheirMeaning() {
        listOf("/settings", "/apps cam", "/Hub", "/private", "/dual", "/work mail").forEach { assertNull(it, QuickActions.parse(it)) }
        assertEquals(QuickActionKind.NOTE, QuickActions.parse("/settingsfoo bar")?.kind)
    }

    @Test
    fun preservesExplicitWebUrl() = assertEquals("https://google.com/path?q=mink", QuickActions.normalizedWebUrl("https://google.com/path?q=mink"))

    @Test
    fun upgradesWwwAndBareDomainsToHttps() {
        assertEquals("https://www.google.com", QuickActions.normalizedWebUrl("www.google.com"))
        assertEquals("https://google.com/maps", QuickActions.normalizedWebUrl("google.com/maps"))
    }

    @Test
    fun plainQueriesAndUnsafeSchemesAreNotUrls() {
        assertNull(QuickActions.normalizedWebUrl("weather tomorrow"))
        assertNull(QuickActions.normalizedWebUrl("javascript:alert(1)"))
        assertNull(QuickActions.normalizedWebUrl("not-a-domain"))
        assertNull(QuickActions.normalizedWebUrl(""))
    }
}
