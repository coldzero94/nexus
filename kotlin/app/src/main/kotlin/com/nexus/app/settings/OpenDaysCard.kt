package com.nexus.app.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.home.AppOpenTracker
import com.nexus.app.ui.NexusCard

/**
 * 이번 주 사용 일수 (#286, E15-19) — D14 게이트(`docs/ALPHA.md`)의 "주 3회+"를 테스터가 **기억이
 * 아니라 화면 숫자**로 답하게 한다. 집계는 core [com.nexus.core.OpenDays], 저장은 [AppOpenTracker]
 * (기기 로컬). 값은 어디로도 전송되지 않는다 — 계측·백업 페이로드·서버 없음.
 *
 * 알파 게이트 판정이 끝나면(#74 D14) 이 카드는 제거하거나 디버그 빌드 전용으로 내린다.
 */
@Composable
internal fun OpenDaysCard() {
    val context = LocalContext.current
    // 화면 진입 시점의 값 — 앱을 연 순간 오늘이 이미 기록돼 있다(MainActivity)
    val openDays = remember { AppOpenTracker(context).openDaysInWindow() }
    NexusCard(title = stringResource(R.string.settings_open_days)) {
        Text(
            stringResource(R.string.settings_open_days_value, openDays),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.settings_open_days_desc), style = MaterialTheme.typography.bodySmall)
    }
}
