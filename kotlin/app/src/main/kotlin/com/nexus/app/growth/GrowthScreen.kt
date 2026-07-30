package com.nexus.app.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.settings.IdentityStore
import com.nexus.app.telemetry.Telemetry
import com.nexus.app.telemetry.TelemetryEvent
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
import com.nexus.core.Badge
import com.nexus.core.ClassAffinity
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.DayXpExplanation
import com.nexus.core.GrowthSummary

/** 성장 변화 연출 대상 (#61) — 성장 탭 진입 시 기준점([GrowthStateStore])과의 차이. */
internal data class GrowthChange(val levelUpTo: Int?, val affinityChangedTo: ClassAffinity?)

/** 성장 탭 화면 상태 — 요약과 오늘 XP 분해는 같은 세션 스냅샷에서 계산(불일치 방지). */
internal data class GrowthUiState(
    val summary: GrowthSummary,
    val today: DayXpExplanation,
    /** 첫 데이터 대기 중 (#213) — 레벨 1·0 XP 나열 대신 '준비 중'. */
    val awaitingFirstData: Boolean = false,
)

@Composable
internal fun GrowthScreen(
    manager: HealthConnectManager,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
    // 테스트가 로드 분기를 직접 세우기 위한 이음새 (#320) — 기본값은 프로덕션 조립 그대로.
    // Robolectric엔 Health Connect가 없어 repo가 항상 null이라, 주입 없이는 미연결 분기만 렌더된다.
    controller: GrowthUiController? = null,
) {
    val context = LocalContext.current
    val ui = controller ?: remember {
        GrowthUiController(
            context = context,
            manager = manager,
            exerciseRepo = manager.exerciseRepositoryOrNull(),
            ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao()),
            stateStore = GrowthStateStore(context),
        )
    }
    LaunchedEffect(ui.reloadKey) { ui.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexusSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg),
    ) {
        // 이름을 지었으면 "OO의 성장"으로 호명, 아니면 무명 카피 폴백 (#216)
        val characterName = remember { IdentityStore(context).name }
        Text(
            characterName?.let { stringResource(R.string.growth_title_named, it) }
                ?: stringResource(R.string.growth_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        when (val current = ui.load) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            GrowthLoad.PermissionDenied ->
                ConnectNotice(
                    onReconnect,
                    body = stringResource(R.string.growth_demo_body, ClassAffinityCalculator.WINDOW_DAYS),
                )

            GrowthLoad.Failure -> RetryNotice(stringResource(R.string.growth_error), ui::retry)

            is GrowthLoad.Success if current.state.awaitingFirstData ->
                // 첫 데이터 대기 중엔 레벨·능력치 0 나열 대신 '준비 중' 하나만 (#213)
                FirstRunNotice(onSyncFinished = ui::refreshAfterSync)

            is GrowthLoad.Success -> GrowthLoaded(current.state, ui)
        }
    }
}

/**
 * 로드 완료 본문 — 변화 축하 → 요약 → 장비 → 배지.
 *
 * ## 축하는 한 번에 하나만 (#218)
 *
 * 레벨업·성향 변화(#61)가 우선이다. 두 축하가 겹치면 각각의 무게가 반씩 깎이고 화면 상단이 카드
 * 두 장으로 막힌다. 배지 축하는 대기 집합에 남아 있으므로 **다음 진입에서 뜬다** — 놓치지 않는다.
 */
@Composable
private fun GrowthLoaded(state: GrowthUiState, ui: GrowthUiController) {
    val change = ui.change
    if (change != null) {
        CelebrationCard(change, visible = ui.celebrationVisible) { ui.dismissCelebration(state) }
    } else {
        // 계측은 획득 시점(commitBadgeProgress)에 이미 찍혔다 — 여기서 찍으면 탭을 오갈 때마다
        // 재발화하고, 레벨업이 우선순위를 가져간 방문에서는 누락된다 (#218 리뷰)
        val newBadges = remember(ui.badgeSections) { ui.badgeSections.newlyUnlockedBadges() }
        BadgeUnlockCard(newBadges, visible = ui.badgeCelebrationVisible) { ui.dismissBadgeCelebration() }
    }
    GrowthContent(state)
    // 오늘 성장이 있으면 걷는 모습으로 미리보기 — 홈 캐릭터와 같은 감각 (#37)
    EquipmentCard(spriteState = if (state.today.cappedXp > 0) "walk" else "idle")
    BadgeSections(ui.badgeSections)
}

/**
 * 축하 대상 배지 (#218) — 대기 집합 ∩ 표에 있는 배지.
 *
 * 표에서 찾는 이유는 이름·설명이 `badges.json`에서 와야 하기 때문이다(카피 하드코딩 금지).
 * 표에 없는 id(표에서 제거된 옛 배지)는 조용히 빠진다 — 이름 없는 축하를 띄우느니 안 띄운다.
 */
private fun BadgeSectionsState.newlyUnlockedBadges(): List<Badge> {
    val state = standard ?: return emptyList()
    val pending = state.newlyUnlocked
    // 상시 + 월 한정을 한 목록으로 (#218) — 대기 집합이 하나라 같은 달에 둘이 함께 열려도 카드 하나다.
    // 월 한정은 표 모양이 달라 표시용 Badge로 옮겨 담는다(글리프 슬롯 규약은 #266으로 공통).
    val standardBadges = state.table.badges.filter { it.id in pending }
    val monthlyBadges = monthly?.badges.orEmpty()
        .filter { it.id in pending }
        .map { Badge(id = it.id, name = it.name, description = it.description, whenExpr = it.whenExpr, icon = it.icon) }
    return standardBadges + monthlyBadges
}

/** 배지 영역 (#175·#206) — 상시 배지 + 이달의 배지. 각각 부가 정보라 없으면 그 카드만 생략한다. */
@Composable
private fun BadgeSections(state: BadgeSectionsState) {
    state.standard?.let { BadgesCard(it) }
    state.monthly?.let { MonthlyBadgesCard(it) }
}
