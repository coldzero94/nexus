package com.nexus.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.app.R
import com.nexus.app.notify.NotificationSettings
import com.nexus.app.notify.ReminderWorker
import com.nexus.app.ui.GoalDayChooser

/** 설정 (#31·#33) — 휴식 모드·리마인더 알림. 연동 상태·백업·데이터 삭제는 후속(E7·E10). */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { RestModeStore(context) }
    var restEnabled by remember { mutableStateOf(store.enabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_rest_mode), style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = restEnabled,
                        onCheckedChange = { checked ->
                            store.setEnabled(checked)
                            restEnabled = checked
                        },
                    )
                }
                Text(stringResource(R.string.settings_rest_mode_desc), style = MaterialTheme.typography.bodySmall)
            }
        }
        ReminderCard()
        WeeklyGoalCard()
        WidgetPinCard()
    }
}

/** 위젯 추가 유도 (#40) — 런처 핀 요청. 위젯은 최상위 리텐션 표면(Finch 4대 장치). */
@Composable
private fun WidgetPinCard() {
    val context = LocalContext.current
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_widget), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_widget_desc), style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val manager = android.appwidget.AppWidgetManager.getInstance(context)
                val provider = android.content.ComponentName(
                    context,
                    com.nexus.app.widget.NexusWidgetReceiver::class.java,
                )
                if (manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(provider, null, null)
                }
            }) {
                Text(stringResource(R.string.settings_widget_add))
            }
        }
    }
}

/** 주간 목표 변경 (#73) — 온보딩에서 정한 값을 언제든 수정. */
@Composable
private fun WeeklyGoalCard() {
    val context = LocalContext.current
    val store = remember { GoalStore(context) }
    var selected by remember { mutableStateOf(store.weeklyGoalDays) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_goal), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_goal_desc), style = MaterialTheme.typography.bodySmall)
            GoalDayChooser(
                selected = selected,
                onSelect = { days ->
                    store.weeklyGoalDays = days
                    selected = days
                },
            )
        }
    }
}

/**
 * 리마인더 알림 토글 (#33) — 기본 꺼짐(옵트인). 켤 때 알림 권한을 요청하고,
 * 거부되면 토글을 켜지 않는다(조르지 않음). 규율(일 2건·조용 시간)은 워커가 판정.
 */
@Composable
private fun ReminderCard() {
    val context = LocalContext.current
    val store = remember { NotificationSettings(context) }
    var enabled by remember { mutableStateOf(store.enabled) }

    fun apply(value: Boolean) {
        store.enabled = value
        enabled = value
        if (value) ReminderWorker.enqueuePeriodic(context) else ReminderWorker.cancel(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) apply(true) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_reminder), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        when {
                            !checked -> apply(false)

                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED ->
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                            else -> apply(true)
                        }
                    },
                )
            }
            Text(stringResource(R.string.settings_reminder_desc), style = MaterialTheme.typography.bodySmall)
        }
    }
}
