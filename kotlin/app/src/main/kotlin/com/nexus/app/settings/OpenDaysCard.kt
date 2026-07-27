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
import java.time.LocalDate

/**
 * 이번 주 사용 일수 (#286, E15-19) — D14 게이트(`docs/ALPHA.md`)의 "주 3회+"를 테스터가 **기억이
 * 아니라 화면 숫자**로 답하게 한다. 집계는 core [com.nexus.core.OpenDays], 저장은 [AppOpenTracker]
 * (기기 로컬). 앱이 전송하지 않는다 — 계측·수동 백업 페이로드·서버 없음(자동 백업은 본인 계정 표면).
 *
 * 알파 게이트 판정이 끝나면(#74 D14) 이 카드는 제거하거나 디버그 빌드 전용으로 내린다.
 */
@Composable
internal fun OpenDaysCard() {
    val context = LocalContext.current
    // 오늘 날짜를 키로 — 자정을 넘겨 화면에 머물러도 다음 리컴포지션에서 갱신된다(#286 리뷰).
    // 값 자체는 MainActivity.onResume이 이미 기록해 둔 것을 읽기만 한다.
    val today = LocalDate.now().toEpochDay()
    val openDays = remember(today) { AppOpenTracker(context).openDaysInWindow(today) }
    NexusCard(title = stringResource(R.string.settings_open_days)) {
        Text(
            stringResource(R.string.settings_open_days_value, openDays),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.settings_open_days_desc), style = MaterialTheme.typography.bodySmall)
    }
}
