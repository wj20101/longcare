package com.ytone.longcare.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ytone.longcare.di.ProcessSessionDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private val encryptedEnvelopeKey = byteArrayPreferencesKey("encrypted_session_envelope_v1")

@Singleton
class EncryptedSessionEnvelopeStore @Inject constructor(
    @param:ProcessSessionDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: SessionCipher,
) : SessionEnvelopePersistence {
    override suspend fun read(): SessionEnvelopeReadResult {
        val encrypted = try {
            dataStore.data.first()[encryptedEnvelopeKey]
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return SessionEnvelopeReadResult.Rejected(error)
        } ?: return SessionEnvelopeReadResult.Missing

        return try {
            SessionEnvelopeReadResult.Loaded(
                SessionEnvelopeCodec.decode(cipher.decrypt(encrypted))
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            try {
                clear()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (clearError: Exception) {
                error.addSuppressed(clearError)
            }
            SessionEnvelopeReadResult.Rejected(error)
        }
    }

    override suspend fun write(envelope: SessionEnvelope) {
        val encrypted = cipher.encrypt(SessionEnvelopeCodec.encode(envelope))
        dataStore.edit { preferences -> preferences[encryptedEnvelopeKey] = encrypted }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(encryptedEnvelopeKey) }
    }
}
