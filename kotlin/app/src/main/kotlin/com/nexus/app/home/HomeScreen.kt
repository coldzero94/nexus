package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.HomeSkeleton
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
import com.nexus.app.ui.StaggerItem
import com.nexus.core.ClassAffinity
import com.nexus.core.ConditionEngine
import com.nexus.core.ExpeditionState
import com.nexus.core.FirstSessionCue
import com.nexus.core.GreetingVariant
import com.nexus.core.Stat

/** 홈 상태 (#32, E4-8) — 3초 내 파악할 것들만. */
internal data class HomeUiState(
    val condition: Double,
    val todayXp: Int,
    val todayActiveMinutes: Int,
    val todaySteps: Long,
    /** 에너지 잔액 (#67) — 원장 파생 획득 − 소모. 원정(#34)의 재화. */
    val energy: Int,
    /** 상한 적용 누적 XP — 에너지 소모 판정 입력(#34 trySpend). */
    val cappedTotalXp: Int,
    val expedition: ExpeditionState,
    /** 어제의 성장 (#36 아침 카드) — 어제 일자 상한 적용 XP·활동 분. */
    val yesterdayXp: Int,
    val yesterdayActiveMinutes: Int,
    /** 기분 배선 입력 (#212) — 홈 로드가 조립, 컨트롤러가 표·표정·대사로 평가. */
    val moodContext: com.nexus.core.MoodContext,
    /** 기세 (#214) — 현재 연속 활동일·최장·오늘 그레이스. */
    val streak: com.nexus.core.StreakStatus,
    /** 이번 주 목표 진척 (#215) — 활동일 M / 목표 N, 주 남은 날. 월요일 KST 경계. */
    val weeklyProgress: WeeklyProgress,
    /** 첫 데이터 대기 중 (#213) — 0을 나열하는 대신 '준비 중' 빈 상태를 그린다. */
    val awaitingFirstData: Boolean,
    /** 첫 세션 안내 (#211) — 첫 행동 코치 또는 첫 활동 XP 축하. 둘은 상호 배타. */
    val firstSessionCue: FirstSessionCue,
    /** 인사 변주 (#220) — 시간대·마지막 활동 경과를 반영한 말풍선 맥락. */
    val greeting: GreetingVariant,
    /** 레벨업 축하(#219)에 필요한 성장 스냅샷 — 성장 탭과 같은 계산(원장 누적 XP 기준). */
    val level: Int,
    val stats: Map<Stat, Int>,
    val affinity: ClassAffinity,
)

/**
 * 이번 주 목표 진척 표시 상태 (#215) — 활동일 [activeDays] / 목표 [goalDays], 주 남은 날 [daysLeft].
 * 계산은 [com.nexus.core.WeeklyGoal](순수), 여기선 UI가 바로 쓰는 형태로만 보관.
 */
internal data class WeeklyProgress(val activeDays: Int, val goalDays: Int, val daysLeft: Int)

internal sealed interface HomeLoad {
    data class Success(val state: HomeUiState) : HomeLoad

    data object PermissionDenied : HomeLoad

    data object Failure : HomeLoad
}

