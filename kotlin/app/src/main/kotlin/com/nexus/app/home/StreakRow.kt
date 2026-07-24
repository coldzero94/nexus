package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.StreakStatus

/**
 * 기세 표시 (#214) — 무처벌·다정체. 끊김을 경고하지 않고, 오늘 미충족은 "채우면 이어짐"으로 권한다.
 * 현재 연속일과 최장(영속)을 보여준다. 표시 전용 — 건강 수치는 화면에만, 페이로드에 없다(②).
 */
@Composable
internal fun StreakRow(streak: StreakStatus, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(NexusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            Text(
                text = if (streak.current > 0) {
                    stringResource(R.string.streak_current, streak.current)
                } else {
                    stringResource(R.string.streak_start)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            // 진행 중인 기세가 있을 때만 그레이스 안내 — current=0이면 "첫 걸음" 카피와 중복되므로 생략
            if (streak.todayPending && streak.current > 0) {
                Text(
                    text = stringResource(R.string.streak_pending, streak.current + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (streak.longest > 0) {
                Text(
                    text = stringResource(R.string.streak_longest, streak.longest),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
