package com.nexus.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.notify.NotificationSettings
import com.nexus.app.notify.ReminderWorker
import com.nexus.app.ui.GoalDayChooser
import kotlinx.coroutines.CancellationException
import java.io.IOException

private const val TAG = "SettingsScreen"

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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        HealthStatusCard(manager, onReconnect)
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
        DeleteDataCard()
    }
}

/**
 * 연동 상태 (#49, E8-4) — 권한 보유 여부만 표시(값 조회 없음). 미연결이면 온보딩 권한
 * 플로우로 복귀([onReconnect] — 홈 ConnectNotice와 같은 경로).
 */
@Composable
private fun HealthStatusCard(manager: HealthConnectManager, onReconnect: (() -> Unit)?) {
    var connected by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        connected = if (manager.isAvailable()) checkPermissionsOrFalse(manager) else false
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_health_title), style = MaterialTheme.typography.titleMedium)
            when (connected) {
                null -> Text(
                    stringResource(R.string.settings_health_checking),
                    style = MaterialTheme.typography.bodySmall,
                )

                true -> Text(
                    stringResource(R.string.settings_health_connected),
                    style = MaterialTheme.typography.bodySmall,
                )

                false -> {
                    Text(
                        stringResource(R.string.settings_health_disconnected),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (onReconnect != null) {
                        Button(onClick = onReconnect) {
                            Text(stringResource(R.string.action_retry_permission))
                        }
                    }
                }
            }
        }
    }
}

/** 권한 확인 — 실패는 미연결로(안내가 뜨는 안전 방향, #130 catch 계약). */
private suspend fun checkPermissionsOrFalse(manager: HealthConnectManager): Boolean = try {
    manager.hasAllPermissions()
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "permission check IO failure", e)
    false
} catch (e: RemoteException) {
    Log.w(TAG, "permission check remote failure", e)
    false
} catch (e: SecurityException) {
    Log.w(TAG, "permission check denied", e)
    false
} catch (e: IllegalStateException) {
    Log.w(TAG, "permission check state failure", e)
    false
}

/**
 * 데이터 삭제 (#49, E8-4) — OS 수준 전체 삭제([ActivityManager.clearApplicationUserData]):
 * 원장 DB·프리퍼런스·WorkManager 큐까지 한 번에, 누락 위험 없음(Play Data safety의 삭제 제공).
 * 성공 시 프로세스가 즉시 종료되고 다음 실행은 온보딩부터. HC 원본 기록은 앱이 저장하지
 * 않으므로 삭제 대상 자체가 없다(카피에 명시).
 */
@Composable
private fun DeleteDataCard() {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_delete_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_delete_desc), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { confirming = true }) {
                Text(
                    stringResource(R.string.settings_delete_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    val am = context.getSystemService(android.app.ActivityManager::class.java)
                    if (!am.clearApplicationUserData()) {
                        Log.w(TAG, "clearApplicationUserData refused") // 성공 시엔 프로세스가 종료된다
                    }
                }) {
                    Text(stringResource(R.string.delete_confirm_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.delete_confirm_no))
                }
            },
        )
    }
}

/** 위젯 추가 유도 (#40) — 런처 핀 요청. 위젯은 최상위 리텐션 표면(Finch 4대 장치). */
@Composable
private fun WidgetPinCard() {
    val context = LocalContext.current
    val pinSupported = remember {
        android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }
    if (!pinSupported) return // 미지원 런처 — 무반응 버튼 대신 카드 자체를 숨김 (#40 리뷰 N1)
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
