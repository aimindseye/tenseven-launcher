package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HubComposeModeTest {
    @Test fun freshInstallAsksEveryTime() {
        assertEquals(HubComposeMode.Ask, HubComposeMode.decode(null))
    }

    @Test fun savedPreferenceIsStable() {
        for (mode in HubComposeMode.entries) {
            assertEquals(mode, HubComposeMode.decode(mode.key))
        }
    }

    @Test fun unexpectedValueFailsSafeToAsk() {
        assertEquals(HubComposeMode.Ask, HubComposeMode.decode("unknown"))
    }
}
