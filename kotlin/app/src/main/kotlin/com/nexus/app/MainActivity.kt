package com.nexus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.nexus.app.growth.GrowthScreen
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.HealthSyncWorker
import com.nexus.app.home.AppOpenTracker
import com.nexus.app.home.HomeScreen
import com.nexus.app.home.WelcomeBackScene
import com.nexus.app.onboarding.OnboardingScreen
import com.nexus.app.settings.SettingsScreen
import com.nexus.app.steps.ActivityScreen
import com.nexus.app.ui.NexusTheme
import com.nexus.core.ActivityType
import com.nexus.core.ReturnWelcomePolicy
import com.nexus.core.XpEngine
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = HealthConnectManager(applicationContext)
        setContent {
            NexusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NexusApp(manager)
                }
            }
        }
    }
}

@Composable
private fun NexusApp(manager: HealthConnectManager) {
    val context = LocalContext.current
    var finished by rememberSaveable { mutableStateOf(false) }
    var connected by rememberSaveable { mutableStateOf(false) }

    // 복귀 환영 (#30): 판정·마커 갱신은 커밋된 컴포지션에서 1회(LaunchedEffect) —
    // 이니셜라이저 부수효과는 프레임 폐기 시 마커만 소모하고 판정을 잃을 수 있다.
    // rememberSaveable(-1 = 미판정)이라 회전·프로세스 복원에도 같은 판정이 유지된다.
    val tracker = remember { AppOpenTracker(context) }
    var welcomeGapDays by rememberSaveable { mutableStateOf(UNDECIDED_GAP) }
    LaunchedEffect(Unit) {
        if (welcomeGapDays == UNDECIDED_GAP) {
            val today = LocalDate.now().toEpochDay()
            val last = tracker.lastOpenEpochDay
            tracker.recordOpen(today)
            welcomeGapDays =
                if (ReturnWelcomePolicy.shouldWelcome(last, today)) {
                    ReturnWelcomePolicy.gapDays(last, today)
                } else {
                    0L
                }
        }
    }

    if (!finished) {
        OnboardingScreen(manager) { isConnected ->
            connected = isConnected
            finished = true
            // 연결 성공 시 15분 주기 백그라운드 동기화 등록 (#8)
            if (isConnected) HealthSyncWorker.enqueuePeriodic(context)
        }
    } else if (connected && welcomeGapDays > 0L) {
        // 3일+ 공백 복귀 → 환영 씬 먼저 (#30, 1급 기능)
        WelcomeBackScene(gapDays = welcomeGapDays, onContinue = { welcomeGapDays = 0L })
    } else if (connected) {
        // 연결됨 → 홈/활동/성장 3탭 (#23·#32).
        ConnectedTabs(manager, onReconnect = { finished = false })
    } else {
        DemoLanding(
            available = manager.isAvailable(),
            onReconnect = { finished = false },
        )
    }
}

/** 복귀 판정 전 표식 — 온보딩이 먼저 렌더되므로 사용자에게 보이는 지연은 없다 (#30). */
private const val UNDECIDED_GAP = -1L

private enum class MainTab(val labelRes: Int) {
    HOME(R.string.tab_home),
    ACTIVITY(R.string.tab_activity),
    GROWTH(R.string.tab_growth),
    SETTINGS(R.string.tab_settings),
}

@Composable
private fun ConnectedTabs(manager: HealthConnectManager, onReconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.HOME -> HomeScreen(manager, Modifier.padding(padding), onReconnect)
            MainTab.ACTIVITY -> ActivityScreen(manager, Modifier.padding(padding), onReconnect)
            MainTab.GROWTH -> GrowthScreen(manager, Modifier.padding(padding), onReconnect)
            MainTab.SETTINGS -> SettingsScreen(Modifier.padding(padding))
        }
    }
}

/** 권한 거부·HC 미가용 시 데모 랜딩. 실제 홈 화면은 E4에서 대체. */
@Composable
private fun DemoLanding(available: Boolean, onReconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val titleRes = if (available) R.string.status_demo_title else R.string.status_unavailable_title
        val bodyRes = if (available) R.string.status_demo_body else R.string.status_unavailable_body
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (available) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReconnect) {
                Text(stringResource(R.string.action_retry_permission))
            }
        }
        Spacer(Modifier.height(24.dp))
        // core(KMP) 연결 증명 — 성장 미리보기(실제 홈은 E4)
        val sampleXp = XpEngine.baseScore(ActivityType.RUNNING, minutes = 30)
        Text(
            text = stringResource(R.string.growth_preview, sampleXp, XpEngine.FORMULA_VERSION),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
