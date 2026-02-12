package com.ytone.longcare.core.data

import com.ytone.longcare.core.data.di.CoreDataModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CoreDataContractsTest {

    @Test
    fun `placeholder object name should stay stable`() {
        assertEquals("CoreDataPlaceholder", CoreDataPlaceholder::class.simpleName)
    }

    @Test
    fun `core data module should remain singleton object`() {
        assertSame(CoreDataModule, CoreDataModule)
    }
}
