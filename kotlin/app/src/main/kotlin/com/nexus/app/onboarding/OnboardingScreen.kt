package com.nexus.app.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private enum class OnboardingStep { Welcome, Rationale, SamsungHealth, WeeklyGoal }

/**
 * 온보딩 v0 (#6): 캐릭터 생성(임시) → 권한 설명 → HC 권한 3종 요청 → 삼성헬스 안내.
 * 권한 거부·HC 미가용 시에도 온보딩은 끝까지 진행되고 [onFinished]에 connected=false로 전달(데모 모드).
 */
@Composable
fun OnboardingScreen(manager: HealthConnectManager, onFinished: (connected: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    var granted by rememberSaveable { mutableStateOf(false) }

    // 스텝 진입 계측 (#226) — 사용자당 1회. 스텝을 되돌아가도 이탈 지점이 흐려지지 않게.
    LaunchedEffect(step) { Telemetry.recordOnce(context, step.enterEvent()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = manager.requestPermissionsContract(),
    ) { result ->
        // 필수 권한만 본다 (#236) — 여기서 ALL을 요구하면 백그라운드 하나 거부로 데모에 갇힌다.
        // 매니저·MainActivity는 #236에서 고쳤는데 이 런처가 남아 있었다.
        granted = ConnectGate.isConnected(result, HealthPermissions.REQUIRED)
        Telemetry.recordOnce(
            context,
            if (granted) TelemetryEvent.PERMISSION_GRANTED else TelemetryEvent.PERMISSION_DENIED,
        )
        step = OnboardingStep.SamsungHealth
    }

    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(
            onNext = {
                // 3상태 소비 (#236) — UpdateRequired에서 권한 요청으로 보내면 실패한다.
                // 안내 스텝으로 보내면 데모 랜딩의 '업데이트' CTA까지 이어진다.
                step = if (manager.availability() == HealthAvailability.Available) {
                    OnboardingStep.Rationale
                } else {
                    OnboardingStep.SamsungHealth
                }
            },
        )

        OnboardingStep.Rationale -> RationaleStep(
            // 요청은 전부, 판정은 필수만 (#236) — 선택 권한도 물어봐야 기능이 켜진다
            onGrant = { permissionLauncher.launch(HealthPermissions.ALL) },
            onSkip = {
                Telemetry.recordOnce(context, TelemetryEvent.DEMO_CHOSEN)
                onFinished(false)
            },
        )

        OnboardingStep.SamsungHealth -> SamsungHealthStep(
            onDone = { step = OnboardingStep.WeeklyGoal },
        )

        OnboardingStep.WeeklyGoal -> WeeklyGoalStep(
            onDone = { onFinished(granted) },
        )
    }
}

/**
 * 주간 목표 스텝 (#73, E7-5) — "며칠 움직일까"를 약속받는다(개인 계수 시스템 입력).
 * 기본 4일(균형 보너스 기준). 선택 즉시 저장, 설정 탭에서 언제든 변경.
 */
@Composable
private fun WeeklyGoalStep(onDone: () -> Unit) = StepScaffold {
    val context = LocalContext.current
    val store = remember { GoalStore(context) }
    var selected by rememberSaveable { mutableStateOf(store.weeklyGoalDays) }

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
    GoalDayChooser(selected = selected, onSelect = { selected = it })
    Spacer(Modifier.height(NexusSpacing.xl))
    Button(onClick = {
        store.weeklyGoalDays = selected
        onDone()
    }) {
        Text(stringResource(R.string.onboarding_goal_confirm))
    }
}

@Composable
private fun StepScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexusSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) = StepScaffold {
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
private fun RationaleStep(onGrant: () -> Unit, onSkip: () -> Unit) = StepScaffold {
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
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue_demo))
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
private fun SamsungHealthStep(onDone: () -> Unit) = StepScaffold {
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
private fun OnboardingStep.enterEvent(): TelemetryEvent = when (this) {
    OnboardingStep.Welcome -> TelemetryEvent.ONBOARDING_STAGE_WELCOME
    OnboardingStep.Rationale -> TelemetryEvent.ONBOARDING_STAGE_RATIONALE
    OnboardingStep.SamsungHealth -> TelemetryEvent.ONBOARDING_STAGE_SAMSUNG_HEALTH
    OnboardingStep.WeeklyGoal -> TelemetryEvent.ONBOARDING_STAGE_WEEKLY_GOAL
}
