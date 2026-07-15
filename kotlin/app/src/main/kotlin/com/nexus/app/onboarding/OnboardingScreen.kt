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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.app.R
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.HealthPermissions

private enum class OnboardingStep { Welcome, Rationale, SamsungHealth }

/**
 * 온보딩 v0 (#6): 캐릭터 생성(임시) → 권한 설명 → HC 권한 3종 요청 → 삼성헬스 안내.
 * 권한 거부·HC 미가용 시에도 온보딩은 끝까지 진행되고 [onFinished]에 connected=false로 전달(데모 모드).
 */
@Composable
fun OnboardingScreen(manager: HealthConnectManager, onFinished: (connected: Boolean) -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    var granted by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = manager.requestPermissionsContract(),
    ) { result ->
        granted = result.containsAll(HealthPermissions.ALL)
        step = OnboardingStep.SamsungHealth
    }

    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(
            onNext = {
                step = if (manager.isAvailable()) OnboardingStep.Rationale else OnboardingStep.SamsungHealth
            },
        )

        OnboardingStep.Rationale -> RationaleStep(
            onGrant = { permissionLauncher.launch(HealthPermissions.ALL) },
            onSkip = { onFinished(false) },
        )

        OnboardingStep.SamsungHealth -> SamsungHealthStep(
            onDone = { onFinished(granted) },
        )
    }
}

@Composable
private fun StepScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) = StepScaffold {
    Text(
        text = stringResource(R.string.onboarding_character_placeholder),
        fontSize = 96.sp,
    )
    Text(
        text = stringResource(R.string.onboarding_character_hint),
        style = MaterialTheme.typography.labelSmall,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
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
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.permission_rationale_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    PermissionRow(R.string.permission_read_title, R.string.permission_read_desc)
    PermissionRow(R.string.permission_background_title, R.string.permission_background_desc)
    PermissionRow(R.string.permission_history_title, R.string.permission_history_desc)
    Spacer(Modifier.height(32.dp))
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
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.samsung_health_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.samsung_health_done))
    }
}
