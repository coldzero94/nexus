package com.nexus.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 브랜드 카드 (#254) — 앱 전역 카드의 단일 규격. 내부 패딩·간격은 [NexusSpacing] 토큰,
 * 색은 [emphasis]로 M3 롤 매핑(하드코딩 색 금지). 헤더(제목 + 우측 값 슬롯)와 본문 슬롯을 제공해
 * 카드마다 복붙되던 `Card { Column(padding, spacedBy) { Text(title) … } }` 패턴을 통일한다.
 *
 * @param titleIcon 제목 앞 개념 아이콘([NexusIcons], #265) — 장식이라 CD=null, tint는 카드 콘텐츠색 상속
 * @param title 헤더 제목(없으면 헤더 행 생략)
 * @param trailing 헤더 우측 값 슬롯(컨디션 값·에너지 등)
 * @param content 본문
 */
@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    emphasis: CardEmphasis = CardEmphasis.Neutral,
    @DrawableRes titleIcon: Int? = null,
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (titleIcon != null) {
                                // 개념 아이콘 — 장식(제목이 의미 전달), tint는 콘텐츠색 상속 (#265)
                                Icon(
                                    painter = painterResource(titleIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(TITLE_ICON_DP.dp),
                                )
                            }
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                // 카드 제목은 구조 표지 (#224) — TalkBack이 제목 단위로 훑을 수 있게
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                    trailing?.invoke(this)
                }
            }
            content()
        }
    }
}

private const val TITLE_ICON_DP = 20

/**
 * 강조 → M3 컨테이너 롤. `internal`인 이유는 세 단계가 서로 다른 색인지 테스트가 확인하기
 * 때문이다(#263) — 히어로와 축하 카드가 같은 색이 되는 사고가 실제로 있었다.
 */
@Composable
internal fun CardEmphasis.colors(): CardColors = when (this) {
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
