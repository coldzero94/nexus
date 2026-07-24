package com.nexus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.app.R

/**
 * NEXUS 워드마크 락업 (#261, E16-11) — 제품 마크([R.drawable.nexus_mark], 상승 화살표) + 워드마크
 * 글자("NEXUS", 시스템 폰트 Bold·와이드 트래킹) 조합. 색은 브랜드 primary(라이트/다크 AA).
 * 온보딩 첫 화면 등 브랜드 최전선에 배치. 마크는 캐릭터(#66)와 구분되는 기하 심볼.
 */
@Composable
fun NexusWordmark(modifier: Modifier = Modifier) {
    val brand = MaterialTheme.colorScheme.primary
    val name = stringResource(R.string.app_name)
    Row(
        // 마크+글자를 하나의 이름(앱 이름)으로 낭독 — 아이콘은 장식, 텍스트가 접근성 이름
        modifier.clearAndSetSemantics { contentDescription = name },
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.nexus_mark),
            contentDescription = null,
            tint = brand,
            modifier = Modifier.size(WORDMARK_MARK_DP.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = WORDMARK_TRACKING_SP.sp,
            color = brand,
        )
    }
}

private const val WORDMARK_MARK_DP = 30
private const val WORDMARK_TRACKING_SP = 2.0
