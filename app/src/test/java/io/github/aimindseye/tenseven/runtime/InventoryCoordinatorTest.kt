package io.github.aimindseye.tenseven.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryCoordinatorTest {
    private val work = mutableListOf<() -> Unit>()
    private val posted = mutableListOf<() -> Unit>()
    private val delivered = mutableListOf<Int>()
    private var next = 0
    private val subject =
        InventoryCoordinator(
            load = { ++next },
            execute = { work += it },
            post = { posted += it },
            deliver = { delivered += it },
        )

    @Test fun refreshChurnDeliversOnlyNewestResult() {
        subject.start()
        subject.refresh()
        work.forEach { it() }
        posted.forEach { it() }
        assertEquals(listOf(2), delivered)
    }

    @Test fun stoppingInvalidatesAlreadyPostedWorkAndAllowsFreshRestart() {
        subject.start()
        work.removeAt(0)()
        subject.stop()
        posted.removeAt(0)()
        assertEquals(emptyList<Int>(), delivered)
        subject.start()
        work.removeAt(0)()
        posted.removeAt(0)()
        assertEquals(listOf(2), delivered)
    }

    @Test fun destroyCannotBeResurrectedByLateCallbacksOrStart() {
        subject.start()
        subject.close()
        work.removeAt(0)()
        posted.removeAt(0)()
        subject.refresh()
        subject.start()
        assertEquals(emptyList<Int>(), delivered)
        assertEquals(0, work.size)
    }

    @Test fun deniedOrFailedLoadDoesNotReplaceTheLastGoodInventory() {
        val failures =
            InventoryCoordinator<Int>(
                load = { error("profile locked") },
                execute = { work += it },
                post = { posted += it },
                deliver = { delivered += it },
            )
        failures.start()
        work.single()()
        assertEquals(emptyList<Int>(), delivered)
        assertEquals(0, posted.size)
    }
}
