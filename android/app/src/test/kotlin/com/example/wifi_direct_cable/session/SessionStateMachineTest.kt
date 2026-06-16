package com.example.wifi_direct_cable.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {
    @Test
    fun happyPathTransitionsToReady() {
        val stateMachine = SessionStateMachine()

        stateMachine.transitionTo(SessionPhase.WIFI_DIRECT_CONNECTED)
        stateMachine.transitionTo(SessionPhase.CONNECTING_TRANSPORT)
        stateMachine.transitionTo(SessionPhase.HANDSHAKING)
        stateMachine.transitionTo(SessionPhase.READY)

        assertEquals(SessionPhase.READY, stateMachine.phase)
    }

    @Test
    fun disconnectFromReadyIsAllowed() {
        val stateMachine = SessionStateMachine()
        stateMachine.transitionTo(SessionPhase.WIFI_DIRECT_CONNECTED)
        stateMachine.transitionTo(SessionPhase.CONNECTING_TRANSPORT)
        stateMachine.transitionTo(SessionPhase.HANDSHAKING)
        stateMachine.transitionTo(SessionPhase.READY)

        stateMachine.transitionTo(SessionPhase.DISCONNECTING)
        stateMachine.transitionTo(SessionPhase.DISCONNECTED)

        assertEquals(SessionPhase.DISCONNECTED, stateMachine.phase)
    }

    @Test(expected = IllegalStateException::class)
    fun cannotSkipTransportAndHandshake() {
        SessionStateMachine().transitionTo(SessionPhase.READY)
    }

    @Test
    fun failedSessionCanStartOver() {
        val stateMachine = SessionStateMachine()
        stateMachine.transitionTo(SessionPhase.WIFI_DIRECT_CONNECTED)
        stateMachine.transitionTo(SessionPhase.FAILED)

        assertTrue(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.WIFI_DIRECT_CONNECTED))
        assertFalse(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.READY))
    }

    @Test
    fun readyCanDegradeAndRecoverToReady() {
        val stateMachine = SessionStateMachine()
        stateMachine.transitionTo(SessionPhase.WIFI_DIRECT_CONNECTED)
        stateMachine.transitionTo(SessionPhase.CONNECTING_TRANSPORT)
        stateMachine.transitionTo(SessionPhase.HANDSHAKING)
        stateMachine.transitionTo(SessionPhase.READY)

        stateMachine.transitionTo(SessionPhase.DEGRADED)
        stateMachine.transitionTo(SessionPhase.READY)

        assertEquals(SessionPhase.READY, stateMachine.phase)
    }

    @Test
    fun readyCanDegradeAndDisconnectWhenGroupIsGone() {
        val stateMachine = SessionStateMachine()
        stateMachine.transitionTo(SessionPhase.WIFI_DIRECT_CONNECTED)
        stateMachine.transitionTo(SessionPhase.CONNECTING_TRANSPORT)
        stateMachine.transitionTo(SessionPhase.HANDSHAKING)
        stateMachine.transitionTo(SessionPhase.READY)

        stateMachine.transitionTo(SessionPhase.DEGRADED)
        stateMachine.transitionTo(SessionPhase.DISCONNECTED)

        assertEquals(SessionPhase.DISCONNECTED, stateMachine.phase)
    }

    @Test
    fun transportFailureBeforeReadyDoesNotEnterDegraded() {
        assertFalse(SessionStateMachine.canTransition(SessionPhase.WIFI_DIRECT_CONNECTED, SessionPhase.DEGRADED))
        assertFalse(SessionStateMachine.canTransition(SessionPhase.CONNECTING_TRANSPORT, SessionPhase.DEGRADED))
        assertTrue(SessionStateMachine.canTransition(SessionPhase.CONNECTING_TRANSPORT, SessionPhase.FAILED))
    }

    @Test
    fun degradedCannotStartOverWithoutDisconnectOrRecovery() {
        assertFalse(SessionStateMachine.canTransition(SessionPhase.DEGRADED, SessionPhase.WIFI_DIRECT_CONNECTED))
        assertFalse(SessionStateMachine.canTransition(SessionPhase.DEGRADED, SessionPhase.HANDSHAKING))
        assertTrue(SessionStateMachine.canTransition(SessionPhase.DEGRADED, SessionPhase.READY))
        assertTrue(SessionStateMachine.canTransition(SessionPhase.DEGRADED, SessionPhase.DISCONNECTING))
    }

    @Test
    fun disconnectingCannotReturnToReady() {
        assertFalse(SessionStateMachine.canTransition(SessionPhase.DISCONNECTING, SessionPhase.READY))
        assertFalse(SessionStateMachine.canTransition(SessionPhase.DISCONNECTING, SessionPhase.WIFI_DIRECT_CONNECTED))
        assertTrue(SessionStateMachine.canTransition(SessionPhase.DISCONNECTING, SessionPhase.DISCONNECTED))
    }

    @Test
    fun failedCanOnlyDisconnectOrStartFreshWifiDirectSession() {
        assertTrue(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.DISCONNECTING))
        assertTrue(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.DISCONNECTED))
        assertTrue(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.WIFI_DIRECT_CONNECTED))
        assertFalse(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.HANDSHAKING))
        assertFalse(SessionStateMachine.canTransition(SessionPhase.FAILED, SessionPhase.READY))
    }
}
