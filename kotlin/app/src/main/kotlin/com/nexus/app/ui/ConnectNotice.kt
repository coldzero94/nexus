package com.nexus.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexus.app.R

/**
 * 미연결 안내 공용 컴포넌트 (#32·#152, #144 패턴) — 권한 문제는 에러가 아니라 "연결하면
 * 자란다"로 유도. 데모 모드 규칙(CLAUDE.md). [body]는 탭별 문구(기본: 홈).
 */
@Composable
fun ConnectNotice(onReconnect: (() -> Unit)?, body: String = stringResource(R.string.home_demo_body)) {
    NexusCard(title = stringResource(R.string.growth_demo_title)) {
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (onReconnect != null) {
            Button(onClick = onReconnect) {
                Text(stringResource(R.string.action_retry_permission))
            }
        }
    }
}
