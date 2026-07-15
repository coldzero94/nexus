package com.nexus.core

/**
 * 성장 계산 입력 — 앱 계층의 세션 요약에서 파생에 필요한 필드만.
 * [epochDay]는 사용자 시간대 기준 날짜(LocalDate.toEpochDay) — 일일 상한 그룹핑 키.
 */
data class SessionInput(val type: ActivityType?, val minutes: Int, val tier: TrustTier, val epochDay: Long)

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
 * 원장 배선(E3) 후 롤업 조회로 대체된다.
 *
 * 산식 의미론(MVP §5): XP는 **달력일 단위**로 신뢰 반영 raw를 합산한 뒤 일일 상한
 * (200 초과분 절반 체감·300 하드캡)을 적용한다 — 세션 단위 상한은 하루 여러 세션으로
 * 상한을 우회하게 하므로 금지(#23 리뷰). 신뢰 계수는 개인 레벨용 [TrustTier.personalXpMultiplier]
 * (A=B=1.0, C=0.0 — 리더보드용 0.85 감산은 여기 미적용). 성향(axisShares·affinity)은 신뢰 계수 이전의
 * 기본점수 비중(Tier C도 "한 활동"으로 반영 — 코스메틱 전용), XP·스탯은 신뢰 계수
 * 이후·상한 이후로 계산한다.
 */
object GrowthCalculator {

    fun compute(sessions: List<SessionInput>): GrowthSummary {
        val axisBase = linkedMapOf<ActivityType, Double>()
        // 일별 → 유형별 신뢰 반영 raw
        val byDay = linkedMapOf<Long, LinkedHashMap<ActivityType, Double>>()
        sessions.forEach { s ->
            val type = s.type ?: return@forEach // 3축 밖 운동은 성장 미반영 (E3-4)
            val base = XpEngine.baseScore(type, s.minutes)
            axisBase[type] = (axisBase[type] ?: 0.0) + base
            if (!TrustPolicy.isXpEligible(s.tier)) return@forEach
            val day = byDay.getOrPut(s.epochDay) { linkedMapOf() }
            // 개인 레벨 계수 — B도 100%(MVP §5 '워치 없어도 1급'); 0.85(xpMultiplier)는 리더보드 전용
            day[type] = (day[type] ?: 0.0) + base * s.tier.personalXpMultiplier
        }

        var totalXp = 0
        val stats = linkedMapOf<Stat, Int>()
        byDay.values.forEach { perType ->
            val rawSum = perType.values.sum()
            if (rawSum <= 0.0) return@forEach
            val dayXp = XpEngine.applyDailyCap(rawSum)
            totalXp += dayXp
            distributeDay(perType, rawSum, dayXp, stats)
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

    /** 상한 적용된 하루 XP를 유형별 raw 비중으로 나눠 스탯에 배분(잔여는 최대 비중 유형에 — 합 보존). */
    private fun distributeDay(
        perType: Map<ActivityType, Double>,
        rawSum: Double,
        dayXp: Int,
        stats: LinkedHashMap<Stat, Int>,
    ) {
        var allocated = 0
        var topType: ActivityType? = null
        var topRaw = -1.0
        perType.forEach { (type, raw) ->
            if (raw > topRaw) {
                topRaw = raw
                topType = type
            }
            val typeXp = (dayXp * (raw / rawSum)).toInt()
            allocated += typeXp
            StatMapping.distribute(type, typeXp).forEach { (stat, pts) ->
                stats[stat] = (stats[stat] ?: 0) + pts
            }
        }
        val remainder = dayXp - allocated
        val top = topType
        if (remainder > 0 && top != null) {
            StatMapping.distribute(top, remainder).forEach { (stat, pts) ->
                stats[stat] = (stats[stat] ?: 0) + pts
            }
        }
    }
}
