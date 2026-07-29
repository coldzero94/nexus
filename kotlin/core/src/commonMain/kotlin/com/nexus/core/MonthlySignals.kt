package com.nexus.core

/**
 * 월 한정 배지 신호 집계 (#206, E5-5 후속) — 순수 함수. [MonthlyBadgeContext]를 채울 값을
 * 일별 데이터에서 뽑는다.
 *
 * 월 경계는 epochDay 범위로 받는다 — 달의 길이·시간대 계산은 플랫폼 달력(호출측)이 하고, 여기선
 * "범위 안을 세는" 순수 규칙만 담는다(core는 Android 비의존).
 */
object MonthlySignals {
    /**
     * 이달 활동일 수 — 범위 안에서 XP가 **양수**인 날. 취소로 상쇄돼 0이 된 날은 활동일이 아니다
     * (원장이 최종 진실 — 지급됐다가 삭제된 세션까지 세면 배지가 거저 열린다).
     */
    fun activeDays(dailyXp: Map<Long, Double>, monthStartEpochDay: Long, monthEndEpochDay: Long): Int {
        if (monthEndEpochDay < monthStartEpochDay) return 0
        return dailyXp.count { (day, xp) -> day in monthStartEpochDay..monthEndEpochDay && xp > 0.0 }
    }

    /**
     * 이달 누적 XP — 범위 안 합에 **일일 상한을 적용**한다([LedgerMath.cappedTotalXp]와 같은 규칙).
     *
     * 원장은 세션 단위 무상한 지급을 박제하므로 원시 합은 화면에 보이는 XP와 다르다 — 하루 900 원시
     * XP인 날이 사흘이면 앱은 900(3×300)을 주는데 원시 합은 2700이다. 배지 조건이 이 값을 쓰면
     * 의도한 노력의 1/3에 열린다.
     */
    fun totalXp(dailyXp: Map<Long, Double>, monthStartEpochDay: Long, monthEndEpochDay: Long): Int {
        if (monthEndEpochDay < monthStartEpochDay) return 0
        return LedgerMath.cappedTotalXp(dailyXp.filterKeys { it in monthStartEpochDay..monthEndEpochDay })
    }

    /** 이달 누적 걸음 — 범위 안 합. 음수·미래 날짜는 들어올 수 없지만 방어적으로 0 클램프. */
    fun totalSteps(dailySteps: Map<Long, Long>, monthStartEpochDay: Long, monthEndEpochDay: Long): Int {
        if (monthEndEpochDay < monthStartEpochDay) return 0
        val sum = dailySteps
            .filterKeys { it in monthStartEpochDay..monthEndEpochDay }
            .values
            .sumOf { it.coerceAtLeast(0L) }
        return sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
