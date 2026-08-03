package com.nexus.app.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.HealthPermissions
import com.nexus.app.settings.GoalStore
import com.nexus.app.telemetry.Telemetry
import com.nexus.app.telemetry.TelemetryEvent
import com.nexus.app.ui.GoalDayChooser
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.NexusWordmark
import com.nexus.core.ConnectGate
import com.nexus.core.HealthAvailability
import com.nexus.core.OnboardingFlow
import com.nexus.core.OnboardingStage

/**
 * 온보딩 v0 (#6·#225): 캐릭터 생성(임시) → 권한 설명 → HC 권한 3종 요청 → 삼성헬스 안내 → 주간 목표.
 * 권한 거부·HC 미가용 시에도 온보딩은 끝까지 진행되고 [onFinished]에 connected=false로 전달(데모 모드).
 *
 * ## 진행 표시와 뒤로가기 (#225)
 *
 * 경로는 [OnboardingFlow]가 정한다 — HC를 쓸 수 없는 기기는 권한 설명을 건너뛰므로, 스텝을 선언
 * 순서로 세면 "1/4"였다가 3번으로 뛴다. 뒤로가기도 그 경로 위에서만 움직여 **건너뛴 스텝으로
 * 되돌아가지 않는다**(권한 요청이 실패하는 화면에 갇히지 않게).
 *
 * 시스템 back도 같은 동작이다([BackHandler]) — 화면의 어포던스와 기기 제스처가 다르게 동작하면
 * 사용자는 둘 중 하나를 신뢰하지 못한다. 첫 스텝에서는 핸들러를 붙이지 않아 시스템 기본(앱 종료)이 산다.
 *
 * ## 뒤로 가도 값이 남는다
 *
 * 권한 결과와 목표 선택을 **화면 수준 `rememberSaveable`**에 둔다. 스텝 컴포저블 안에 두면 뒤로
 * 갔다 오는 사이 컴포지션을 떠나 초기값으로 돌아가고, 사용자는 방금 고른 걸 다시 고르게 된다.
 * 회전·프로세스 사망에도 같은 이유로 살아남는다.
 */
