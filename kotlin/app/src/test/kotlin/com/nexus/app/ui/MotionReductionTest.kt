package com.nexus.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.nexus.app.character.breathTargetScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 모션 감축 계약 (#228) — **감사 결과 비어 있던 자리를 메운다** (#338).
 *
 * 프로덕션의 `reduceMotion()` 분기는 다섯 곳인데, 그중 [celebrationEnter]는 축하 카드 **다섯 장**
 * (레벨업·성향 변화·배지 해금·첫 XP·원정 개봉)의 등장 연출을 한 곳에서 정하면서도 테스트가
 * 하나도 없었다. scaleIn 스프링은 전정계 민감 사용자에게 부담이 가장 큰 종류라, 여기가 조용히
 * 깨지면 #228이 지키려던 사용자에게 정확히 그 연출이 나간다.
 *
 * ## 왜 이건 관측 가능한가
 *
 * `celebrationEnter()`는 **값을 돌려주는** 컴포저블이다. 대부분의 연출과 달리 그리기 부수효과가
 * 아니라 반환값이라, 시맨틱도 픽셀도 필요 없이 그냥 비교하면 된다. 연출을 테스트 가능하게 만드는
 * 가장 값싼 방법이 이 모양이라는 선례이기도 하다(`skeletonColors`·`skeletonBandLeft`와 같은 규율).
 */
@RunWith(RobolectricTestRunner::class)
class MotionReductionTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * `NexusTheme`은 [LocalMotionScale]을 **시스템 값으로 다시 공급한다**(NexusTheme.kt).
     * 그래서 주입은 반드시 테마 **안쪽**이어야 한다 — 바깥에 두면 조용히 무시된다.
     * 이 함정은 실제로 #268의 리듀스드모션 테스트 전체를 무력화했다.
     */
    private fun enterTransition(motionScale: Float): EnterTransition {
        lateinit var transition: EnterTransition
        composeRule.setContent {
            NexusTheme {
                CompositionLocalProvider(LocalMotionScale provides motionScale) {
                    transition = celebrationEnter()
                }
            }
        }
        composeRule.waitForIdle()
        return transition
    }

    @Test
    fun `애니메이션 제거면 축하는 페이드만 한다`() {
        // 튕겨 나오는 scaleIn 없이 — 위치·크기가 변하지 않는 대체안
        assertEquals(fadeIn(), enterTransition(motionScale = 0f), "축하가 여전히 튕겨 나온다")
    }

    /** 양성 대조 — 평소에는 스프링이 붙는다. 없으면 위 단언이 "원래 페이드뿐"으로 참이 된다. */
    @Test
    fun `평소 축하는 스케일 스프링이 붙는다`() {
        assertNotEquals(fadeIn(), enterTransition(motionScale = 1f), "축하 연출이 페이드로 죽었다")
    }

    // ── 상시 미동 (#217) ──

    /**
     * 캐릭터 숨쉬기는 상시 반복이라 **가장 타협 불가**한 자리다 — 전정기관 장애가 있는 사용자에게
     * 증상을 유발할 수 있다. 그런데 진폭은 `graphicsLayer` 안에서만 읽혀 관측할 수 없었고,
     * 게이트를 걷어내도 아무 테스트도 안 깨졌다(#338 감사). 결정을 순수 함수로 끌어내 고정한다.
     */
    @Test
    fun `애니메이션 제거면 숨쉬기 진폭이 없다`() {
        assertEquals(1f, breathTargetScale(motionScale = 0f), message = "감축인데 캐릭터가 계속 숨쉰다")
    }

    @Test
    fun `평소에는 숨쉬기 진폭이 있다`() {
        assertNotEquals(1f, breathTargetScale(motionScale = 1f), message = "숨쉬기가 죽었다 — 정적 마스코트가 된다")
    }

    @Test
    fun `부분 감속에서도 숨쉰다`() {
        assertNotEquals(1f, breathTargetScale(motionScale = 0.5f), message = "0.5배를 '제거'로 뭉갰다")
    }

    /**
     * 부분 감속(0.5배)은 '제거'가 아니다 — `ReduceMotion.isReduced`가 **정확히 0**만 제거로 본다.
     * "느리게"를 고른 사용자에게 연출을 없애 버리면 그건 다른 요구를 들어준 것이다.
     */
    @Test
    fun `부분 감속은 연출을 없애지 않는다`() {
        assertNotEquals(fadeIn(), enterTransition(motionScale = 0.5f), "0.5배를 '제거'로 뭉갰다")
    }
}
