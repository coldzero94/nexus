package com.nexus.app.character

import android.content.Context
import android.util.Log
import com.nexus.core.Baseline
import com.nexus.core.MoodContext
import com.nexus.core.MoodEvaluator
import com.nexus.core.MoodResult
import com.nexus.core.SessionInput
import com.nexus.core.XpEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate

private const val TAG = "MoodResolver"

/**
 * 기분 배선 (#212, E14-2) — 홈·위젯이 정적 idle/walk 2상태가 아니라 [MoodEvaluator]가 고른
 * 기분/표정/대사로 캐릭터를 렌더하게 잇는다. 순수 조립([buildMoodContext])과 자산 로드
 * ([resolveMood])를 분리해 전자를 단위 테스트로 고정한다(#28 엔진은 core에서 이미 검증).
 *
 * highIntensity·leveledUp·newBadge·newRecord는 아직 홈 로드에 신호원이 없어 기본값(false)이다
 * — 신남은 personalCoef, 뿌듯은 weeklyGoalMet로 이미 도달 가능하고, 신호원이 생기면 여기 인자만
 * 채우면 표·표정·대사 무수정으로 승격된다(E4-4 계약).
 */
object MoodResolver {

    /** 홈에서 가용한 신호로 [MoodContext] 조립 — 순수 함수(단위 테스트 대상). */
    fun buildMoodContext(
        todayActiveMin: Int,
        personalCoef: Double,
        restMode: Boolean,
        weeklyGoalMet: Boolean,
        condition: Int,
    ): MoodContext = MoodContext(
        todayActiveMin = todayActiveMin,
        personalCoef = personalCoef,
        restMode = restMode,
        weeklyGoalMet = weeklyGoalMet,
        condition = condition,
    )

    /** 이번 주 활동일이 주간 목표일 수 이상인가 — 순수. */
    fun weeklyGoalMet(activeDaysThisWeek: Int, goalDays: Int): Boolean = activeDaysThisWeek >= goalDays

    /** 최근 세션에서 홈이 가진 신호로 [MoodContext] 조립 — 홈 로드의 단일 진입점(#212). */
    fun contextFromSessions(
        sessions: List<SessionInput>,
        today: LocalDate,
        restMode: Boolean,
        goalDays: Int,
        condition: Int,
    ): MoodContext {
        val todayEpoch = today.toEpochDay()
        return buildMoodContext(
            todayActiveMin = sessions.filter { it.epochDay == todayEpoch && it.type != null }.sumOf { it.minutes },
            personalCoef = personalCoefToday(sessions, todayEpoch),
            restMode = restMode,
            weeklyGoalMet = weeklyGoalMet(activeDaysThisWeek(sessions, today), goalDays),
            condition = condition,
        )
    }

    /** 오늘의 개인 계수 — 오늘 기본점수 대 최근 활동일 평균([Baseline]). */
    private fun personalCoefToday(sessions: List<SessionInput>, todayEpoch: Long): Double {
        val dailyBase = sessions
            .filter { it.type != null }
            .groupBy { it.epochDay }
            .mapValues { (_, s) -> s.sumOf { XpEngine.baseScore(it.type!!, it.minutes) }.toDouble() }
        val todayBase = dailyBase[todayEpoch] ?: 0.0
        val prior = dailyBase.filterKeys { it < todayEpoch }.toSortedMap().values.toList()
        return Baseline.personalCoefficient(todayBase, prior)
    }

    /** 이번 주(월요일 시작) 활동일 수 — 주간 목표 달성 판정용. */
    private fun activeDaysThisWeek(sessions: List<SessionInput>, today: LocalDate): Int {
        val weekStart = today.with(DayOfWeek.MONDAY).toEpochDay()
        return sessions
            .filter { it.type != null && it.epochDay in weekStart..today.toEpochDay() }
            .map { it.epochDay }
            .distinct()
            .count()
    }

    /** 기분 표를 로드해 평가 — 부가 정보라 실패는 null(호출자가 idle/walk 폴백, #130 catch 계약). */
    suspend fun resolveMood(context: Context, moodContext: MoodContext): MoodResult? = try {
        withContext(Dispatchers.IO) {
            MoodEvaluator.evaluate(CharacterAssets(context).loadMoodTable(), moodContext)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.w(TAG, "mood table load failure", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "mood table invalid", e) // SerializationException 포함(하위 타입)
        null
    }

    /**
     * 렌더 상태 결정 — 표정 아트([face])가 있으면 그 상태, 없으면 활동 여부로 idle/walk 폴백.
     * 표정 5종 아트(#66)가 랜딩하면 코드 무수정으로 표정이 살아난다(에셋 규약 character_{face}_0).
     */
    fun renderState(assets: CharacterAssets, face: String?, todayActiveMin: Int): String {
        if (face != null && assets.frameResIdOrNull(face, 0) != null) return face
        return if (todayActiveMin > 0) "walk" else "idle"
    }
}
