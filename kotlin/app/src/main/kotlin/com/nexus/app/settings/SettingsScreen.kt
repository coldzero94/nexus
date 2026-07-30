package com.nexus.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.diag.DeveloperToolsCard
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing

/** 설정 (#31·#33·#49) — 연동 상태·휴식 모드·리마인더·목표·위젯·데이터 삭제. 백업은 E8-6(#51). */
@Composable
fun SettingsScreen(manager: HealthConnectManager, modifier: Modifier = Modifier, onReconnect: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { RestModeStore(context) }
    var restEnabled by remember { mutableStateOf(store.enabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexusSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        HealthStatusCard(manager, onReconnect)
        OpenDaysCard()
        CharacterNameCard()
        NexusCard(
            title = stringResource(R.string.settings_rest_mode),
            trailing = {
                Switch(
                    checked = restEnabled,
                    onCheckedChange = { checked ->
                        store.setEnabled(checked)
                        restEnabled = checked
                    },
                )
            },
        ) {
            Text(stringResource(R.string.settings_rest_mode_desc), style = MaterialTheme.typography.bodySmall)
        }
        ReminderCard()
        WeeklyGoalCard()
        WidgetPinCard()
        BackupCard()
        DeleteDataCard()
        // 디버그 소스셋에만 실체가 있다 — 릴리스는 no-op 짝이 컴파일된다 (#245).
        // 조건 없이 부르는 게 의도다: BuildConfig.DEBUG 분기면 도구 코드·문자열이 릴리스에 실린다.
        DeveloperToolsCard(manager)
    }
}
