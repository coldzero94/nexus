package com.nexus.app.home

import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.settings.RestModeStore
import com.nexus.core.StreakCalculator
import com.nexus.core.StreakStatus
import java.time.LocalDate

/** 현재 기세 계산 창(일) — 이보다 긴 현재 연속일은 상한이나, 최장은 [StreakStore]가 영속 보존. */
private const val STREAK_WINDOW_DAYS = 90

/**
 * 기세 해석 (#214) — 원장 일별 순 XP(#163)로 활동 시리즈를 만들고, 휴식일(#31)을 활동으로 접어
 * 끊김을 막은 뒤 [StreakCalculator.status]로 그레이스·최장을 계산한다. 최장은 [StreakStore]에
 * 단조 영속(창 밖 기록 보존). 순수 표시값이라 페이로드에 실리지 않는다(불변식 ②).
 */
internal suspend fun resolveStreak(
    ledger: RewardLedgerRepository,
    restStore: RestModeStore,
    streakStore: StreakStore,
    today: LocalDate,
): StreakStatus {
    val xpByDay = ledger.dailyXpMap()
    val todayEpoch = today.toEpochDay()
    val startEpoch = todayEpoch - (STREAK_WINDOW_DAYS - 1)
    // 오래된→최신 순 활동 시리즈: 그날 순 XP>0 이거나 휴식일이면 활동으로 본다(끊김 방지)
    val active = (startEpoch..todayEpoch).map { day ->
        (xpByDay[day] ?: 0.0) > 0.0 || restStore.isRestDay(day)
    }
    val status = StreakCalculator.status(active, streakStore.longest)
    streakStore.observe(status.longest)
    return status
}
