package com.nexus.app.health

import com.nexus.core.ActivityType
import com.nexus.core.Baseline
import com.nexus.core.ClassAffinity
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.GrowthRollup
import com.nexus.core.RollupPrecompute
import com.nexus.core.StepConversion
import com.nexus.core.XpEngine
import java.time.LocalDate
import java.time.ZoneId

/** 성장 화면(#23)이 읽는 실데이터 묶음 (#170). */
data class GrowthData(val rollup: GrowthRollup, val affinity: ClassAffinity)

/**
 * 엔진 실데이터 통합 (#170): HC 걸음(#7)·운동세션(#8) → 일별 기본점수 → dailyXp → 롤업 + 클래스 성향.
 * 걷기는 걸음 수로만 계산(세션 '걷기'는 이중계산 방지로 제외). 신뢰 계수는 개인 레벨상 A·B=1.0(#62).
 */
class GrowthRepository(private val stepRepo: StepRepository, private val exerciseRepo: ExerciseRepository) {
    suspend fun computeGrowth(days: Int = WINDOW_DAYS): GrowthData {
        val steps = stepRepo.readDailySteps(days) // oldest→newest
        val sessions = exerciseRepo.readRecentSessions(days)

        val sessionBaseByDate = sessionBaseByDate(sessions)
        val dailyBases = steps.map { StepConversion.walkingBase(it.steps) + (sessionBaseByDate[it.date] ?: 0.0) }
        val dailyXps = dailyXpSeries(dailyBases)
        val rollup = RollupPrecompute.compute(dailyXps)
        val affinity = computeAffinity(steps.sumOf { StepConversion.walkingBase(it.steps) }, sessions)
        return GrowthData(rollup, affinity)
    }

    /** 러닝·근력 세션의 날짜별 기본점수 합(걷기 세션은 제외 — 걸음으로 계산). */
    private fun sessionBaseByDate(sessions: List<ExerciseSummary>): Map<LocalDate, Double> {
        val zone = ZoneId.systemDefault()
        val byDate = mutableMapOf<LocalDate, Double>()
        for (session in sessions) {
            val base = sessionBase(session.type, session.durationMinutes)
            if (base <= 0.0) continue
            val date = session.start.atZone(zone).toLocalDate()
            byDate[date] = (byDate[date] ?: 0.0) + base
        }
        return byDate
    }

    /** 일별 기본점수 → 일별 XP (개인계수=활동일 baseline, 신뢰=1.0). */
    private fun dailyXpSeries(dailyBases: List<Double>): List<Int> = dailyBases.mapIndexed { i, base ->
        val coef = Baseline.personalCoefficient(base, dailyBases.subList(0, i))
        XpEngine.dailyXp(basePoints = base, personalCoef = coef, trustMultiplier = 1.0)
    }

    private fun computeAffinity(walkBase: Double, sessions: List<ExerciseSummary>): ClassAffinity {
        val runBase = typeBaseTotal(sessions, ActivityType.RUNNING)
        val strBase = typeBaseTotal(sessions, ActivityType.STRENGTH)
        return ClassAffinityCalculator.affinity(walkBase, runBase, strBase)
    }

    private fun typeBaseTotal(sessions: List<ExerciseSummary>, type: ActivityType): Double =
        sessions.filter { it.type == type }.sumOf { sessionBase(it.type, it.durationMinutes) }

    private fun sessionBase(type: ActivityType?, durationMinutes: Long): Double = when (type) {
        ActivityType.RUNNING -> XpEngine.baseScore(ActivityType.RUNNING, durationMinutes.toInt()).toDouble()
        ActivityType.STRENGTH -> XpEngine.baseScore(ActivityType.STRENGTH, durationMinutes.toInt()).toDouble()
        else -> 0.0 // 걷기 세션·기타는 제외(걸음으로 계산)
    }

    companion object {
        const val WINDOW_DAYS = 28
    }
}
