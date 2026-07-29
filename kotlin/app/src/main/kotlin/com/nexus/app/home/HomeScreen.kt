package com.nexus.app.home

import android.os.RemoteException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.MoodResolver
import com.nexus.app.data.EnergyStore
import com.nexus.app.data.ExpeditionStore
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.SleepRepository
import com.nexus.app.health.StepRepository
import com.nexus.app.health.sleepHoursOrNull
import com.nexus.app.notify.ExpeditionReturnWorker
import com.nexus.app.onboarding.OnboardingStore
import com.nexus.app.settings.GoalStore
import com.nexus.app.settings.RestModeStore
import com.nexus.app.telemetry.Telemetry
import com.nexus.app.telemetry.TelemetryEvent
import com.nexus.app.ui.ConnectNotice
import com.nexus.app.ui.FirstRunNotice
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.RetryNotice
import com.nexus.app.widget.WidgetUpdater
import com.nexus.core.ConditionEngine
import com.nexus.core.DialogueSelector
import com.nexus.core.EnergyEngine
import com.nexus.core.ExpeditionEngine
import com.nexus.core.ExpeditionState
import com.nexus.core.FirstRun
import com.nexus.core.FirstSessionCue
import com.nexus.core.SessionInput
import com.nexus.core.XpEngine
import com.nexus.core.XpExplainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TAG = "HomeScreen"

/** 컨디션 파생 창 — 원장 배선 전의 표시 전용 폴드 범위 (ConditionEngine.fromDailyPoints). */
private const val CONDITION_WINDOW_DAYS = 28

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
fun HomeScreen(manager: HealthConnectManager, modifier: Modifier = Modifier, onReconnect: (() -> Unit)? = null) {
    val context = LocalContext.current
    val exerciseRepo = remember { manager.exerciseRepositoryOrNull() }
    val stepRepo = remember { manager.stepRepositoryOrNull() }
    val sleepRepo = remember { manager.sleepRepositoryOrNull() }
    val ui = remember { HomeUiController(HomeStores(context), context) }
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
            null -> CircularProgressIndicator()

            HomeLoad.PermissionDenied -> ConnectNotice(onReconnect)

            HomeLoad.Failure ->
                RetryNotice(stringResource(R.string.home_error)) { ui.retry() }

            is HomeLoad.Success -> HomeLoaded(state = current.state, ui = ui)
        }
    }
}

/**
 * 홈 카드 상태·행위 홀더 (#35·#36) — 카드가 늘 때 화면 함수가 길어지지 않게 콜백을 메서드로.
 * Compose 상태는 프로퍼티 델리게이트로 소유(리컴포지션 트리거 유지).
 */
private class HomeUiController(val stores: HomeStores, private val context: android.content.Context) {
    var load by mutableStateOf<HomeLoad?>(null)
        private set
    var reloadKey by mutableIntStateOf(0)
        private set
    var settlementDelta by mutableStateOf<Int?>(null)
        private set
    var morningVisible by mutableStateOf(false)
        private set
    var journalVisible by mutableStateOf(false)
        private set

