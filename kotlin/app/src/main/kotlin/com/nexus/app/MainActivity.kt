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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.nexus.app.onboarding.OnboardingScreen
import com.nexus.app.steps.ActivityScreen
import com.nexus.core.ActivityType
import com.nexus.core.XpEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = HealthConnectManager(applicationContext)
        setContent {
            MaterialTheme {
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

    if (!finished) {
        OnboardingScreen(manager) { isConnected ->
            connected = isConnected
            finished = true
            // 연결 성공 시 15분 주기 백그라운드 동기화 등록 (#8)
            if (isConnected) HealthSyncWorker.enqueuePeriodic(context)
        }
    } else if (connected) {
        // 연결됨 → 활동/성장 2탭 (#23). 홈(캐릭터) 탭은 E4에서 추가.
        ConnectedTabs(manager)
    } else {
        DemoLanding(
            available = manager.isAvailable(),
            onReconnect = { finished = false },
        )
    }
}

private enum class MainTab(val labelRes: Int) {
    ACTIVITY(R.string.tab_activity),
    GROWTH(R.string.tab_growth),
}

@Composable
private fun ConnectedTabs(manager: HealthConnectManager) {
    var tab by rememberSaveable { mutableStateOf(MainTab.ACTIVITY) }
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
            MainTab.ACTIVITY -> ActivityScreen(manager, Modifier.padding(padding))
            MainTab.GROWTH -> GrowthScreen(manager, Modifier.padding(padding))
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
