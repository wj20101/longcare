package com.ytone.longcare.data.userstorage

import com.squareup.moshi.Moshi
import com.ytone.longcare.model.UserScopeKey
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserNamespaceMetadataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val store = UserNamespaceMetadataStore(Moshi.Builder().build())

    @Test
    fun `new namespace initialization is durable and idempotent`() {
        val paths = pathsFor(UserScopeKey(101, 202, 303))

        store.verifyOrCreate(paths)
        val firstContents = paths.metadataFile.readText()
        store.verifyOrCreate(paths)

        assertEquals(firstContents, paths.metadataFile.readText())
        assertEquals(UserNamespaceMetadata.from(paths.scopeKey), metadata(paths.metadataFile))
    }

    @Test
    fun `corrupt missing mismatched or unsupported metadata fails closed`() {
        val paths = pathsFor(UserScopeKey(111, 222, 333))
        paths.metadataFile.parentFile?.mkdirs()
        val invalidPayloads = listOf(
            "not-json",
            """{"formatVersion":1,"companyId":111,"accountId":222,"userId":333}""",
            """{"formatVersion":2,"namespaceId":"${paths.namespaceId.value}","companyId":111,"accountId":222,"userId":333}""",
            """{"formatVersion":1,"namespaceId":"${paths.namespaceId.value}","companyId":111,"accountId":222,"userId":334}""",
            """{"formatVersion":1,"namespaceId":"v1_${"0".repeat(64)}","companyId":111,"accountId":222,"userId":333}""",
        )

        invalidPayloads.forEach { payload ->
            paths.metadataFile.writeText(payload)
            assertThrows(NamespaceOwnershipException::class.java) {
                store.verifyOrCreate(paths)
            }
        }
    }

    @Test
    fun `existing storage without descriptor fails closed`() {
        listOf(
            { paths: UserNamespacePaths -> paths.databaseFile.apply { parentFile?.mkdirs(); writeText("db") } },
            { paths: UserNamespacePaths -> paths.dataStoreFile.apply { parentFile?.mkdirs(); writeText("prefs") } },
            { paths: UserNamespacePaths -> paths.persistentFile("images", "photo.jpg").apply { parentFile?.mkdirs(); writeText("image") } },
        ).forEachIndexed { index, createLegacyData ->
            val paths = pathsFor(UserScopeKey(120 + index, 220, 320))
            createLegacyData(paths)

            assertThrows(NamespaceOwnershipException::class.java) {
                store.verifyOrCreate(paths)
            }
        }
    }

    private fun metadata(file: File): UserNamespaceMetadata =
        requireNotNull(Moshi.Builder().build().adapter(UserNamespaceMetadata::class.java).fromJson(file.readText()))

    private fun pathsFor(scopeKey: UserScopeKey): UserNamespacePaths {
        val root = temporaryFolder.newFolder(scopeKey.namespaceId().value)
        val namespaceRoot = File(root, "namespace")
        return UserNamespacePaths(
            scopeKey = scopeKey,
            namespaceId = scopeKey.namespaceId(),
            databaseFile = File(root, "database.db"),
            dataStoreFile = File(root, "datastore.preferences_pb"),
            namespaceRoot = namespaceRoot,
            metadataFile = File(namespaceRoot, "namespace.json"),
            persistentRoot = File(namespaceRoot, "persistent"),
            sessionRoot = File(root, "session"),
        )
    }
}
