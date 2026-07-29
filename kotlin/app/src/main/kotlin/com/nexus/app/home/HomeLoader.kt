package com.nexus.app.home

import android.os.RemoteException
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.nexus.app.character.MoodResolver
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.SleepRepository
import com.nexus.app.health.StepRepository
import com.nexus.app.health.sleepHoursOrNull
import com.nexus.app.settings.RestModeStore
import com.nexus.core.ConditionEngine
import com.nexus.core.EnergyEngine
import com.nexus.core.ExpeditionEngine
import com.nexus.core.FirstRun
import com.nexus.core.SessionInput
import com.nexus.core.XpEngine
import com.nexus.core.XpExplainer
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 홈 로드·판정 계층 (#311) — 성장 탭의 `GrowthLoader`와 같은 분리.
 *
 * `HomeScreen.kt`는 렌더만 맡고, Health Connect 읽기·원장 집계·카드 노출 판정은 여기 모은다.
 * 카드가 늘 때 화면 파일이 detekt 임계로 밀리던 원인이 이 두 관심사의 동거였다.
 */
private const val TAG = "HomeLoader"

/** 컨디션 파생 창 — 원장 배선 전의 표시 전용 폴드 범위 (ConditionEngine.fromDailyPoints). */
private const val CONDITION_WINDOW_DAYS = 28

/**
 * 아침 카드 노출 판정 (#36) — 오늘 아직 확인 안 했으면 노출. 소비는 확인 시점(#61 패턴).
 * 최초 실행은 카드 없이 오늘로 기준점만 설정(온보딩 직후 낮의 "좋은 아침" 방지 — 정산과 대칭).
 */
internal fun shouldShowMorningCard(store: MorningCardStore): Boolean {
    val today = LocalDate.now().toEpochDay()
    if (store.lastShownEpochDay == MorningCardStore.UNSET) {
        store.markShown(today)
        return false
    }
    return store.lastShownEpochDay != today
}

/**
 * 저녁 일지 노출 판정 (#70) — [EveningJournalStore.OPEN_HOUR] 이후·오늘 미확인일 때.
 * 최초는 기준점만(아침 카드와 대칭). 저녁 전에 확인 못 한 어제 일지는 이월하지 않는다 —
 * 아침 카드가 "어제의 성장"을 이미 전달하므로 중복 서사 방지.
 */
internal fun shouldShowJournal(store: EveningJournalStore, now: java.time.LocalDateTime): Boolean {
    val today = now.toLocalDate().toEpochDay()
    if (store.lastShownEpochDay == EveningJournalStore.UNSET) {
        // 일지의 콘텐츠는 "오늘" — 기준점을 어제로 두어 설치 당일 저녁 일지를 보존(#70 리뷰 N2)
        store.markShown(today - 1)
    }
    return now.hour >= EveningJournalStore.OPEN_HOUR && store.lastShownEpochDay != today
}

/** 로드 시 정산 적용 (#35) — 순수 판정([decideSettlement]) 후 필요 시 기준점 동기화, 카드 차액 반환. */
internal fun settleOnLoad(store: SettlementStore, currentXp: Int): Int? {
    val decision = decideSettlement(store.lastSeenXp, currentXp)
    if (decision.syncBaseline) store.markSeen(currentXp)
    return decision.deltaXp
}

/** ActivityScreen.loadActivity와 같은 catch 계약 (#130) + 권한 회수는 안내로 (#144 패턴). */
internal suspend fun loadHome(
    exerciseRepo: ExerciseRepository,
    stepRepo: StepRepository,
    sleepRepo: SleepRepository?,
    stores: HomeStores,
): HomeLoad = try {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val raw = exerciseRepo.readRecentSessions(days = CONDITION_WINDOW_DAYS)
    // 홈 로드도 원장을 최신으로(멱등) — 성장 탭·워커와 같은 진입점 (#163)
    stores.ledger.grantSessions(raw, zone, epochMillis = System.currentTimeMillis())
    val sessions = raw.map {
        SessionInput(
            type = it.type,
            minutes = it.durationMinutes.toInt(),
            tier = it.trustTier,
            epochDay = it.start.atZone(zone).toLocalDate().toEpochDay(),
        )
    }
    // 활동 기반 컨디션에 지난밤 수면을 소프트 보정 (#180) — 수면 없으면 무보정
    val condition = ConditionEngine.applySleep(
        deriveCondition(sessions, today, stores.rest),
        sleepHoursOrNull(sleepRepo),
    )
    HomeLoad.Success(assembleHomeState(sessions, condition, today, stepRepo, stores))
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "home load IO failure", e)
    HomeLoad.Failure
} catch (e: RemoteException) {
    Log.w(TAG, "home load remote failure", e)
    HomeLoad.Failure
} catch (e: SecurityException) {
    Log.w(TAG, "home load permission failure", e)
    HomeLoad.PermissionDenied
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "home load invalid-argument failure", e)
    HomeLoad.Failure
} catch (e: IllegalStateException) {
    Log.w(TAG, "home load state failure", e)
    HomeLoad.Failure
} catch (e: android.database.SQLException) {
    Log.w(TAG, "home ledger db failure", e)
    HomeLoad.Failure
}