@Composable
fun OnboardingScreen(manager: HealthConnectManager, onFinished: (connected: Boolean) -> Unit) {
    val context = LocalContext.current
    // 가용성은 **저장 상태**다. remember로 두면 프로세스 사망 후 stage는 복원되는데 경로는 다시
    // 계산돼 둘이 어긋난다 — RATIONALE이 복원됐는데 가용성이 false면 진행 표시도 뒤로가기도
    // 사라지고 시스템 back이 앱을 종료한다(AC ②가 요구하는 스텝에서 뒤로가 없어진다).
    var healthAvailable by rememberSaveable { mutableStateOf(manager.availability() == HealthAvailability.Available) }
    var stage by rememberSaveable { mutableStateOf(OnboardingStage.WELCOME) }
    var granted by rememberSaveable { mutableStateOf(false) }
    // 목표 선택도 화면 수준에 — 스텝 안에 두면 뒤로 갔다 오는 사이 초기값으로 돌아간다
    val goalStore = remember { GoalStore(context) }
    var goalDays by rememberSaveable { mutableIntStateOf(goalStore.weeklyGoalDays) }

    // 스텝 진입 계측 (#226) — 사용자당 1회. 스텝을 되돌아가도 이탈 지점이 흐려지지 않게.
    LaunchedEffect(stage) { Telemetry.recordOnce(context, stage.enterEvent()) }

    // 가용성 갱신은 **첫 스텝에서만**. 사용자가 온보딩을 열어둔 채 Health Connect를 설치하고
    // 돌아오는 경로를 살리려면 다시 읽어야 하지만(#236), 중간 스텝에서 읽으면 전체 단계 수가
    // 3↔4로 바뀌어 진행 표시가 튄다. WELCOME은 두 경로 모두 1번이라 안전하다.
    LaunchedEffect(stage) {
        if (stage == OnboardingStage.WELCOME) {
            healthAvailable = manager.availability() == HealthAvailability.Available
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = manager.requestPermissionsContract(),
    ) { result ->
        // 필수 권한만 본다 (#236) — 여기서 ALL을 요구하면 백그라운드 하나 거부로 데모에 갇힌다.
        granted = ConnectGate.isConnected(result, HealthPermissions.REQUIRED)
        Telemetry.recordOnce(
            context,
            if (granted) TelemetryEvent.PERMISSION_GRANTED else TelemetryEvent.PERMISSION_DENIED,
        )
        stage = OnboardingStage.SAMSUNG_HEALTH
    }

    val previous = OnboardingFlow.previousOf(stage, healthAvailable)
    val onBack = previous?.let { { stage = it } }
    // 시스템 back = 화면의 뒤로 버튼. 첫 스텝에선 붙이지 않아 앱 종료가 그대로 동작한다.
    if (onBack != null) BackHandler(onBack = onBack)

    val progress: @Composable () -> Unit = {
        OnboardingFlow.positionOf(stage, healthAvailable)?.let { position ->
            OnboardingProgress(
                current = position,
                total = OnboardingFlow.steps(healthAvailable).size,
                onBack = onBack,
            )
        }
    }

    OnboardingSteps(
        stage = stage,
        progress = progress,
        granted = granted,
        goalDays = goalDays,
        healthAvailable = healthAvailable,
        onStage = { stage = it },
        onGoalDays = { goalDays = it },
        onGrant = { permissionLauncher.launch(HealthPermissions.ALL) },
        onFinished = onFinished,
        onGoalConfirm = { goalStore.weeklyGoalDays = goalDays },
    )
}

/**
 * 스텝 라우팅 (#225) — 화면 함수에서 떼어냈다.
 *
 * 상태 소유(화면)와 스텝 선택(여기)을 나누면 스텝이 늘어도 화면 함수가 길어지지 않는다.
 * 파라미터가 많은 건 이 함수가 **상태를 갖지 않는다**는 뜻이고, 그게 의도다.
 */
@Suppress("LongParameterList")
@Composable
private fun OnboardingSteps(
    stage: OnboardingStage,
    progress: @Composable () -> Unit,
    granted: Boolean,
    goalDays: Int,
    healthAvailable: Boolean,
    onStage: (OnboardingStage) -> Unit,
    onGoalDays: (Int) -> Unit,
    onGrant: () -> Unit,
    onFinished: (Boolean) -> Unit,
    onGoalConfirm: () -> Unit,
) {
    val context = LocalContext.current
    when (stage) {
        OnboardingStage.WELCOME -> WelcomeStep(
            progress = progress,
            // 3상태 소비 (#236) — UpdateRequired에서 권한 요청으로 보내면 실패한다.
            // 안내 스텝으로 보내면 데모 랜딩의 '업데이트' CTA까지 이어진다.
            // 캐릭터 만들기는 가용성과 무관하게 항상 다음이다 (#42) — 권한보다 먼저 와야 한다
            onNext = { onStage(OnboardingStage.CREATE) },
        )

        OnboardingStage.CREATE -> StepScaffold(progress) {
            CreateCharacterContent(
                onDone = {
                    onStage(if (healthAvailable) OnboardingStage.RATIONALE else OnboardingStage.SAMSUNG_HEALTH)
                },
            )
        }

        OnboardingStage.RATIONALE -> RationaleStep(
            progress = progress,
            // 요청은 전부, 판정은 필수만 (#236) — 선택 권한도 물어봐야 기능이 켜진다
            onGrant = onGrant,
            // 이미 승인한 사용자에겐 데모 탈출구를 보여주지 않는다 (#225).
            // 뒤로가기가 생기면서 이 스텝이 **승인 후에도 도달 가능**해졌는데, 그때 '데모로 계속'은
            // 자기 상태와 모순이다: 누르면 connected=false로 끝나 초기 레벨 연출(#44)이 밀리고,
            // 퍼널엔 PERMISSION_GRANTED 직후 DEMO_CHOSEN이 찍히고, 목표 스텝을 건너뛰어 방금 고른
            // 목표가 버려진다.
            onSkip = if (granted) {
                null
            } else {
                {
                    Telemetry.recordOnce(context, TelemetryEvent.DEMO_CHOSEN)
                    onFinished(false)
                }
            },
        )

        OnboardingStage.SAMSUNG_HEALTH -> SamsungHealthStep(
            progress = progress,
            onDone = { onStage(OnboardingStage.WEEKLY_GOAL) },
        )

        OnboardingStage.WEEKLY_GOAL -> WeeklyGoalStep(
            progress = progress,
            selected = goalDays,
            onSelect = onGoalDays,
            onDone = {
                onGoalConfirm()
                onFinished(granted)
            },
        )
    }
}

/**
 * 주간 목표 스텝 (#73, E7-5) — "며칠 움직일까"를 약속받는다(개인 계수 시스템 입력).
 * 기본 4일(균형 보너스 기준). **확정 버튼을 눌러야 저장**된다(#225에서 상태를 화면 수준으로
 * 올렸다 — 뒤로 갔다 와도 선택이 남게). 설정 탭에서 언제든 변경.
 */
@Composable
private fun WeeklyGoalStep(
    progress: @Composable () -> Unit,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDone: () -> Unit,
) = StepScaffold(progress) {
    Text(
        text = stringResource(R.string.onboarding_goal_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.sm))
    Text(
        text = stringResource(R.string.onboarding_goal_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.xl))
    GoalDayChooser(selected = selected, onSelect = onSelect)
    Spacer(Modifier.height(NexusSpacing.xl))
    Button(onClick = onDone) {
        Text(stringResource(R.string.onboarding_goal_confirm))
    }
}

/**
 * 스텝 공통 골격 (#225) — 진행 표시를 **상단에 고정**하고 본문은 가운데 정렬.
 *
 * 진행 표시를 본문과 같은 Column에 넣으면 `Arrangement.Center` 때문에 본문과 함께 가운데로 밀려
 * 스텝마다 위치가 달라진다. 스텝을 넘길 때 인디케이터가 움직이면 진행이 아니라 잡음으로 읽힌다.
 *
 * ## 시스템 인셋을 반드시 준다
 *
 * 앱은 `enableEdgeToEdge()`이고 온보딩은 탭 화면과 달리 `Scaffold` 없이 그려진다. 이전에는 내용이
 * 전부 가운데 정렬이라 상태바에 가려질 게 없어 문제가 드러나지 않았는데, 진행 표시를 상단에 붙이자
 * **뒤로 버튼이 상태바 아래로 들어가 탭이 시스템으로 먹혔다**(실기 확인에서 잡았다 — 화면에는
 * 버튼이 보이는데 눌리지 않는다). 그래서 `safeDrawing`으로 인셋을 준다.
 */
@Composable
private fun StepScaffold(progress: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(NexusSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        progress()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun WelcomeStep(progress: @Composable () -> Unit, onNext: () -> Unit) = StepScaffold(progress) {
    // 브랜드 워드마크 (#261) — 온보딩 최전선 상단
    NexusWordmark()
    Spacer(Modifier.height(NexusSpacing.xl))
    CharacterComposer.CharacterSprite(state = "idle", modifier = Modifier.size(120.dp))
    Text(
        text = stringResource(R.string.onboarding_character_hint),
        style = MaterialTheme.typography.labelSmall,
    )
    Spacer(Modifier.height(NexusSpacing.xl))
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.md))
    Text(
        text = stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.xxl))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_next))
    }
}

