package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #213 — 빈 상태 노출 3분기. 두 방향으로 틀리면 안 된다:
 * - 안 떠야 할 때 뜨면: 기존 사용자에게 "곧 시작해요"가 떠 앱이 자기를 기억 못 하는 것처럼 읽힌다.
 * - **실데이터를 가리면**: 걸음은 XP를 만들지 않으므로 걷기만 하는 사용자는 XP가 계속 0이다.
 *   여기서 걸음 막대를 숨기면 빈 상태가 오히려 "고장"을 만든다(고치려던 바로 그 문제).
 */
class FirstRunTest {
    @Test
    fun `XP도 데이터도 없으면 대기 상태`() {
        assertTrue(FirstRun.isAwaitingFirstData(lifetimeXp = 0, hasAnyHealthData = false))
    }

    @Test
    fun `데이터가 있으면 대기 아님 — 걸음만 있어도 가리지 않는다`() {
        // 걸음은 XP를 만들지 않는다 → XP는 0인데 보여줄 막대는 있는 상태
        assertFalse(FirstRun.isAwaitingFirstData(lifetimeXp = 0, hasAnyHealthData = true))
    }

    @Test
    fun `XP가 있으면 대기 아님`() {
        assertFalse(FirstRun.isAwaitingFirstData(lifetimeXp = 120, hasAnyHealthData = true))
        // 표시 창은 비었지만(긴 휴식) 원장에 기록이 있는 기존 사용자 — 시작한 사람에게 할 말이 아니다
        assertFalse(FirstRun.isAwaitingFirstData(lifetimeXp = 120, hasAnyHealthData = false))
    }

    @Test
    fun `음수 XP 방어 — 상쇄로 0 아래가 되어도 0과 같게 다룬다`() {
        assertTrue(FirstRun.isAwaitingFirstData(lifetimeXp = -5, hasAnyHealthData = false))
    }
}
