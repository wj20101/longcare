package com.ytone.longcare.data.userstorage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.entity.UserNamespaceMetadataEntityDb
import com.ytone.longcare.model.UserScopeKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class UserDatabaseFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val pathsFactory: UserNamespacePathsFactory,
) {
    suspend fun open(scopeKey: UserScopeKey): LongCareDatabase = withContext(ioDispatcher) {
        val paths = pathsFactory.forScope(scopeKey)
        val existedBeforeOpen = paths.databaseFile.exists()
        val existingVersion = paths.databaseFile.takeIf(File::exists)?.let { file ->
            runCatching {
                SQLiteDatabase.openDatabase(
                    file.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use(SQLiteDatabase::getVersion)
            }.getOrNull()
        }
        val destructiveRebuildExpected = existingVersion != null &&
            existingVersion != LongCareDatabase.DATABASE_VERSION
        val database = Room.databaseBuilder(
            context = context,
            klass = LongCareDatabase::class.java,
            name = paths.databaseFile.name,
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

        try {
            val dao = database.userNamespaceMetadataDao()
            val metadata = dao.get()
            if (metadata == null) {
                if (existedBeforeOpen && !destructiveRebuildExpected) {
                    throw NamespaceOwnershipException("Existing database has no namespace metadata")
                }
                dao.insert(scopeKey.toDatabaseMetadata())
            } else if (!metadata.matches(scopeKey)) {
                throw NamespaceOwnershipException("Database metadata does not match requested scope")
            }
            database
        } catch (error: Exception) {
            database.close()
            throw error
        }
    }
}

private fun UserScopeKey.toDatabaseMetadata() = UserNamespaceMetadataEntityDb(
    formatVersion = UserNamespaceMetadata.CURRENT_FORMAT_VERSION,
    namespaceId = namespaceId().value,
    companyId = companyId,
    accountId = accountId,
    userId = userId,
)

private fun UserNamespaceMetadataEntityDb.matches(scopeKey: UserScopeKey): Boolean =
    id == UserNamespaceMetadataEntityDb.SINGLE_ROW_ID &&
        formatVersion == UserNamespaceMetadata.CURRENT_FORMAT_VERSION &&
        namespaceId == scopeKey.namespaceId().value &&
        companyId == scopeKey.companyId &&
        accountId == scopeKey.accountId &&
        userId == scopeKey.userId
