package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpeditionEngineTest {

    private val start = 1_000_000L

    @Test
    fun lifecycle_idleToInProgressToReady() {
        assertEquals(ExpeditionState.Idle, ExpeditionEngine.stateAt(null, start))
        val mid = ExpeditionEngine.stateAt(start, start + ExpeditionEngine.DURATION_MILLIS / 2)
        assertEquals(ExpeditionEngine.DURATION_MILLIS / 2, (mid as ExpeditionState.InProgress).remainingMillis)
        assertEquals(
            ExpeditionState.ReadyToOpen,
            ExpeditionEngine.stateAt(start, start + ExpeditionEngine.DURATION_MILLIS),
        )
    }

    @Test
    fun restart_sameInputs_sameState() {
        // 재시작 정합: 상태가 시작 시각에서만 파생 — 같은 입력 = 같은 상태
        val now = start + 3_600_000L
        assertEquals(ExpeditionEngine.stateAt(start, now), ExpeditionEngine.stateAt(start, now))
    }

    @Test
    fun clockRollback_clampsRemaining_neverExceedsDuration() {
        // 시계 후퇴: 잔여가 전체 길이를 넘지 않는다 (음수 elapsed 방어)
        val state = ExpeditionEngine.stateAt(start, nowMillis = start - 10_000L)
        assertTrue((state as ExpeditionState.InProgress).remainingMillis <= ExpeditionEngine.DURATION_MILLIS)
    }

    @Test
    fun clockForward_completesImmediately() {
        // 시계 전진: 완료 — 이득은 개봉 1회분뿐(보상 상한은 E5-7)
        assertEquals(
            ExpeditionState.ReadyToOpen,
            ExpeditionEngine.stateAt(start, start + ExpeditionEngine.DURATION_MILLIS * 10),
        )
    }
}
