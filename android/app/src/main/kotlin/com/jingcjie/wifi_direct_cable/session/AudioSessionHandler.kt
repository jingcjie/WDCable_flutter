package com.jingcjie.wifi_direct_cable.session

import com.jingcjie.wifi_direct_cable.protocol.ProtocolFrame
import org.json.JSONObject

interface AudioSessionHandler {
    fun onAudioControlMessage(metadata: JSONObject)

    fun onAudioFrame(frame: ProtocolFrame)

    fun onAudioFeatureError(code: String, message: String, streamId: Long)

    fun onAudioTransportClosed(reason: String)

    fun onSessionEnded(reason: String)
}

data class AudioSessionInfo(
    val sessionId: String,
    val role: SessionRole,
    val peerAddress: String?,
    val peerCapabilities: Set<String>
)
