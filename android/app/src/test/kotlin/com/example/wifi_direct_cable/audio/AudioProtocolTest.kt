package com.example.wifi_direct_cable.audio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AudioProtocolTest {
    @Test
    fun offerMetadataParsesRequiredFields() {
        val metadata = AudioProtocol.offer(99L, "offer-1")

        val offer = AudioProtocol.parseOffer(metadata)

        assertEquals(99L, offer.streamId)
        assertEquals("offer-1", offer.offerId)
        assertEquals(AudioProtocol.SOURCE_MICROPHONE, offer.source)
        assertEquals(AudioProtocol.CODEC_OPUS, offer.codec)
        assertEquals(AudioProtocol.SAMPLE_RATE, offer.sampleRate)
        assertEquals(AudioProtocol.CHANNELS, offer.channels)
    }

    @Test
    fun transportMetadataParsesPort() {
        val transport = AudioProtocol.parseTransport(AudioProtocol.transport(12L, 44001))

        assertEquals(12L, transport.streamId)
        assertEquals(AudioProtocol.TRANSPORT_TCP, transport.transport)
        assertEquals(44001, transport.port)
    }

    @Test
    fun missingOfferIdIsRejected() {
        try {
            AudioProtocol.parseOffer(
                JSONObject()
                    .put("kind", AudioProtocol.KIND_OFFER)
                    .put("streamId", 1L)
                    .put("source", AudioProtocol.SOURCE_MICROPHONE)
                    .put("codec", AudioProtocol.CODEC_OPUS)
            )
            fail("Expected missing offer id to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }
}
