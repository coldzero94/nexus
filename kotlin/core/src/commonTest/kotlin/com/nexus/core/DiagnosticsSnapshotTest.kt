package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #245 — 진단 스냅샷. 가장 중요한 단언은 마지막 두 개다: **수치를 담을 그릇이 없다**는 것과
 * 키 목록이 고정돼 있다는 것. 나머지는 그 구조가 실제로 작동하는지 본다.
 */
class DiagnosticsSnapshotTest {

    @Test
    fun `플래그와 선택지와 식별자를 조립한다`() {
        val out = DiagnosticsSnapshot.assemble(
            mapOf(
                DiagnosticsKey.REQUIRED_PERMISSIONS to DiagnosticsValue.Flag(true),
                DiagnosticsKey.HEALTH_AVAILABILITY to DiagnosticsValue.Choice(HealthAvailability.Available.name),
                DiagnosticsKey.BUILD_ID to DiagnosticsValue.Id("1.0.0-42"),
            ),
        )

        assertEquals("true", out["REQUIRED_PERMISSIONS"])
        assertEquals("Available", out["HEALTH_AVAILABILITY"])
        assertEquals("1.0.0-42", out["BUILD_ID"])
    }

    @Test
    fun `키가 선언한 종류와 다른 값은 거부한다`() {
        // 플래그 자리에 문자열이 들어오면 "false"가 아닌 무언가가 조용히 실릴 수 있다
        assertFailsWith<IllegalArgumentException> {
            DiagnosticsSnapshot.assemble(
                mapOf(DiagnosticsKey.REQUIRED_PERMISSIONS to DiagnosticsValue.Choice("아마도")),
            )
        }
    }

