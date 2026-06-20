package com.jingcjie.wifi_direct_cable.audio

import com.jingcjie.wifi_direct_cable.session.SessionTransportRole
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AudioProtocolTest {
    @Test
    fun offerMetadataParsesRequiredV2Fields() {
        val metadata = AudioProtocol.offer(
            streamId = 99L,
            offerId = "offer-1",
            rtpSsrc = 0xabcdef01L,
            transportRole = SessionTransportRole.LISTENER,
            latencyMode = AudioProtocol.LATENCY_STABLE,
            bitrateBps = AudioProtocol.BITRATE_BPS
        )

        val offer = AudioProtocol.parseOffer(metadata)

        assertEquals(99L, offer.streamId)
        assertEquals("offer-1", offer.offerId)
        assertEquals(AudioProtocol.TRANSPORT_RTP_UDP, offer.transport)
        assertEquals(AudioProtocol.SOURCE_MICROPHONE, offer.source)
        assertEquals(AudioProtocol.CODEC_OPUS, offer.codec)
        assertEquals(AudioProtocol.CODEC_IMPL_LIBOPUS, offer.codecImpl)
        assertEquals(AudioProtocol.SAMPLE_RATE, offer.sampleRate)
        assertEquals(AudioProtocol.CHANNELS, offer.channels)
        assertEquals(AudioProtocol.LATENCY_STABLE, offer.latencyMode)
        assertEquals(AudioProtocol.BITRATE_BPS, offer.bitrateBps)
        assertEquals(AudioProtocol.RTP_PAYLOAD_TYPE, offer.rtpPayloadType)
        assertEquals(0xabcdef01L, offer.rtpSsrc)
        assertEquals(SessionTransportRole.LISTENER, offer.transportRole)
        assertTrue(AudioProtocol.validateOffer(offer))
    }

    @Test
    fun acceptMetadataParsesRequiredV2Fields() {
        val metadata = AudioProtocol.accept(
            streamId = 12L,
            offerId = "offer-2",
            rtpSsrc = 0x12345678L,
            transportRole = SessionTransportRole.CONNECTOR,
            receiverProbeRequired = true,
            latencyMode = AudioProtocol.LATENCY_STABLE,
            bitrateBps = AudioProtocol.BITRATE_BPS
        )

        val accept = AudioProtocol.parseAccept(metadata)

        assertEquals(12L, accept.streamId)
        assertEquals("offer-2", accept.offerId)
        assertEquals(AudioProtocol.TRANSPORT_RTP_UDP, accept.transport)
        assertEquals(AudioProtocol.CODEC_IMPL_LIBOPUS, accept.codecImpl)
        assertEquals(AudioProtocol.LATENCY_STABLE, accept.latencyMode)
        assertEquals(AudioProtocol.BITRATE_BPS, accept.bitrateBps)
        assertEquals(0x12345678L, accept.rtpSsrc)
        assertEquals(SessionTransportRole.CONNECTOR, accept.transportRole)
        assertTrue(accept.receiverProbeRequired)
        assertTrue(AudioProtocol.validateAccept(accept))
    }

    @Test
    fun acceptEchoesOfferedLatencyAndBitrate() {
        val offer = AudioProtocol.parseOffer(
            AudioProtocol.offer(
                streamId = 34L,
                offerId = "offer-echo",
                rtpSsrc = 0x11223344L,
                transportRole = SessionTransportRole.LISTENER,
                latencyMode = AudioProtocol.LATENCY_STABLE,
                bitrateBps = AudioProtocol.BITRATE_BPS
            )
        )

        val accept = AudioProtocol.parseAccept(
            AudioProtocol.accept(
                streamId = offer.streamId,
                offerId = offer.offerId,
                rtpSsrc = 0x55667788L,
                transportRole = SessionTransportRole.CONNECTOR,
                receiverProbeRequired = true,
                latencyMode = offer.latencyMode,
                bitrateBps = offer.bitrateBps
            )
        )

        assertEquals(offer.latencyMode, accept.latencyMode)
        assertEquals(offer.bitrateBps, accept.bitrateBps)
    }

    @Test
    fun unsupportedLatencyOrBitrateIsRejected() {
        val unsupportedLatency = AudioProtocol.parseOffer(
            AudioProtocol.offer(
                streamId = 45L,
                offerId = "offer-bad-latency",
                rtpSsrc = 1L,
                transportRole = SessionTransportRole.LISTENER,
                latencyMode = "receiverChoice",
                bitrateBps = AudioProtocol.BITRATE_BPS
            )
        )
        val unsupportedBitrate = AudioProtocol.parseOffer(
            AudioProtocol.offer(
                streamId = 46L,
                offerId = "offer-bad-bitrate",
                rtpSsrc = 2L,
                transportRole = SessionTransportRole.LISTENER,
                latencyMode = AudioProtocol.LATENCY_LOW,
                bitrateBps = 24_000
            )
        )

        assertFalse(AudioProtocol.validateOffer(unsupportedLatency))
        assertFalse(AudioProtocol.validateOffer(unsupportedBitrate))
    }

    @Test
    fun missingOfferIdIsRejected() {
        try {
            AudioProtocol.parseOffer(
                JSONObject()
                    .put("kind", AudioProtocol.KIND_OFFER)
                    .put("streamId", 1L)
                    .put("transport", AudioProtocol.TRANSPORT_RTP_UDP)
                    .put("source", AudioProtocol.SOURCE_MICROPHONE)
                    .put("codec", AudioProtocol.CODEC_OPUS)
                    .put("codecImpl", AudioProtocol.CODEC_IMPL_LIBOPUS)
                    .put("sampleRate", AudioProtocol.SAMPLE_RATE)
                    .put("channels", AudioProtocol.CHANNELS)
                    .put("frameDurationMs", AudioProtocol.FRAME_DURATION_MS)
                    .put("latencyMode", AudioProtocol.LATENCY_LOW)
                    .put("bitrateBps", AudioProtocol.BITRATE_BPS)
                    .put("rtpPayloadType", AudioProtocol.RTP_PAYLOAD_TYPE)
                    .put("rtpClockRate", AudioProtocol.RTP_CLOCK_RATE)
                    .put("rtpSsrc", 1L)
                    .put("transportRole", SessionTransportRole.LISTENER.eventName)
            )
            fail("Expected missing offer id to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun wrongMetadataKindIsRejected() {
        try {
            AudioProtocol.parseAccept(
                AudioProtocol.offer(2L, "offer-2", 1L, SessionTransportRole.LISTENER)
            )
            fail("Expected wrong metadata kind to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun portOwnershipUsesTransportRoleOnly() {
        assertTrue(AudioProtocol.receiverProbeRequired(SessionTransportRole.CONNECTOR))
        assertFalse(AudioProtocol.receiverProbeRequired(SessionTransportRole.LISTENER))
        assertTrue(
            AudioProtocol.senderWaitsForReceiverProbe(
                SessionTransportRole.LISTENER,
                receiverProbeRequired = true
            )
        )
        assertFalse(
            AudioProtocol.senderWaitsForReceiverProbe(
                SessionTransportRole.CONNECTOR,
                receiverProbeRequired = true
            )
        )
    }
}
