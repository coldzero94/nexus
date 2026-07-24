package com.nexus.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.core.XpEngine
import com.nexus.core.XpGaugeScale

/**
 * 오늘 XP 진행 게이지 (#259, E16-9) — 성장 [TodayXpCard] 헤더·홈 TodaySummaryCard가 공유하는 단일
 * 컴포넌트. 오늘 XP를 하루 소프트 니([XpEngine.DAILY_KNEE]=200)와 하드캡(300) 스케일 위에 그린다.
 *
 * 니는 '벽/한도'가 아니라 '여기부터 천천히 쌓여요' 지점 — 니 이후 트랙을 [VizColors.xpBonusTrack]
 * (로즈틴트)로 구분하고 마커·긍정 캡션으로 무처벌 곡선을 시각화한다(#215 주간목표와 무중복).
 * 값·라벨은 호스트(카드 헤더/요약행)가 ink로 제공 — 이 컴포넌트는 진행 바+캡션만.
 */
@Composable
internal fun XpGauge(todayXp: Int, modifier: Modifier = Modifier) {
    val viz = VizColors.current
    val kneeFrac = XpGaugeScale.kneeFraction()
    val fillFrac = XpGaugeScale.fillFraction(todayXp)
    val reached = XpGaugeScale.reachedKnee(todayXp)
    val knee = XpEngine.DAILY_KNEE.toInt()
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val caption = if (reached) {
        stringResource(R.string.xp_gauge_over_knee)
    } else {
        stringResource(R.string.xp_gauge_to_knee, knee, (knee - todayXp).coerceAtLeast(0))
    }
    val cd = stringResource(R.string.xp_gauge_desc, todayXp, knee)

    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = cd },
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(GAUGE_HEIGHT.dp)
                .clip(RoundedCornerShape(percent = HALF_PERCENT)),
        ) {
            val h = size.height
            val w = size.width
            val kneeX = w * kneeFrac
            val fillX = w * fillFrac
            // 기본 트랙 → 니 이후 보너스 트랙(로즈틴트) → 채움 → 니 마커 순(마커가 채움 위에 보이게)
            drawRect(viz.xpTrack, size = Size(w, h))
            drawRect(viz.xpBonusTrack, topLeft = Offset(kneeX, 0f), size = Size(w - kneeX, h))
            if (fillX > 0f) drawRect(viz.xp, size = Size(fillX, h))
            drawLine(
                color = markerColor,
                start = Offset(kneeX, 0f),
                end = Offset(kneeX, h),
                strokeWidth = h * MARKER_WIDTH,
            )
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val GAUGE_HEIGHT = 12
private const val HALF_PERCENT = 50
private const val MARKER_WIDTH = 0.16f
