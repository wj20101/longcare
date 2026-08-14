package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SingleFrameLeaseTest {
    @Test
    fun `completion closes acquired frame exactly once`() {
        val closedFrames = mutableListOf<Any>()
        val lease = SingleFrameLease<Any> { closedFrames += it }
        val frame = Any()

        assertThat(lease.acquire(frame)).isTrue()
        lease.close(frame)
        lease.close(frame)

        assertThat(closedFrames).containsExactly(frame)
    }

    @Test
    fun `release keeps active frame valid until processing completes`() {
        val closedFrames = mutableListOf<Any>()
        val lease = SingleFrameLease<Any> { closedFrames += it }
        val frame = Any()
        var leaseDrained = false

        assertThat(lease.acquire(frame)).isTrue()
        lease.stopAcceptingFrames { leaseDrained = true }

        assertThat(closedFrames).isEmpty()
        assertThat(leaseDrained).isFalse()
        assertThat(lease.acquire(Any())).isFalse()

        lease.close(frame)

        assertThat(closedFrames).containsExactly(frame)
        assertThat(leaseDrained).isTrue()
    }

    @Test
    fun `release drains immediately when there is no active frame`() {
        val lease = SingleFrameLease<Any> {}
        var leaseDrained = false

        lease.stopAcceptingFrames { leaseDrained = true }

        assertThat(leaseDrained).isTrue()
        assertThat(lease.acquire(Any())).isFalse()
    }

    @Test
    fun `second frame cannot replace an active lease`() {
        val lease = SingleFrameLease<Any> {}

        assertThat(lease.acquire(Any())).isTrue()
        assertThat(lease.acquire(Any())).isFalse()
    }
}