    @Test
    fun `건강 용어를 가리키는 선택지는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            DiagnosticsSnapshot.assemble(
                mapOf(DiagnosticsKey.LAST_FAILURE to DiagnosticsValue.Choice("STEP_COUNT_TOO_HIGH")),
            )
        }
    }

    @Test
    fun `평문은 선언 순서로 나오고 없는 키는 빠진다`() {
        val text = DiagnosticsSnapshot.render(
            mapOf(
                DiagnosticsKey.LEDGER_EMPTY to DiagnosticsValue.Flag(false),
                DiagnosticsKey.BUILD_ID to DiagnosticsValue.Id("abc"),
            ),
        )

        // BUILD_ID가 LEDGER_EMPTY보다 먼저 선언됐으므로 입력 순서와 무관하게 먼저 나온다
        assertEquals("BUILD_ID=abc\nLEDGER_EMPTY=false", text)
    }

    @Test
    fun `빈 스냅샷도 조립된다`() {
        assertEquals("", DiagnosticsSnapshot.render(emptyMap()))
    }

    @Test
    fun `키 이름이 건강 항목을 가리키지 않는다`() {
        // 키 이름도 페이로드다 — 'STEP_COUNT' 같은 키가 생기면 값이 없어도 항목이 새어 나간다
        val offenders = DiagnosticsKey.entries.map { it.name }.filter { HealthTermDenylist.isRawHealthTerm(it) }
        assertEquals(emptyList(), offenders, "진단 키가 원시 건강 항목을 가리킨다 (불변식 ②)")
    }

    /**
     * 이 테스트가 이 파일의 존재 이유다. 수치를 담을 그릇이 **하나라도** 생기면 걸음·XP 합·세션
     * 시간이 들어올 자리가 만들어진다.
     *
     * 밀봉 변종을 리플렉션으로 세지 않는 이유는 `sealedSubclasses`가 JVM 전용이라 commonTest에서
     * iOS 타깃 컴파일을 깨뜨리기 때문이다. 대신 **두 겹**으로 막는다: ① 이 종류 목록 고정,
     * ② [DiagnosticsSnapshot]의 `kind()`/`render()`가 exhaustive `when`이라 변종 추가만으로
     * 컴파일이 깨진다. 새 변종을 몰래 통과시키려면 이 둘을 다 손대야 한다.
     */
    @Test
    fun `값 종류에 수치 그릇이 없다`() {
        assertEquals(
            setOf("FLAG", "CHOICE", "ID"),
            DiagnosticsKind.entries.map { it.name }.toSet(),
            "진단 값 종류가 늘었다 — 수치를 담을 수 있는지 확인하세요 (불변식 ②·③)",
        )
    }

    @Test
    fun `키 목록이 고정 allowlist와 정확히 일치한다`() {
        val allowed = setOf(
            "BUILD_ID",
            "INSTALL_ID",
            "HEALTH_AVAILABILITY",
            "REQUIRED_PERMISSIONS",
            "OPTIONAL_PERMISSIONS_MISSING",
            "SYNC_FRESHNESS",
            "LAST_FAILURE",
            "FAILURE_STREAK",
            "HAS_CHANGES_TOKEN",
            "LEDGER_EMPTY",
            "LEDGER_INTEGRITY_OK",
            "SINGLE_FORMULA_VERSION",
            "BATTERY_UNRESTRICTED",
            "REDUCE_MOTION",
        )

        assertEquals(
            allowed,
            DiagnosticsKey.entries.map { it.name }.toSet(),
            "진단 키를 추가·삭제했다 — 건강 파생 값이 아닌지 확인하고 목록을 갱신하세요.",
        )
    }

    @Test
    fun `모든 선택지 키는 실제 enum 이름으로 채울 수 있다`() {
        // CHOICE 키에 자유텍스트를 넣게 되면 그때부터 값이 무엇이든 될 수 있다 — enum 대응을 고정한다
        val choices = mapOf(
            DiagnosticsKey.HEALTH_AVAILABILITY to HealthAvailability.entries.map { it.name },
            DiagnosticsKey.SYNC_FRESHNESS to SyncFreshnessBucket.entries.map { it.name },
            DiagnosticsKey.LAST_FAILURE to FailureCategory.entries.map { it.name },
            DiagnosticsKey.FAILURE_STREAK to FailureStreak.entries.map { it.name },
        )

        assertEquals(
            DiagnosticsKey.entries.filter { it.kind == DiagnosticsKind.CHOICE }.toSet(),
            choices.keys,
            "CHOICE 키가 늘었다 — 어떤 enum이 값을 채우는지 여기 등록하세요.",
        )
        choices.forEach { (key, names) ->
            names.forEach { name ->
                DiagnosticsSnapshot.assemble(mapOf(key to DiagnosticsValue.Choice(name)))
            }
        }
    }
}

/**
 * #245 — 구간화. 진단에 분 단위·횟수 대신 구간을 싣는 이유가 값이 곧 활동 시각이기 때문이므로,
 * 경계가 흐려지면 그 보호가 사라진다.
 */
class DiagnosticsBucketTest {

    @Test
    fun `한 번도 동기화 안 했으면 NEVER`() {
        assertEquals(SyncFreshnessBucket.NEVER, SyncFreshnessBucket.of(SyncFreshness.Never))
    }

    @Test
    fun `지연 고지 임계 이내는 FRESH`() {
        val edge = SyncFreshness.Synced(SyncFreshness.DELAY_NOTICE_MINUTES)

        assertEquals(SyncFreshnessBucket.FRESH, SyncFreshnessBucket.of(SyncFreshness.Synced(0)))
        assertEquals(SyncFreshnessBucket.FRESH, SyncFreshnessBucket.of(edge))
    }

    @Test
    fun `임계를 넘으면 DELAYED`() {
        val over = SyncFreshness.Synced(SyncFreshness.DELAY_NOTICE_MINUTES + 1)

        assertEquals(SyncFreshnessBucket.DELAYED, SyncFreshnessBucket.of(over))
    }

    @Test
    fun `하루 이상이면 STALE`() {
        assertEquals(SyncFreshnessBucket.STALE, SyncFreshnessBucket.of(SyncFreshness.Synced(24 * 60)))
        assertEquals(SyncFreshnessBucket.DELAYED, SyncFreshnessBucket.of(SyncFreshness.Synced(24 * 60 - 1)))
    }

    @Test
    fun `실패 0회는 NONE이고 음수도 NONE이다`() {
        assertEquals(FailureStreak.NONE, FailureStreak.of(0))
        assertEquals(FailureStreak.NONE, FailureStreak.of(-1))
    }

    @Test
    fun `임계 미만은 TRANSIENT 이상은 PERSISTENT`() {
        assertEquals(FailureStreak.TRANSIENT, FailureStreak.of(1))
        assertEquals(FailureStreak.TRANSIENT, FailureStreak.of(FailureStreak.PERSISTENT_THRESHOLD - 1))
        assertEquals(FailureStreak.PERSISTENT, FailureStreak.of(FailureStreak.PERSISTENT_THRESHOLD))
    }

    @Test
    fun `구간 이름은 원래 수치를 복원하지 못한다`() {
        // 구간화의 목적 — 3회와 30회가 구분되지 않아야 값이 새지 않는다
        assertEquals(FailureStreak.of(3), FailureStreak.of(30))
        assertTrue(
            SyncFreshnessBucket.of(SyncFreshness.Synced(500)) == SyncFreshnessBucket.of(SyncFreshness.Synced(600)),
        )
        assertFalse(SyncFreshnessBucket.entries.any { it.name.any(Char::isDigit) })
    }
}
