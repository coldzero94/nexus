package com.nexus.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexus.app.R

/**
 * 로드 실패 안내 + 재시도 (#227, E14-17) — 세 화면(홈·활동·성장)이 공유한다.
 *
 * 이전에는 "잠시 후 다시 시도해 주세요"라는 **문구만** 있고 실제 재시도 수단이 없어, 사용자가 탭을
 * 떠났다 돌아오거나 앱을 껐다 켜야 했다. 권한 거부에는 재연결 버튼([ConnectNotice])이 있는데 일반
 * 오류에만 없어 완성도가 비대칭이었고, 카피와 UI가 모순됐다.
 *
 * 권한 문제와는 계속 분리한다 — 권한은 '연결하기'(원인 해결), 일반 오류는 '다시 시도'(재시도).
 */
@Composable
fun RetryNotice(message: String, onRetry: () -> Unit) {
    NexusCard(title = stringResource(R.string.error_title)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
