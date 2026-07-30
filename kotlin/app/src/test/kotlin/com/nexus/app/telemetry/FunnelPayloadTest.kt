package com.nexus.app.telemetry

import com.nexus.core.HealthTermDenylist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 퍼널 이벤트 페이로드 안전성 (#226 완료 기준) — occurrence-only가 실제로 지켜지는지.
 *
 * 신호 이름 자체가 유일한 페이로드다. 파라미터는 정책이 비운 상태([TelemetryPolicy.allowedParamKeys]가
 * 의도적으로 빈 집합)이므로, 검사 대상은 **이름**이다: 건강 항목을 가리키거나 숫자를 담으면 안 된다.
 */
class FunnelPayloadTest {
    private val signals = TelemetryEvent.entries.map { it.signal }

    @Test
    fun `신호 이름에 건강 항목이 없다`() {
        val offenders = signals.filter { HealthTermDenylist.isRawHealthTerm(it) }
        assertEquals(emptyList(), offenders, "이벤트 이름이 원시 건강 항목을 가리킨다 (불변식 ②)")
    }

    @Test
    fun `신호 이름에 숫자가 없다`() {
        // 수치가 이름으로 새는 통로 차단 — "steps8432" 같은 형태를 애초에 막는다
        assertEquals(emptyList(), signals.filter { s -> s.any(Char::isDigit) })
    }

    @Test
    fun `파라미터는 어떤 것도 허용되지 않는다`() {
        // MVP는 발생 사실만 — allowlist가 비어 있어야 수치가 실릴 자리가 없다
        assertTrue(TelemetryPolicy.allowedParamKeys.isEmpty())
        assertTrue(TelemetryPolicy.violations(mapOf("todaySteps" to "8432")).isNotEmpty())
        assertTrue(TelemetryPolicy.violations(mapOf("characterName" to "루디")).isNotEmpty())
    }

    @Test
    fun `온보딩 스텝 네 개가 모두 신호를 갖는다`() {
        // 스텝을 추가하고 계측을 빼면 이탈 지점에 구멍이 생긴다 — 4단계 대응을 못 박는다
        val stepSignals = signals.filter { it.startsWith("funnel.onboardingStage") }
        assertEquals(4, stepSignals.size, "온보딩 스텝 수와 계측 신호 수가 어긋난다")
    }

    @Test
    fun `권한 거부와 데모 선택이 별도 신호다`() {
        // 거부는 카피의 문제, 미가용은 기기의 문제 — 대응이 달라 합칠 수 없다
        assertTrue("funnel.permissionDenied" in signals)
        assertTrue("funnel.demoChosen" in signals)
    }
}
