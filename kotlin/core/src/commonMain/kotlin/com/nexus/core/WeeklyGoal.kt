package com.nexus.core

/**
 * 주간 목표 진척 (#215, E14-5) — 순수 함수. 온보딩(#73)에서 받은 '주 N일' 약속의 이번 주 진행을
 * 표시 상태로 계산한다. 주 시작은 월요일(#21 주간 정산과 동일 경계) — 경계 계산은 호출측(플랫폼
 * 달력)이 하고 여기선 활동일 목록을 받는다.
 *
 * 무처벌 원칙: 미달을 '실패'로 규정하지 않는다. 남은 일수는 '더 하면 좋은 것'이고, 지난 주 성적은
 * 다루지 않는다(주 경계에서 리셋).
 */
object WeeklyGoal {
    /**
     * 이번 주 진척 — [activeEpochDays](활동한 날의 epochDay 집합)에서 [weekStartEpochDay]~[todayEpochDay]
     * 구간에 속한 날의 수를 센다. 미래 날짜·중복은 제외되고, 경계가 뒤집힌 입력은 0.
     */
    fun activeDaysThisWeek(activeEpochDays: Collection<Long>, weekStartEpochDay: Long, todayEpochDay: Long): Int {
        if (todayEpochDay < weekStartEpochDay) return 0
        return activeEpochDays.toSet().count { it in weekStartEpochDay..todayEpochDay }
    }

    /**
     * 세션에서 이번 주 활동일 수 — **활동일 = 그날 XP를 받은 날**. 종목이 있고 신뢰 등급이 XP 대상인
     * 세션만 센다([TrustPolicy.isXpEligible]) — 수기(Tier C)는 XP가 0이라 기세·오늘 XP와 어긋나므로
     * 제외한다(#215 리뷰). 활동일 정의의 단일 원천 — 기분 평가(weeklyGoalMet)와 홈 카드가 공유한다.
     */
    fun activeDaysFromSessions(sessions: List<SessionInput>, weekStartEpochDay: Long, todayEpochDay: Long): Int =
        activeDaysThisWeek(
            sessions.filter { it.type != null && TrustPolicy.isXpEligible(it.tier) }.map { it.epochDay },
            weekStartEpochDay,
            todayEpochDay,
        )

    /** 목표 달성 여부 — 활동일이 목표일 이상. [goalDays] 0 이하(비정상)는 항상 달성으로 보지 않는다. */
    fun isMet(activeDays: Int, goalDays: Int): Boolean = goalDays > 0 && activeDays >= goalDays

    /** 목표까지 남은 일수 — 달성 시 0. 표시용(재촉 아님, '더 하면' 프레이밍). */
    fun remainingDays(activeDays: Int, goalDays: Int): Int = (goalDays - activeDays).coerceAtLeast(0)

    /**
     * 이번 주 남은 날짜 수(오늘 포함) — 남은 목표가 물리적으로 가능한지 판단해 카피를 고르는 데 쓴다.
     * 예: 남은 목표 3일인데 남은 날이 2일이면 '다음 주에 이어가요' 톤(질책 금지).
     */
    fun daysLeftInWeek(weekStartEpochDay: Long, todayEpochDay: Long): Int {
        val elapsed = todayEpochDay - weekStartEpochDay
        if (elapsed < 0) return DAYS_IN_WEEK
        return (DAYS_IN_WEEK - elapsed).toInt().coerceIn(0, DAYS_IN_WEEK)
    }

    const val DAYS_IN_WEEK = 7
}
