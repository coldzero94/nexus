package com.nexus.core

/** 성장 계산 입력 — 앱 계층의 세션 요약에서 파생에 필요한 필드만. */
data class SessionInput(val type: ActivityType?, val minutes: Int, val tier: TrustTier)

/** 성장 탭 파생 상태 (#23, E3-11). */
data class GrowthSummary(
    val totalXp: Int,
    val level: Int,
    val progress: Double,
    val affinity: ClassAffinity,
    val axisShares: Map<ActivityType, Double>,
    val stats: Map<Stat, Int>,
)

/**
 * 세션 목록 → 성장 요약. 원장(Room) 영속화 전의 표시 전용 v1 — 걸음 XP는 미포함이며
 * 원장 배선(E3) 후 롤업 조회로 대체된다. 성향(axisShares·affinity)은 신뢰 계수 이전의
 * 기본점수 비중으로, XP·스탯은 신뢰 계수 이후로 계산한다(Tier C는 XP 제외, 성향엔 반영).
 */
object GrowthCalculator {

    fun compute(sessions: List<SessionInput>): GrowthSummary {
        var totalXp = 0
        val stats = linkedMapOf<Stat, Int>()
        val axisBase = linkedMapOf<ActivityType, Double>()
        sessions.forEach { s ->
            val type = s.type ?: return@forEach // 3축 밖 운동은 성장 미반영 (E3-4)
            val base = XpEngine.baseScore(type, s.minutes)
            axisBase[type] = (axisBase[type] ?: 0.0) + base
            if (!TrustPolicy.isXpEligible(s.tier)) return@forEach
            val xp = XpEngine.dailyXp(base.toDouble(), trustMultiplier = s.tier.xpMultiplier)
            totalXp += xp
            StatMapping.distribute(type, xp).forEach { (stat, pts) ->
                stats[stat] = (stats[stat] ?: 0) + pts
            }
        }
        val total = axisBase.values.sum()
        return GrowthSummary(
            totalXp = totalXp,
            level = LevelCurve.displayLevel(totalXp),
            progress = LevelCurve.progressToNextLevel(totalXp),
            affinity = ClassAffinityCalculator.affinity(
                walkBase = axisBase[ActivityType.WALKING] ?: 0.0,
                runBase = axisBase[ActivityType.RUNNING] ?: 0.0,
                strengthBase = axisBase[ActivityType.STRENGTH] ?: 0.0,
            ),
            axisShares = if (total <= 0.0) emptyMap() else axisBase.mapValues { it.value / total },
            stats = stats,
        )
    }
}
