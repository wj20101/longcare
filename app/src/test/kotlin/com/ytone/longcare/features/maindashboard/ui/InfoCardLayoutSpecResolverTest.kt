package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoCardLayoutSpecResolverTest {

    @Test
    fun compact_width_hides_subtitle_and_uses_smallest_supported_title_size() {
        val spec = resolveInfoCardLayoutSpec(cardWidth = 160.dp, hasBadge = true)

        assertEquals(13.sp, spec.titleFontSize)
        assertFalse(spec.showSubtitle)
    }

    @Test
    fun regular_width_keeps_default_title_size_and_subtitle() {
        val spec = resolveInfoCardLayoutSpec(cardWidth = 176.dp, hasBadge = false)

        assertEquals(15.sp, spec.titleFontSize)
        assertTrue(spec.showSubtitle)
    }

    @Test
    fun badge_presence_does_not_change_compact_mode_decision() {
        val compact = resolveInfoCardLayoutSpec(cardWidth = 160.dp, hasBadge = true)
        val noBadge = resolveInfoCardLayoutSpec(cardWidth = 160.dp, hasBadge = false)

        assertEquals(compact.titleFontSize, noBadge.titleFontSize)
        assertEquals(compact.showSubtitle, noBadge.showSubtitle)
    }
}
