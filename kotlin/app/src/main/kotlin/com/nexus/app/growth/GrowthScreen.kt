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
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
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
fun GrowthScreen(manager: HealthConnectManager, modifier: Modifier = Modifier, onReconnect: (() -> Unit)? = null) {
    val context = LocalContext.current
    val ui = remember {
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

/** 로드 완료 본문 — 변화 축하 → 요약 → 장비 → 배지. */
@Composable
private fun GrowthLoaded(state: GrowthUiState, ui: GrowthUiController) {
    ui.change?.let { change ->
        CelebrationCard(change, visible = ui.celebrationVisible) { ui.dismissCelebration(state) }
    }
    GrowthContent(state)
    // 오늘 성장이 있으면 걷는 모습으로 미리보기 — 홈 캐릭터와 같은 감각 (#37)
    EquipmentCard(spriteState = if (state.today.cappedXp > 0) "walk" else "idle")
    BadgeSections(ui.badgeSections)
}

/** 배지 영역 (#175·#206) — 상시 배지 + 이달의 배지. 각각 부가 정보라 없으면 그 카드만 생략한다. */
@Composable
private fun BadgeSections(state: BadgeSectionsState) {
    state.standard?.let { BadgesCard(it) }
    state.monthly?.let { MonthlyBadgesCard(it) }
}
