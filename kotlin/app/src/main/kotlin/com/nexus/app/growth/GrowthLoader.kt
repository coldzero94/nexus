package com.nexus.app.growth

import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.GrowthCalculator
import com.nexus.core.GrowthSummary
import com.nexus.core.LevelCurve
import com.nexus.core.SessionInput
import com.nexus.core.XpExplainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "GrowthLoader"

/**
 * 성장 로드 결과 (#144): 권한 문제는 "실패"가 아니라 미연결 상태 — 에러 문구 대신
 * 데모 안내로 라우팅한다. [Failure]만 growth_error를 쓴다(#130 catch 경로).
 */
internal sealed interface GrowthLoad {
    data class Success(val state: GrowthUiState) : GrowthLoad

    data object PermissionDenied : GrowthLoad

    data object Failure : GrowthLoad
}

/**
 * 성장 화면 로드 오케스트레이션 (#206) — 요약을 **먼저** 흘려보내고, 배지는 그 뒤에 채운다.
 *
 * 요약이 준비되면 [onSummary]로 즉시 알린다: 배지 로드(assets 파싱·prefs·HC 걸음 집계·원장 조회)를
 * 기다렸다가 한꺼번에 그리면 레벨·XP 카드까지 스피너 뒤에 갇힌다. 배지는 부가 정보라 나중에 붙어도
 * 되지만 본문은 그러면 안 된다.
 *
 * HC 미가용(repo null)과 권한 회수(SecurityException)는 같은 "미연결" 안내로 (#144).
 */
internal suspend fun loadGrowthScreen(
    context: Context,
    manager: HealthConnectManager,
    exerciseRepo: ExerciseRepository?,
    ledger: RewardLedgerRepository,
    stateStore: GrowthStateStore,
    onSummary: (GrowthLoad, GrowthChange?) -> Unit,
): BadgeSectionsState {
    val loaded = if (exerciseRepo == null) GrowthLoad.PermissionDenied else loadGrowth(exerciseRepo, ledger)
    if (loaded !is GrowthLoad.Success) {
        onSummary(loaded, null)
        return BadgeSectionsState()
    }

    val change = detectChange(stateStore, loaded.state.summary)
    // 기준점 소비는 "확인"(dismiss) 시점 — 감지 시점에 갱신하면 회전·프로세스 사망으로 카드가 영영
    // 소실된다(#61 리뷰). 변화가 없을 때만 여기서 기준점을 세팅(최초 방문 포함).
    if (change == null) stateStore.recordSeen(loaded.state.summary.level, loaded.state.summary.affinity)
    onSummary(loaded, change)

    // 두 배지 로더는 서로 독립 — 순차로 돌리면 HC 걸음 집계를 두 번 기다린다 (#175·#206)
    return coroutineScope {
        val standard = async { loadBadges(context, manager, cumulativeXp = loaded.state.summary.totalXp) }
        val monthly = async { loadMonthlyBadges(context, manager, ledger) }
        BadgeSectionsState(standard = standard.await(), monthly = monthly.await())
    }
}

/** 배지 영역 상태 (#175·#206) — 둘 다 부가 정보라 각각 null이면 그 카드만 생략한다. */
internal data class BadgeSectionsState(val standard: BadgeState? = null, val monthly: MonthlyBadgeState? = null)

/**
 * 기준점 대비 변화 감지 (#61): 레벨업은 상승만(최초 방문·창 이탈로 인한 하락은 무연출),
 * 성향 변화는 기준점이 있을 때만. 변화 없으면 null(카드 미노출).
 */
private fun detectChange(store: GrowthStateStore, summary: GrowthSummary): GrowthChange? {
    val lastLevel = store.lastSeenLevel
    val lastAffinity = store.lastSeenAffinity
    val levelUpTo = summary.level.takeIf { lastLevel in 1 until it }
    val affinityChangedTo = summary.affinity.takeIf { lastAffinity != null && lastAffinity != it }
    if (levelUpTo == null && affinityChangedTo == null) return null
    return GrowthChange(levelUpTo, affinityChangedTo)
}

/**
 * ActivityScreen.loadActivity와 같은 catch 계약 (#130 — 실패는 드러내고 취소는 전파).
 * 단 SecurityException은 실패가 아닌 [GrowthLoad.PermissionDenied]로 — 권한 회수는
 * 데모 안내+재연결 유도가 맞다(#144).
 */
private suspend fun loadGrowth(repo: ExerciseRepository, ledger: RewardLedgerRepository): GrowthLoad = try {
    val zone = ZoneId.systemDefault()
    val raw = repo.readRecentSessions(days = ClassAffinityCalculator.WINDOW_DAYS)
    // 화면 로드도 원장을 최신으로(멱등) — 워커 주기를 기다리지 않고 표시가 원장과 일치 (#163)
    ledger.grantSessions(raw, zone, epochMillis = System.currentTimeMillis())
    val sessions = raw.map {
        SessionInput(
            type = it.type,
            minutes = it.durationMinutes.toInt(),
            tier = it.trustTier,
            // 일일 상한 그룹핑 키 — 사용자 시간대 기준 날짜 (GrowthCalculator KDoc)
            epochDay = it.start.atZone(zone).toLocalDate().toEpochDay(),
        )
    }
    // 누적 XP·레벨은 전 기간 원장 합산 (#163) — 28일 창 이탈로 레벨이 내려가던 v1 한계 해소.
    // 성향·능력치·오늘 분해는 스펙대로 28일 창 유지.
    val ledgerTotal = ledger.cappedTotalXp()
    val summary = GrowthCalculator.compute(sessions)
    GrowthLoad.Success(
        GrowthUiState(
            summary = summary.copy(
                totalXp = ledgerTotal,
                level = LevelCurve.displayLevel(ledgerTotal),
                progress = LevelCurve.progressToNextLevel(ledgerTotal),
            ),
            today = XpExplainer.explainDay(sessions, epochDay = LocalDate.now(zone).toEpochDay()),
        ),
    )
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "growth load IO failure", e)
    GrowthLoad.Failure
} catch (e: RemoteException) {
    Log.w(TAG, "growth load remote failure", e)
    GrowthLoad.Failure
} catch (e: SecurityException) {
    Log.w(TAG, "growth load permission failure", e)
    GrowthLoad.PermissionDenied
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "growth load invalid-argument failure", e)
    GrowthLoad.Failure
} catch (e: IllegalStateException) {
    Log.w(TAG, "growth load state failure", e)
    GrowthLoad.Failure
} catch (e: android.database.SQLException) {
    // 원장 DB 문제(디스크·손상)는 화면 크래시 대신 에러 표시 (#163)
    Log.w(TAG, "growth ledger db failure", e)
    GrowthLoad.Failure
}
