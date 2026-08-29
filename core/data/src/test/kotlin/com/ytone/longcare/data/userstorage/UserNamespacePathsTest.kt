package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.model.UserScopeKey
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserNamespacePathsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = UserNamespacePathsFactory(context)
    private val scopes = mutableSetOf<UserScopeKey>()

    @After
    fun cleanUp() {
        scopes.forEach { scope ->
            factory.forScope(scope).also { paths ->
                context.deleteDatabase(paths.databaseFile.name)
                paths.dataStoreFile.delete()
                paths.namespaceRoot.deleteRecursively()
                paths.sessionRoot.parentFile?.deleteRecursively()
            }
        }
    }

    @Test
    fun `all paths use one stable opaque namespace`() {
        val scope = trackedScope(7101, 8202, 9303)
        val paths = factory.forScope(scope)
        val namespace = scope.namespaceId().value
        val digest = namespace.removePrefix("v1_")

        assertEquals("longcare_user_$namespace.db", paths.databaseFile.name)
        assertEquals("user_$namespace.preferences_pb", paths.dataStoreFile.name)
        assertEquals(digest, paths.namespaceRoot.name)
        assertEquals(digest, paths.sessionRoot.parentFile?.name)
        assertEquals(paths.namespaceRoot.resolve("persistent"), paths.persistentRoot)
        assertTrue(paths.sessionFile("uploads", "42/photo.jpg").isWithin(paths.sessionRoot))
        assertTrue(paths.persistentFile("images", "42/photo.jpg").isWithin(paths.persistentRoot))

        val serializedPaths = listOf(
            paths.databaseFile,
            paths.dataStoreFile,
            paths.namespaceRoot,
            paths.sessionRoot,
        ).joinToString("\n") { it.path }
        assertFalse(serializedPaths.contains("user_${scope.userId}"))
        assertFalse(serializedPaths.contains("${scope.companyId}_${scope.accountId}_${scope.userId}"))
        assertFalse(serializedPaths.contains("/${scope.companyId}/${scope.accountId}/${scope.userId}/"))
        assertEquals(paths.databaseFile, factory.forScope(scope).databaseFile)
    }

    @Test
    fun `different composite identities have physically different paths`() {
        val base = trackedScope(7101, 8202, 9303)
        val otherCompany = trackedScope(7102, 8202, 9303)
        val otherAccount = trackedScope(7101, 8203, 9303)
        val otherUser = trackedScope(7101, 8202, 9304)

        val paths = listOf(base, otherCompany, otherAccount, otherUser).map(factory::forScope)
        assertEquals(paths.size, paths.map { it.namespaceId }.toSet().size)
        assertEquals(paths.size, paths.map { it.databaseFile.canonicalPath }.toSet().size)
        assertEquals(paths.size, paths.map { it.dataStoreFile.canonicalPath }.toSet().size)
        assertNotEquals(paths.first().namespaceRoot, paths.last().namespaceRoot)
    }

    @Test
    fun `absolute traversal and invalid purpose cannot escape namespace`() {
        val paths = factory.forScope(trackedScope(7111, 8222, 9333))

        assertThrows(IllegalArgumentException::class.java) {
            paths.persistentFile("images", File(context.filesDir, "escape.jpg").absolutePath)
        }
        assertThrows(IllegalArgumentException::class.java) {
            paths.sessionFile("uploads", "../../escape.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            paths.sessionFile("../uploads", "escape.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            paths.sessionFile("uploads", " ")
        }
    }

    private fun trackedScope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)
}
