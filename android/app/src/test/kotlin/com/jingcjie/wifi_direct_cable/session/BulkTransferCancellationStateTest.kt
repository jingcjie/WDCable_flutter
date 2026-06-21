package com.jingcjie.wifi_direct_cable.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class BulkTransferCancellationStateTest {
    @Test
    fun cancelFrameCannotBeClaimedBeforeStart() {
        val requested = AtomicBoolean(true)
        val state = BulkTransferCancellationState(requested::get)

        assertTrue(state.isCancellationRequested())
        assertFalse(state.claimCancelFrame())
    }

    @Test
    fun cancelFrameCanBeClaimedOnlyOnceAfterStart() {
        val requested = AtomicBoolean(false)
        val state = BulkTransferCancellationState(requested::get)
        state.markStartSent()
        requested.set(true)

        assertTrue(state.isCancellationRequested())
        assertTrue(state.claimCancelFrame())
        assertFalse(state.claimCancelFrame())
    }
}
