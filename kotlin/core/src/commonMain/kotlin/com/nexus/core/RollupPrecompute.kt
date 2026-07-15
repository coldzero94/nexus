package com.nexus.core

/** 일별 롤업 (#22). */
data class DailyRollup(val dayIndex: Int, val xp: Int, val active: Boolean)

/** 주별 롤업 — [settledXp]는 주간 정산(#21) 적용값. */
data class WeeklyRollup(val weekIndex: Int, val baseXp: Int, val activeDays: Int, val settledXp: Int)

/** 성장 요약 — 성장 탭(#23)이 읽는 프리컴퓨트 결과. */
data class GrowthRollup(
    val totalXp: Int,
    val level: Int,
    val progressToNextLevel: Double,
    val daily: List<DailyRollup>,
    val weekly: List<WeeklyRollup>,
)

/**
 * 일/주 롤업 프리컴퓨트 (#22, E3-10). 일별 XP → 일/주 롤업 + 성장 요약.
 * #21 정산 · #18 레벨 · #62 표시 레벨 바닥값을 합성 → **성장 탭은 이 결과만 읽는다**(완료 기준).
 */
object RollupPrecompute {
    private const val DAYS_PER_WEEK = 7

    fun compute(dailyXp: List<Int>): GrowthRollup {
        val daily = dailyXp.mapIndexed { i, xp -> DailyRollup(dayIndex = i, xp = xp, active = xp > 0) }
        val weekChunks = dailyXp.chunked(DAYS_PER_WEEK)
        val weekly =
            weekChunks.mapIndexed { w, week ->
                val activeDays = week.count { it > 0 }
                val prevActive = if (w > 0) weekChunks[w - 1].count { it > 0 } else 0
                val baseXp = week.sum()
                WeeklyRollup(
                    weekIndex = w,
                    baseXp = baseXp,
                    activeDays = activeDays,
                    settledXp = WeeklySettlement.settle(baseXp, activeDays, prevActive),
                )
            }
        val total = weekly.sumOf { it.settledXp }
        return GrowthRollup(
            totalXp = total,
            level = LevelCurve.displayLevel(total),
            progressToNextLevel = LevelCurve.progressToNextLevel(total),
            daily = daily,
            weekly = weekly,
        )
    }
}
