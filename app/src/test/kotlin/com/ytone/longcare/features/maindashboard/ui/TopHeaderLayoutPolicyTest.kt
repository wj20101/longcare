package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TopHeaderLayoutPolicyTest {
    @Test
    fun compact_width_uses_120_dp_user_block() {
        assertEquals(120.dp, topHeaderUserBlockWidth(479.dp))
    }

    @Test
    fun medium_width_uses_160_dp_user_block_at_breakpoint() {
        assertEquals(160.dp, topHeaderUserBlockWidth(480.dp))
    }
}
