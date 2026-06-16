package com.example.wifi_direct_cable.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class ProtocolCodecTest {
    @Test
    fun validFrameRoundTrip() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val frame = ProtocolFrame(
            type = ProtocolFrameType.CONTROL_MESSAGE,
            channel = ProtocolChannel.CONTROL,
            flags = 7,
            streamId = 11L,
            sequenceNumber = 12L,
            correlationId = UUID.fromString("12345678-1234-5678-1234-567812345678"),
            metadataJson = """{"kind":"chat","messageId":"m1"}""",
            payload = payload
        )

        val decoded = ProtocolCodec.readFrame(ByteArrayInputStream(ProtocolCodec.encode(frame)))

        require(decoded != null)
        assertEquals(frame.type, decoded.type)
        assertEquals(frame.channel, decoded.channel)
        assertEquals(frame.flags, decoded.flags)
        assertEquals(frame.streamId, decoded.streamId)
        assertEquals(frame.sequenceNumber, decoded.sequenceNumber)
        assertEquals(frame.correlationId, decoded.correlationId)
        assertEquals(frame.metadataJson, decoded.metadataJson)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun emptyInputReturnsNull() {
        assertNull(ProtocolCodec.readFrame(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun partialHeaderThrowsTypedError() {
        val encoded = ProtocolCodec.encode(
            ProtocolFrame(
                type = ProtocolFrameType.HEARTBEAT_PING,
                channel = ProtocolChannel.CONTROL
            )
        )
        val partial = encoded.copyOfRange(0, ProtocolConstants.HEADER_SIZE - 2)

        assertProtocolError(ProtocolError.PARTIAL_READ) {
            ProtocolCodec.readFrame(ByteArrayInputStream(partial))
        }
    }

    @Test
    fun malformedMagicThrowsTypedError() {
        val encoded = ProtocolCodec.encode(
            ProtocolFrame(
                type = ProtocolFrameType.HEARTBEAT_PING,
                channel = ProtocolChannel.CONTROL
            )
        )
        encoded[0] = 0

        assertProtocolError(ProtocolError.MALFORMED_MAGIC) {
            ProtocolCodec.readFrame(ByteArrayInputStream(encoded))
        }
    }

    @Test
    fun invalidHeaderSizeThrowsTypedError() {
        val header = headerWithLengths(
            metadataLength = 0,
            payloadLength = 0,
            headerSize = ProtocolConstants.HEADER_SIZE + 1
        )

        assertProtocolError(ProtocolError.INVALID_HEADER_SIZE) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun unknownFrameTypeThrowsTypedError() {
        val header = headerWithLengths(
            metadataLength = 0,
            payloadLength = 0,
            frameType = 999
        )

        assertProtocolError(ProtocolError.INVALID_FRAME_TYPE) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun unknownChannelThrowsTypedError() {
        val header = headerWithLengths(
            metadataLength = 0,
            payloadLength = 0,
            channel = 999
        )

        assertProtocolError(ProtocolError.INVALID_CHANNEL) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun unsupportedVersionThrowsTypedError() {
        val encoded = ProtocolCodec.encode(
            ProtocolFrame(
                type = ProtocolFrameType.HEARTBEAT_PING,
                channel = ProtocolChannel.CONTROL
            )
        )
        encoded[5] = 2

        assertProtocolError(ProtocolError.UNSUPPORTED_VERSION) {
            ProtocolCodec.readFrame(ByteArrayInputStream(encoded))
        }
    }

    @Test
    fun oversizedMetadataRejectedOnEncode() {
        val metadata = "x".repeat(ProtocolConstants.MAX_METADATA_BYTES + 1)

        assertProtocolError(ProtocolError.METADATA_TOO_LARGE) {
            ProtocolCodec.encode(
                ProtocolFrame(
                    type = ProtocolFrameType.CONTROL_MESSAGE,
                    channel = ProtocolChannel.CONTROL,
                    metadataJson = metadata
                )
            )
        }
    }

    @Test
    fun oversizedMetadataRejectedOnDecode() {
        val header = headerWithLengths(
            metadataLength = ProtocolConstants.MAX_METADATA_BYTES + 1,
            payloadLength = 0
        )

        assertProtocolError(ProtocolError.METADATA_TOO_LARGE) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun negativeMetadataLengthRejectedOnDecode() {
        val header = headerWithLengths(
            metadataLength = -1,
            payloadLength = 0
        )

        assertProtocolError(ProtocolError.INVALID_LENGTH) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun negativePayloadLengthRejectedOnDecode() {
        val header = headerWithLengths(
            metadataLength = 0,
            payloadLength = -1
        )

        assertProtocolError(ProtocolError.INVALID_LENGTH) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun oversizedPayloadRejectedOnEncode() {
        val payload = ByteArray(ProtocolConstants.MAX_PAYLOAD_BYTES + 1)

        assertProtocolError(ProtocolError.PAYLOAD_TOO_LARGE) {
            ProtocolCodec.encode(
                ProtocolFrame(
                    type = ProtocolFrameType.BULK_CHUNK,
                    channel = ProtocolChannel.BULK,
                    payload = payload
                )
            )
        }
    }

    @Test
    fun oversizedPayloadRejectedOnDecode() {
        val header = headerWithLengths(
            metadataLength = 0,
            payloadLength = ProtocolConstants.MAX_PAYLOAD_BYTES + 1
        )

        assertProtocolError(ProtocolError.PAYLOAD_TOO_LARGE) {
            ProtocolCodec.readFrame(ByteArrayInputStream(header))
        }
    }

    @Test
    fun zeroLengthPayloadRoundTrip() {
        val decoded = ProtocolCodec.readFrame(
            ByteArrayInputStream(
                ProtocolCodec.encode(
                    ProtocolFrame(
                        type = ProtocolFrameType.ACK,
                        channel = ProtocolChannel.CONTROL,
                        metadataJson = """{"ok":true}"""
                    )
                )
            )
        )

        require(decoded != null)
        assertEquals(0, decoded.payload.size)
        assertEquals("""{"ok":true}""", decoded.metadataJson)
    }

    @Test
    fun audioFrameRoundTrip() {
        val frame = ProtocolFrame(
            type = ProtocolFrameType.AUDIO_FRAME,
            channel = ProtocolChannel.AUDIO,
            streamId = 42L,
            sequenceNumber = 7L,
            metadataJson = """{"codec":"opus","durationMs":20}""",
            payload = byteArrayOf(11, 12, 13)
        )

        val decoded = ProtocolCodec.readFrame(ByteArrayInputStream(ProtocolCodec.encode(frame)))

        require(decoded != null)
        assertEquals(ProtocolFrameType.AUDIO_FRAME, decoded.type)
        assertEquals(ProtocolChannel.AUDIO, decoded.channel)
        assertEquals(42L, decoded.streamId)
        assertEquals(7L, decoded.sequenceNumber)
        assertEquals(frame.metadataJson, decoded.metadataJson)
        assertArrayEquals(frame.payload, decoded.payload)
    }

    @Test
    fun jsonMetadataRoundTripPreservesUtf8() {
        val metadata = """{"deviceName":"Pixel 8","message":"hello \uD83D\uDC4B"}"""

        val decoded = ProtocolCodec.readFrame(
            ByteArrayInputStream(
                ProtocolCodec.encode(
                    ProtocolFrame(
                        type = ProtocolFrameType.HANDSHAKE_HELLO,
                        channel = ProtocolChannel.CONTROL,
                        metadataJson = metadata
                    )
                )
            )
        )

        require(decoded != null)
        assertEquals(metadata, decoded.metadataJson)
    }

    @Test
    fun invalidPayloadPartialReadThrowsTypedError() {
        val encoded = ProtocolCodec.encode(
            ProtocolFrame(
                type = ProtocolFrameType.BULK_CHUNK,
                channel = ProtocolChannel.BULK,
                payload = byteArrayOf(9, 8, 7, 6)
            )
        )
        val partial = encoded.copyOfRange(0, encoded.size - 1)

        assertProtocolError(ProtocolError.PARTIAL_READ) {
            ProtocolCodec.readFrame(ByteArrayInputStream(partial))
        }
    }

    private fun headerWithLengths(
        metadataLength: Int,
        payloadLength: Int,
        headerSize: Int = ProtocolConstants.HEADER_SIZE,
        frameType: Int = ProtocolFrameType.CONTROL_MESSAGE.id,
        channel: Int = ProtocolChannel.CONTROL.id
    ): ByteArray {
        return ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(ProtocolConstants.MAGIC)
            .putShort(ProtocolConstants.VERSION.toShort())
            .putShort(headerSize.toShort())
            .putShort(frameType.toShort())
            .putShort(0)
            .putShort(channel.toShort())
            .putShort(0)
            .putLong(0L)
            .putLong(0L)
            .putLong(0L)
            .putLong(0L)
            .putInt(metadataLength)
            .putInt(payloadLength)
            .array()
    }

    private fun assertProtocolError(
        expected: ProtocolError,
        block: () -> Unit
    ) {
        try {
            block()
            fail("Expected protocol error $expected")
        } catch (exception: ProtocolException) {
            assertEquals(expected, exception.error)
        }
    }
}