@Composable
private fun RationaleStep(progress: @Composable () -> Unit, onGrant: () -> Unit, onSkip: (() -> Unit)?) =
    StepScaffold(progress) {
        Text(
            text = stringResource(R.string.permission_rationale_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexusSpacing.md))
        Text(
            text = stringResource(R.string.permission_rationale_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexusSpacing.xl))
        PermissionRow(R.string.permission_read_title, R.string.permission_read_desc)
        PermissionRow(R.string.permission_background_title, R.string.permission_background_desc)
        PermissionRow(R.string.permission_history_title, R.string.permission_history_desc)
        Spacer(Modifier.height(NexusSpacing.xxl))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_grant))
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_continue_demo))
            }
        }
    }

@Composable
private fun PermissionRow(titleRes: Int, descRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SamsungHealthStep(progress: @Composable () -> Unit, onDone: () -> Unit) = StepScaffold(progress) {
    Text(
        text = stringResource(R.string.samsung_health_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.md))
    Text(
        text = stringResource(R.string.samsung_health_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.xxl))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.samsung_health_done))
    }
}

/** 스텝 → 진입 이벤트 (#226). 매핑을 한곳에 둬 스텝 추가 시 계측 누락을 눈에 보이게 한다. */
private fun OnboardingStage.enterEvent(): TelemetryEvent = when (this) {
    OnboardingStage.WELCOME -> TelemetryEvent.ONBOARDING_STAGE_WELCOME
    OnboardingStage.CREATE -> TelemetryEvent.ONBOARDING_STAGE_CREATE
    OnboardingStage.RATIONALE -> TelemetryEvent.ONBOARDING_STAGE_RATIONALE
    OnboardingStage.SAMSUNG_HEALTH -> TelemetryEvent.ONBOARDING_STAGE_SAMSUNG_HEALTH
    OnboardingStage.WEEKLY_GOAL -> TelemetryEvent.ONBOARDING_STAGE_WEEKLY_GOAL
}
