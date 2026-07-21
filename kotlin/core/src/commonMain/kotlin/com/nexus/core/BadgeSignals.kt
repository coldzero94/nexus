package com.nexus.core

/**
 * 연속 활동일 계산 (#175, E5-11) — 배지 `streakDays` 신호원. 순수 함수라 플랫폼 시계에 의존하지
 * 않는다(호출자가 일별 활동 시리즈를 만든다). [active]는 **오래된→최신** 순.
 */
object StreakCalculator {
    /**
     * 시리즈 끝(가장 최근)에서부터 끊기지 않고 이어지는 연속 활동일. 마지막 날이 비활동이면 0이다
     * — '오늘 아직 안 움직임'을 스트릭 유지로 볼지(그레이스)는 호출자가 시리즈 구성으로 정한다.
     */
    fun currentStreak(active: List<Boolean>): Int {
        var streak = 0
        for (i in active.indices.reversed()) {
            if (!active[i]) break
            streak++
        }
        return streak
    }

    /** 시리즈 전체에서 가장 긴 연속 활동일. */
    fun longestStreak(active: List<Boolean>): Int {
        var longest = 0
        var run = 0
        for (day in active) {
            run = if (day) run + 1 else 0
            if (run > longest) longest = run
        }
        return longest
    }

    /**
     * 사용자 표시용 기세 상태 (#214) — 무처벌·용서형. [active]는 오래된→최신, 마지막이 오늘.
     * **오늘 아직 안 채운 것은 끊김이 아니다**: 오늘이 비활동이면 어제까지의 기세를 유지하고
     * `todayPending`으로 "오늘 채우면 +1"을 알린다(그레이스). 휴식일은 호출자가 [active]에서 true로
     * 접어 넣어 유지시킨다. 최장 기세는 [priorLongest]와 합쳐 **단조 증가**(퇴행 없음, 불변식 ④).
     */
    fun status(active: List<Boolean>, priorLongest: Int): StreakStatus {
        val todayActive = active.lastOrNull() == true
        val effective = if (todayActive) active else active.dropLast(1)
        val current = currentStreak(effective)
        val longest = maxOf(priorLongest, longestStreak(active), current)
        return StreakStatus(current = current, longest = longest, todayPending = !todayActive)
    }
}

/**
 * 기세 표시 상태 (#214) — [current] 현재 연속일, [longest] 최장(영속·단조), [todayPending] 오늘 미충족
 * (그레이스: 끊긴 게 아니라 "채우면 이어짐").
 */
data class StreakStatus(val current: Int, val longest: Int, val todayPending: Boolean)

/**
 * 배지 해금 신호 조립 (#175, E5-11) — 원장 누적 XP(#163)와 일별 활동 시리즈로 [BadgeContext]를
 * 만든다. [level]은 [LevelCurve.displayLevel]로 도출해 **성장 탭 표시 레벨과 일치**시킨다(원장 기준).
 * 경로 이원화를 피하려고 이 조립은 원장·걸음 값을 직접 받는다([GrowthCalculator]/[RollupPrecompute]
 * 어느 쪽 산출이든 무관).
 */
object BadgeSignals {
    /**
     * @param cumulativeXp 원장 상한 적용 누적 XP(성장 탭과 동일 소스)
     * @param dailyActive 창 내 일별 활동 여부(오래된→최신). activeDaysTotal·streakDays의 입력
     * @param bestDaySteps 창 내 하루 최대 걸음(#7)
     * @param expeditionsCompleted 완료 원정 수(카운트 소스 마련 전엔 0)
     */
    fun build(
        cumulativeXp: Int,
        dailyActive: List<Boolean>,
        bestDaySteps: Int,
        expeditionsCompleted: Int,
    ): BadgeContext = BadgeContext(
        level = LevelCurve.displayLevel(cumulativeXp),
        cumulativeXp = cumulativeXp,
        activeDaysTotal = dailyActive.count { it },
        streakDays = StreakCalculator.currentStreak(dailyActive),
        expeditionsCompleted = expeditionsCompleted,
        bestDaySteps = bestDaySteps,
    )
}
