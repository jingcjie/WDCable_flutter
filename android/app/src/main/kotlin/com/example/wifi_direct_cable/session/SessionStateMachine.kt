package com.example.wifi_direct_cable.session

class SessionStateMachine(initialPhase: SessionPhase = SessionPhase.DISCONNECTED) {
    private val lock = Any()

    var phase: SessionPhase = initialPhase
        private set

    fun transitionTo(nextPhase: SessionPhase): SessionPhase {
        synchronized(lock) {
            if (!canTransition(phase, nextPhase)) {
                throw IllegalStateException("Invalid session transition: $phase -> $nextPhase")
            }
            phase = nextPhase
            return phase
        }
    }

    fun reset(nextPhase: SessionPhase = SessionPhase.DISCONNECTED): SessionPhase {
        synchronized(lock) {
            phase = nextPhase
            return phase
        }
    }

    companion object {
        fun canTransition(from: SessionPhase, to: SessionPhase): Boolean {
            if (from == to) return true
            return when (from) {
                SessionPhase.DISCONNECTED -> to == SessionPhase.WIFI_DIRECT_CONNECTED
                SessionPhase.WIFI_DIRECT_CONNECTED -> to == SessionPhase.CONNECTING_TRANSPORT ||
                    to == SessionPhase.DISCONNECTING ||
                    to == SessionPhase.FAILED
                SessionPhase.CONNECTING_TRANSPORT -> to == SessionPhase.HANDSHAKING ||
                    to == SessionPhase.DISCONNECTING ||
                    to == SessionPhase.FAILED
                SessionPhase.HANDSHAKING -> to == SessionPhase.READY ||
                    to == SessionPhase.DISCONNECTING ||
                    to == SessionPhase.FAILED
                SessionPhase.READY -> to == SessionPhase.DEGRADED ||
                    to == SessionPhase.DISCONNECTING ||
                    to == SessionPhase.DISCONNECTED ||
                    to == SessionPhase.FAILED
                SessionPhase.DEGRADED -> to == SessionPhase.READY ||
                    to == SessionPhase.DISCONNECTING ||
                    to == SessionPhase.DISCONNECTED ||
                    to == SessionPhase.FAILED
                SessionPhase.DISCONNECTING -> to == SessionPhase.DISCONNECTED
                SessionPhase.FAILED -> to == SessionPhase.DISCONNECTING ||
                    to == SessionPhase.DISCONNECTED ||
                    to == SessionPhase.WIFI_DIRECT_CONNECTED
            }
        }
    }
}
