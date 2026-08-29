package com.ytone.longcare.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.data.session.AndroidKeystoreSessionCipher
import com.ytone.longcare.data.session.SessionCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProcessSessionDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessSessionCryptoModule {
    @Binds
    @Singleton
    abstract fun bindSessionCipher(implementation: AndroidKeystoreSessionCipher): SessionCipher
}

@Module
@InstallIn(SingletonComponent::class)
object ProcessSessionDataStoreModule {
    @Provides
    @Singleton
    @ProcessSessionDataStore
    fun provideProcessSessionDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = applicationScope) {
        File(context.noBackupFilesDir, "session/longcare_session_v1.preferences_pb").also { file ->
            file.parentFile?.let { parent ->
                check(parent.exists() || parent.mkdirs()) { "Unable to create session store directory" }
            }
        }
    }
}
