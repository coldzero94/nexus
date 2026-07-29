package com.nexus.core

/** 첫 세션에 홈이 띄울 안내 (#211) — 둘은 **상호 배타**다. 한 화면에 코치와 축하를 겹치지 않는다. */
sealed interface FirstSessionCue {
    /** 띄울 것 없음. */
    data object None : FirstSessionCue

    /** 첫 행동 코치 — "잠깐 걸으면 첫 성장을 볼 수 있어요". 오늘 활동이 0일 때 1회. */
    data object Coach : FirstSessionCue

    /** 첫 활동 XP 축하 — 사용자의 움직임이 원장에 처음 쌓인 순간 1회. */
    data object FirstXp : FirstSessionCue
}

/**
 * 첫 세션 루프 판정 (#211, E14-1) — '만났다 → 자랐다'를 첫날에 닫는다.
 *
 * 소급 레벨 연출(#44)은 이력 레벨 2+에만 발동해서, 완전 신규·저이력·데모→연결 사용자는 홈에
 * 착지해도 정적 캐릭터와 0 요약만 봤다. 리텐션 최대 레버인 '첫 성공 경험'이 통째로 비어 있던 것이다.
 *
 * ## 소급 XP를 축하하지 않는 방법 — 기준선
 *
 * "첫 XP"를 `lifetimeXp > 0`으로 잡으면 #44가 소급 지급한 지난 28일치가 곧바로 축하를 터뜨린다.
 * 사용자가 **아무것도 안 했는데** "네 움직임이 자라게 했어요"라고 말하는 셈이라, 증명하려던 약속을
 * 오히려 무너뜨린다.
 *
 * 그래서 연결 직후 한 번 [baselineXp]에 그 시점 누적 XP를 박아 두고(= 소급분은 전부 여기 흡수),
 * **기준선을 넘어선 증가분**만 사용자 활동으로 본다. 데모로 둘러보다 나중에 연결한 사용자도 같다 —
 * 연결 시점에 기준선이 잡히므로 연결과 함께 들어온 이력이 아니라 그 뒤의 첫 실활동에서 발동한다.
 */
object FirstSession {
    /** 기준선 미설정 — 아직 연결 전이거나 첫 연결 로드 중. */
    const val NO_BASELINE: Int = -1

    /**
     * @param baselineXp 연결 시점에 박아 둔 누적 XP. [NO_BASELINE]이면 아직 판정하지 않는다.
     * @param lifetimeXp 현재 누적(상한 적용) XP.
     * @param todayActivityXp 오늘 활동으로 얻은 XP — 0이어야 코치가 의미 있다(이미 움직였으면 코치는 잔소리).
     * @param coachShown 코치를 이미 1회 보여줬는가.
     * @param celebrated 첫 XP 축하를 이미 1회 보여줬는가.
     */
    fun cue(
        baselineXp: Int,
        lifetimeXp: Int,
        todayActivityXp: Int,
        coachShown: Boolean,
        celebrated: Boolean,
    ): FirstSessionCue = when {
        // 기준선이 없으면 소급분과 실활동을 구분할 수 없다 — 호출측이 이 로드에서 세운다
        baselineXp == NO_BASELINE -> FirstSessionCue.None

        // 축하는 **오늘 움직임**이 있을 때만. 기준선 비교만으로는 부족하다: HC 전파 지연(30~60분) 때문에
        // 온보딩 시점엔 이력이 0건이라 기준선이 0으로 잡히고, 30분 뒤 도착한 지난 28일치가 그대로
        // '증가분'으로 보인다. 지난 날짜의 소급분은 오늘 활동 XP를 올리지 못하므로 이 조건이 걸러낸다.
        !celebrated && lifetimeXp > baselineXp && todayActivityXp > 0 -> FirstSessionCue.FirstXp

        // 코치는 오늘 아직 안 움직였을 때만 — 이미 움직인 사람에게 "걸어보세요"는 잔소리다.
        // 축하 조건과 todayActivityXp로 갈려 둘이 동시에 참일 수 없다(상호 배타가 구조로 보장된다).
        !coachShown && todayActivityXp <= 0 -> FirstSessionCue.Coach

        else -> FirstSessionCue.None
    }
}
