package com.nexus.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nexus.app.R

/**
 * 로딩 스켈레톤 (#268, E16-18) — **콘텐츠와 닮은 자리**를 먼저 그려 완료 순간의 점프를 없앤다.
 *
 * 전에는 화면 한가운데 맨 `CircularProgressIndicator`(홈·성장)나 맨 `Text`(활동) 하나였다.
 * 그러면 로딩 중 화면 높이가 사실상 0이고, 완료 순간 카드 네 장이 한꺼번에 튀어나오며 레이아웃이
 * 크게 흔들린다. Health Connect 지연으로 로드가 수백ms~수초 걸리는 실조건에서 매번 겪는 일이다.
 *
 * ## 왜 shimmer 구동을 화면 하나로 모으는가
 *
 * 블록마다 `rememberInfiniteTransition`을 두면 한 화면에 무한 애니메이션이 열 개 넘게 돈다.
 * [LocalShimmerPhase]로 한 번만 구동해 모든 블록이 같은 위상을 읽는다 — 시각적으로도 그게 맞다
 * (블록마다 제각각 반짝이면 한 덩어리로 안 읽힌다). 렌더 낭비를 줄인 #246과 같은 규율이다.
 *
 * ## 접근성
 *
 * 스켈레톤은 **한 노드**다([SkeletonScreen]이 `clearAndSetSemantics`). 빈 블록 열두 개를 각각
 * 읽으면 스크린리더 사용자에겐 의미 없는 소음이고, 정작 "불러오는 중"이라는 사실이 전달되지 않는다.
 */
private val LocalShimmerPhase = compositionLocalOf<Float?> { null }

/**
 * 스켈레톤 화면 루트 — shimmer를 한 번 구동하고, 전체를 낭독 한 문장으로 묶는다.
 *
 * 시스템 '애니메이션 제거'(#228 판정 재사용)면 shimmer를 **기동하지 않는다** — 무한 반복은
 * duration 스케일링만으로 없앨 수 없어 `reduceMotion()`으로 분기해야 하는 쪽이다.
 */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val label = stringResource(R.string.a11y_loading)
    // null = 정적 — 리듀스드모션이면 위상 자체를 만들지 않는다(무한 반복은 duration 스케일링으로 못 없앤다)
    val phase = if (reduceMotion()) null else rememberShimmerPhase()
    CompositionLocalProvider(LocalShimmerPhase provides phase) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = label },
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg),
            content = content,
        )
    }
}

@Composable
private fun rememberShimmerPhase(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    // 무한 전환은 프레임 클럭 위에서 돌아 화면이 안 그려지면 자연히 멈춘다(#246의 티커와 다른 점).
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
    return phase
}

/**
 * 스켈레톤 카드 — [NexusCard]와 같은 모양·여백의 빈 껍데기.
 *
 * 진짜 카드를 재사용하지 않는 이유: `NexusCard`의 제목 슬롯은 문자열을 받고 `heading()` 시맨틱을
 * 단다. 스켈레톤은 읽을 제목이 없고 시맨틱도 루트 한 곳에만 있어야 한다.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardEmphasis.Neutral.colors()) {
        Column(
            Modifier.fillMaxWidth().padding(NexusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
            content = content,
        )
    }
}

/**
 * 자리표시 블록 — 실제 콘텐츠의 **덩어리 크기**를 흉내 낸다.
 *
 * @param widthFraction 가로 비율. 텍스트 줄은 1f가 아니라 0.4~0.7이어야 문단처럼 보인다.
 * @param height 블록 높이. 제목·본문·게이지가 각각 다른 높이를 갖는 게 형태 유사성의 핵심이다.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Int = SkeletonHeight.BODY) {
    val phase = LocalShimmerPhase.current
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(RoundedCornerShape(CORNER_DP.dp))
            .drawBehind { drawRect(skeletonBrush(phase, base, highlight, size.width)) },
    ) {}
}

/**
 * 블록이 실제로 칠해질 브러시 (#268 AC ③).
 *
 * 순수 함수인 이유는 **이게 검증 가능한 유일한 형태**이기 때문이다. shimmer는 크기도 위치도
 * 시맨틱도 바꾸지 않고 픽셀만 바꾸는데, 이 하네스(Robolectric)에서는 컴포즈 노드의 픽셀을
 * 캡처할 수 없다(`captureToImage`가 idle에 도달하지 못한다). 그리기 결정을 값으로 끌어내면
 * "리듀스드모션에서 정적 회색인가"를 그대로 단언할 수 있다.
 *
 * @param phase null이면 정적(리듀스드모션) — 하이라이트 없는 단색.
 */
internal fun skeletonBrush(phase: Float?, base: Color, highlight: Color, width: Float): Brush {
    if (phase == null) return SolidColor(base)
    // 밴드가 왼쪽 밖에서 오른쪽 밖까지 지나가게 한 폭만큼 여유를 준다
    val travel = width * (BAND_FRACTION * 2 + 1)
    val start = -width * BAND_FRACTION + travel * phase
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + width * BAND_FRACTION, 0f),
    )
}

/** 블록 높이 규격 — 실제 타이포·컴포넌트 높이에 맞춘 값. */
object SkeletonHeight {
    /** 카드 제목 한 줄. */
    const val TITLE = 20

    /** 본문 한 줄. */
    const val BODY = 14

    /** 게이지·진행바. */
    const val GAUGE = 12

    /** 히어로 스프라이트 자리. */
    const val HERO = 140

    /** 막대 차트 자리. */
    const val CHART = 96

    /** 목록 행 한 줄 — 라벨·값이 위아래 여백과 함께 차지하는 높이([NexusListRow] 기준). */
    const val ROW = 44
}

private const val SHIMMER_MS = 1_200
private const val BAND_FRACTION = 0.4f
private const val CORNER_DP = 6
