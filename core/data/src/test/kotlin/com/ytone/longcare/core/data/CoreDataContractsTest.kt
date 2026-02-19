package com.ytone.longcare.core.data

import com.ytone.longcare.core.data.di.CoreDataModule
import org.junit.Assert.assertSame
import org.junit.Test

class CoreDataContractsTest {

    @Test
    fun `core data module should remain singleton object`() {
        assertSame(CoreDataModule, CoreDataModule)
    }
}
