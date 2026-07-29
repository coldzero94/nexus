package com.nexus.app.growth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.settings.IdentityStore
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
import com.nexus.app.ui.animatedGaugeProgress
import com.nexus.core.ActivityType
import com.nexus.core.ClassAffinity
import com.nexus.core.ClassAffinityCalculator
import com.nexus.core.DayXpExplanation
import com.nexus.core.GrowthSummary
import com.nexus.core.StatMapping
import com.nexus.core.XpEngine

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
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val ledger = remember { RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao()) }
    val stateStore = remember { GrowthStateStore(context) }
    var load by remember { mutableStateOf<GrowthLoad?>(null) }
    var change by remember { mutableStateOf<GrowthChange?>(null) }
    // 상시 배지(#175) + 이달의 배지(#206) — 함께 로드해 한 상태로 들고 있는다
    var badgeSections by remember { mutableStateOf(BadgeSectionsState()) }
    var celebrationVisible by remember { mutableStateOf(true) }
    // 로드 실패 후 재시도 트리거 (#227)
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(exerciseRepo, reloadKey) {
        // 요약이 먼저 도착해 본문이 그려지고, 배지는 뒤이어 채워진다 (#206)
        badgeSections = loadGrowthScreen(context, manager, exerciseRepo, ledger, stateStore) { l, c ->
            load = l
            change = c
        }
    }

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
        when (val current = load) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            GrowthLoad.PermissionDenied ->
                ConnectNotice(
                    onReconnect,
                    body = stringResource(R.string.growth_demo_body, ClassAffinityCalculator.WINDOW_DAYS),
                )

            GrowthLoad.Failure ->
                RetryNotice(stringResource(R.string.growth_error)) {
                    load = null
                    reloadKey++
                }

            is GrowthLoad.Success if current.state.awaitingFirstData ->
                // 첫 데이터 대기 중엔 레벨·능력치 0 나열 대신 '준비 중' 하나만 (#213)
                FirstRunNotice(onSyncFinished = { reloadKey++ })

            is GrowthLoad.Success -> {
                change?.let { c ->
                    // dismiss는 visible 토글 — 노드를 즉시 제거하면 exit 연출이 생략된다
                    CelebrationCard(c, visible = celebrationVisible) {
                        celebrationVisible = false
                        // 확인한 순간이 기준점 — 재진입 시 같은 변화를 다시 축하하지 않는다
                        stateStore.recordSeen(current.state.summary.level, current.state.summary.affinity)
                    }
                }
                GrowthContent(current.state)
                // 오늘 성장이 있으면 걷는 모습으로 미리보기 — 홈 캐릭터와 같은 감각 (#37)
                EquipmentCard(spriteState = if (current.state.today.cappedXp > 0) "walk" else "idle")
                BadgeSections(badgeSections)
            }
        }
    }
}

/** 배지 영역 (#175·#206) — 상시 배지 + 이달의 배지. 각각 부가 정보라 없으면 그 카드만 생략한다. */
@Composable
private fun BadgeSections(state: BadgeSectionsState) {
    state.standard?.let { BadgesCard(it) }
    state.monthly?.let { MonthlyBadgesCard(it) }
}

@Composable
private fun GrowthContent(data: GrowthUiState) {
    TodayXpCard(data.today)
    LevelCard(data.summary)
    AffinityCard(data.summary)
    StatsCard(data.summary)
    Text(
        stringResource(R.string.growth_scope_note, ClassAffinityCalculator.WINDOW_DAYS),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LevelCard(data: GrowthSummary) {
    // 성장 탭의 히어로 — 레벨 수는 titleLarge로 크게, 카드는 Highlight로 강조(형제 카드와 위계 차)
    NexusCard(emphasis = CardEmphasis.Highlight) {
        Text(
            stringResource(R.string.growth_level_format, data.level),
            style = MaterialTheme.typography.titleLarge,
        )
        // 레벨 진행은 상방 전용(불퇴행) — 증가는 감속 보간, 레벨업 리셋은 즉시(뒤로 안 빠짐) (#262)
        val levelProgress = animatedGaugeProgress(data.progress.toFloat(), upwardOnly = true, label = "level")
        LinearProgressIndicator(
            progress = { levelProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.growth_xp_format, data.totalXp, XpEngine.FORMULA_VERSION),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AffinityCard(data: GrowthSummary) {
    NexusCard(
        title = stringResource(R.string.growth_affinity_format, stringResource(data.affinity.labelRes())),
    ) {
        ActivityType.entries.forEach { type ->
            ShareRow(stringResource(type.labelRes()), data.axisShares[type] ?: 0.0)
        }
    }
}

@Composable
private fun ShareRow(label: String, share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { share.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatsCard(data: GrowthSummary) {
    NexusCard(title = stringResource(R.string.growth_stats_title)) {
        StatMapping.unlockedStats.forEach { stat ->
            StatRow(stringResource(stat.labelRes()), data.stats[stat] ?: 0)
        }
        StatMapping.lockedStats.forEach { stat ->
            Text(
                stringResource(R.string.growth_stat_locked_format, stringResource(stat.labelRes())),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.growth_stat_value_format, value),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
