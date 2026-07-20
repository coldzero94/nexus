package com.nexus.app.telemetry

import com.nexus.app.telemetry.TelemetryPolicy.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 계측 정책 고정 (#46, E8-1) — 완료 기준 그 자체: **건강 파생 수치 필드가 테스트에서 걸린다**.
 * 이 테스트가 깨지지 않고는 금지 필드를 이벤트에 실을 수 없다.
 */
class TelemetryPolicyTest {

    @Test
    fun healthDerivedKeysAreCaught() {
        // Play 정책상 실려선 안 되는 대표 키들 — 전부 걸려야 한다
        val forbidden = listOf(
            "steps", "todayXp", "heartRate", "avgBpm", "sleepHours",
            "durationMinutes", "conditionScore", "level", "workoutType",
            "distanceKm", "calories", "energyBalance",
        )
        forbidden.forEach { key ->
            val violations = TelemetryPolicy.violations(mapOf(key to "value"))
            assertTrue(
                violations.any { it.kind == Kind.HEALTH_TERM_IN_KEY && it.key == key },
                "$key: 건강 파생 키가 정책을 통과함",
            )
        }
    }

    @Test
    fun numericValuesAreCaught_evenOnAllowedKeys() {
        // 미래에 키가 allowlist에 추가돼도 수치는 실을 수 없다 — 발생 사실만 기록 원칙
        val violations = TelemetryPolicy.violationsFor(mapOf("screen" to "8432"), allowedKeys = setOf("screen"))
        assertEquals(listOf(TelemetryPolicy.Violation(Kind.NUMERIC_VALUE, "screen")), violations)
    }

    @Test
    fun unknownKeysAreCaught() {
        val violations = TelemetryPolicy.violations(mapOf("screen" to "home"))
        assertTrue(violations.any { it.kind == Kind.KEY_NOT_ALLOWED && it.key == "screen" })
    }

    @Test
    fun cleanParamsPass() {
        assertTrue(TelemetryPolicy.violations(emptyMap()).isEmpty())
        assertTrue(TelemetryPolicy.violationsFor(mapOf("screen" to "home"), allowedKeys = setOf("screen")).isEmpty())
    }

    @Test
    fun signalAllowlistIsPinned() {
        // 새 이벤트 추가 시 이 목록도 함께 갱신해야 한다 — 리뷰를 강제하는 이중 장부
        val pinned = setOf(
            "app.opened",
            "funnel.onboardingCompleted",
            "funnel.permissionGranted",
            "funnel.firstXp",
            "funnel.widgetInstalled",
            "funnel.expeditionOpened",
        )
        assertEquals(pinned, TelemetryEvent.entries.map { it.signal }.toSet())
    }
}
