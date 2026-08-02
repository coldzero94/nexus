package com.nexus.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
     * shimmer가 도는지는 **무엇이 칠해지는가**로 본다. 크기·위치·시맨틱이 전혀 안 바뀌는 연출이라
     * 시맨틱 트리로는 볼 수 없고, 이 하네스에선 컴포즈 노드의 픽셀도 캡처할 수 없다
     * (`captureToImage`가 idle에 도달하지 못한다). 그래서 그리기 결정을 순수 함수로 끌어냈다.
     */
    @Test
    fun `애니메이션 제거면 하이라이트 없는 단색을 칠한다`() {
        val brush = skeletonBrush(phase = null, base = BASE, highlight = HIGHLIGHT, width = WIDTH)

        assertEquals(SolidColor(BASE), brush, "리듀스드모션인데 그라디언트를 칠한다")
    }

    @Test
    fun `평소에는 하이라이트가 지나가는 그라디언트를 칠한다`() {
        val brush = skeletonBrush(phase = 0.5f, base = BASE, highlight = HIGHLIGHT, width = WIDTH)

        assertTrue(brush is LinearGradient, "shimmer가 단색으로 죽었다")
    }

    /** 위상이 바뀌면 브러시도 바뀐다 — 같으면 반짝임이 화면에서 멈춰 있다는 뜻이다. */
    @Test
    fun `위상이 다르면 그라디언트도 다르다`() {
        assertNotEquals(
            skeletonBrush(phase = 0f, base = BASE, highlight = HIGHLIGHT, width = WIDTH),
            skeletonBrush(phase = 0.5f, base = BASE, highlight = HIGHLIGHT, width = WIDTH),
        )
    }

    /**
     * 무한 애니메이션이 실제로 **기동되지 않는지**. 돌고 있으면 컴포지션이 idle에 도달하지 못해
     * `waitForIdle()`이 타임아웃한다 — 리듀스드모션에서 이 호출이 즉시 돌아온다는 것 자체가 단언이다.
     */
    @Test
    fun `애니메이션 제거면 무한 전환이 기동되지 않는다`() {
        render(motionScale = 0f) { HomeSkeleton() }

        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(loading()).assertIsDisplayed()
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
        val BASE = Color(0xFF888888)
        val HIGHLIGHT = Color(0xFFCCCCCC)
        const val WIDTH = 300f
        const val MARKER = "스태거 대상"
        const val MARKER_DP = 40
        const val TAIL_INDEX = 4
    }
}
