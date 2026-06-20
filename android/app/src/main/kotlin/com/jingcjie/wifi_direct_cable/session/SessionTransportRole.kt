package com.jingcjie.wifi_direct_cable.session

enum class SessionTransportRole(val eventName: String) {
    LISTENER("listener"),
    CONNECTOR("connector")
}

fun SessionRole.transportRole(): SessionTransportRole {
    return when (this) {
        SessionRole.GROUP_OWNER -> SessionTransportRole.CONNECTOR
        SessionRole.CLIENT -> SessionTransportRole.LISTENER
    }
}
