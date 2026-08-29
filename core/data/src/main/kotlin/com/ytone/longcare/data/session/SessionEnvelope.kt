package com.ytone.longcare.data.session

import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.SessionLoginPayload
import com.ytone.longcare.model.User
import com.ytone.longcare.model.toSessionLoginPayload
import com.ytone.longcare.model.toUser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

enum class SessionEnvelopePhase {
    PENDING,
    ACTIVE,
}

data class SessionEnvelope(
    val phase: SessionEnvelopePhase,
    val sessionEpoch: SessionEpoch,
    val payload: SessionLoginPayload,
)

internal object SessionEnvelopeCodec {
    private const val MAGIC = 0x4C435331
    private const val FORMAT_VERSION = 1
    private const val MAX_PAYLOAD_BYTES = 1024 * 1024

    fun encode(envelope: SessionEnvelope): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            val payload = envelope.payload.toUser().encode()
            require(payload.size <= MAX_PAYLOAD_BYTES) { "Session payload is too large" }
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeByte(envelope.phase.ordinal)
            output.writeLong(envelope.sessionEpoch.value)
            output.writeInt(payload.size)
            output.write(payload)
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): SessionEnvelope = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Invalid session envelope magic")
            if (input.readInt() != FORMAT_VERSION) throw IOException("Unsupported session envelope format")
            val phase = SessionEnvelopePhase.entries.getOrNull(input.readUnsignedByte())
                ?: throw IOException("Invalid session phase")
            val epoch = SessionEpoch(input.readLong())
            val payloadSize = input.readInt()
            if (payloadSize !in 1..MAX_PAYLOAD_BYTES || payloadSize != input.available()) {
                throw IOException("Invalid session payload size")
            }
            val payload = ByteArray(payloadSize).also(input::readFully)
            SessionEnvelope(
                phase = phase,
                sessionEpoch = epoch,
                payload = User.ADAPTER.decode(payload).toSessionLoginPayload(),
            )
        }
    } catch (error: Exception) {
        if (error is IOException) throw error
        throw IOException("Failed to decode session envelope", error)
    }
}

sealed interface SessionEnvelopeReadResult {
    data object Missing : SessionEnvelopeReadResult
    data class Loaded(val envelope: SessionEnvelope) : SessionEnvelopeReadResult
    data class Rejected(val cause: Throwable) : SessionEnvelopeReadResult
}

interface SessionEnvelopePersistence {
    suspend fun read(): SessionEnvelopeReadResult
    suspend fun write(envelope: SessionEnvelope)
    suspend fun clear()
}
