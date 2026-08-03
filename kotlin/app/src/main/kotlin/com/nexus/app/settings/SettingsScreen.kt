package com.nexus.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.diag.DeveloperToolsCard
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.NexusSwitchCard

/**
 * 설정 (#31·#33·#49·#264) — 연동 상태·휴식 모드·리마인더·목표·위젯·백업·데이터 삭제.
 *
 * ## 섹션 구조 (#264)
 *
 * 이전에는 7장이 **동일 강조로 평평히** 쌓여 무관한 항목이 섞여 있었다. 주제별 3개 섹션으로 묶어
 * "지금 바꾸려는 게 어디 있나"를 라벨로 찾게 한다([SettingsSection]).
 *
 * ## 파괴적 액션은 아래에 떼어 놓는다
 *
 * '데이터 삭제'가 '위젯 추가'와 **같은 시각 무게**로 나란히 있었다 — 되돌릴 수 없는 작업이
 * 편의 기능과 같은 줄에 있으면 오조작 위험이 그만큼 커진다. 구분선과 넓은 여백으로 떼어 놓고
 * 별 라벨을 붙인다. 삭제 자체의 확인 다이얼로그·`clearApplicationUserData` 계약은 손대지 않았다.
 */
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

        SettingsSection(stringResource(R.string.settings_section_connection)) {
            HealthStatusCard(manager, onReconnect)
        }

        // 이름과 동작을 한 섹션에 둔다 — 둘 다 "이 캐릭터를 어떻게 부르고 어떻게 자라게 할지"다.
        // 이름을 연동 섹션에 넣었다가 옮겼다: 연동(Health Connect)과 이름(IdentityStore)은 공유하는
        // 개념이 없어서, 합친 라벨이 "묶었다"가 아니라 "세워뒀다"로 읽혔다.
        SettingsSection(stringResource(R.string.settings_section_activity)) {
            CharacterNameCard()
            // 알파 게이트 리드아웃(#286) — 금요일에 팀이 물어보면 테스터가 이 숫자를 읽어준다.
            // 백업 섹션에 두면 '내보내기/가져오기'로 읽혀 찾지 못한다(ALPHA.md §게이트 ①).
            OpenDaysCard()
            NexusSwitchCard(
                title = stringResource(R.string.settings_rest_mode),
                description = stringResource(R.string.settings_rest_mode_desc),
                checked = restEnabled,
                onCheckedChange = { checked ->
                    store.setEnabled(checked)
                    restEnabled = checked
                },
            )
            ReminderCard()
            WeeklyGoalCard()
            WidgetPinCard()
        }

        SettingsSection(stringResource(R.string.settings_section_data)) {
            BackupCard()
            AnalyticsConsentCard()
        }

        // 디버그 소스셋에만 실체가 있다 — 릴리스는 no-op 짝이 컴파일된다 (#245).
        // 조건 없이 부르는 게 의도다: BuildConfig.DEBUG 분기면 도구 코드·문자열이 릴리스에 실린다.
        // 파괴적 영역보다 **위**에 둔다: 아래에 두면 '되돌릴 수 없는 작업' 라벨이 이 카드까지
        // 덮는 것처럼 보이고(카드에 '원장 전체 삭제' 버튼이 있어 더 헷갈린다), 릴리스에서는
        // 이 자리가 비어 파괴적 영역이 화면 맨 끝이 된다 — 두 빌드의 순서가 같아진다.
        DeveloperToolsCard(manager)

        DangerZone()
    }
}

/**
 * 되돌릴 수 없는 작업 (#264) — 구분선 + 넓은 여백으로 위쪽 설정과 떼어 놓는다.
 *
 * 여백만으로는 스크롤 중에 경계가 흐려진다. 구분선이 "여기부터는 성질이 다르다"를 한 픽셀로
 * 말해주고, 라벨이 그걸 문장으로 확인해준다 — 오조작 방지에는 둘 다 필요하다.
 */
@Composable
private fun DangerZone() {
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg), modifier = Modifier.fillMaxWidth()) {
        // 구분선 위 여백을 xl로 명시한다 — 바깥 spacedBy(lg)에 더해져 위쪽 설정과 확실히 벌어진다.
        // lg를 겹쳐 쌓으면 의도한 간격이 코드에 안 적혀 다음 사람이 우연으로 읽는다.
        HorizontalDivider(Modifier.padding(top = NexusSpacing.xl).testTag(DANGER_DIVIDER_TAG))
        SettingsSection(stringResource(R.string.settings_section_danger)) {
            DeleteDataCard()
        }
    }
}

/** 파괴적 영역 구분선 테스트 태그 — 구분선은 시맨틱이 없어 존재를 셀 수 없다. */
internal const val DANGER_DIVIDER_TAG = "settings_danger_divider"
