package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #236 — 연결 게이트. 핵심은 **오탈락 방지**다: 걸음·운동을 다 승인한 사용자가 백그라운드 권한
 * 하나 거부로 영구 데모 모드에 갇히던 것이 이 티켓의 발단이다.
 */
class ConnectGateTest {
    private val steps = "read_steps"
    private val exercise = "read_exercise"
    private val heartRate = "read_hr"
    private val background = "read_background"
    private val history = "read_history"

    private val required = setOf(steps, exercise)
    private val optional = setOf(heartRate, background, history)

    @Test
    fun `필수 둘이면 연결`() {
        assertTrue(ConnectGate.isConnected(granted = required, required = required))
    }

    @Test
    fun `선택 권한이 전부 없어도 연결은 유지된다`() {
        // 이게 이 티켓의 존재 이유 — 백그라운드·과거이력은 안드로이드가 별도 게이팅해 자주 거부된다
        assertTrue(ConnectGate.isConnected(granted = required, required = required))
        assertEquals(optional, ConnectGate.missingOptional(required, optional))
    }

    @Test
    fun `운동이 빠지면 연결 아님`() {
        assertFalse(ConnectGate.isConnected(granted = setOf(steps), required = required))
    }

    @Test
    fun `걸음이 빠지면 연결 아님`() {
        assertFalse(ConnectGate.isConnected(granted = setOf(exercise), required = required))
    }

    @Test
    fun `선택 권한만 있으면 연결 아님`() {
        assertFalse(ConnectGate.isConnected(granted = optional, required = required))
    }

    @Test
    fun `필수 집합이 비면 연결로 보지 않는다`() {
        // 구성 실수로 required가 비면 "아무 권한 없이 연결됨"이 되어 조용히 빈 화면을 낳는다
        assertFalse(ConnectGate.isConnected(granted = emptySet(), required = emptySet()))
        assertFalse(ConnectGate.isConnected(granted = required, required = emptySet()))
    }

    @Test
    fun `선택 권한 일부 승인은 그만큼만 빠진 것으로 센다`() {
        val granted = required + heartRate
        assertTrue(ConnectGate.isConnected(granted, required))
        assertEquals(setOf(background, history), ConnectGate.missingOptional(granted, optional))
    }

    @Test
    fun `전부 승인이면 빠진 선택 권한이 없다`() {
        assertTrue(ConnectGate.missingOptional(required + optional, optional).isEmpty())
    }
}