/** 홈 (#32) — 캐릭터·컨디션·오늘 요약·다음 목표. 원정 상태는 E5에서 실데이터로. */
@Composable
internal fun HomeScreen(
    manager: HealthConnectManager,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
    // 테스트용 이음새 (#320) — 기본값은 프로덕션 조립 그대로
    controller: HomeUiController? = null,
) {
    val context = LocalContext.current
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val stepRepo = remember { manager.stepRepositoryOrNull() }
    val sleepRepo = remember { manager.sleepRepositoryOrNull() }
    val ui = controller ?: remember { HomeUiController(HomeStores(context), context) }
    LaunchedEffect(exerciseRepo, stepRepo, ui.reloadKey) {
        ui.onLoaded(
            if (exerciseRepo == null || stepRepo == null) {
                HomeLoad.PermissionDenied
            } else {
                loadHome(exerciseRepo, stepRepo, sleepRepo, ui.stores)
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexusSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg),
    ) {
        when (val current = ui.load) {
            null -> HomeSkeleton()

            HomeLoad.PermissionDenied -> ConnectNotice(onReconnect)

            HomeLoad.Failure ->
                RetryNotice(stringResource(R.string.home_error)) { ui.retry() }

            is HomeLoad.Success -> HomeLoaded(state = current.state, ui = ui)
        }
    }
}

/** 로드 완료 상태 — 정산 카드(#35)가 있으면 콘텐츠 위에 얹는다. */
@Composable
private fun HomeLoaded(state: HomeUiState, ui: HomeUiController) {
    // 첫 데이터 대기 중엔 0 나열 대신 '준비 중' (#213) — 아침 카드·정산도 보여줄 게 없다
    if (state.awaitingFirstData) {
        FirstRunNotice(onSyncFinished = ui::refreshAfterSync)
        // 빈 상태의 대상이 곧 코치의 대상이다 (#211) — 이력이 전혀 없는 완전 신규가 여기 온다.
        // 빈 상태만 두면 "기다리세요"로 끝나고, 정작 P0인 '첫 행동 다리'가 첫 세션에 안 걸린다.
        if (ui.firstSessionVisible && state.firstSessionCue == FirstSessionCue.Coach) {
            FirstCoachCard { ui.dismissFirstSession(FirstSessionCue.Coach) }
        }
        return
    }
    // 레벨업이 있으면 그것만 (#219 AC: 다른 오버레이와 동시 스택 금지) — 축하가 카드 더미에 묻히지 않게
    ui.levelUp?.let { up ->
        LevelUpCard(up.level, up.risenStats, visible = true) { ui.dismissLevelUp(state) }
        return
    }
    if (ui.morningVisible) MorningCard(state, onDismiss = { ui.dismissCard(HomeCard.MORNING) })
    // 첫 세션 루프 (#211) — 코치와 축하는 상호 배타(core 판정), 각각 1회
    when (state.firstSessionCue) {
        FirstSessionCue.Coach ->
            if (ui.firstSessionVisible) FirstCoachCard { ui.dismissFirstSession(FirstSessionCue.Coach) }

        // visible을 넘겨 exit 연출을 살린다 — 노드를 즉시 빼면 fadeOut이 생략된다
        FirstSessionCue.FirstXp ->
            FirstXpCard(ui.spriteState, ui.firstSessionVisible) {
                ui.dismissFirstSession(FirstSessionCue.FirstXp)
            }

        FirstSessionCue.None -> Unit
    }
    ui.settlementDelta?.let { delta ->
        SettlementCard(deltaXp = delta, onOpen = { ui.openSettlement(state.cappedTotalXp) })
    }
    HomeContent(
        state = state,
        spriteState = ui.spriteState,
        moodLines = ui.moodLines,
        expedition = ExpeditionUi(
            onDepart = { ui.depart(state.cappedTotalXp) },
            onOpen = ui::openExpedition,
            reward = ui.reward,
            onDismissReward = { ui.dismissCard(HomeCard.EXPEDITION_REWARD) },
        ),
        onSyncFinished = ui::refreshAfterSync,
    )
    if (ui.journalVisible) EveningJournalCard(state, onDismiss = { ui.dismissCard(HomeCard.JOURNAL) })
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    spriteState: String,
    moodLines: List<String>,
    expedition: ExpeditionUi,
    onSyncFinished: () -> Unit,
) {
    // 상단 히어로 — 캐릭터·대사·컨디션을 묶어 최상위 앵커로 (#256). 아래는 종속 상세 카드.
    // 인덱스 순서대로 짧게 시차를 두고 올라온다 (#268) — 한꺼번에 튀면 어디를 볼지 알 수 없다.
    StaggerItem(StaggerOrder.HERO) {
        HomeHero(spriteState, moodLines, state.condition, state.moodContext.restMode, state.greeting)
    }
    StaggerItem(StaggerOrder.STREAK) { StreakRow(state.streak) }
    // 이번 주 목표 진척 (#215) — 기세(일 단위 연속) 다음에 주 단위 리듬
    StaggerItem(StaggerOrder.WEEKLY) { WeeklyGoalRow(state.weeklyProgress) }
    StaggerItem(StaggerOrder.TODAY) { TodaySummaryCard(state) }
    // 개봉 결과를 원정 카드 **위**에 둔다 — 방금 누른 버튼 바로 위에 답이 나타나야 인과가 읽힌다
    ExpeditionResultCard(expedition.reward, expedition.onDismissReward)
    StaggerItem(StaggerOrder.TAIL) {
        ExpeditionCard(state.expedition, state.energy, expedition.onDepart, expedition.onOpen)
    }
    // 다음 목표를 카드로 편입 — 맨 Text로 두면 카드 스택 리듬이 끊긴다 (#254)
    StaggerItem(StaggerOrder.TAIL) {
        NexusCard {
            Text(text = nextGoalText(state), style = MaterialTheme.typography.bodyMedium)
        }
    }
    // 신선도는 푸터 위치 (#221) — 평소엔 조용한 한 줄, 오래 밀렸을 때만 안내 카드로 커진다
    FreshnessRow(onSyncFinished = onSyncFinished)
}

@Composable
private fun nextGoalText(state: HomeUiState): String = when {
    state.todayActiveMinutes < ACTIVE_GOAL_MINUTES ->
        stringResource(R.string.home_goal_move, ACTIVE_GOAL_MINUTES)

    state.condition < ConditionEngine.DEFAULT ->
        stringResource(R.string.home_goal_recovering)

    else -> stringResource(R.string.home_goal_done)
}

/** 다음 목표 문구의 활동 기준(분) — 컨디션 활동 문턱(10pt≈걷기 10분)과 맞춘다. */
private const val ACTIVE_GOAL_MINUTES = 10

/**
 * 홈 본문 카드의 등장 순서 (#268). 화면에 보이는 순서와 같아야 위→아래로 읽힌다.
 * [TAIL]은 지연 상한에 걸리는 인덱스로, 아래 카드들이 같은 시점에 함께 올라온다.
 */
private object StaggerOrder {
    const val HERO = 0
    const val STREAK = 1
    const val WEEKLY = 2
    const val TODAY = 3
    const val TAIL = 4
}