    /** 기분 배선 (#212) — 렌더 상태(표정 아트 or idle/walk 폴백)와 채택 기분 대사 풀. */
    var spriteState by mutableStateOf("idle")
        private set
    var moodLines by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * 첫 세션 카드 가시성 (#211) — dismiss는 토글, 소비(플래그 기록)는 그 시점에 한 번.
     * 노드를 즉시 제거하면 축하 exit 연출이 생략된다(#61 패턴).
     *
     * **카드마다 따로** 들고 있다. 하나로 묶으면 "코치 확인 → 걷기 → 축하"가 같은 세션에서
     * 닫히지 않는다 — 코치를 끈 플래그가 축하까지 삼킨다. 그 한 세션이 이 티켓의 P0다.
     */
    var coachVisible by mutableStateOf(true)
        private set
    var firstXpVisible by mutableStateOf(true)
        private set

    /** 코치를 확인함 — 다시 뜨지 않는다. */
    fun dismissCoach() {
        stores.onboarding.firstCoachShown = true
        coachVisible = false
    }

    /**
     * 첫 XP 축하를 확인함 — 코치도 함께 소진한다.
     *
     * 첫 성장을 이미 축하한 사용자에게 며칠 뒤 "첫 성장까지, 10분"이 뜨면 카드 문구 자체가 거짓이 된다.
     */
    fun dismissFirstXp() {
        stores.onboarding.firstXpCelebrated = true
        stores.onboarding.firstCoachShown = true
        firstXpVisible = false
    }

    /** 카드가 "어느 날"의 것인지 — dismiss가 노출 판정과 같은 날짜를 소비(자정 경계, #70 리뷰 N3). */
    private var cardEpochDay = 0L

    suspend fun onLoaded(loaded: HomeLoad) {
        load = loaded
        if (loaded is HomeLoad.Success) {
            // 기분 평가 (#212) — 표정/대사 결정, 표정 아트 없으면 idle/walk 폴백. 홈·위젯 동일 상태.
            val mood = MoodResolver.resolveMood(context, loaded.state.moodContext)
            spriteState = MoodResolver.renderState(
                CharacterAssets(context),
                mood?.face,
                loaded.state.todayActiveMinutes,
            )
            moodLines = mood?.lines ?: emptyList()
            // 위젯 갱신 (#40) — 앱 사용 시 즉시, 컨디션·기분 실값 포함(워커는 마지막 기분 보존)
            WidgetUpdater.update(
                context = context,
                cappedTotalXp = loaded.state.cappedTotalXp,
                todayXp = loaded.state.todayXp,
                spriteState = spriteState,
                condition = loaded.state.condition.roundToInt(),
            )
            // 첫 XP 퍼널 (#47) — 원장에 무언가 적립된 사실만, 수치는 싣지 않는다
            if (loaded.state.cappedTotalXp > 0) {
                Telemetry.recordOnce(context, TelemetryEvent.FIRST_XP)
            }
            val now = java.time.LocalDateTime.now()
            cardEpochDay = now.toLocalDate().toEpochDay()
            settlementDelta = settleOnLoad(stores.settlement, loaded.state.cappedTotalXp)
            morningVisible = shouldShowMorningCard(stores.morning)
            journalVisible = shouldShowJournal(stores.journal, now)
        }
    }

    fun dismissMorning() {
        stores.morning.markShown(cardEpochDay)
        morningVisible = false
    }

    fun dismissJournal() {
        stores.journal.markShown(cardEpochDay)
        journalVisible = false
    }

    /** 개봉한 순간이 기준점 — 확인 전 재진입엔 다시 뜬다 (#61 패턴). */
    fun openSettlement(currentXp: Int) {
        stores.settlement.markSeen(currentXp)
        settlementDelta = null
    }

    /** 출발 = 에너지 확정 소모(#67) + 시작 시각 기록(#34) + 완료 알림 예약(#71). */
    // 로드 실패 후 재시도 (#227) — 기존 reloadKey를 재사용해 LaunchedEffect를 다시 태운다
    fun retry() {
        load = null // 로딩 인디케이터로 되돌려 "눌렸다"는 피드백을 준다
        reloadKey++
    }

    /**
     * 수동 동기화 완료 후 재로드 (#221) — 원장이 갱신됐으니 화면 수치도 다시 읽는다.
     *
     * [retry]와 달리 `load`를 비우지 않는다: 이미 보이는 값이 있는데 스피너로 되돌리면 "지금 확인"이
     * 화면을 깜빡이게 만든다. 갱신되면 조용히 바뀌는 편이 낫다.
     */
    fun refreshAfterSync() {
        reloadKey++
    }

    fun depart(currentXp: Int) {
        if (stores.energy.trySpend(currentXp, EnergyEngine.EXPEDITION_COST)) {
            stores.expedition.start(System.currentTimeMillis())
            ExpeditionReturnWorker.scheduleFor(context)
            reloadKey++
        }
    }

    fun openExpedition() {
        // 열 원정이 없으면(연타 등) 계측·후속 보상까지 건너뛴다 — 반복 참여 지표가 부풀지 않게(#204 리뷰)
        if (!stores.expedition.open()) return // 보상 지급·연출은 E5-7(#68)에서 이 지점에 연결
        ExpeditionReturnWorker.cancel(context) // 이미 확인한 원정은 알리지 않는다 (#71)
        Telemetry.record(TelemetryEvent.EXPEDITION_OPENED) // 반복 참여 지표 겸 퍼널 종점 (#47)
        reloadKey++
    }
}

/** 홈이 쓰는 로컬 스토어 묶음 — 화면당 1회 생성(remember). 카드가 늘 때 파라미터 폭발 방지. */
private class HomeStores(context: android.content.Context) {
    val rest = RestModeStore(context)
    val ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao())
    val energy = EnergyStore(context)
    val expedition = ExpeditionStore(context)
    val settlement = SettlementStore(context)
    val morning = MorningCardStore(context)
    val journal = EveningJournalStore(context)
    val goal = GoalStore(context)
    val streak = StreakStore(context)
    val onboarding = OnboardingStore(context)
}

/**
 * 아침 카드 노출 판정 (#36) — 오늘 아직 확인 안 했으면 노출. 소비는 확인 시점(#61 패턴).
 * 최초 실행은 카드 없이 오늘로 기준점만 설정(온보딩 직후 낮의 "좋은 아침" 방지 — 정산과 대칭).
 */
private fun shouldShowMorningCard(store: MorningCardStore): Boolean {
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
private fun shouldShowJournal(store: EveningJournalStore, now: java.time.LocalDateTime): Boolean {
    val today = now.toLocalDate().toEpochDay()
    if (store.lastShownEpochDay == EveningJournalStore.UNSET) {
        // 일지의 콘텐츠는 "오늘" — 기준점을 어제로 두어 설치 당일 저녁 일지를 보존(#70 리뷰 N2)
        store.markShown(today - 1)
    }
    return now.hour >= EveningJournalStore.OPEN_HOUR && store.lastShownEpochDay != today
}

/** 로드 시 정산 적용 (#35) — 순수 판정([decideSettlement]) 후 필요 시 기준점 동기화, 카드 차액 반환. */
private fun settleOnLoad(store: SettlementStore, currentXp: Int): Int? {
    val decision = decideSettlement(store.lastSeenXp, currentXp)
    if (decision.syncBaseline) store.markSeen(currentXp)
    return decision.deltaXp
}

/** 로드 완료 상태 — 정산 카드(#35)가 있으면 콘텐츠 위에 얹는다. */
@Composable
private fun HomeLoaded(state: HomeUiState, ui: HomeUiController) {
    // 첫 데이터 대기 중엔 0 나열 대신 '준비 중' (#213) — 아침 카드·정산도 보여줄 게 없다
    if (state.awaitingFirstData) {
        FirstRunNotice(onSyncFinished = ui::refreshAfterSync)
        // 빈 상태의 대상이 곧 코치의 대상이다 (#211) — 이력이 전혀 없는 완전 신규가 여기 온다.
        // 빈 상태만 두면 "기다리세요"로 끝나고, 정작 P0인 '첫 행동 다리'가 첫 세션에 안 걸린다.
        if (ui.coachVisible && state.firstSessionCue == FirstSessionCue.Coach) {
            FirstCoachCard(onDismiss = ui::dismissCoach)
        }
        return
    }
    if (ui.morningVisible) MorningCard(state, onDismiss = ui::dismissMorning)
    // 첫 세션 루프 (#211) — 코치와 축하는 상호 배타(core 판정), 각각 1회
    when (state.firstSessionCue) {
        FirstSessionCue.Coach -> if (ui.coachVisible) FirstCoachCard(onDismiss = ui::dismissCoach)

        // visible을 넘겨 exit 연출을 살린다 — 노드를 즉시 빼면 fadeOut이 생략된다
        FirstSessionCue.FirstXp -> FirstXpCard(ui.spriteState, ui.firstXpVisible, ui::dismissFirstXp)

        FirstSessionCue.None -> Unit
    }
    ui.settlementDelta?.let { delta ->
        SettlementCard(deltaXp = delta, onOpen = { ui.openSettlement(state.cappedTotalXp) })
    }
    HomeContent(
        state = state,
        spriteState = ui.spriteState,
        moodLines = ui.moodLines,
        onDepart = { ui.depart(state.cappedTotalXp) },
        onOpen = ui::openExpedition,
        onSyncFinished = ui::refreshAfterSync,
    )
    if (ui.journalVisible) EveningJournalCard(state, onDismiss = ui::dismissJournal)
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    spriteState: String,
    moodLines: List<String>,
    onDepart: () -> Unit,
    onOpen: () -> Unit,
    onSyncFinished: () -> Unit,
) {
    // 상단 히어로 — 캐릭터·대사·컨디션을 묶어 최상위 앵커로 (#256). 아래는 종속 상세 카드.
    HomeHero(spriteState, moodLines, state.condition, state.moodContext.restMode)
    StreakRow(state.streak)
    // 이번 주 목표 진척 (#215) — 기세(일 단위 연속) 다음에 주 단위 리듬
    WeeklyGoalRow(state.weeklyProgress)
    TodaySummaryCard(state)
    ExpeditionCard(state.expedition, state.energy, onDepart, onOpen)
    // 다음 목표를 카드로 편입 — 맨 Text로 두면 카드 스택 리듬이 끊긴다 (#254)
    NexusCard {
        Text(text = nextGoalText(state), style = MaterialTheme.typography.bodyMedium)
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

/** ActivityScreen.loadActivity와 같은 catch 계약 (#130) + 권한 회수는 안내로 (#144 패턴). */
private suspend fun loadHome(
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
