package com.example.wifi_direct_cable.session

enum class SessionPhase(val eventName: String) {
    WIFI_DIRECT_CONNECTED("WifiDirectConnected"),
    CONNECTING_TRANSPORT("ConnectingTransport"),
    HANDSHAKING("Handshaking"),
    READY("Ready"),
    DEGRADED("Degraded"),
    DISCONNECTING("Disconnecting"),
    DISCONNECTED("Disconnected"),
    FAILED("Failed")
}
