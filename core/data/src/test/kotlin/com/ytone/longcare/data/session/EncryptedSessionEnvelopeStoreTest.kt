package com.ytone.longcare.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.SessionLoginPayload
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedSessionEnvelopeStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `pending active phase and complete payload are one encrypted durable value`() = runTest {
        val file = temporaryFolder.root.resolve("no_backup/session.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            file.also { it.parentFile?.mkdirs() }
        }
        val cipher = TestAesGcmCipher()
        val store = EncryptedSessionEnvelopeStore(dataStore, cipher)
        val pending = SessionEnvelope(SessionEnvelopePhase.PENDING, SessionEpoch(41), payload())

        store.write(pending)
        assertEquals(SessionEnvelopeReadResult.Loaded(pending), store.read())
        val active = pending.copy(phase = SessionEnvelopePhase.ACTIVE)
        store.write(active)
        assertEquals(SessionEnvelopeReadResult.Loaded(active), store.read())

        val diskBytes = file.readBytes()
        assertFalse(diskBytes.containsSequence(pending.payload.token.encodeToByteArray()))
        assertFalse(diskBytes.containsSequence(pending.payload.identityCardNumber.encodeToByteArray()))
        assertFalse(diskBytes.containsSequence(pending.payload.userName.encodeToByteArray()))
    }

    @Test
    fun `wrong key or corrupted ciphertext fails closed and removes durable session`() = runTest {
        val file = temporaryFolder.root.resolve("no_backup/rejected.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            file.also { it.parentFile?.mkdirs() }
        }
        val cipher = TestAesGcmCipher()
        val store = EncryptedSessionEnvelopeStore(dataStore, cipher)
        store.write(SessionEnvelope(SessionEnvelopePhase.ACTIVE, SessionEpoch(99), payload()))

        cipher.invalidateKey()
        assertTrue(store.read() is SessionEnvelopeReadResult.Rejected)
        assertEquals(SessionEnvelopeReadResult.Missing, store.read())
    }

    @Test
    fun `read cancellation is rethrown without deleting a valid durable session`() = runTest {
        val file = temporaryFolder.root.resolve("no_backup/cancelled.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            file.also { it.parentFile?.mkdirs() }
        }
        val validCipher = TestAesGcmCipher()
        val validStore = EncryptedSessionEnvelopeStore(dataStore, validCipher)
        val envelope = SessionEnvelope(SessionEnvelopePhase.ACTIVE, SessionEpoch(101), payload())
        validStore.write(envelope)
        val cancelledStore = EncryptedSessionEnvelopeStore(
            dataStore = dataStore,
            cipher = object : SessionCipher {
                override fun encrypt(plaintext: ByteArray): ByteArray = plaintext

                override fun decrypt(ciphertext: ByteArray): ByteArray {
                    throw CancellationException("cancel read")
                }
            },
        )

        val failure = runCatching { cancelledStore.read() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(SessionEnvelopeReadResult.Loaded(envelope), validStore.read())
    }

    private fun payload() = SessionLoginPayload(
        companyId = 10,
        accountId = 20,
        userId = 30,
        userName = "sensitive-name",
        headUrl = "https://example.test/avatar",
        userIdentity = 2,
        identityCardNumber = "330000199901011234",
        gender = 1,
        token = "secret-token-value",
    )

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { offset ->
                this[start + offset] == sequence[offset]
            }
        }

    private class TestAesGcmCipher : SessionCipher {
        private var keyBytes = ByteArray(32) { index -> (index + 1).toByte() }

        override fun encrypt(plaintext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            val encrypted = cipher.doFinal(plaintext)
            return ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
                .put(cipher.iv.size.toByte())
                .put(cipher.iv)
                .put(encrypted)
                .array()
        }

        override fun decrypt(ciphertext: ByteArray): ByteArray {
            val buffer = ByteBuffer.wrap(ciphertext)
            val iv = ByteArray(buffer.get().toInt() and 0xff).also(buffer::get)
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                doFinal(encrypted)
            }
        }

        fun invalidateKey() {
            keyBytes = ByteArray(32) { index -> (index + 33).toByte() }
        }
    }
}
