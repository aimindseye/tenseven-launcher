package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.model.ActiveFrame
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.platform.PlatformCapabilities
import org.junit.Assert.*
import org.junit.Test

class FramesTest {
    private val personal = FrameApp("mail", 0)
    private val work = FrameApp("mail", 10)
    private val task = ActiveFrame(7, "mail", "Inbox", 0, false)

    private fun caps(
        trusted: Boolean = false,
        usage: Boolean = true,
    ) = PlatformCapabilities.evaluate(
        true,
        true,
        usage,
        true,
        false,
        trusted,
        io.github.aimindseye.tenseven.platform
            .FramePlatformAccess(trusted, trusted, trusted),
    )

    private fun input(
        native: List<ActiveFrame> = listOf(task),
        recent: List<ForegroundEvent> = listOf(ForegroundEvent("mail", 100)),
        apps: Set<FrameApp> = setOf(personal, work),
        settings: FrameSettings = FrameSettings(),
        trusted: Boolean = true,
    ) = FrameInput(native, recent, apps, 0, caps(trusted), settings)

    @Test fun partialNativeResultsMergeWithStableRecentIdentityAndStrictProfiles() {
        val native = task.copy(userId = 10)
        val first = FramePolicy.state(input(listOf(native)), emptyMap(), 1)
        assertEquals(listOf(personal, work), first.items.map { it.app })
        assertNull(first.items.first().task)
        val second =
            FramePolicy.state(
                input(
                    emptyList(),
                    listOf(ForegroundEvent("other", 200), ForegroundEvent("mail", 100)),
                    setOf(personal, FrameApp("other", 0)),
                ),
                emptyMap(),
                2,
            )
        assertEquals(first.items.first().key, second.items.last().key)
        assertTrue(FramePolicy.state(input(apps = emptySet()), emptyMap(), 3).items.isEmpty())
    }

    @Test fun limitsNormalizeAndNeverExceedFourEightOrTwelve() {
        val events = (1..20).map { ForegroundEvent("app$it", it.toLong()) }
        val apps = events.map { FrameApp(it.packageName, 0) }.toSet()
        for (limit in FrameSettings.LIMITS + 999) {
            val state = FramePolicy.state(input(emptyList(), events + events, apps, FrameSettings(maximum = limit)), emptyMap(), 1)
            assertEquals(if (limit == 999) 8 else limit, state.items.size)
            assertEquals(
                "app20",
                state.items
                    .first()
                    .app.packageName,
            )
        }
    }

    @Test fun nativeDismissalDoesNotReturnThroughAppendAndReopenRestoresIt() {
        val dismissed = mapOf(personal.key to 150L)
        assertTrue(FramePolicy.state(input(), dismissed, 1).items.isEmpty())
        assertEquals(1, FramePolicy.state(input(recent = listOf(ForegroundEvent("mail", 151))), dismissed, 2).items.size)
        val pausedProfile = FramePolicy.state(input(listOf(task.copy(userId = 10)), emptyList(), setOf(personal)), emptyMap(), 3)
        assertTrue(pausedProfile.items.isEmpty())
    }

    @Test fun settingsGateRecentsAndSnapshotsAndExplainPermissionFallback() {
        val state = FramePolicy.state(input(settings = FrameSettings(rememberReturnedApps = false, snapshots = false)), emptyMap(), 1)
        assertFalse(state.snapshotsAllowed)
        assertEquals(1, state.items.size) // Genuine native task may remain; synthetic history is off.
        assertTrue(
            FramePolicy.state(input(emptyList(), settings = FrameSettings(rememberReturnedApps = false)), emptyMap(), 2).items.isEmpty(),
        )
        val ordinary = input(trusted = false).copy(capabilities = caps(false, false))
        assertFalse(FramePolicy.state(ordinary, emptyMap(), 1).usageAllowed)
        assertTrue(ordinary.capabilities.frameFallbackReason.contains("Usage access"))
        assertTrue(caps(false).frameFallbackReason.contains("usage access"))
        assertTrue(caps(true).frameFallbackReason.contains("OS-authorized"))
    }

    @Test fun dismissalCodecRejectsMalformedAndBoundsStorage() {
        assertTrue(FrameDismissals.decode(null).isEmpty())
        assertTrue(FrameDismissals.decode("bad\n0:mail\t-1\n0:mail\tno\n../x\t5\n0:mail\t1\textra").isEmpty())
        val entries = (0..120).associate { "0:app$it" to it.toLong() }
        val restored = FrameDismissals.decode(FrameDismissals.encode(entries))
        assertEquals(96, restored.size)
        assertEquals(120L, restored["0:app120"])
        assertFalse(restored.containsKey("0:app0"))
    }

    @Test fun newMessageCountsRequireAccessOptInAndExactProfile() {
        val state = FramesState(settings = FrameSettings(), notificationsAllowed = true)
        val item = FrameItem(personal)
        val counts =
            listOf(
                io.github.aimindseye.tenseven.live
                    .AppUnreadCount("mail", 3),
            )
        assertEquals(3, FrameMessages.count(state, item, counts, setOf("mail"), 0))
        assertEquals(0, FrameMessages.count(state, item, counts, emptySet(), 0))
        assertEquals(0, FrameMessages.count(state, FrameItem(work), counts, setOf("mail"), 0))
        assertEquals(0, FrameMessages.count(state.copy(notificationsAllowed = false), item, counts, setOf("mail"), 0))
        assertEquals(0, FrameMessages.count(state.copy(settings = FrameSettings(newMessages = false)), item, counts, setOf("mail"), 0))
        assertEquals(0, FrameMessages.count(state, item, emptyList(), setOf("mail"), 0))
    }

