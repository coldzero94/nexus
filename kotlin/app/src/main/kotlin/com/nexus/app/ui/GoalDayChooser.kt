package com.nexus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.settings.GoalStore
import com.nexus.app.ui.NexusSpacing

/**
 * 주간 목표 일수 선택 (#73) — 온보딩·설정이 공유. 칩이 좁은 화면 너비를 넘기면 다음 줄로 감긴다
 * ([FlowRow]) — 고정 Row는 마지막 칩이 짓눌려 글자가 세로로 깨졌다(#248).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalDayChooser(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
    ) {
        for (days in GoalStore.MIN_DAYS..GoalStore.MAX_DAYS) {
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text(stringResource(R.string.goal_days_format, days)) },
            )
        }
    }
}
