package com.example.wifi_direct_cable.protocol

import java.io.IOException

class ProtocolException(
    val error: ProtocolError,
    message: String
) : IOException(message)
