package com.nexus.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nexus.core.ReduceMotion
import kotlinx.coroutines.delay

/**
 * 콘텐츠 등장 스태거 (#268, E16-18) — 로딩이 끝난 카드들이 **위에서부터 차례로** 자리를 잡는다.
 *
 * 한꺼번에 나타나면 화면 전체가 한 번에 튀어 어디를 볼지 알 수 없다. 인덱스에 비례한 짧은 지연으로
 * 읽는 순서(위→아래)를 만들어 주면 같은 시간이 걸려도 더 빠르게 느껴진다.
 *
 * ## 계약: 최초 1회
 *
 * `LaunchedEffect(Unit)`이라 **컴포지션 인스턴스당 한 번만** 재생된다. 스크롤로는 재생되지 않는다 —
 * 이 화면들은 `Column` + `verticalScroll`이라 자식이 화면 밖으로 나가도 컴포지션에 남는다
 * (`LazyColumn`이었다면 재진입마다 다시 튀었을 것이고, 그때는 키가 안정적인 상태 보관이 필요하다).
 * 재컴포지션에도 [Animatable]이 `remember`로 살아 있어 진행값이 유지된다.
 *
 * ## 왜 graphicsLayer인가
 *
 * 진행값을 `graphicsLayer` 람다 안에서 읽으면 **그리기 단계에서만** 읽힌다 — 프레임마다
 * 재컴포지션·재측정이 도는 대신 레이어 속성만 갱신된다. 카드 네 장이 동시에 도는 연출이라
 * 이 차이가 저사양 기기에서 그대로 드러난다(#246과 같은 규율).
 *
 * @param index 화면 위에서부터의 순서. 지연은 [STEP_MS]씩 늘고 [MAX_DELAY_MS]에서 멈춘다 —
 *   상한이 없으면 카드가 늘어날수록 마지막 카드가 눈에 띄게 늦게 온다.
 */
@Composable
fun Modifier.staggeredAppearance(index: Int): Modifier {
    val scale = LocalMotionScale.current
    val reduced = ReduceMotion.isReduced(scale)
    // 상태와 이펙트를 **분기 위로** 올린다. 분기 아래에 두면 시스템 애니메이션을 껐다 켜는 순간
    // 새 Animatable(0f)이 만들어져, 이미 다 읽은 화면의 카드들이 통째로 사라졌다가 다시 올라온다
    // (`rememberSystemMotionScale`이 포그라운드 복귀마다 재조회하므로 실제로 지나는 경로다).
    val duration = motionDuration(NexusMotion.DURATION_MEDIUM)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (progress.value == 1f) return@LaunchedEffect // 이미 자리를 잡았으면 다시 재생하지 않는다
        if (reduced) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(NexusMotion.scaledDuration(staggerDelayMs(index).toInt(), scale).toLong())
        progress.animateTo(
            targetValue = 1f,
            // 부분 감속(0.5배·10배)도 살린다 — 이진값으로 뭉개면 탭 전환은 늘어지는데 카드만
            // 전속력으로 끝나 둘이 어긋난다(`NexusMotion` 계약)
            animationSpec = tween(duration, easing = NexusMotion.EmphasizedDecelerate),
        )
    }
    return graphicsLayer {
        alpha = progress.value
        // 아래에서 올라오며 나타난다 — 위에서 내려오면 이미 자리 잡은 위 카드와 겹쳐 보인다
        translationY = SLIDE_DP.dp.toPx() * (1f - progress.value)
    }
}

/** 인덱스 → 지연(ms). 상한이 있어 카드 수가 늘어도 마지막 카드가 뒤처지지 않는다. */
internal fun staggerDelayMs(index: Int): Long = (index.coerceAtLeast(0).toLong() * STEP_MS).coerceAtMost(MAX_DELAY_MS)

/** 항목 간 지연. 40ms보다 길면 순서가 아니라 '느림'으로 읽힌다. */
internal const val STEP_MS = 40L

/** 지연 상한 — 다섯 번째 항목부터는 더 늦추지 않는다. */
internal const val MAX_DELAY_MS = 160L

private const val SLIDE_DP = 12

/**
 * 등장 순서를 갖는 항목 하나 — 자식을 감싸 [staggeredAppearance]를 적용한다.
 *
 * 모든 카드가 `modifier`를 받지는 않아 감싸는 쪽을 택했다. 카드는 어차피 `fillMaxWidth`라
 * 래퍼가 레이아웃을 바꾸지 않는다.
 *
 * **축하·안내 오버레이에는 쓰지 않는다.** 그쪽은 각자의 등장 연출(`AnimatedVisibility`)을 갖고 있어
 * 두 연출이 겹치면 서로를 갉아먹는다 — 여기 대상은 매번 그려지는 본문 카드 스택이다.
 */
@Composable
fun StaggerItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().staggeredAppearance(index)) { content() }
}
