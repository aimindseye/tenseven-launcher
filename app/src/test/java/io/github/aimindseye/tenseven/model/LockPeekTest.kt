package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LockPeekTest {
    @Test fun stagesAdvance() {
        assertEquals(LockPeek.Stage.PEEK, LockPeek.next(LockPeek.Stage.COUNTS))
        assertEquals(LockPeek.Stage.EXPANDED, LockPeek.next(LockPeek.Stage.PEEK))
    }
    @Test fun stagesCollapse() {
        assertEquals(LockPeek.Stage.PEEK, LockPeek.previous(LockPeek.Stage.EXPANDED))
        assertEquals(LockPeek.Stage.COUNTS, LockPeek.previous(LockPeek.Stage.PEEK))
    }
}
