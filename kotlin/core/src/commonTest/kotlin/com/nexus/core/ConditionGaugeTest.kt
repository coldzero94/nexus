package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** #257 — 게이지 채움 비율 매핑·존 분류. 바닥(불퇴행) 클램프가 핵심. */
class ConditionGaugeTest {
    private val floor = ConditionEngine.SOFT_FLOOR // 20
    private val max = ConditionEngine.MAX // 100

    @Test
    fun `바닥은 0, 최대는 1로 매핑`() {
        assertEquals(0.0, ConditionGauge.fillRatio(floor))
        assertEquals(1.0, ConditionGauge.fillRatio(max))
    }

    @Test
    fun `중간값은 구간 내 상대 위치`() {
        // 20~100 구간의 중점 60 → 0.5
        assertEquals(0.5, ConditionGauge.fillRatio(60.0))
        // 40 → (40-20)/80 = 0.25
        assertEquals(0.25, ConditionGauge.fillRatio(40.0))
    }

    @Test
    fun `바닥 미만도 0 밑으로 안 떨어진다 - 불퇴행`() {
        assertEquals(0.0, ConditionGauge.fillRatio(0.0))
        assertEquals(0.0, ConditionGauge.fillRatio(-50.0))
    }

    @Test
    fun `최대 초과는 1로 클램프`() {
        assertEquals(1.0, ConditionGauge.fillRatio(150.0))
    }

    @Test
    fun `비정상 구간(floor gte max)은 0`() {
        assertEquals(0.0, ConditionGauge.fillRatio(80.0, floor = 100.0, max = 100.0))
        assertEquals(0.0, ConditionGauge.fillRatio(80.0, floor = 100.0, max = 20.0))
    }

    @Test
    fun `존 경계 - 경계값은 상위 존에 포함`() {
        assertEquals(ConditionGauge.Zone.Recovering, ConditionGauge.zoneOf(20.0))
        assertEquals(ConditionGauge.Zone.Recovering, ConditionGauge.zoneOf(39.9))
        assertEquals(ConditionGauge.Zone.Stable, ConditionGauge.zoneOf(40.0))
        assertEquals(ConditionGauge.Zone.Stable, ConditionGauge.zoneOf(69.9))
        assertEquals(ConditionGauge.Zone.Good, ConditionGauge.zoneOf(70.0))
        assertEquals(ConditionGauge.Zone.Good, ConditionGauge.zoneOf(100.0))
    }
}
