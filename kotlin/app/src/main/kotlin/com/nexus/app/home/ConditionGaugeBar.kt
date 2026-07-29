package com.nexus.app.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.VizColors
import com.nexus.app.ui.animatedGaugeProgress
import com.nexus.core.ConditionEngine
import com.nexus.core.ConditionGauge
import kotlin.math.roundToInt

/**
 * 컨디션 게이지 (#257, E16-7) — 스톡 LinearProgressIndicator 대신 브랜드 커스텀 시각화.
 * 소프트 바닥([ConditionEngine.SOFT_FLOOR])~[ConditionEngine.MAX] 구간을 3존([VizColors]) 착색으로
 * 그리고, 좌측 바닥 마커 + 캡션으로 "불퇴행"을 시각 증거화한다(무처벌 — 회복중도 적색 아님).
 *
 * 접근성: 색만으로 상태를 전달하지 않도록 존 라벨·값을 텍스트로 병기(캔버스 바는 장식). 값 텍스트는
 * ink(onSurface) — 존색 재사용 금지(AC). 휴식 모드는 캡션+테두리로 '유지돼요'를 별도 표시.
 */
@Composable
internal fun ConditionGaugeBar(condition: Double, restMode: Boolean, modifier: Modifier = Modifier) {
    val viz = VizColors.current
    val value = condition.roundToInt()
    // 컨디션 변화는 완만 감속 보간 (#262) — 하향도 툭 떨어지지 않게. 존 색은 목표값 기준(고정).
    val ratio = animatedGaugeProgress(ConditionGauge.fillRatio(condition).toFloat(), label = "condition")
    val zone = ConditionGauge.zoneOf(condition)
    val zoneColor = when (zone) {
        ConditionGauge.Zone.Recovering -> viz.conditionRecovering
        ConditionGauge.Zone.Stable -> viz.conditionStable
        ConditionGauge.Zone.Good -> viz.conditionGood
    }
    val zoneLabel = stringResource(
        when (zone) {
            ConditionGauge.Zone.Recovering -> R.string.home_condition_zone_recovering
            ConditionGauge.Zone.Stable -> R.string.home_condition_zone_stable
            ConditionGauge.Zone.Good -> R.string.home_condition_zone_good
        },
    )

    // 회전·테마 전환에도 열린 설명이 닫히지 않게 (#222 패턴)
    var explaining by rememberSaveable { mutableStateOf(false) }
    val openLabel = stringResource(R.string.condition_explain_open)

    Column(
        modifier
            .fillMaxWidth()
            // 게이지 전체가 설명 진입점 — 숫자만 있고 이유가 없으면 하락이 처벌로 읽힌다 (#223)
            .clickable(onClickLabel = openLabel, role = Role.Button) { explaining = true },
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_condition_title), style = MaterialTheme.typography.titleMedium)
            Text(
                // 값은 ink(onSurface) — 존색 재사용 금지(가독 대비, #257 AC)
                stringResource(R.string.home_condition_value, value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(GAUGE_HEIGHT.dp),
        ) {
            drawGauge(ratio, zoneColor, viz.conditionTrack, viz.floorMarker, restMode)
        }
        ConditionLegend(zoneColor, zoneLabel, restMode)
    }
    if (explaining) {
        ConditionExplainDialog(restMode = restMode, onDismiss = { explaining = false })
    }
}

/** 게이지 하단 범례 — 좌: 존 점(색)+라벨(ink), 우: 바닥/휴식 캡션. */
@Composable
private fun ConditionLegend(zoneColor: Color, zoneLabel: String, restMode: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 색 채널은 비텍스트 점(3:1로 충분) — 라벨 텍스트는 ink(가독 4.5:1↑, #257 리뷰):
            // 색이 상태의 유일 채널이 되지 않게 점+단어를 함께.
            Box(Modifier.size(ZONE_DOT.dp).clip(CircleShape).background(zoneColor))
            Text(
                zoneLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            if (restMode) {
                stringResource(R.string.home_condition_rest_caption)
            } else {
                stringResource(R.string.home_condition_floor_caption, ConditionEngine.SOFT_FLOOR.roundToInt())
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 트랙 → 채움 → 바닥 마커(→ 휴식 테두리) 순으로 그린다. 채움은 최소 한 캡 폭 이상 유지(ratio 0에서도
 * 소멸 없음 = 불퇴행). 좌측 바닥 마커는 "이 왼쪽 끝은 0이 아니라 바닥"임을 표식.
 *
 * 마커는 **트랙색으로 그려 채움에 파인 홈**처럼 보이게 한다 (#308). 채움 최소 폭이 한 캡(=높이)이라
 * 마커 위치(높이의 0.5배)는 **항상 채움 위**에 놓이는데, 마커를 단색 토큰으로 칠하면 3존 채움색 중
 * 어느 것과도 명도가 비슷해져(라이트 0.02·다크 0.01 차) 사실상 보이지 않았다. 트랙색은 모든 존과
 * 명도차 0.35 이상이라 존이 바뀌어도 대비가 유지된다.
 */
private fun DrawScope.drawGauge(
    ratio: Float,
    fillColor: Color,
    trackColor: Color,
    floorColor: Color,
    restMode: Boolean,
) {
    val h = size.height
    val w = size.width
    val radius = CornerRadius(h / 2f, h / 2f)
    drawRoundRect(color = trackColor, cornerRadius = radius, size = Size(w, h))
    // 최소 한 캡(=높이) 이상 → 바닥에서도 눈에 보이는 채움. w<h(0폭 첫 프레임 등)에서도 coerceIn 안전.
    val fillW = (w * ratio).coerceIn(minOf(h, w), w)
    drawRoundRect(color = fillColor, cornerRadius = radius, size = Size(fillW, h))
    // 바닥 마커 — 좌측 캡 안쪽 짧은 세로 눈금(0이 아니라 바닥선). 채움에 파인 홈으로 (#308)
    val markerX = h * FLOOR_MARKER_X_FACTOR
    drawLine(
        color = trackColor,
        start = Offset(markerX, h * FLOOR_MARKER_INSET),
        end = Offset(markerX, h * (1f - FLOOR_MARKER_INSET)),
        strokeWidth = h * FLOOR_MARKER_WIDTH,
    )
    // 휴식 모드 — 전체 트랙에 '유지' 테두리(멈춰 있음의 시각 신호)
    if (restMode) {
        drawRoundRect(
            color = floorColor,
            cornerRadius = radius,
            size = Size(w, h),
            style = Stroke(width = h * REST_BORDER_WIDTH),
        )
    }
}

private const val GAUGE_HEIGHT = 14
private const val ZONE_DOT = 8
private const val FLOOR_MARKER_X_FACTOR = 0.5f
private const val FLOOR_MARKER_INSET = 0.22f
private const val FLOOR_MARKER_WIDTH = 0.14f
private const val REST_BORDER_WIDTH = 0.14f
