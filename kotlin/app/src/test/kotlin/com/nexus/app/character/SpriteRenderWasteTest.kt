package com.nexus.app.character

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
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
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `프레임이 도는 동안 res id 해석이 늘지 않는다`() {
        val before = CharacterAssets.resolveCount.get()
        composeRule.setContent {
            NexusTheme { CharacterComposer.CharacterSprite("walk", Modifier.size(SPRITE_DP.dp)) }
        }
        // 표는 IO 디스패처에서 읽히므로 해석이 끝날 때까지 기다린다 — 여기서 서두르면 0을 재고
        // "늘지 않았다"가 공허하게 참이 된다
        composeRule.waitUntil { CharacterAssets.resolveCount.get() > before }
        val settled = CharacterAssets.resolveCount.get()

        tick(TICKS)

        // 음성: 프레임 루프 안에서 해석하면 틱 수만큼 늘어난다
        assertEquals(settled, CharacterAssets.resolveCount.get(), "프레임마다 res id를 다시 해석하고 있다")
        // 양성 대조: 실제로 스프라이트가 그려졌는가. 없으면 위 단언은 0 == 0이라 늘 참이다
        assertTrue(settled > before, "해석이 한 번도 일어나지 않았다 — 화면이 뜨지 않았다는 뜻")
    }

    // ── AC ② 비가시 정지 ──

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun renderTicker(owner: TestOwner, motionScale: Float = 1f, anim: AnimationState = WALK) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalMotionScale provides motionScale,
            ) {
                BasicText("$FRAME_PREFIX${rememberSpriteFrame(anim, "walk")}")
            }
        }
    }

    /** 모션 스케일을 **도중에** 바꿀 수 있는 하네스 — 토글 계약(#228)은 정적 값으로는 못 본다. */
    private fun renderTogglableTicker(owner: TestOwner): MutableFloatState {
        val scale = mutableFloatStateOf(1f)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalMotionScale provides scale.floatValue,
            ) {
                BasicText("$FRAME_PREFIX${rememberSpriteFrame(WALK, "walk")}")
            }
        }
        return scale
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

        // 티커가 계속 돌았다면 홀수 틱만큼 흘러 0이 된다
        assertFrame(1)
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

    /**
     * 기준이 RESUMED면 여기서 캐릭터가 **보이는 채로** 멈춘다. 분할 화면에서 다른 창을 만지면
     * 이 액티비티는 화면에 그대로 있으면서 STARTED로 내려가는데, 절약분은 전부 그 아래(백그라운드)에
     * 있으므로 STARTED에서 멈출 이유가 없다 — 사용자에겐 죽은 펫으로 읽힌다(#217).
     */
    @Test
    fun `보이지만 포커스가 없을 때는 계속 돈다`() {
        val owner = TestOwner()
        renderTicker(owner)
        moveTo(owner, Lifecycle.State.RESUMED)
        tick()
        assertFrame(1)

        moveTo(owner, Lifecycle.State.STARTED)
        tick()

        assertFrame(0) // 2프레임 루프 — 계속 돌았다면 뒤집힌다
    }

    /**
     * #228의 진짜 계약은 "리듀스드모션이면 안 돈다"가 아니라 **"켜는 순간 프레임 0으로 돌아간다"**이다.
     * 걷는 중간 자세로 굳으면 프레임 0을 쓰는 위젯과 어긋난다(#228 리뷰). 이 티켓이 그 코드를
     * `rememberSpriteFrame`으로 옮겼으므로 여기서 다시 못박는다 — 정적 0f로 시작하는 테스트는
     * 토글을 한 번도 건너지 않아 이 계약을 보지 못한다.
     */
    @Test
    fun `움직이는 도중 애니메이션 제거를 켜면 프레임 0으로 돌아간다`() {
        val owner = TestOwner()
        val scale = renderTogglableTicker(owner)
        moveTo(owner, Lifecycle.State.RESUMED)
        tick()
        assertFrame(1)

        composeRule.runOnIdle { scale.floatValue = 0f }
        composeRule.waitForIdle()

        assertFrame(0)
    }

    /**
     * 비루프 연출은 마지막 프레임에 멈추고, 화면을 나갔다 와도 되감기지 않아야 한다
     * (일회성 축하가 복귀마다 재생되면 안 된다). 지금 표엔 비루프 상태가 없지만 KDoc이 약속한 계약이라
     * 여기서 고정한다 — 첫 일회성 연출이 들어오는 날 깨지면 원인을 찾기 어렵다.
     */
    @Test
    fun `비루프는 마지막 프레임에 멈추고 복귀해도 되감기지 않는다`() {
        val owner = TestOwner()
        renderTicker(owner, anim = ONE_SHOT)
        moveTo(owner, Lifecycle.State.RESUMED)

        tick(TICKS)
        assertFrame(ONE_SHOT.frames - 1)

        moveTo(owner, Lifecycle.State.CREATED)
        moveTo(owner, Lifecycle.State.RESUMED)
        tick(TICKS)

        assertFrame(ONE_SHOT.frames - 1)
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

        // Bitmap은 equals를 재정의하지 않으므로 이건 **참조** 비교다 — 키 충돌만 잡는다는 뜻이고,
        // 픽셀이 다른지는 아래 `비어 있지 않다`가 따로 본다
        assertNotSame(idle, walk, "상태가 달라도 같은 비트맵을 돌려주면 위젯이 안 바뀐다")
    }

    /**
     * 캐시가 **빈 그림을 영구히 붙잡지 않는지**. 해석에 실패한 레이어는 투명한 비트맵을 만드는데,
     * 그걸 캐시하면 위젯 캐릭터가 프로세스가 죽을 때까지 빈 구멍으로 남는다.
     */
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `합성된 비트맵이 비어 있지 않다`() {
        CharacterComposer.clearBitmapCache()

        val bitmap = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0), SPRITE_PX)

        val pixels = IntArray(SPRITE_PX * SPRITE_PX)
        bitmap.getPixels(pixels, 0, SPRITE_PX, 0, 0, SPRITE_PX, SPRITE_PX)
        assertTrue(pixels.any { it != 0 }, "투명한 비트맵이 나왔다 — 스프라이트가 그려지지 않았다")
    }

    @Test
    fun `해석 못 한 레이어가 섞이면 캐시하지 않는다`() {
        CharacterComposer.clearBitmapCache()

        val first = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0, "no_such_layer" to 0), SPRITE_PX)
        val second = CharacterComposer.composeFrameBitmap(context, listOf("idle" to 0, "no_such_layer" to 0), SPRITE_PX)

        // 부분 렌더는 그대로 돌려주되(빈 위젯보다 낫다) 굳히지는 않는다 — 다음 갱신이 복구할 길을 남긴다
        assertNotSame(first, second, "불완전한 합성이 캐시에 박혔다 — 복구 경로가 사라진다")
    }

    /**
     * 비트맵 캐시가 안전한 **전제**를 고정한다.
     *
     * 캐시 키에 설정(다크 모드·밀도)이 없다. 지금은 캐릭터 드로어블이 설정 비의존이라 맞지만,
     * 누군가 `drawable-night/character_idle_0.xml`을 추가하는 순간 다크 모드에서 낮 스프라이트가
     * 프로세스가 죽을 때까지 남는다 — 캐시가 조용히 틀린 그림을 돌려주는 부류의 결함이라
     * 화면을 봐도 원인이 안 보인다. 그 전제가 깨지는 순간을 여기서 잡는다.
     */
    @Test
    fun `캐릭터 드로어블에 런타임 설정별 변형이 없다`() {
        val res = File(File("..").canonicalFile, "app/src/main/res")
        // 경로가 어긋나면 listFiles()가 null → orEmpty() → 빈 목록 → 무조건 통과한다.
        // 이 가드가 조용히 무력해지는 걸 막는 게 이 두 줄이다(모듈 이동·작업 디렉터리 변경).
        assertTrue(res.isDirectory, "리소스 경로가 어긋났다: $res")
        assertTrue(File(res, "drawable/character_idle_0.xml").isFile, "스캔 기준 파일이 없다 — 경로를 확인하세요")

        val variants = res.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("drawable-") && !DENSITY_ONLY.matches(it.name) }
            .flatMap { dir -> dir.listFiles().orEmpty().map { "${dir.name}/${it.name}" } }
            .filter { it.substringAfter('/').startsWith("character_") }

        assertTrue(
            variants.isEmpty(),
            "런타임 설정별 캐릭터 드로어블이 생겼다: $variants — composeFrameBitmap 캐시 키에 설정을 넣어야 한다",
        )
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

        /**
         * **홀수여야 한다.** 2프레임 루프에서 짝수 번 흘리면 프레임이 제자리로 돌아와,
         * 티커가 계속 돌아도 "멈췄다"는 단언이 통과한다 — 처음 쓴 10이 실제로 그랬다.
         */
        const val TICKS = 9
        const val FRAME_PREFIX = "frame="

        /** 밀도 버킷은 캐시에 무해하다 — 기기마다 고정이고 합성은 명시 크기로 그린다. */
        val DENSITY_ONLY = Regex("drawable-(l|m|h|x{1,3}h|no|any)dpi(-v\\d+)?")
        val WALK = AnimationState(frames = 2, frameDurationMs = WALK_FRAME_MS)
        val ONE_SHOT = AnimationState(frames = 3, frameDurationMs = WALK_FRAME_MS, loop = false)
    }
}
