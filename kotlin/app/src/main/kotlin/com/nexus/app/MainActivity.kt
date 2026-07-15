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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.onboarding.OnboardingScreen
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
    var finished by rememberSaveable { mutableStateOf(false) }
    var connected by rememberSaveable { mutableStateOf(false) }

    if (!finished) {
        OnboardingScreen(manager) { isConnected ->
            connected = isConnected
            finished = true
        }
    } else {
        PostOnboarding(
            connected = connected,
            available = manager.isAvailable(),
            onReconnect = { finished = false },
        )
    }
}

/** 온보딩 이후 임시 랜딩 — 연결/데모 상태 표시. 실제 홈 화면은 E4에서 대체. */
@Composable
private fun PostOnboarding(connected: Boolean, available: Boolean, onReconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (connected) {
            Text(
                text = stringResource(R.string.status_connected),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        } else {
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
