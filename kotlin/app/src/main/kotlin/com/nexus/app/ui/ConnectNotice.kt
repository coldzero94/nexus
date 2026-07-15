package com.nexus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R

/**
 * 미연결 안내 공용 컴포넌트 (#32, #144 패턴) — 권한 문제는 에러가 아니라 "연결하면
 * 자란다"로 유도. 데모 모드 규칙(CLAUDE.md). 활동·성장 탭 채택은 #152.
 */
@Composable
fun ConnectNotice(onReconnect: (() -> Unit)?) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.growth_demo_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_demo_body), style = MaterialTheme.typography.bodyMedium)
            if (onReconnect != null) {
                Button(onClick = onReconnect) {
                    Text(stringResource(R.string.action_retry_permission))
                }
            }
        }
    }
}
