package com.nexus.app.crash

import com.nexus.core.FailureCategory
import com.nexus.core.HealthTermDenylist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 처리된-실패 페이로드에 건강 파생값이 없다 (#239 완료 기준 ⑤).
 *
 * 보내는 건 [FailureCategory] 이름 하나뿐이므로, 검사할 대상도 그 이름 집합이다. 예외 메시지·스택을
 * 싣지 않는 설계 자체가 1차 방어이고(`CrashReporting.recordHandledFailure`), 이름이 항목을 가리키지
 * 않는지가 2차 방어다. 미래에 페이로드가 늘면 이 테스트가 먼저 걸린다.
 */
class HandledFailurePayloadTest {
    @Test
    fun `분류 이름에 건강 항목이 없다`() {
        val offenders = FailureCategory.entries.map { it.name }.filter { HealthTermDenylist.isRawHealthTerm(it) }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `분류 이름에 숫자가 없다 — 수치가 실릴 통로 차단`() {
        // TelemetryPolicy의 3중 방어와 같은 원칙: 숫자를 담을 수 있는 형태를 애초에 막는다
        val withDigits = FailureCategory.entries.map { it.name }.filter { it.any(Char::isDigit) }
        assertEquals(emptyList(), withDigits)
    }

    @Test
    fun `분류가 아홉 개를 넘지 않는다 — 신호가 늘면 리뷰가 필요하다`() {
        // 상한 자체가 목적이 아니라, 늘어날 때 반드시 한 번 생각하게 만드는 장치다.
        // 8 → 9: LEDGER_INTEGRITY 추가(#245). LEDGER_DB와 합치지 않은 이유는 대응이 정반대라서다 —
        // DB 오류는 재시도·디스크 정리로 풀리고, 무결성 위반은 우리가 코드를 고쳐야 한다.
        assertTrue(
            FailureCategory.entries.size <= 9,
            "진단 신호가 ${FailureCategory.entries.size}개로 늘었다 — 각각이 원격으로 나가도 되는지 확인하세요",
        )
    }
}
