package com.nexus.app.home

import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.settings.RestModeStore
import com.nexus.core.StreakCalculator
import com.nexus.core.StreakStatus
import java.time.LocalDate

/**
 * 현재 기세 계산 창(일). 이보다 긴 연속은 이 창 안에서만 계산되므로 current·창내 longest 모두 상한이나,
 * 관측된 최장은 [StreakStore]가 단조 영속하므로 창 밖으로 밀려도 기록 자체는 잃지 않는다.
 */
private const val STREAK_WINDOW_DAYS = 90

/**
 * 오래된→최신 활동 시리즈 조립 (#214) — 그날 순 XP>0 이거나 휴식일이면 활동으로 접는다(끊김 방지).
 * 순수 함수라 휴식 유지 계약을 단위 테스트로 고정한다([StreakResolverTest]).
 *
 * 휴식 계약(#214, RestModeStore 단일 since 모델 한계): 휴식은 **켜져 있는 동안만** 유지에 반영된다.
 * 퍼-데이 휴식 이력이 없어 휴식 OFF 시 과거 휴식일은 소급 판정되지 않는다(현재 상태로 재계산). 또한
 * 휴식 중엔 무활동 휴식일도 active라 연속이 유지를 넘어 증가할 수 있다 — MVP는 다정한 방향으로 수용.
 */
internal fun buildActiveSeries(
    xpByDay: Map<Long, Double>,
    isRestDay: (Long) -> Boolean,
    startEpoch: Long,
    todayEpoch: Long,
): List<Boolean> = (startEpoch..todayEpoch).map { day ->
    (xpByDay[day] ?: 0.0) > 0.0 || isRestDay(day)
}

/**
 * 기세 해석 (#214) — 원장 일별 순 XP(#163)로 활동 시리즈를 만들고 [StreakCalculator.status]로
 * 그레이스·최장을 계산한다. 최장은 [StreakStore]에 단조 영속. 표시값이라 페이로드 미탑재(②).
 */
internal suspend fun resolveStreak(
    ledger: RewardLedgerRepository,
    restStore: RestModeStore,
    streakStore: StreakStore,
    today: LocalDate,
): StreakStatus {
    val todayEpoch = today.toEpochDay()
    val active = buildActiveSeries(
        xpByDay = ledger.dailyXpMap(),
        isRestDay = restStore::isRestDay,
        startEpoch = todayEpoch - (STREAK_WINDOW_DAYS - 1),
        todayEpoch = todayEpoch,
    )
    val status = StreakCalculator.status(active, streakStore.longest)
    streakStore.observe(status.longest)
    return status
}
