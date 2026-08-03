package com.nexus.app.growth

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.loadMilestoneTable
import com.nexus.app.data.ExpeditionStore
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusIcons
import com.nexus.core.BadgeContext
import com.nexus.core.BadgeEvaluator
import com.nexus.core.BadgeTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 평생 누적 마일스톤 (#113, E5-13) — 배지와 **별개 축**.
 *
 * 배지는 "무엇을 해냈나"(첫걸음·레벨 10·만보), 마일스톤은 "얼마나 오래·얼마나 많이"(100일 동행,
 * 누적 1만 XP). 게임화 동기는 수 주 내 약해지므로(RESEARCH.md) 레벨·배지와 다른 축의 장기 훅이
 * 필요하고, 누적 기록 자체가 애착 자산이라는 컨셉('내 기록의 거울')과도 맞는다.
 *
 * ## 왜 엔진을 복제하지 않는가
 *
 * 표 형식·해금 판정·영속·글리프 목록을 배지와 **그대로 공유**한다. 따로 만들면 파서·평가기·
 * 저장소·행 컴포넌트를 전부 다시 만들게 되고, 그때부터 두 축의 동작이 조용히 갈라진다.
 * 다른 건 **표 파일과 카드 하나**뿐이다(월 한정 배지 #38이 같은 방식).
 *
 * ## 평생 지표는 원장에서만 온다
 *
 * Health Connect 읽기는 창(7·28일)이라 "함께한 100일"을 알 수 없다. 원장은 append-only라 첫날부터
 * 갖고 있고 줄어들지 않는다. 대신 **원시 걸음·거리는 원장에 없어**(제품 불변식) 그 축의 마일스톤은
 * 만들 수 없다 — 티켓의 "총 걸음 100km" 예시를 뺀 이유다.
 *
 * 획득 영속은 배지와 **다른 prefs**를 쓴다 — 두 축의 획득이 섞이면 목록 개수가 서로 오염된다.
 */
private const val MILESTONE_PREFS = "nexus_milestone_progress"

internal suspend fun loadMilestones(
    context: Context,
    ledger: RewardLedgerRepository,
    cumulativeXp: Int,
): MilestoneState? = try {
    val (table, store, signals) = withContext(Dispatchers.IO) {
        Triple<BadgeTable, BadgeProgressStore, BadgeContext>(
            CharacterAssets(context).loadMilestoneTable(),
            BadgeProgressStore(context, MILESTONE_PREFS),
            BadgeContext(
                cumulativeXp = cumulativeXp,
                expeditionsCompleted = ExpeditionStore(context).completedCount,
                // 평생 활동일 — 읽기 창과 무관하게 원장 전 기간 집계 (#113)
                activeDaysLifetime = ledger.activeDaysLifetime(),
            ),
        )
    }
    val currently = BadgeEvaluator.unlocked(table, signals)
    val newly = currently - store.earned
    val celebration = withContext(Dispatchers.IO) { commitBadgeProgress(context, store, newly, currently) }
    // 표시는 영속 합집합 — 원장이 append-only라 조건이 거짓으로 돌아갈 일은 없지만,
    // 배지와 같은 불퇴행 규율을 따른다(#177)
    MilestoneState(table = table, unlocked = store.earned + currently, newlyUnlocked = celebration)
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG_MILESTONE, "milestone load IO failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG_MILESTONE, "milestone table invalid", e)
    null
} catch (e: IllegalStateException) {
    Log.w(TAG_MILESTONE, "milestone load state failure", e)
    null
}

@Composable
internal fun MilestonesCard(state: MilestoneState, modifier: Modifier = Modifier) {
    NexusCard(
        modifier = modifier,
        titleIcon = NexusIcons.level,
        title = stringResource(R.string.growth_milestones_title, state.unlocked.size, state.table.badges.size),
    ) {
        state.table.badges.forEach { milestone ->
            BadgeGlyphRow(
                name = milestone.name,
                description = milestone.description,
                icon = milestone.icon,
                earned = milestone.id in state.unlocked,
            )
        }
    }
}

private const val TAG_MILESTONE = "Milestones"
