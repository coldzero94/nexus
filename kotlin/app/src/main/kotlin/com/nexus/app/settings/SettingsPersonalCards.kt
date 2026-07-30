package com.nexus.app.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.nexus.app.R
import com.nexus.app.notify.NotificationSettings
import com.nexus.app.notify.ReminderWorker
import com.nexus.app.ui.GoalDayChooser
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.NexusSwitchCard
import com.nexus.core.CharacterName
import kotlinx.coroutines.launch

/**
 * 설정 개인화 카드 (#311 분리) — 캐릭터 이름·주간 목표·리마인더는 한 파일에 모은다.
 *
 * 캐릭터 이름 (#216, E14-6) — 지어준 이름을 앱 카피가 호명하게 하는 진입점. 저장 규칙은
 * core [CharacterName](1~12자·공백 방지), 저장은 로컬 [IdentityStore]. 이름은 앱이 전송하지 않는다
 * (텔레메트리·크래시 페이로드·서버 전송 없음 — Android 자동 백업은 사용자 본인 계정 표면).
 */
@Composable
internal fun CharacterNameCard() {
    val context = LocalContext.current
    val store = remember { IdentityStore(context) }
    var saved by remember { mutableStateOf(store.name) }
    var input by remember { mutableStateOf(saved.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }

    NexusCard(title = stringResource(R.string.settings_name)) {
        Text(stringResource(R.string.settings_name_desc), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = input,
            onValueChange = {
                // 저장 시점이 아니라 입력 시점에 상한 — 40자 치고 나서야 알게 되지 않도록(#216 리뷰)
                if (it.length <= CharacterName.MAX_LENGTH) input = it
                invalid = false
            },
            label = { Text(stringResource(R.string.settings_name_label, CharacterName.MAX_LENGTH)) },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.settings_name_invalid, CharacterName.MAX_LENGTH)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                saved?.let { stringResource(R.string.settings_name_saved, it) }
                    ?: stringResource(R.string.settings_name_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NameActions(
                canClear = saved != null,
                onClear = {
                    store.clear()
                    saved = null
                    input = ""
                    invalid = false
                },
                onSave = {
                    if (store.setName(input)) {
                        saved = store.name
                        input = saved.orEmpty()
                        invalid = false
                    } else {
                        invalid = true
                    }
                },
            )
        }
    }
}

/** 이름 카드 액션 (#216) — 지우기(설정돼 있을 때만)와 저장. 지운 뒤엔 무명 카피로 돌아간다. */
@Composable
private fun NameActions(canClear: Boolean, onClear: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
        if (canClear) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.settings_name_clear)) }
        }
        Button(onClick = onSave) { Text(stringResource(R.string.settings_name_save)) }
    }
}

/** 주간 목표 변경 (#73) — 온보딩에서 정한 값을 언제든 수정. */
@Composable
internal fun WeeklyGoalCard() {
    val context = LocalContext.current
    val store = remember { GoalStore(context) }
    var selected by remember { mutableStateOf(store.weeklyGoalDays) }

    NexusCard(title = stringResource(R.string.settings_goal)) {
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

/**
 * 리마인더 알림 토글 (#33) — 기본 꺼짐(옵트인). 켤 때 알림 권한을 요청하고,
 * 거부되면 토글을 켜지 않는다(조르지 않음). 규율(일 2건·조용 시간)은 워커가 판정.
 */
@Composable
internal fun ReminderCard() {
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

    NexusSwitchCard(
        title = stringResource(R.string.settings_reminder),
        description = stringResource(R.string.settings_reminder_desc),
        checked = enabled,
        onCheckedChange = { checked ->
            when {
                !checked -> apply(false)

                // minSdk 34라 POST_NOTIFICATIONS(33+)는 항상 런타임 권한 — 버전 가드 불필요(#242 lint)
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