/** 홈 상태 조립 (#32) — loadHome이 계산한 조각을 UI 상태로. 기분 컨텍스트도 여기서 구성(#212). */
private suspend fun assembleHomeState(
    sessions: List<SessionInput>,
    condition: Double,
    today: LocalDate,
    stepRepo: StepRepository,
    stores: HomeStores,
): HomeUiState {
    val todayEpoch = today.toEpochDay()
    val cappedTotal = stores.ledger.cappedTotalXp()
    val todaySteps = stepRepo.readDailySteps(days = 1).firstOrNull { it.date == today }?.steps ?: 0L
    val todayXp = XpExplainer.explainDay(sessions, epochDay = todayEpoch).cappedXp
    val awaiting = FirstRun.isAwaitingFirstData(
        lifetimeXp = cappedTotal,
        hasAnyHealthData = todaySteps > 0L || sessions.any { it.type != null },
    )
    return HomeUiState(
        condition = condition,
        todayXp = todayXp,
        todayActiveMinutes = sessions.filter { it.epochDay == todayEpoch && it.type != null }.sumOf { it.minutes },
        todaySteps = todaySteps,
        energy = EnergyEngine.balance(cappedTotal, stores.energy.totalSpent),
        cappedTotalXp = cappedTotal,
        expedition = ExpeditionEngine.stateAt(stores.expedition.startedAtMillis, System.currentTimeMillis()),
        yesterdayXp = stores.ledger.cappedXpOn(todayEpoch - 1),
        yesterdayActiveMinutes = sessions
            .filter { it.epochDay == todayEpoch - 1 && it.type != null }
            .sumOf { it.minutes },
        moodContext = MoodResolver.contextFromSessions(
            sessions = sessions,
            today = today,
            restMode = stores.rest.enabled,
            goalDays = stores.goal.weeklyGoalDays,
            condition = condition.roundToInt(),
        ),
        streak = resolveStreak(stores.ledger, stores.rest, stores.streak, today),
        weeklyProgress = resolveWeeklyProgress(sessions, today, stores.goal.weeklyGoalDays),
        // 원장 합계는 위에서 이미 구했다 — 게이트가 다시 질의하면 전체 원장 집계가 로드마다 두 번 돈다
        awaitingFirstData = awaiting,
        firstSessionCue = resolveFirstSessionCue(stores.onboarding, cappedTotal, todayXp, awaiting),
    )
}

/**
 * 컨디션 파생 (#32·#31) — Tier C 포함 기본점수(코스메틱, ConditionEngine KDoc)를 일자별 폴드.
 * 첫 기록일 이전은 "무활동"이 아니라 "데이터 없음" — 거기서부터 폴드해야 신규 사용자가
 * 빈 창 28일치 하락(≈바닥)으로 시작하지 않는다. 휴식 시작일 이후의 날은 하락 면제.
 */
private fun deriveCondition(sessions: List<SessionInput>, today: LocalDate, restStore: RestModeStore): Double {
    val pointsByDay = sessions
        .filter { it.type != null }
        .groupBy { it.epochDay }
        .mapValues { (_, day) -> day.sumOf { XpEngine.baseScore(it.type!!, it.minutes).toDouble() } }
    val windowDays = (CONDITION_WINDOW_DAYS - 1 downTo 0).map { offset ->
        pointsByDay[today.minusDays(offset.toLong()).toEpochDay()] ?: 0.0
    }
    val firstRecordedIdx = windowDays.indexOfFirst { it > 0.0 }
    if (firstRecordedIdx == -1) return ConditionEngine.DEFAULT
    var prevPoints = 0.0
    return windowDays.withIndex().drop(firstRecordedIdx).fold(ConditionEngine.DEFAULT) { acc, (idx, points) ->
        val epochDay = today.minusDays((CONDITION_WINDOW_DAYS - 1 - idx).toLong()).toEpochDay()
        val next = ConditionEngine.nextDay(
            acc,
            points,
            restMode = restStore.isRestDay(epochDay),
            // 휴식일 버프 (#63): 어제 쉬고 오늘 움직인 날은 회복 보너스
            restedYesterday = idx > firstRecordedIdx &&
                prevPoints < ConditionEngine.ACTIVE_DAY_THRESHOLD_POINTS,
        )
        prevPoints = points
        next
    }
}
