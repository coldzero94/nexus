package com.nexus.app.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.MoodResolver
import com.nexus.app.data.EnergyStore
import com.nexus.app.data.ExpeditionStore
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.notify.ExpeditionReturnWorker
import com.nexus.app.onboarding.OnboardingStore
import com.nexus.app.settings.GoalStore
import com.nexus.app.settings.RestModeStore
import com.nexus.app.telemetry.Telemetry
import com.nexus.app.telemetry.TelemetryEvent
import com.nexus.app.widget.WidgetUpdater
import com.nexus.core.EnergyEngine
import kotlin.math.roundToInt

/**
 * 홈 카드 상태·행위 홀더 (#35·#36) — 카드가 늘 때 화면 함수가 길어지지 않게 콜백을 메서드로.
 * Compose 상태는 프로퍼티 델리게이트로 소유(리컴포지션 트리거 유지).
 */
internal class HomeUiController(val stores: HomeStores, private val context: android.content.Context) {
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
internal class HomeStores(context: android.content.Context) {
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
