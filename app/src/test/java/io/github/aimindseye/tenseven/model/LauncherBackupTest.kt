package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherBackupTest {
    private val sample =
        mapOf(
            "favorite_apps" to BackupValue.Items(setOf("a/b@0", "c \"quoted\"/d@10")),
            "quick_bar" to BackupValue.Text("one\ntwo\\three\ttab ünï"),
            "bb7_home_rows" to BackupValue.Number(3),
            "home_style" to BackupValue.Text("bb7"),
            "wallpaper_set" to BackupValue.Text("aurora"),
        )

    @Test
    fun roundTrips() {
        val decoded = LauncherBackup.decode(LauncherBackup.encode(sample))
        assertEquals(sample, decoded.getOrThrow())
    }

    @Test
    fun emptySettingsRoundTrip() {
        assertEquals(emptyMap<String, BackupValue>(), LauncherBackup.decode(LauncherBackup.encode(emptyMap())).getOrThrow())
    }

    @Test
    fun selectKeepsOnlyIncludedKeysOfTheRightType() {
        val picked =
            LauncherBackup.select(
                mapOf(
                    "home_style" to "bb10",
                    "bb7_home_rows" to 2,
                    "favorite_apps" to setOf("x"),
                    "onboarding_done" to true,
                    "home_mode" to 5,
                    "something_else" to "no",
                ),
            )
        assertEquals(setOf("home_style", "bb7_home_rows", "favorite_apps"), picked.keys)
    }

    @Test
    fun oldBackupWithoutWallpaperSetStillDecodes() {
        val previous = sample - "wallpaper_set"
        assertEquals(previous, LauncherBackup.decode(LauncherBackup.encode(previous)).getOrThrow())
    }

    @Test
    fun refusesUnknownKeysAndWrongTypes() {
        fun doc(settings: String) = """{"format":"tenseven-backup","version":1,"settings":{$settings}}"""
        assertTrue(LauncherBackup.decode(doc("\"onboarding_done\": true")).isFailure)
        assertTrue(LauncherBackup.decode(doc("\"bb7_home_rows\": \"3\"")).isFailure)
        assertTrue(LauncherBackup.decode(doc("\"favorite_apps\": \"x\"")).isFailure)
        assertTrue(LauncherBackup.decode(doc("\"favorite_apps\": [1]")).isFailure)
        assertTrue(LauncherBackup.decode(doc("\"home_style\": \"bb7\"")).isSuccess)
    }

    @Test
    fun refusesOtherFilesAndVersions() {
        assertTrue(LauncherBackup.decode("not json").isFailure)
        assertTrue(LauncherBackup.decode("[]").isFailure)
        assertTrue(LauncherBackup.decode("""{"format":"other","version":1,"settings":{}}""").isFailure)
        assertTrue(LauncherBackup.decode("""{"format":"tenseven-backup","version":2,"settings":{}}""").isFailure)
        assertTrue(LauncherBackup.decode("""{"format":"tenseven-backup","version":1}""").isFailure)
        assertTrue(LauncherBackup.decode("""{"format":"tenseven-backup","version":1,"settings":{}} trailing""").isFailure)
        assertTrue(LauncherBackup.decode("x".repeat(LauncherBackup.MAX_CHARACTERS + 1)).isFailure)
    }

    @Test
    fun everyIncludedKeyIsOneTheRepositoryOwns() {
        val owned =
            setOf(
                StartStateRepository.KEY_FAVORITE_APPS,
                StartStateRepository.KEY_START_TILES,
                StartStateRepository.KEY_HOME_HIDDEN_SECTIONS,
                StartStateRepository.KEY_QUICK_BAR,
                StartStateRepository.KEY_HOME_APPS,
                StartStateRepository.KEY_GLANCE_WIDGETS,
                StartStateRepository.KEY_GRID_ORDER,
                StartStateRepository.KEY_HOME_MODE,
                StartStateRepository.KEY_HOME_STYLE,
                StartStateRepository.KEY_ICON_PACK,
                StartStateRepository.KEY_BB7_HOME_ROWS,
                StartStateRepository.KEY_BB7_PANELS,
                StartStateRepository.KEY_BB7_SCRIM,
                StartStateRepository.KEY_TYPE_TO_SEARCH,
                StartStateRepository.KEY_BB7_ICON_SIZE,
                StartStateRepository.KEY_WIDGETS_PAGE,
                StartStateRepository.KEY_BB7_DESKTOP,
                StartStateRepository.KEY_GRID_COLUMNS,
                StartStateRepository.KEY_GRID_LABELS,
                StartStateRepository.KEY_LOCK_LAYER,
                StartStateRepository.KEY_HOME_WALLPAPER,
                StartStateRepository.KEY_WALLPAPER_SET,
                StartStateRepository.KEY_HUB_EXCLUDED,
                StartStateRepository.KEY_HUB_PEEK_TEXT,
                StartStateRepository.KEY_APPEARANCE_MODE,
                StartStateRepository.KEY_APPEARANCE_ACCENT,
                StartStateRepository.KEY_APPEARANCE_CUSTOM,
                StartStateRepository.KEY_CUSTOM_THEMES,
            )
        assertEquals(owned, LauncherBackup.keys)
        assertFalse(LauncherBackup.keys.contains(StartStateRepository.KEY_ONBOARDING_DONE))
        // Widget ids belong to this phone's widget host; restoring them elsewhere would point at nothing.
        assertFalse(LauncherBackup.keys.contains(StartStateRepository.KEY_WIDGET_IDS))
        assertFalse(LauncherBackup.keys.contains(StartStateRepository.KEY_HOME_WIDGET_IDS))
    }
}
