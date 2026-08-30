package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopHeaderLayoutSpecResolverTest {

    @Test
    fun width_below_340_and_font_scale_at_1_3_uses_compact_layout() {
        val spec = resolveTopHeaderLayoutSpec(availableWidth = 339.dp, fontScale = 1.3f)

        assertTrue(spec.useCompactLargeTextLayout)
    }

    @Test
    fun width_at_340_does_not_use_compact_layout() {
        val spec = resolveTopHeaderLayoutSpec(availableWidth = 340.dp, fontScale = 1.3f)

        assertFalse(spec.useCompactLargeTextLayout)
    }

    @Test
    fun font_scale_below_1_3_does_not_use_compact_layout() {
        val spec = resolveTopHeaderLayoutSpec(availableWidth = 339.dp, fontScale = 1.29f)

        assertFalse(spec.useCompactLargeTextLayout)
    }

    @Test
    fun width_below_480_uses_120dp_regular_user_block() {
        val spec = resolveTopHeaderLayoutSpec(availableWidth = 479.dp, fontScale = 1f)

        assertEquals(120.dp, spec.regularUserBlockWidth)
    }

    @Test
    fun width_at_480_uses_160dp_regular_user_block() {
        val spec = resolveTopHeaderLayoutSpec(availableWidth = 480.dp, fontScale = 1f)

        assertEquals(160.dp, spec.regularUserBlockWidth)
    }
}
