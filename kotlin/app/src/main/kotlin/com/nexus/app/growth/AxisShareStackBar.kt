package com.nexus.app.growth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.VizColors
import com.nexus.core.ActivityType
import com.nexus.core.AxisSegment
import com.nexus.core.AxisShareBar

/**
 * 성향 비중 스택 바 (#263, E16-13) — 스톡 `LinearProgressIndicator` 3줄을 **하나의 구성 바**로.
 *
 * ## 왜 한 바인가
 *
 * 3줄로 나누면 각 축의 절대 비율은 읽히지만 **구성**이 안 읽힌다. "나는 걷기 중심인가 근력 중심인가"는
 * 세 값을 눈으로 합산해야 알 수 있는 정보였고, 색이 전부 제네릭 회색이라 '직업 성향'이라는 정체성
 * 요소가 시각적으로 죽어 있었다. 한 바에 이어 붙이면 구성이 그 자체로 그림이 된다.
 *
 * ## 색만으로 구분하지 않는다
 *
 * 세그먼트 아래에 **라벨 + 퍼센트를 항상 병기**한다([AxisLegend]). 카테고리 색은 CVD 시뮬레이션에서
 * 인접쌍이 분리되는지 검증하지만([com.nexus.app.ui.VizColorsContrastTest]), 색이 1차 인코딩이면
 * 안 되는 게 원칙이라 라벨을 2차가 아니라 **동등한 채널**로 둔다.
 *
 * ## 지배 성향은 색이 아니라 **높이**로 강조한다
 *
 * 처음엔 비지배 세그먼트를 알파로 물러나게 했는데, 그게 이 티켓이 고친 문제를 되살렸다: 알파를
 * 트랙 위에 얹으면 실제로 칠해지는 색이 토큰이 아니라 **혼합색**이고, 걷기 0.72알파는 우리가
 * 방금 걷어낸 옛 색(#8A6D2E)으로 되돌아가 적록 색약에서 달리기와 다시 붕괴했다. `walkingMuted`가
 * 고정 톤으로 정의된 이유가 정확히 이것이다(#258: "알파 감쇠는 라이트에서 3:1 붕괴").
 *
 * 그래서 색은 토큰 그대로 쓰고, 비지배 세그먼트만 위아래를 살짝 깎아 **낮은 띠**로 만든다.
 * 색 채널을 건드리지 않으므로 CVD 검증이 화면에 칠해지는 색과 정확히 같은 대상을 본다.
 *
 * ## 접근성
 *
 * 바는 시맨틱에서 완전히 지운다 — Canvas라 읽을 게 없고, 값은 **아래 범례가 이미 라벨과 함께**
 * 갖고 있다. 바에 구성 문장을 또 붙이면 같은 값이 두 번 들린다(#258 리뷰에서 정한 규칙).
 */
@Composable
internal fun AxisShareStackBar(axisShares: Map<ActivityType, Double>, modifier: Modifier = Modifier) {
    val segments = AxisShareBar.segments(axisShares)
    val drawable = AxisShareBar.drawable(axisShares)
    val colorOf = axisColors()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
        if (drawable.isEmpty()) {
            // 데이터가 없을 때 빈 바를 그리면 '0%인 상태'가 아니라 '깨진 바'로 보인다 (#213 정합)
            Text(
                stringResource(R.string.growth_affinity_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StackBar(drawable, colorOf)
        }
        AxisLegend(segments, colorOf)
    }
}

/**
 * 스택 바 본체.
 *
 * 세그먼트를 **각각 캡슐로** 그리지 않는다. 그러면 접점마다 두 캡의 곡선이 서로 등을 돌려 바 높이만큼
 * 벌어진 나비넥타이 구멍이 생긴다 — 2px 구획선을 만들려던 자리에 15dp 짜리 얼룩이 남는다.
 * 대신 **직각 사각형으로 맞대고**(butt joint) 바 전체를 한 번 클립해 바깥 모서리만 둥글게 한다.
 */
@Composable
private fun StackBar(drawable: List<AxisSegment>, colorOf: Map<ActivityType, Color>) {
    val track = VizColors.current.conditionTrack
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            // Canvas라 읽을 게 없고 값은 범례가 갖고 있다 — 문장을 또 붙이면 이중 낭독이 된다 (#258 규칙)
            .clearAndSetSemantics {},
    ) {
        // 트랙을 먼저 깐다 — 갭 사이로 보이는 색이 서피스가 아니라 트랙이어야
        // 바가 하나의 물체로 읽힌다(갭이 '틈'이 아니라 '구획선'이 된다)
        drawRect(color = track)

        val gap = GAP.dp.toPx()
        // 갭은 세그먼트 사이에만 들어간다 — 개수는 (n - 1)
        val available = (size.width - gap * (drawable.size - 1)).coerceAtLeast(0f)
        var x = 0f
        drawable.forEach { segment ->
            val width = available * segment.fraction.toFloat()
            // 지배 성향은 바 전체 높이, 나머지는 위아래를 깎아 낮은 띠로 — 색은 토큰 그대로다
            val inset = if (segment.dominant) 0f else size.height * SUBORDINATE_INSET
            drawRect(
                color = colorOf.getValue(segment.type),
                topLeft = Offset(x, inset),
                size = Size(width, size.height - inset * 2),
            )
            x += width + gap
        }
    }
}

/**
 * 범례 — **0%인 축도 남긴다.** 목록에서 사라지면 "이 축은 0"과 "이 축이 없다"가 구분되지 않는다.
 *
 * 지배 성향은 굵기로 한 번 더 표시한다. 바의 높이와 중복이지만, 색을 못 보는 경우에 위계가
 * 완전히 사라지지 않게 하는 것이 목적이다.
 */
@Composable
private fun AxisLegend(segments: List<AxisSegment>, colorOf: Map<ActivityType, Color>) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
        segments.forEach { segment ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(SWATCH.dp)) {
                    drawRoundRect(
                        color = colorOf.getValue(segment.type),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.growth_axis_legend_format,
                        stringResource(segment.type.labelRes()),
                        segment.percent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (segment.dominant) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun axisColors(): Map<ActivityType, Color> {
    val viz = VizColors.current
    return mapOf(
        ActivityType.WALKING to viz.walking,
        ActivityType.RUNNING to viz.running,
        ActivityType.STRENGTH to viz.strength,
    )
}

/** 바 높이 — 세그먼트 라운딩이 보일 만큼은 두껍고, 카드 안에서 히어로를 누르지 않을 만큼 얇게. */
private const val BAR_HEIGHT = 14

/**
 * 세그먼트 간 갭(dp). 완료 기준은 "2px"이지만 dp로 잡는다 — raw px은 3배 밀도에서 0.67dp가 되어
 * 구획선이 서브픽셀로 사라진다(밀도별로 다르게 보이는 게 더 나쁘다).
 */
private const val GAP = 2

/** 비지배 세그먼트의 위아래 깎임 비율 — 색 채널을 건드리지 않고 높이로만 위계를 준다. */
private const val SUBORDINATE_INSET = 0.22f

/** 범례 색 스와치 크기. */
private const val SWATCH = 10