    private inner class Harness {
        var data = input()
        var saved = emptyMap<String, Long>()
        var failRead = false
        var resumed = 0
        var launched = 0
        var resumeResult = true
        val background = mutableListOf<() -> Unit>()
        val foreground = mutableListOf<() -> Unit>()
        val published = mutableListOf<FramesState>()
        val source =
            object : FrameSource {
                override fun read(): FrameInput {
                    if (failRead) error("revoked")
                    return data
                }

                override fun loadDismissals() = saved

                override fun saveDismissals(value: Map<String, Long>) {
                    saved = value
                }

                override fun resume(item: FrameItem): Boolean {
                    resumed++
                    return resumeResult
                }

                override fun launch(app: FrameApp) {
                    launched++
                }
            }

        fun coordinator() = FramesCoordinator(source, { background.add(it) }, { foreground.add(it) }, { 150L }, { published.add(it) })

        fun drain() {
            while (background.isNotEmpty()) background.removeAt(0).invoke()
            while (foreground.isNotEmpty()) foreground.removeAt(0).invoke()
        }
    }

    @Test fun staleRefreshStopDestroyAndReadFailureNeverPublishPrivateResults() {
        val h = Harness()
        val c = h.coordinator()
        c.refresh()
        assertTrue(h.background.isEmpty())
        c.onResume()
        c.refresh()
        h.background.removeAt(0).invoke()
        h.data = h.data.copy(availableApps = emptySet())
        h.drain()
        assertTrue(c.state.items.isEmpty())
        c.onResume()
        c.onStop()
        h.drain()
        assertTrue(c.state.items.isEmpty())
        h.failRead = true
        c.onResume()
        h.drain()
        assertTrue(c.state.items.isEmpty())
        c.destroy()
        c.onResume()
        c.refresh()
        assertTrue(h.background.isEmpty())
    }

    @Test fun closeIsImmediatePersistsAcrossRecreationAndNeverRemovesNativeTask() {
        val h = Harness()
        val c = h.coordinator()
        c.onResume()
        h.drain()
        val item = c.state.items.single()
        assertTrue(c.acceptsSnapshot(item, c.state.generation))
        c.close(item)
        assertTrue(c.state.items.isEmpty())
        c.close(item)
        assertEquals(mapOf(item.key to 150L), h.saved)
        c.destroy()
        val recreated = h.coordinator()
        recreated.close(item)
        recreated.onResume()
        h.drain()
        assertTrue(recreated.state.items.isEmpty())
        h.data = h.data.copy(recent = listOf(ForegroundEvent("mail", 200)))
        recreated.refresh()
        h.drain()
        assertEquals(1, recreated.state.items.size)
        val current = recreated.state.items.single()
        assertFalse(recreated.acceptsSnapshot(item, 1))
        recreated.onStop()
        assertFalse(recreated.acceptsSnapshot(current, recreated.state.generation))
    }

    @Test fun closingOneNativeTaskKeepsOtherTaskForTheSameApp() {
        val h = Harness()
        h.data = input(native = listOf(task, task.copy(taskId = 8)))
        val c = h.coordinator()
        c.onResume()
        h.drain()
        val first = c.state.items.first()
        c.close(first)
        assertEquals(listOf(8), c.state.items.map { it.task?.taskId })
        c.refresh()
        h.drain()
        assertEquals(listOf(8), c.state.items.map { it.task?.taskId })
        val restored = FrameDismissals.decode(FrameDismissals.encode(h.saved))
        assertEquals(h.saved, restored)
    }

    @Test fun resumeUsesActualNativeTaskOnlyAndFallsBackToExactLaunch() {
        val h = Harness()
        val c = h.coordinator()
        c.onResume()
        h.drain()
        val item = c.state.items.single()
        c.open(item)
        h.drain()
        assertEquals(1, h.resumed)
        assertEquals(0, h.launched)
        h.resumeResult = false
        c.open(item)
        h.drain()
        assertEquals(1, h.launched)
        h.data = h.data.copy(capabilities = caps(false))
        c.open(item)
        h.drain()
        assertEquals(2, h.resumed)
        assertEquals(2, h.launched)
        h.data = h.data.copy(availableApps = setOf(work))
        c.open(item)
        h.drain()
        assertEquals(2, h.launched)
    }

    @Test fun openAndSnapshotRespectPermissionChangesSettingsAndStaleLifecycle() {
        val h = Harness()
        val c = h.coordinator()
        c.onResume()
        h.drain()
        val item = c.state.items.single()
        h.data = h.data.copy(settings = FrameSettings(openApps = false))
        c.open(item)
        h.drain()
        assertEquals(0, h.launched)
        c.refresh()
        h.drain()
        c.open(c.state.items.single())
        assertTrue(h.background.isEmpty())
        h.data = input(native = emptyList(), trusted = false)
        c.refresh()
        h.drain()
        val synthetic = c.state.items.single()
        assertFalse(c.acceptsSnapshot(synthetic, c.state.generation))
        c.open(synthetic)
        h.drain()
        assertEquals(1, h.launched)
        c.open(synthetic)
        c.onStop()
        h.drain()
        assertEquals(1, h.launched)
        c.open(synthetic)
        assertTrue(h.background.isEmpty())
        c.onResume()
        h.drain()
        h.failRead = true
        c.open(c.state.items.single())
        h.drain()
        assertEquals(1, h.launched)
    }
}
