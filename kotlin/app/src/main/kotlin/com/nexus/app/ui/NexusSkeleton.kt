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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
 *
 * 위상 공급 지점이 `internal`인 이유는 **리듀스드모션에서 무한 전환이 정말 안 도는지**를 테스트가
 * 이 값으로만 확인할 수 있기 때문이다 — shimmer는 크기·위치·시맨틱을 바꾸지 않고, 무한 전환이
 * 돌아도 `waitForIdle()`은 이 하네스에서 그냥 돌아온다(리뷰가 그 함정을 실증했다).
 */
internal val LocalShimmerPhase = compositionLocalOf<State<Float>?> { null }

/**
 * 스켈레톤 화면 루트 — shimmer를 한 번 구동하고, 전체를 낭독 한 문장으로 묶는다.
 *
 * 시스템 '애니메이션 제거'(#228 판정 재사용)면 shimmer를 **기동하지 않는다** — 무한 반복은
 * duration 스케일링만으로 없앨 수 없어 `reduceMotion()`으로 분기해야 하는 쪽이다.
 */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val label = stringResource(R.string.a11y_loading)
    // null = 정적 — 리듀스드모션이면 위상 자체를 만들지 않는다(무한 반복은 duration 스케일링으로 못 없앤다).
    // **State를 그대로 넘긴다**: 여기서 `.value`를 읽으면 이 컴포저블이 매 프레임 재구성되고, 그러면
    // 위의 stringResource(= Resources.getString)까지 프레임마다 돈다. 값이 아니라 참조를 내려보내면
    // 구독은 실제로 그리는 곳([SkeletonBlock]의 drawBehind)에서만 일어난다 (#246 규율).
    val phase = if (reduceMotion()) null else rememberShimmerPhase()
    CompositionLocalProvider(LocalShimmerPhase provides phase) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = label
                    // 로딩은 사용자 조작 없이 나타났다 사라진다 — live region이 없으면 재시도로 다시
                    // 로딩에 들어간 것도, 로딩이 끝난 것도 스크린리더에 아무 말 없이 지나간다
                    // (`docs/A11Y-TALKBACK.md`의 비동기 도착 콘텐츠 규칙)
                    liveRegion = LiveRegionMode.Polite
                },
            verticalArrangement = Arrangement.spacedBy(NexusSpacing.lg),
            content = content,
        )
    }
}

@Composable
private fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton")
    // 무한 전환은 프레임 클럭 위에서 돌아 화면이 안 그려지면 자연히 멈춘다(#246의 티커와 다른 점).
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
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
    val (base, highlight) = skeletonColors(MaterialTheme.colorScheme)
    // 밴드 브러시는 **크기 고정**이라 한 번 만들면 프레임마다 재사용된다 — 매 프레임 새 Brush를 만들면
    // ShaderBrush의 크기 기반 셰이더 캐시가 한 번도 안 맞아 네이티브 그라디언트가 계속 새로 생긴다.
    val band = remember(highlight) {
        Brush.horizontalGradient(listOf(Color.Transparent, highlight, Color.Transparent))
    }
    Column(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(RoundedCornerShape(CORNER_DP.dp))
            .drawBehind {
                drawRect(SolidColor(base))
                // 위상 구독이 여기서만 일어난다 — 재구성이 아니라 다시 그리기만 돈다
                val p = phase?.value ?: return@drawBehind
                val bandWidth = size.width * BAND_FRACTION
                translate(left = skeletonBandLeft(p, size.width, bandWidth)) {
                    drawRect(band, size = Size(bandWidth, size.height))
                }
            },
    ) {}
}

/**
 * 스켈레톤 블록의 바탕·하이라이트 색 (#268).
 *
 * `surface`를 하이라이트로 쓰면 **다크에서 뒤집힌다** — 라이트에서는 `surfaceVariant`보다 밝지만
 * 다크에서는 훨씬 어두워서, 반짝임이 아니라 카드에 뚫린 검은 구멍이 지나가는 것처럼 보인다.
 * `onSurface`의 알파 두 단계로 잡으면 두 스킴 모두에서 "하이라이트가 바탕보다 카드와 더 대비된다"가
 * 자동으로 성립한다. `SkeletonContrastTest`가 그 순서를 두 스킴에서 고정한다.
 */
internal fun skeletonColors(scheme: ColorScheme): Pair<Color, Color> =
    scheme.onSurface.copy(alpha = BASE_ALPHA) to scheme.onSurface.copy(alpha = HIGHLIGHT_ALPHA)

/**
 * 밴드의 왼쪽 좌표 (#268) — 위상 0에서 왼쪽 밖, 1에서 오른쪽 밖.
 *
 * 순수 함수인 이유는 shimmer가 **그리기만** 바꿔 시맨틱으로도 픽셀로도(이 하네스에선
 * `captureToImage`가 idle에 도달하지 못한다) 관측할 수 없기 때문이다.
 */
internal fun skeletonBandLeft(phase: Float, width: Float, bandWidth: Float): Float =
    -bandWidth + (width + bandWidth * 2) * phase

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
private const val BASE_ALPHA = 0.10f
private const val HIGHLIGHT_ALPHA = 0.22f
private const val CORNER_DP = 6
