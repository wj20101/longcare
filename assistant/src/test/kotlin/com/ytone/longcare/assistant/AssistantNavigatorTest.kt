package com.ytone.longcare.assistant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AssistantNavigatorTest {
    @Test fun routesAndEntryIdentitySurviveSerialization() {
        val routes = listOf(AssistantHome, AssistantLogin) + AssistantTool.entries.map { AssistantToolRoute(it, Long.MAX_VALUE) }
        routes.forEach {
            val entry = AssistantEntry(it)
            assertEquals(entry, Json.decodeFromString<AssistantEntry>(Json.encodeToString(entry)))
            assertNotEquals(entry.id, AssistantEntry(it).id)
        }
    }

    @Test fun loginResumeIsOnceAndStaleLoginCannotNavigateAgain() {
        val stack = mutableListOf<NavKey>(AssistantEntry(AssistantHome))
        val nav = AssistantNavigator(stack)
        nav.login()
        val login = nav.forEntry((stack.last() as AssistantEntry).id)
        login.resume(AssistantToolRoute(AssistantTool.DEFAULT_FACE, 42))
        login.resume(AssistantToolRoute(AssistantTool.DEFAULT_FACE, 42))
        assertEquals(2, stack.size)
        assertEquals(AssistantToolRoute(AssistantTool.DEFAULT_FACE, 42), (stack.last() as AssistantEntry).route)
    }

    @Test fun rootBackIsSafeAndResetCreatesNewIdentity() {
        val first = AssistantEntry(AssistantHome)
        val stack = mutableListOf<NavKey>(first)
        val nav = AssistantNavigator(stack)
        nav.back()
        assertEquals(listOf(first), stack)
        nav.navigate(AssistantToolRoute(AssistantTool.CAMERA))
        val old = nav.forEntry((stack.last() as AssistantEntry).id)
        nav.reset()
        old.navigate(AssistantLogin)
        assertEquals(1, stack.size)
        assertNotEquals(first.id, (stack.last() as AssistantEntry).id)
    }

    @Test fun nestedToolHomeDropsEveryChild() {
        val stack = mutableListOf<NavKey>(AssistantEntry(AssistantHome))
        val nav = AssistantNavigator(stack)
        nav.navigate(AssistantToolRoute(AssistantTool.DEFAULT_FACE))
        nav.navigate(AssistantToolRoute(AssistantTool.DEFAULT_FACE, 7))
        nav.forEntry((stack.last() as AssistantEntry).id).home()
        assertEquals(1, stack.size)
    }
}
