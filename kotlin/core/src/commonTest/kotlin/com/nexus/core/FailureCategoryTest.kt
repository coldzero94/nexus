package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #239 — 진단 신호 allowlist 고정. `TelemetryEvent`와 같은 강제 장치다:
 * 신호를 추가하면 이 목록도 함께 갱신해야 CI가 통과한다(리뷰 강제).
 */
class FailureCategoryTest {
    /** 보낼 수 있는 신호의 전부 — 늘릴 때 여기도 갱신해야 한다. */
    private val allowed = setOf(
        "SYNC_IO",
        "SYNC_REMOTE",
        "SYNC_PERMISSION",
        "LEDGER_DB",
        "LEDGER_INTEGRITY",
        "BACKUP_IMPORT",
        "HOME_LOAD",
        "ACTIVITY_LOAD",
        "GROWTH_LOAD",
    )

    @Test
    fun `분류 목록이 고정 allowlist와 정확히 일치한다`() {
        assertEquals(
            allowed,
            FailureCategory.entries.map { it.name }.toSet(),
            "진단 신호를 추가·삭제했다 — 이 값이 원격으로 나가도 되는지 확인하고 목록을 갱신하세요.",
        )
    }

    @Test
    fun `분류 이름이 건강 항목을 가리키지 않는다`() {
        // 이름 자체가 페이로드다 — 'stepSyncFailed' 같은 이름이 생기면 수치는 없어도 항목이 새어 나간다
        val offenders = FailureCategory.entries.map { it.name }.filter { HealthTermDenylist.isRawHealthTerm(it) }
        assertEquals(emptyList(), offenders, "분류 이름이 원시 건강 항목을 가리킨다 (불변식 ②)")
    }

    @Test
    fun `권한 실패는 재시도 대상과 구분된다`() {
        // 운영상 가장 중요한 구분 — 이것만 사용자 조치가 필요하고 나머지는 기다리면 된다
        assertTrue(FailureCategory.SYNC_PERMISSION in FailureCategory.entries)
        assertTrue(FailureCategory.SYNC_IO != FailureCategory.SYNC_PERMISSION)
    }
}
