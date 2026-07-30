package com.nexus.app.growth

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.data.ExpeditionStore
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusIcons
import com.nexus.core.BadgeEvaluator
import com.nexus.core.BadgeSignals
import com.nexus.core.BadgeTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "BadgesSection"

/** 배지 노출 상태 (#175) — 표 전체 + 획득 집합 + 이번에 새로 열린 것(연출은 후속). */
internal data class BadgeState(val table: BadgeTable, val unlocked: Set<String>, val newlyUnlocked: Set<String>)

/**
 * 배지 해금 로드 (#175) — 부가 정보라 실패는 null(성장 화면 유지, #130 catch 계약). 원장 누적 XP는
 * 성장 요약에서 받아 레벨을 화면과 일치시킨다([BadgeSignals.build]). 획득 집합은 [BadgeProgressStore]에 영속.
 * expeditionsCompleted는 [ExpeditionStore]의 개봉 카운터(#204) — 이게 0으로 하드코딩돼 있던 동안
 * 탐험가 배지가 영구 잠김이었다.
 */
internal suspend fun loadBadges(context: Context, manager: HealthConnectManager, cumulativeXp: Int): BadgeState? = try {
    val repo = manager.growthRepositoryOrNull() ?: return null
    val inputs = repo.computeBadgeInputs()
    // 에셋 파싱·프리퍼런스 최초 로드는 모두 디스크 IO — 한 번의 컨텍스트 전환으로 묶는다
    val (table, store, expeditionsCompleted) = withContext(Dispatchers.IO) {
        Triple(
            CharacterAssets(context).loadBadgeTable(),
            BadgeProgressStore(context),
            ExpeditionStore(context).completedCount,
        )
    }
    val signals = BadgeSignals.build(
        cumulativeXp = cumulativeXp,
        dailyActive = inputs.dailyActive,
        bestDaySteps = inputs.bestDaySteps,
        expeditionsCompleted = expeditionsCompleted,
    )
    val currently = BadgeEvaluator.unlocked(table, signals)
    val newly = currently - store.earned
    withContext(Dispatchers.IO) { store.addEarned(currently) }
    // 표시는 영속 합집합 — 조건이 다시 거짓이 돼도(창 이탈·스트릭 끊김) 획득 배지는 잠기지 않는다
    // (#177 리뷰 Critical: 불퇴행 불변식)
    BadgeState(table = table, unlocked = store.earned + currently, newlyUnlocked = newly)
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "badge load IO failure", e)
    null
} catch (e: RemoteException) {
    Log.w(TAG, "badge load remote failure", e)
    null
} catch (e: SecurityException) {
    Log.w(TAG, "badge load permission failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "badge load invalid-argument failure", e)
    null
} catch (e: IllegalStateException) {
    Log.w(TAG, "badge load state failure", e)
    null
}

@Composable
internal fun BadgesCard(state: BadgeState, modifier: Modifier = Modifier) {
    NexusCard(
        modifier = modifier,
        titleIcon = NexusIcons.badge,
        title = stringResource(R.string.growth_badges_title, state.unlocked.size, state.table.badges.size),
    ) {
        state.table.badges.forEach { badge ->
            // 획득 판정은 영속 합집합(unlocked) — 조건이 다시 거짓이 돼도 잠금으로 회귀하지 않는다
            // (#177 리뷰 Critical: 성취 불퇴행). 아이콘 상태도 같은 값을 따르므로 함께 지켜진다.
            BadgeGlyphRow(
                name = badge.name,
                description = badge.description,
                icon = badge.icon,
                earned = badge.id in state.unlocked,
            )
        }
    }
}
