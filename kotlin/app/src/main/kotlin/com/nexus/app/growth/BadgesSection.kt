package com.nexus.app.growth

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.Badge
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
 * expeditionsCompleted는 완료 카운트 소스 마련 전까지 0(원정 배지 후속).
 */
internal suspend fun loadBadges(context: Context, manager: HealthConnectManager, cumulativeXp: Int): BadgeState? = try {
    val repo = manager.growthRepositoryOrNull() ?: return null
    val inputs = repo.computeBadgeInputs()
    // 에셋 파싱·프리퍼런스 최초 로드는 디스크 IO — 메인 밖에서 (#177 리뷰)
    val (table, store) = withContext(Dispatchers.IO) {
        CharacterAssets(context).loadBadgeTable() to BadgeProgressStore(context)
    }
    val signals = BadgeSignals.build(
        cumulativeXp = cumulativeXp,
        dailyActive = inputs.dailyActive,
        bestDaySteps = inputs.bestDaySteps,
        expeditionsCompleted = 0,
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
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(NexusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        ) {
            Text(
                stringResource(R.string.growth_badges_title, state.unlocked.size, state.table.badges.size),
                style = MaterialTheme.typography.titleMedium,
            )
            state.table.badges.forEach { badge ->
                BadgeRow(badge, earned = badge.id in state.unlocked)
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, earned: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (earned) badge.name else stringResource(R.string.growth_badge_locked, badge.name),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(badge.description, style = MaterialTheme.typography.bodySmall)
    }
}
