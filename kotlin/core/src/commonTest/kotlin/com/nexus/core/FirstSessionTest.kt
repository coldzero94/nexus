package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #211 — 첫 세션 루프. 가장 중요한 건 **소급 XP로는 축하하지 않는다**는 것:
 * 아무것도 안 한 사용자에게 "네 움직임이 자라게 했어요"라고 하면 증명하려던 약속이 거짓이 된다.
 */
class FirstSessionTest {
    private fun cue(
        baseline: Int = 0,
        lifetime: Int = 0,
        todayActivity: Int = 0,
        coachShown: Boolean = false,
        celebrated: Boolean = false,
    ) = FirstSession.cue(baseline, lifetime, todayActivity, coachShown, celebrated)

    @Test
    fun `기준선 전에는 아무것도 띄우지 않는다`() {
        // 이 로드에서 기준선을 세운다 — 소급분과 실활동을 구분할 근거가 아직 없다
        assertEquals(FirstSessionCue.None, cue(baseline = FirstSession.NO_BASELINE, lifetime = 900))
    }

    @Test
    fun `소급 XP는 축하하지 않는다 — 기준선에 흡수된다`() {
        // #44가 지난 28일치 900 XP를 소급 지급한 직후: 기준선도 900이라 증가분이 없다
        assertEquals(FirstSessionCue.Coach, cue(baseline = 900, lifetime = 900))
    }

    @Test
    fun `기준선을 넘어선 증가분이 첫 활동 XP다`() {
        assertEquals(FirstSessionCue.FirstXp, cue(baseline = 900, lifetime = 960, todayActivity = 60))
    }

    @Test
    fun `늦게 도착한 소급분은 축하하지 않는다 — 오늘 활동이 0이면 증가분이어도 아니다`() {
        // HC 전파 지연으로 온보딩 땐 이력 0건 → 기준선 0. 30분 뒤 지난 28일치 900이 도착한 상황.
        // 지난 날짜의 소급분은 오늘 활동 XP를 올리지 못한다 — 여기서 축하하면 명백한 거짓말이다.
        assertEquals(FirstSessionCue.Coach, cue(baseline = 0, lifetime = 900, todayActivity = 0))
    }

    @Test
    fun `이력이 전혀 없는 신규도 같은 규칙 — 오늘 움직이면 축하`() {
        assertEquals(FirstSessionCue.Coach, cue(baseline = 0, lifetime = 0))
        assertEquals(FirstSessionCue.FirstXp, cue(baseline = 0, lifetime = 40, todayActivity = 40))
    }

    @Test
    fun `축하와 코치는 구조적으로 배타 — 오늘 활동 유무로 갈린다`() {
        // todayActivity > 0 이면 축하 후보, <= 0 이면 코치 후보. 둘이 동시에 참일 수 없다.
        assertEquals(FirstSessionCue.FirstXp, cue(baseline = 0, lifetime = 40, todayActivity = 40))
        assertEquals(FirstSessionCue.Coach, cue(baseline = 0, lifetime = 40, todayActivity = 0))
    }

    @Test
    fun `1회 계약 — 이미 보여줬으면 다시 뜨지 않는다`() {
        // 둘 다 소진하면 아무것도 안 뜬다(회전·프로세스 사망·재실행에도 동일)
        assertEquals(FirstSessionCue.None, cue(baseline = 0, lifetime = 40, coachShown = true, celebrated = true))
        assertEquals(FirstSessionCue.None, cue(baseline = 0, lifetime = 0, coachShown = true))
    }

    @Test
    fun `이미 오늘 움직였으면 코치는 잔소리 — 띄우지 않는다`() {
        // 축하는 이미 소진했고 오늘 활동도 있는 상태
        assertEquals(FirstSessionCue.None, cue(baseline = 0, lifetime = 40, todayActivity = 40, celebrated = true))
    }

    @Test
    fun `축하를 소진했으면 코치도 함께 소진된다 — 첫 성장 뒤 첫 코치는 거짓말이다`() {
        // 호출측(dismissFirstXp)이 coachShown도 함께 세운다. 그 계약이 지켜지면 아무것도 안 뜬다.
        assertEquals(
            FirstSessionCue.None,
            cue(baseline = 900, lifetime = 960, todayActivity = 0, coachShown = true, celebrated = true),
        )
    }
}
