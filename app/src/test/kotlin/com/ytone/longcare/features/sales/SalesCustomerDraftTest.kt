package com.ytone.longcare.features.sales

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class SalesCustomerDraftTest {
    @Test
    fun `default and reset draft have no disability and empty remarks`() {
        val edited = SalesCustomerDraft(userName = "测试客户", isDisability = true, remarks = "备注")
        assertEquals(1, edited.toRequest(null, emptyList()).isDisability)
        val reset = SalesCustomerDraft()
        assertFalse(reset.isDisability)
        assertEquals("", reset.remarks)
        assertEquals(0, reset.toRequest(null, emptyList()).isDisability)
    }

    @Test
    fun `request trims remarks without removing internal newlines or making them required`() {
        val draft = SalesCustomerDraft(userName = " 客户 ", isDisability = true, remarks = " \n 中文备注\n第二行 \n ")
        val request = draft.toRequest(null, listOf("photo-key"))
        assertEquals("客户", request.userName)
        assertEquals(1, request.isDisability)
        assertEquals("中文备注\n第二行", request.remarks)
        assertEquals("photo-key", request.img1)
        assertEquals("", request.img2)
        assertNull(draft.validationMessageRes())
        assertEquals("", draft.copy(remarks = " \n ").toRequest(null, emptyList()).remarks)
        assertNull(draft.copy(remarks = "").validationMessageRes())
    }

    @Test
    fun `saver round trip preserves all original input including new fields`() {
        val draft = SalesCustomerDraft("客户", "证件", "联系人", "电话", "关系", "地址", true, " 备注\n第二行 ")
        val saved = with(salesCustomerDraftSaver) { SaverScope { true }.save(draft) }
        assertEquals(draft, salesCustomerDraftSaver.restore(requireNotNull(saved)))
    }

    @Test
    fun `old six field saved draft restores with new defaults`() {
        val restored = salesCustomerDraftSaver.restore(listOf("客户", "证件", "联系人", "电话", "关系", "地址"))
        assertEquals(SalesCustomerDraft("客户", "证件", "联系人", "电话", "关系", "地址"), restored)
    }
}
