package com.nexus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 브랜드 카드 (#254) — 앱 전역 카드의 단일 규격. 내부 패딩·간격은 [NexusSpacing] 토큰,
 * 색은 [emphasis]로 M3 롤 매핑(하드코딩 색 금지). 헤더(제목 + 우측 값 슬롯)와 본문 슬롯을 제공해
 * 카드마다 복붙되던 `Card { Column(padding, spacedBy) { Text(title) … } }` 패턴을 통일한다.
 *
 * @param title 헤더 제목(없으면 헤더 행 생략)
 * @param trailing 헤더 우측 값 슬롯(컨디션 값·에너지 등)
 * @param content 본문
 */
@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    emphasis: CardEmphasis = CardEmphasis.Neutral,
    title: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = emphasis.colors()) {
        Column(
            Modifier.fillMaxWidth().padding(NexusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        ) {
            if (title != null || trailing != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (title != null) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    trailing?.invoke(this)
                }
            }
            content()
        }
    }
}

@Composable
private fun CardEmphasis.colors(): CardColors = when (this) {
    // 종속 카드는 surfaceContainerLow(밝음) — 히어로(surfaceContainerHigh, 진함)보다 물러나게
    // (M3 filled Card 기본값 surfaceContainerHighest는 히어로보다 진해 위계가 역전됐다, #256 리뷰)
    CardEmphasis.Neutral -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )

    CardEmphasis.Highlight -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    CardEmphasis.Celebration -> CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
