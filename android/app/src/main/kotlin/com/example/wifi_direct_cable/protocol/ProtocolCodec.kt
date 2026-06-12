package com.example.wifi_direct_cable.protocol

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object ProtocolCodec {
    fun encode(frame: ProtocolFrame): ByteArray {
        val metadataBytes = frame.metadataJson.toByteArray(Charsets.UTF_8)
        validateLengths(metadataBytes.size, frame.payload.size)

        val header = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(ProtocolConstants.MAGIC)
            .putShort(ProtocolConstants.VERSION.toShort())
            .putShort(ProtocolConstants.HEADER_SIZE.toShort())
            .putShort(frame.type.id.toShort())
            .putShort(frame.flags.toShort())
            .putShort(frame.channel.id.toShort())
            .putShort(0)
            .putLong(frame.streamId)
            .putLong(frame.sequenceNumber)
            .putLong(frame.correlationId.mostSignificantBits)
            .putLong(frame.correlationId.leastSignificantBits)
            .putInt(metadataBytes.size)
            .putInt(frame.payload.size)
            .array()

        return ByteArrayOutputStream(
            ProtocolConstants.HEADER_SIZE + metadataBytes.size + frame.payload.size
        ).use { output ->
            output.write(header)
            output.write(metadataBytes)
            output.write(frame.payload)
            output.toByteArray()
        }
    }

    fun writeFrame(frame: ProtocolFrame, outputStream: OutputStream) {
        outputStream.write(encode(frame))
        outputStream.flush()
    }

    fun readFrame(inputStream: InputStream): ProtocolFrame? {
        val header = ByteArray(ProtocolConstants.HEADER_SIZE)
        val firstByte = inputStream.read()
        if (firstByte == -1) return null
        header[0] = firstByte.toByte()
        readFully(
            inputStream = inputStream,
            buffer = header,
            offset = 1,
            length = ProtocolConstants.HEADER_SIZE - 1,
            error = ProtocolError.PARTIAL_READ,
            label = "header"
        )

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.int
        if (magic != ProtocolConstants.MAGIC) {
            throw ProtocolException(
                ProtocolError.MALFORMED_MAGIC,
                "Malformed protocol magic: 0x${magic.toUInt().toString(16)}"
            )
        }

        val version = buffer.unsignedShort()
        if (version != ProtocolConstants.VERSION) {
            throw ProtocolException(
                ProtocolError.UNSUPPORTED_VERSION,
                "Unsupported protocol version: $version"
            )
        }

        val headerSize = buffer.unsignedShort()
        if (headerSize != ProtocolConstants.HEADER_SIZE) {
            throw ProtocolException(
                ProtocolError.INVALID_HEADER_SIZE,
                "Invalid protocol header size: $headerSize"
            )
        }

        val type = ProtocolFrameType.fromId(buffer.unsignedShort())
        val flags = buffer.unsignedShort()
        val channel = ProtocolChannel.fromId(buffer.unsignedShort())
        buffer.unsignedShort() // Reserved.
        val streamId = buffer.long
        val sequenceNumber = buffer.long
        val correlationId = UUID(buffer.long, buffer.long)
        val metadataLength = buffer.int
        val payloadLength = buffer.int
        validateLengths(metadataLength, payloadLength)

        val metadataBytes = ByteArray(metadataLength)
        if (metadataLength > 0) {
            readFully(
                inputStream = inputStream,
                buffer = metadataBytes,
                offset = 0,
                length = metadataLength,
                error = ProtocolError.PARTIAL_READ,
                label = "metadata"
            )
        }

        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            readFully(
                inputStream = inputStream,
                buffer = payload,
                offset = 0,
                length = payloadLength,
                error = ProtocolError.PARTIAL_READ,
                label = "payload"
            )
        }

        return ProtocolFrame(
            type = type,
            channel = channel,
            flags = flags,
            streamId = streamId,
            sequenceNumber = sequenceNumber,
            correlationId = correlationId,
            metadataJson = metadataBytes.toString(Charsets.UTF_8),
            payload = payload
        )
    }

    private fun validateLengths(metadataLength: Int, payloadLength: Int) {
        if (metadataLength < 0 || payloadLength < 0) {
            throw ProtocolException(
                ProtocolError.INVALID_LENGTH,
                "Negative metadata or payload length"
            )
        }
        if (metadataLength > ProtocolConstants.MAX_METADATA_BYTES) {
            throw ProtocolException(
                ProtocolError.METADATA_TOO_LARGE,
                "Metadata length $metadataLength exceeds ${ProtocolConstants.MAX_METADATA_BYTES}"
            )
        }
        if (payloadLength > ProtocolConstants.MAX_PAYLOAD_BYTES) {
            throw ProtocolException(
                ProtocolError.PAYLOAD_TOO_LARGE,
                "Payload length $payloadLength exceeds ${ProtocolConstants.MAX_PAYLOAD_BYTES}"
            )
        }
    }

    private fun readFully(
        inputStream: InputStream,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        error: ProtocolError,
        label: String
    ) {
        var totalRead = 0
        while (totalRead < length) {
            val read = inputStream.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) {
                throw ProtocolException(
                    error,
                    "Unexpected end of stream while reading $label"
                )
            }
            totalRead += read
        }
    }

    private fun ByteBuffer.unsignedShort(): Int = short.toInt() and 0xffff
}
