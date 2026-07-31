package com.nexus.app.character

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.ui.LocalMotionScale
import com.nexus.app.ui.NexusTheme
import com.nexus.core.AnimationState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 렌더 낭비 제거 (#246, E15-18) — 배터리·저사양 프레임 드랍.
 *
 * 여기서 고정하는 건 **횟수와 인스턴스 동일성**이다. 화면은 전과 똑같이 보이므로 스크린샷도
 * 시맨틱 단언도 회귀를 잡지 못한다: "매 프레임 리플렉션이 도는가", "백그라운드에서도 도는가",
 * "같은 비트맵을 다시 굽는가"는 셀 수 있어야만 검증된다.
 */
@RunWith(RobolectricTestRunner::class)
class SpriteRenderWasteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * 프레임 한 틱.
     *
     * 티커의 `delay`는 **컴포즈 프레임 클럭이 아니라 Robolectric 메인 루퍼**가 굴린다
     * (이펙트 코루틴이 `AndroidUiDispatcher` 위에 산다). `mainClock.advanceTimeBy`만 부르면
     * 티커가 선 채로 테스트가 조용히 통과한다 — 실제로 이 파일을 쓰면서 한 번 그렇게 됐다.
     */
    private fun tick(times: Int = 1) = repeat(times) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WALK_FRAME_MS))
        composeRule.mainClock.advanceTimeBy(WALK_FRAME_MS)
        composeRule.waitForIdle()
    }

    // ── AC ① 상태 진입 시 1회 해석 ──

    @Test
    fun `같은 이름은 한 번만 조회된다`() {
        var calls = 0
        val assets = CharacterAssets(context) {
            calls++
            0
        }

        repeat(5) { assets.frameResIdOrNull("idle", 0) }

        assertEquals(1, calls, "이름당 한 번이어야 한다 — 리플렉션이 반복되고 있다")
    }

    @Test
    fun `없는 이름도 캐시한다`() {
        // 폴백 경로(없는 프레임 → 기본 상태)가 매 프레임 헛조회를 반복하면 캐시의 의미가 없다
        var calls = 0
        val assets = CharacterAssets(context) {
            calls++
            0
        }

        repeat(3) { assertNull(assets.frameResIdOrNull("nothing_here", 0)) }

        assertEquals(1, calls)
    }

    @Test
    fun `이름이 다르면 각각 조회한다`() {
        var calls = 0
        val assets = CharacterAssets(context) {
            calls++
            0
        }

        assets.frameResIdOrNull("idle", 0)
        assets.frameResIdOrNull("idle", 1)
        assets.frameResIdOrNull("walk", 0)

        assertEquals(3, calls, "서로 다른 이름이 같은 캐시 항목으로 뭉갰다")
    }

    @Test
    fun `프레임 목록은 프레임마다 한 번씩 해석한다`() {
        var calls = 0
        val assets = CharacterAssets(context) {
            calls++
            0
        }

        val ids = assets.frameResIds("idle", 3)

        assertEquals(3, ids.size)
        assertEquals(3, calls)
    }

    /**
     * AC ①의 측정 가능한 형태 — **실제 화면**을 태우고 프레임을 돌린 뒤 조회 횟수를 본다.
     * 상태 해석이 프레임 루프 안에 있으면(예전 코드) 여기서 틱 수만큼 증가한다.
     */
    @Test
    fun `프레임이 도는 동안 res id 조회가 늘지 않는다`() {
        composeRule.setContent {
            NexusTheme { CharacterComposer.CharacterSprite("walk", Modifier.size(SPRITE_DP.dp)) }
        }
        // 표는 IO 디스패처에서 읽히므로 해석이 끝날 때까지 기다린다 — 여기서 서두르면 0을 재고
        // "조회가 늘지 않았다"가 공허하게 참이 된다
        composeRule.waitUntil { CharacterAssets.lookupCount.get() > 0 }
        val settled = CharacterAssets.lookupCount.get()

        tick(TICKS)

        assertEquals(settled, CharacterAssets.lookupCount.get(), "프레임마다 getIdentifier가 돌고 있다")
    }

    // ── AC ② 비가시 정지 ──

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun renderTicker(owner: TestOwner, motionScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalMotionScale provides motionScale,
            ) {
                BasicText("$FRAME_PREFIX${rememberSpriteFrame(WALK)}")
            }
        }
    }

    private fun assertFrame(expected: Int) = composeRule.onNodeWithText("$FRAME_PREFIX$expected").assertExists()

    /** 가시성 전환 — 상태 반영이 한 번의 idle을 더 타므로 설정 후 대기까지가 한 동작이다. */
    private fun moveTo(owner: TestOwner, state: Lifecycle.State) {
        composeRule.runOnIdle { owner.registry.currentState = state }
        composeRule.waitForIdle()
    }

    @Test
    fun `화면이 보이면 프레임이 돈다`() {
        val owner = TestOwner()
        renderTicker(owner)
        moveTo(owner, Lifecycle.State.RESUMED)

        tick()

        assertFrame(1)
    }

    /**
     * 이 티켓의 핵심. 홈을 열어둔 채 앱을 내리면 컴포지션은 살아 있고 `LaunchedEffect`도 그대로라
     * **아무도 보지 않는 화면의 프레임이 계속 갈린다.** RESUMED 아래로 내려가면 멈춰야 한다.
     */
    @Test
    fun `화면이 안 보이면 프레임이 멈춘다`() {
        val owner = TestOwner()
        renderTicker(owner)
        moveTo(owner, Lifecycle.State.RESUMED)
        tick()
        assertFrame(1)

        moveTo(owner, Lifecycle.State.CREATED)
        tick(TICKS)

        assertFrame(1) // 정지 중 열 번을 흘려보내도 그대로
    }

    @Test
    fun `다시 보이면 프레임이 이어진다`() {
        val owner = TestOwner()
        renderTicker(owner)
        moveTo(owner, Lifecycle.State.RESUMED)
        tick()
        moveTo(owner, Lifecycle.State.CREATED)
        tick(TICKS)

        moveTo(owner, Lifecycle.State.RESUMED)
        tick()

        assertFrame(0) // 2프레임 루프 — 1 다음은 0
    }

    /**
     * 정지 트리거가 둘이라는 게 계약이다(#228 리듀스드모션 / #246 비가시). 화면이 보여도
     * 리듀스드모션이면 돌지 않아야 한다 — 가시성 게이트를 넣으면서 이쪽을 삼키지 않았는지 본다.
     */
    @Test
    fun `리듀스드모션이면 보여도 돌지 않는다`() {
        val owner = TestOwner()
        renderTicker(owner, motionScale = 0f)
        moveTo(owner, Lifecycle.State.RESUMED)

        tick(TICKS)

        assertFrame(0)
    }

    // ── AC ④ 비트맵 캐시 ──

    @Test
    fun `같은 상태는 비트맵을 다시 굽지 않는다`() {
        CharacterComposer.clearBitmapCache()

        val first = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0), SPRITE_PX)
        val second = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0), SPRITE_PX)

        assertSame(first, second, "같은 요청에 168px ARGB를 새로 래스터화하고 있다")
    }

    @Test
    fun `상태가 다르면 따로 굽는다`() {
        CharacterComposer.clearBitmapCache()

        val idle = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0), SPRITE_PX)
        val walk = CharacterComposer.composeFrameBitmap(context, listOf("walk" to 0), SPRITE_PX)

        assertNotEquals(idle, walk, "상태가 달라도 같은 비트맵을 돌려주면 위젯이 안 바뀐다")
    }

    @Test
    fun `크기가 다르면 따로 굽는다`() {
        CharacterComposer.clearBitmapCache()

        val small = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0), SPRITE_PX / 2)
        val large = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0), SPRITE_PX)

        assertNotEquals(small.width, large.width)
    }

    private companion object {
        const val SPRITE_DP = 96
        const val SPRITE_PX = 168
        const val WALK_FRAME_MS = 350L
        const val TICKS = 10
        const val FRAME_PREFIX = "frame="
        val WALK = AnimationState(frames = 2, frameDurationMs = WALK_FRAME_MS)
    }
}
