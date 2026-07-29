package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #228 — 감축 판정의 경계. 핵심은 **부분 감축을 제거로 오인하지 않는 것**:
 * 0.5배를 고른 사용자는 "느리게"를 원한 것이지 "없애라"가 아니다.
 */
class ReduceMotionTest {
    @Test
    fun `0이면 감축`() {
        assertTrue(ReduceMotion.isReduced(0f))
    }

    @Test
    fun `정상 배율은 감축 아님`() {
        assertFalse(ReduceMotion.isReduced(1f))
    }

    @Test
    fun `부분 감축은 감축이 아니다 — 연출을 빼앗지 않는다`() {
        assertFalse(ReduceMotion.isReduced(0.5f))
        assertFalse(ReduceMotion.isReduced(0.25f))
        // 아주 작아도 0이 아니면 사용자는 움직임을 껐다고 말한 적이 없다
        assertFalse(ReduceMotion.isReduced(0.01f))
    }

    @Test
    fun `배속도 감축 아님`() {
        assertFalse(ReduceMotion.isReduced(2f))
    }

    @Test
    fun `손상된 음수 값은 0과 같게`() {
        assertTrue(ReduceMotion.isReduced(-1f))
    }
}
