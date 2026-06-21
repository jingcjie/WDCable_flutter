package com.jingcjie.wifi_direct_cable.session

import org.json.JSONObject

interface AudioSessionHandler {
    fun onAudioControlMessage(metadata: JSONObject)

    fun onAudioFeatureError(code: String, message: String, streamId: Long)

    fun onSessionEnded(reason: String)
}

data class AudioSessionInfo(
    val sessionId: String,
    val role: SessionRole,
    val transportRole: SessionTransportRole,
    val peerAddress: String?,
    val peerCapabilities: Set<String>
)
