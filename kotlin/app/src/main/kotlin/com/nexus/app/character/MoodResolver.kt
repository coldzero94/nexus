package com.nexus.app.character

import android.content.Context
import android.util.Log
import com.nexus.core.ActivityType
import com.nexus.core.Baseline
import com.nexus.core.MoodContext
import com.nexus.core.MoodEvaluator
import com.nexus.core.MoodResult
import com.nexus.core.SessionInput
import com.nexus.core.WeeklyGoal
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

    /**
     * 홈에서 가용한 신호로 [MoodContext] 조립 — 순수 함수(단위 테스트 대상).
     *
     * `todayActiveMin`을 인자로 받지 않고 [minutesByType]에서 더하는 이유: 둘을 따로 받으면
     * "총 20분인데 종류별은 전부 0분"처럼 **production에서 만들 수 없는 조합**이 테스트에서
     * 만들어진다. 그 조합으로 통과한 단언은 실제 화면과 무관하다(#114 리뷰).
     */
    fun buildMoodContext(
        minutesByType: Map<ActivityType, Int>,
        personalCoef: Double,
        restMode: Boolean,
        weeklyGoalMet: Boolean,
        condition: Int,
    ): MoodContext = MoodContext(
        todayActiveMin = minutesByType.values.sum(),
        walkMin = minutesByType[ActivityType.WALKING] ?: 0,
        runMin = minutesByType[ActivityType.RUNNING] ?: 0,
        strengthMin = minutesByType[ActivityType.STRENGTH] ?: 0,
        personalCoef = personalCoef,
        restMode = restMode,
        weeklyGoalMet = weeklyGoalMet,
        condition = condition,
    )

    /**
     * 오늘 종류별 활동 분 (#114) — 순수. 종류가 없는 세션(매핑 안 된 종목)은 빠진다:
     * XP도 안 주는 세션에 반응만 붙으면 "얘가 뭘 보고 저러지"가 된다.
     */
    fun minutesByTypeToday(sessions: List<SessionInput>, todayEpoch: Long): Map<ActivityType, Int> = sessions
        .filter { it.epochDay == todayEpoch && it.type != null }
        .groupBy { it.type!! }
        .mapValues { (_, s) -> s.sumOf { it.minutes } }

    /**
     * 주간 목표를 **오늘 넘겼는가** — 순수 (#114 리뷰).
     *
     * "이번 주 달성했다"(`activeDays >= goalDays`)로 두면 안 된다. 활동일 수는 주 안에서 줄지
     * 않으므로 한 번 달성한 뒤로는 **남은 요일 내내 참**이 되고, 그러면 뿌듯(p1)이 다른 모든
     * 기분을 먹는다. 매일 운동하는 사용자일수록 반응 다양성을 못 보게 되는, 정확히 거꾸로 된 결과다
     * (주 4일 목표 기준 7일 중 4일). CHARACTER.md §3도 p1을 '오늘 성취 이벤트'로 적고 있다.
     *
     * 오늘 활동이 없으면 거짓이다 — 안 그러면 달성한 주의 쉬는 날마다 다시 축하한다.
     */
    fun weeklyGoalMet(activeDaysThisWeek: Int, goalDays: Int, activeToday: Boolean): Boolean =
        activeToday && activeDaysThisWeek >= goalDays && activeDaysThisWeek - 1 < goalDays

    /** 최근 세션에서 홈이 가진 신호로 [MoodContext] 조립 — 홈 로드의 단일 진입점(#212). */
    fun contextFromSessions(
        sessions: List<SessionInput>,
        today: LocalDate,
        restMode: Boolean,
        goalDays: Int,
        condition: Int,
    ): MoodContext {
        val todayEpoch = today.toEpochDay()
        val minutesByType = minutesByTypeToday(sessions, todayEpoch)
        return buildMoodContext(
            minutesByType = minutesByType,
            personalCoef = personalCoefToday(sessions, todayEpoch),
            restMode = restMode,
            weeklyGoalMet = weeklyGoalMet(
                activeDaysThisWeek(sessions, today),
                goalDays,
                activeToday = minutesByType.isNotEmpty(),
            ),
            condition = condition,
        )
    }

    /**
     * 오늘의 개인 계수 — 오늘 기본점수 대 최근 활동일 평균([Baseline]). 기분 트리거용 **근사치**로,
     * 정식 XP 경로의 개인계수(걸음 base·dense 창)와 정확히 일치하진 않는다(코스메틱, #212 리뷰 P1).
     */
    private fun personalCoefToday(sessions: List<SessionInput>, todayEpoch: Long): Double {
        val dailyBase = sessions
            .filter { it.type != null }
            .groupBy { it.epochDay }
            .mapValues { (_, s) -> s.sumOf { XpEngine.baseScore(it.type!!, it.minutes) }.toDouble() }
        val todayBase = dailyBase[todayEpoch] ?: 0.0
        val prior = dailyBase.filterKeys { it < todayEpoch }.toSortedMap().values.toList()
        return Baseline.personalCoefficient(todayBase, prior)
    }

    /**
     * 이번 주(월요일 시작) 활동일 수 — 판정은 core [WeeklyGoal] 단일 원천에 위임(#215 리뷰).
     * 홈 카드(#215)와 같은 정의(수기 Tier C 제외)라 두 표시가 어긋나지 않는다.
     */
    private fun activeDaysThisWeek(sessions: List<SessionInput>, today: LocalDate): Int =
        WeeklyGoal.activeDaysFromSessions(
            sessions,
            today.with(DayOfWeek.MONDAY).toEpochDay(),
            today.toEpochDay(),
        )

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
