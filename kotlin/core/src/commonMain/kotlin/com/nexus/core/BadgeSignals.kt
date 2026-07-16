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
}

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
