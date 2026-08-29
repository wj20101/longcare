package com.ytone.longcare.data.userstorage

import com.ytone.longcare.model.UserScopeKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserDataStoreRegistryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `concurrent requests for canonical equivalent path create one instance`() = runTest {
        val registry = UserDataStoreRegistry(backgroundScope)
        val root = temporaryFolder.newFolder("datastore")
        val canonical = File(root, "user.preferences_pb")
        val equivalent = File(root, "nested/../user.preferences_pb")

        val instances = List(64) { index ->
            async(Dispatchers.Default) {
                registry.getOrCreate(if (index % 2 == 0) canonical else equivalent)
            }
        }.awaitAll()

        assertEquals(1, registry.cachedInstanceCount())
        assertTrue(instances.all { it === instances.first() })
    }

    @Test
    fun `different user files stay separate while same file survives relogin lookup`() = runTest {
        val registry = UserDataStoreRegistry(backgroundScope)
        val root = temporaryFolder.newFolder("users")
        val firstFile = File(root, "user_v1_first.preferences_pb")
        val secondFile = File(root, "user_v1_second.preferences_pb")

        val firstLogin = registry.getOrCreate(firstFile)
        val secondUser = registry.getOrCreate(secondFile)
        val relogin = registry.getOrCreate(firstFile)

        assertSame(firstLogin, relogin)
        assertNotSame(firstLogin, secondUser)
        assertEquals(2, registry.cachedInstanceCount())
    }

    @Test
    fun `ownership is initialized once and mismatched scope fails closed`() = runTest {
        val registry = UserDataStoreRegistry(backgroundScope)
        val store = registry.getOrCreate(temporaryFolder.newFile("owned.preferences_pb"))
        val owner = UserScopeKey(1, 2, 3)

        registry.verifyOrInitializeOwnership(store, owner)
        registry.verifyOrInitializeOwnership(store, owner)
        val error = runCatching {
            registry.verifyOrInitializeOwnership(store, owner.copy(userId = 4))
        }.exceptionOrNull()

        assertTrue(error is NamespaceOwnershipException)
    }
}
