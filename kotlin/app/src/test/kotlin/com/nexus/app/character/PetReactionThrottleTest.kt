package com.nexus.app.character

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 쓰다듬기 연타 억제 (#217 완료 기준) — 두 방향으로 틀리면 안 된다:
 * 너무 무르면 바운스가 매 탭마다 재시작해 경련처럼 보이고 대사 풀이 순식간에 소진되며,
 * 너무 빡세면 "만졌는데 반응이 없다"가 되어 애착 훅 자체가 죽는다.
 */
class PetReactionThrottleTest {
    private val window = PetReactionThrottle.DEFAULT_WINDOW_MILLIS

    @Test
    fun `첫 탭은 언제나 수락된다`() {
        assertTrue(PetReactionThrottle().accept(0L))
        // 시계가 0에서 시작하지 않아도 동일 — 초기값과의 뺄셈에 기대지 않는다
        assertTrue(PetReactionThrottle().accept(Long.MAX_VALUE / 2))
    }

    @Test
    fun `창 안의 연타는 버린다`() {
        val throttle = PetReactionThrottle()
        assertTrue(throttle.accept(1_000L))
        assertFalse(throttle.accept(1_001L))
        assertFalse(throttle.accept(1_000L + window - 1))
    }

    @Test
    fun `창이 지나면 다시 수락한다`() {
        val throttle = PetReactionThrottle()
        assertTrue(throttle.accept(1_000L))
        assertTrue(throttle.accept(1_000L + window))
    }

    @Test
    fun `버려진 탭은 창을 밀지 않는다`() {
        // 거절된 탭이 기준 시각을 갱신하면, 연타하는 동안 영원히 반응이 안 나온다
        val throttle = PetReactionThrottle()
        assertTrue(throttle.accept(1_000L))
        repeat(10) { assertFalse(throttle.accept(1_000L + it * 10L)) }
        assertTrue(throttle.accept(1_000L + window), "연타 중에도 창이 지나면 반응해야 한다")
    }
}
