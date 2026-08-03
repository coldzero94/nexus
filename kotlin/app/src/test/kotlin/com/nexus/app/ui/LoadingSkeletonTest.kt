package com.nexus.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로딩 스켈레톤 (#268, E16-18).
 *
 * 이 티켓이 없애려는 건 **완료 순간의 레이아웃 점프**다. 그래서 검증도 "무엇이 보이는가"가 아니라
 * ① 로딩 중 화면이 실제 콘텐츠만한 높이를 차지하는가 ② 스크린리더에 한 문장으로 들리는가
 * ③ 애니메이션 제거를 켜면 움직임이 사라지는가 — 세 가지다.
 */
@RunWith(RobolectricTestRunner::class)
class LoadingSkeletonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * `NexusTheme`이 [LocalMotionScale]을 **시스템 값으로 다시 공급**하므로(NexusTheme.kt:41)
     * 테마 바깥에서 주입하면 덮어써진다 — 실제로 이 파일의 리듀스드모션 테스트들이 그렇게 무력했다.
     * 반드시 테마 안쪽에서 덮어써야 한다.
     */
    private fun render(motionScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            NexusTheme {
                CompositionLocalProvider(LocalMotionScale provides motionScale) { content() }
            }
        }
    }

    // ── AC ① 콘텐츠와 닮은 형태 ──

    /**
     * 이게 이 티켓의 전부다. 전에는 로딩 분기가 중앙 스피너 하나여서 화면 높이가 사실상 0이었고,
     * 완료 순간 카드 네 장이 한꺼번에 밀려 들어왔다. 스켈레톤이 **높이를 미리 차지**해야 점프가 준다.
     */
    @Test
    fun `홈 스켈레톤이 콘텐츠만한 높이를 차지한다`() {
        render { HomeSkeleton() }

        val height = composeRule.onNodeWithContentDescription(loading()).fetchSemanticsNode().size.height
        assertTrue(height > MIN_SKELETON_PX, "스켈레톤 높이가 ${height}px — 자리를 잡지 못하고 있다")
    }

    @Test
    fun `성장 스켈레톤이 콘텐츠만한 높이를 차지한다`() {
        render { GrowthSkeleton() }

        val height = composeRule.onNodeWithContentDescription(loading()).fetchSemanticsNode().size.height
        assertTrue(height > MIN_SKELETON_PX, "스켈레톤 높이가 ${height}px")
    }

    @Test
    fun `활동 스켈레톤이 콘텐츠만한 높이를 차지한다`() {
        render { ActivitySkeleton() }

        val height = composeRule.onNodeWithContentDescription(loading()).fetchSemanticsNode().size.height
        assertTrue(height > MIN_SKELETON_PX, "스켈레톤 높이가 ${height}px")
    }

    /**
     * 탭마다 **다른 형태**여야 한다. 셋을 같은 스켈레톤으로 바꿔치기해도 위 세 테스트는 전부 통과한다 —
     * 사실상 같은 테스트를 세 번 쓴 셈이었다(리뷰 지적). 카드 구성이 실제로 다른지 본다.
     */
    @Test
    fun `탭마다 스켈레톤 형태가 다르다`() {
        // 셋을 한 컴포지션에 나란히 세워 각자의 고유 높이를 잰다(스크롤 컨테이너라 잘리지 않는다).
        // setContent는 테스트당 한 번만 부를 수 있어 이렇게 묶는다.
        composeRule.setContent {
            NexusTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeSkeleton()
                    GrowthSkeleton()
                    ActivitySkeleton()
                }
            }
        }
        composeRule.waitForIdle()

        val heights = composeRule.onAllNodesWithContentDescription(loading())
            .fetchSemanticsNodes()
            .map { it.size.height }

        assertEquals(3, heights.size)
        assertEquals(heights.distinct().size, heights.size, "탭 스켈레톤이 서로 같다: $heights")
    }

    // ── 접근성: 한 노드 ──

    private fun loading() = context.getString(R.string.a11y_loading)

    /**
     * 빈 블록 열두 개가 각각 읽히면 스크린리더 사용자에겐 소음이고, 정작 "불러오는 중"이라는
     * 사실은 전달되지 않는다. 루트 한 문장으로 묶는다 (`docs/A11Y-TALKBACK.md`).
     */
    @Test
    fun `스켈레톤은 한 문장으로 낭독된다`() {
        render { HomeSkeleton() }

        composeRule.onNodeWithContentDescription(loading()).assertIsDisplayed()
    }

    /**
     * 묶음이 **실제로 안쪽을 삼키는지**. 빈 블록만 넣으면 애초에 시맨틱이 없어 `semantics`든
     * `clearAndSetSemantics`든 결과가 같다 — 그래서 이 테스트는 낭독되는 자식을 일부러 심는다.
     * (처음 쓴 버전은 이 구분을 못 해 묶음을 풀어도 통과했다.)
     */
    @Test
    fun `스켈레톤 안의 낭독 대상은 밖으로 새지 않는다`() {
        render {
            SkeletonScreen {
                SkeletonBlock()
                Marker()
            }
        }

        composeRule.onNodeWithContentDescription(loading()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(MARKER).assertDoesNotExist()
    }

    // ── AC ③ 리듀스드모션 ──

    /**
     * shimmer 색 순서가 **두 스킴 모두에서** 성립하는지.
     *
     * 처음엔 하이라이트로 `surface`를 썼다. 라이트에서는 `surfaceVariant`보다 밝지만 다크에서는
     * 훨씬 어두워서, 반짝임이 아니라 **카드에 뚫린 검은 구멍**이 지나가는 것처럼 보였다. 다크 테마가
     * 갤럭시 기본값인 걸 생각하면 그대로 나갈 수 없는 결함이다. `onSurface` 알파 두 단계로 바꿔
     * "하이라이트가 바탕보다 카드와 더 대비된다"가 스킴과 무관하게 성립하게 했다.
     */
    @Test
    fun `하이라이트는 두 스킴 모두에서 바탕보다 대비가 크다`() {
        listOf("라이트" to NexusLightColors, "다크" to NexusDarkColors).forEach { (name, scheme) ->
            val (base, highlight) = skeletonColors(scheme)
            val card = scheme.surfaceContainerLow

            val baseGap = kotlin.math.abs(over(base, card).luminance() - card.luminance())
            val highlightGap = kotlin.math.abs(over(highlight, card).luminance() - card.luminance())

            assertTrue(highlightGap > baseGap, "$name: 하이라이트가 바탕보다 덜 도드라진다 — 밴드가 구멍처럼 보인다")
        }
    }

    /** 알파 합성 — 반투명 색을 카드 위에 얹었을 때 실제로 보이는 색. */
    private fun over(fg: Color, bg: Color): Color = Color(
        red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
        green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
        blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    )

    /**
     * 밴드가 왼쪽 밖에서 오른쪽 밖까지 지나가는지. shimmer는 그리기만 바꿔 시맨틱으로도
     * 픽셀로도(이 하네스에선 `captureToImage`가 idle에 도달하지 못한다) 관측할 수 없어,
     * 위치 산술을 순수 함수로 끌어내 고정한다.
     */
    @Test
    fun `밴드는 화면 밖에서 시작해 화면 밖으로 나간다`() {
        val width = 300f
        val band = width * 0.4f

        assertTrue(skeletonBandLeft(0f, width, band) <= -band, "시작이 화면 안이라 밴드가 튀어나온다")
        assertTrue(skeletonBandLeft(1f, width, band) >= width, "끝이 화면 안이라 밴드가 걸린 채 끝난다")
    }

    @Test
    fun `위상이 커지면 밴드가 오른쪽으로 간다`() {
        val width = 300f
        val band = width * 0.4f

        assertTrue(skeletonBandLeft(0.6f, width, band) > skeletonBandLeft(0.3f, width, band))
    }

    /**
     * 무한 애니메이션이 실제로 **기동되지 않는지**를 위상 공급값으로 본다.
     *
     * 처음엔 `waitForIdle()`이 타임아웃하지 않는 것으로 대신했는데, 이 하네스에서는 무한 전환이
     * 돌아도 idle이 그냥 돌아온다 — shimmer를 항상 켜도 통과하는 공허한 테스트였다(리뷰가 실증).
     */
    private fun shimmerPhase(motionScale: Float): Float? {
        var phase: State<Float>? = null
        render(motionScale = motionScale) {
            SkeletonScreen { phase = LocalShimmerPhase.current }
        }
        composeRule.waitForIdle()
        return phase?.value
    }

    @Test
    fun `애니메이션 제거면 무한 전환이 기동되지 않는다`() {
        assertNull(shimmerPhase(motionScale = 0f), "리듀스드모션인데 위상이 공급됐다 — shimmer가 돈다")
    }

    @Test
    fun `평소에는 위상이 공급된다`() {
        assertNotNull(shimmerPhase(motionScale = 1f), "shimmer가 아예 기동되지 않았다")
    }

    // ── AC ② 스태거 ──

    @Test
    fun `스태거 지연은 인덱스에 비례하고 상한에서 멈춘다`() {
        assertEquals(0L, staggerDelayMs(0))
        assertEquals(STEP_MS, staggerDelayMs(1))
        assertEquals(STEP_MS * 2, staggerDelayMs(2))
        assertEquals(MAX_DELAY_MS, staggerDelayMs(4))
        // 상한이 없으면 카드가 늘어날수록 마지막 카드가 눈에 띄게 늦게 온다
        assertEquals(MAX_DELAY_MS, staggerDelayMs(20))
    }

    @Test
    fun `음수 인덱스는 지연이 없다`() {
        assertEquals(0L, staggerDelayMs(-1))
    }

    /**
     * 리듀스드모션이면 지연도 이동도 없이 **즉시 최종 상태**여야 한다. 지연만 남기면 사용자는
     * 움직임 없이 '늦게 뜨는' 화면을 보게 되는데, 그건 애니메이션 제거의 취지가 아니다.
     *
     * 관측은 **루트 기준 위치**로 한다. 스태거는 `graphicsLayer`의 alpha·translationY만 바꿔
     * 측정 크기를 건드리지 않으므로, 높이를 재는 방식은 스태거가 돌아도 통과한다
     * (처음 쓴 버전이 실제로 그랬다). `positionInRoot`는 레이어 변환을 반영한다.
     */
    @Test
    fun `애니메이션 제거면 스태거 없이 즉시 자리를 잡는다`() {
        composeRule.mainClock.autoAdvance = false
        render(motionScale = 0f) {
            Column { StaggerItem(TAIL_INDEX) { Marker() } }
        }
        composeRule.waitForIdle()

        // 시간을 전혀 흘리지 않았는데도 제자리여야 한다
        assertEquals(0f, markerTop(), "리듀스드모션인데 아래에서 올라오는 중이다")
    }

    /** 양성 대조 — 평소에는 실제로 아래에서 올라온다(위 단언이 '늘 0'이라 통과한 게 아니다). */
    @Test
    fun `평소에는 아래에서 올라온다`() {
        composeRule.mainClock.autoAdvance = false
        render {
            Column { StaggerItem(TAIL_INDEX) { Marker() } }
        }
        composeRule.waitForIdle()

        assertTrue(markerTop() > 0f, "스태거가 시작 위치를 잡지 못했다")
    }

    /**
     * AC ②의 "최초 1회". **되돌리면 카드가 영영 안 보인다** — `remember`를 빼면 재구성마다 새
     * `Animatable(0f)`이 생기고 이펙트는 옛 인스턴스를 잡고 있어 alpha가 0에 굳는다. 그런데도
     * 지금까지 아무 테스트도 깨지지 않았다(리뷰 지적).
     *
     * 관측은 `onGloballyPositioned`의 **궤적**으로 한다. `fetchSemanticsNode().positionInRoot`는
     * `setContent` 직후 첫 프레임에만 믿을 수 있어(그 뒤엔 낡은 값을 계속 준다) 재생 여부 비교에
     * 쓰면 조용히 공허해진다 — 같은 함정을 세 번째로 밟지 않기 위한 방식이다.
     */
    @Test
    fun `자리를 잡은 뒤에는 재구성해도 다시 재생되지 않는다`() {
        val seen = mutableListOf<Float>()
        val bump = mutableIntStateOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NexusTheme {
                Column {
                    StaggerItem(0) {
                        Column(
                            Modifier
                                .width(MARKER_DP.dp)
                                .height((MARKER_DP + bump.intValue).dp)
                                .onGloballyPositioned { seen += it.positionInRoot().y }
                                .semantics { contentDescription = MARKER },
                        ) {}
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        assertTrue(seen.any { it > 0f }, "등장이 아예 재생되지 않았다 — 아래 단언이 공허해진다")
        assertEquals(0f, seen.last(), "등장이 끝나지 않았다")
        seen.clear()

        // 대상 자신을 재구성시킨다(부모만 흔들면 컴포즈가 건너뛰어 아무것도 증명하지 못한다)
        composeRule.runOnIdle { bump.intValue = 1 }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()

        assertTrue(seen.all { it == 0f }, "재구성만으로 등장이 다시 재생됐다: $seen")
    }

    private fun markerTop(): Float =
        composeRule.onNodeWithContentDescription(MARKER).fetchSemanticsNode().positionInRoot.y

    @Composable
    private fun Marker() {
        Column(
            Modifier
                .width(MARKER_DP.dp)
                .height(MARKER_DP.dp)
                .semantics { contentDescription = MARKER },
        ) {}
    }

    private companion object {
        /** 카드 몇 장 분량 — 중앙 스피너 하나(≈100px)와 확실히 구분되는 값. */
        const val MIN_SKELETON_PX = 400
        const val MARKER = "스태거 대상"
        const val MARKER_DP = 40
        const val TAIL_INDEX = 4
        const val SETTLE_MS = 2_000L
    }
}
