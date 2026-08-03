package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #225 — 온보딩 경로. 흐름이 모든 사용자에게 같지 않다는 게 이 계산의 존재 이유다: HC를 쓸 수 없는
 * 기기는 권한 설명을 건너뛰므로, 스텝을 선언 순서로 세면 **"1/4"였다가 3번으로 뛰는** 인디케이터가
 * 되고 뒤로가기는 권한 요청이 실패하는 화면으로 되돌린다.
 */
class OnboardingFlowTest {

    @Test
    fun `가용하면 다섯 스텝을 모두 지난다`() {
        assertEquals(
            listOf(
                OnboardingStage.WELCOME,
                OnboardingStage.CREATE,
                OnboardingStage.RATIONALE,
                OnboardingStage.SAMSUNG_HEALTH,
                OnboardingStage.WEEKLY_GOAL,
            ),
            OnboardingFlow.steps(healthAvailable = true),
        )
    }

    /**
     * 캐릭터 만들기는 **권한 요청 전**이어야 한다 (#42). 순서가 뒤집히면 아직 아무것도 아닌 앱이
     * 건강 데이터부터 요구하는 모양이 되고, 그건 온보딩 이탈의 전형이다.
     */
    @Test
    fun `캐릭터 만들기가 권한보다 먼저다`() {
        listOf(true, false).forEach { available ->
            val path = OnboardingFlow.steps(available)
            val create = path.indexOf(OnboardingStage.CREATE)
            assertTrue(create >= 0, "가용=$available 경로에 캐릭터 만들기가 없다")
            listOf(OnboardingStage.RATIONALE, OnboardingStage.SAMSUNG_HEALTH).forEach { later ->
                val at = path.indexOf(later)
                if (at >= 0) assertTrue(create < at, "가용=$available: 캐릭터 만들기가 $later 뒤에 있다")
            }
        }
    }

    @Test
    fun `미가용이면 권한 설명을 건너뛴다`() {
        // 권한 요청이 실패하는 스텝을 보여주면 안 된다 (#236)
        val path = OnboardingFlow.steps(healthAvailable = false)

        assertTrue(OnboardingStage.RATIONALE !in path)
        assertEquals(4, path.size)
    }

    @Test
    fun `어느 경로에서도 환영이 처음이고 주간 목표가 마지막이다`() {
        listOf(true, false).forEach { available ->
            val path = OnboardingFlow.steps(available)
            assertEquals(OnboardingStage.WELCOME, path.first(), "가용=$available")
            assertEquals(OnboardingStage.WEEKLY_GOAL, path.last(), "가용=$available")
        }
    }

    // ── 진행 위치 ──

    @Test
    fun `가용 경로의 위치는 1부터 5까지다`() {
        assertEquals(1, OnboardingFlow.positionOf(OnboardingStage.WELCOME, true))
        assertEquals(2, OnboardingFlow.positionOf(OnboardingStage.CREATE, true))
        assertEquals(3, OnboardingFlow.positionOf(OnboardingStage.RATIONALE, true))
        assertEquals(4, OnboardingFlow.positionOf(OnboardingStage.SAMSUNG_HEALTH, true))
        assertEquals(5, OnboardingFlow.positionOf(OnboardingStage.WEEKLY_GOAL, true))
    }

    @Test
    fun `미가용 경로에서는 번호가 뛰지 않는다`() {
        // 이게 이 계산의 핵심 — 선언 순서로 세면 1 다음이 3이 된다
        assertEquals(1, OnboardingFlow.positionOf(OnboardingStage.WELCOME, false))
        assertEquals(2, OnboardingFlow.positionOf(OnboardingStage.CREATE, false))
        assertEquals(3, OnboardingFlow.positionOf(OnboardingStage.SAMSUNG_HEALTH, false))
        assertEquals(4, OnboardingFlow.positionOf(OnboardingStage.WEEKLY_GOAL, false))
    }

    @Test
    fun `경로 밖 스텝은 위치가 없다`() {
        // 틀린 숫자를 보여주는 것보다 안 보여주는 게 낫다(가용성이 바뀌는 순간의 과도기)
        assertNull(OnboardingFlow.positionOf(OnboardingStage.RATIONALE, healthAvailable = false))
    }

    @Test
    fun `위치는 경로 길이를 넘지 않는다`() {
        listOf(true, false).forEach { available ->
            val size = OnboardingFlow.steps(available).size
            OnboardingFlow.steps(available).forEach { stage ->
                val position = OnboardingFlow.positionOf(stage, available)
                assertTrue(position != null && position in 1..size, "가용=$available $stage → $position")
            }
        }
    }

    // ── 뒤로가기 ──

    @Test
    fun `첫 스텝에서는 뒤로 갈 곳이 없다`() {
        assertNull(OnboardingFlow.previousOf(OnboardingStage.WELCOME, true))
        assertNull(OnboardingFlow.previousOf(OnboardingStage.WELCOME, false))
    }

    @Test
    fun `가용 경로의 뒤로가기는 한 칸씩 되돌린다`() {
        assertEquals(OnboardingStage.WELCOME, OnboardingFlow.previousOf(OnboardingStage.CREATE, true))
        assertEquals(OnboardingStage.CREATE, OnboardingFlow.previousOf(OnboardingStage.RATIONALE, true))
        assertEquals(OnboardingStage.RATIONALE, OnboardingFlow.previousOf(OnboardingStage.SAMSUNG_HEALTH, true))
        assertEquals(OnboardingStage.SAMSUNG_HEALTH, OnboardingFlow.previousOf(OnboardingStage.WEEKLY_GOAL, true))
    }

    /**
     * 미가용 기기가 SAMSUNG_HEALTH에서 뒤로 가면 **RATIONALE이 아니라 WELCOME**이다.
     * 건너뛴 스텝으로 되돌리면 권한 요청이 실패하는 화면에 갇힌다.
     */
    @Test
    fun `건너뛴 스텝으로는 되돌아가지 않는다`() {
        assertEquals(OnboardingStage.CREATE, OnboardingFlow.previousOf(OnboardingStage.SAMSUNG_HEALTH, false))
    }

    @Test
    fun `경로 밖 스텝에서는 뒤로 갈 곳이 없다`() {
        assertNull(OnboardingFlow.previousOf(OnboardingStage.RATIONALE, healthAvailable = false))
    }

    @Test
    fun `뒤로가기를 반복하면 첫 스텝에 도달하고 멈춘다`() {
        listOf(true, false).forEach { available ->
            var stage = OnboardingStage.WEEKLY_GOAL
            var hops = 0
            while (true) {
                stage = OnboardingFlow.previousOf(stage, available) ?: break
                hops++
                assertTrue(hops <= OnboardingFlow.steps(available).size, "뒤로가기가 순환한다 (가용=$available)")
            }
            assertEquals(OnboardingStage.WELCOME, stage, "가용=$available")
            assertEquals(OnboardingFlow.steps(available).size - 1, hops)
        }
    }
}
