package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #245 — 원장 자기 불변식 검사. 각 케이스는 **정상 원장에서 한 가지만 깨뜨려**, 그 위반만
 * 나오는지 본다. 여러 위반이 동시에 나오면 분류가 겹친다는 뜻이라 진단 해상도가 떨어진다.
 */
class LedgerIntegrityTest {
    private fun grant(sequence: Long, key: String, xp: Int = 100, day: Long = 20_000L, version: Int = 1) =
        LedgerRow(sequence, key, xp, RewardEventType.GRANT, version, day)

    private fun cancel(sequence: Long, key: String, xp: Int = -100, day: Long = 20_000L, version: Int = 1) =
        LedgerRow(sequence, key, xp, RewardEventType.CANCELLATION, version, day)

    /** 지급 둘 + 그중 하나의 정상 취소. 아래 케이스들이 이걸 한 군데씩 망친다. */
    private val healthy = listOf(grant(1, "a"), grant(2, "b"), cancel(3, "b"))

    @Test
    fun `정상 원장은 위반이 없다`() {
        assertEquals(emptySet(), LedgerIntegrity.check(healthy))
    }

    @Test
    fun `빈 원장은 위반이 없다`() {
        // 신규 설치의 정상 상태 — 여기서 위반이 나오면 첫 실행마다 크래시한다
        assertEquals(emptySet(), LedgerIntegrity.check(emptyList()))
    }

    @Test
    fun `입력 순서가 섞여 있어도 시퀀스로 정렬해 판정한다`() {
        assertEquals(emptySet(), LedgerIntegrity.check(healthy.reversed()))
    }

    /**
     * 로컬 원장에서는 `sequence`가 autoGenerate 기본키라 이 상태가 될 수 없다 — 이 검사는 시퀀스가
     * 밖에서 들어오는 경로(S9 임포트·백업 병합)와 인덱스 유실 대비다. 손으로 만든 행으로 테스트하는
     * 게 그래서 정당하다: 그 경로가 정확히 이렇게 생긴 입력을 준다.
     */
    @Test
    fun `시퀀스가 중복되면 위반`() {
        val rows = listOf(grant(1, "a"), grant(1, "b"))

        assertEquals(setOf(LedgerViolation.DUPLICATE_SEQUENCE), LedgerIntegrity.check(rows))
    }

    @Test
    fun `정렬해도 중복을 놓치지 않는다`() {
        // 정렬한 목록에서 인접 비교를 하면 이 검사는 사실상 동등 비교로 축약된다 — 입력 순서를
        // 뒤집어도 같은 결과가 나와야 그 축약에 기대지 않는다는 뜻이다
        val rows = listOf(grant(2, "a"), grant(1, "b"), grant(1, "c"))

        assertEquals(setOf(LedgerViolation.DUPLICATE_SEQUENCE), LedgerIntegrity.check(rows))
    }

    @Test
    fun `같은 키로 지급이 두 번이면 중복 지급`() {
        val rows = listOf(grant(1, "a"), grant(2, "a"))

        assertEquals(setOf(LedgerViolation.DUPLICATE_GRANT), LedgerIntegrity.check(rows))
    }

    @Test
    fun `짝 없는 취소는 고아 취소`() {
        val rows = listOf(grant(1, "a"), cancel(2, "없는키"))

        assertEquals(setOf(LedgerViolation.ORPHAN_CANCELLATION), LedgerIntegrity.check(rows))
    }

    @Test
    fun `같은 키를 두 번 취소하면 중복 취소`() {
        val rows = listOf(grant(1, "a"), cancel(2, "a"), cancel(3, "a"))

        assertEquals(setOf(LedgerViolation.DUPLICATE_CANCELLATION), LedgerIntegrity.check(rows))
    }

    @Test
    fun `취소액이 지급액의 반대값이 아니면 부호 불일치`() {
        val rows = listOf(grant(1, "a", xp = 100), cancel(2, "a", xp = -40))

        assertEquals(setOf(LedgerViolation.CANCELLATION_SIGN_MISMATCH), LedgerIntegrity.check(rows))
    }

    /**
     * 지급보다 앞선 취소. 고아 취소와 **구분돼야** 한다 — 원인이 다르다(순서 섞임 vs 지급 유실).
     * 지급을 먼저 전부 모으지 않으면 이 케이스가 고아로 잡혀 이 단언이 깨진다.
     */
    @Test
    fun `취소가 지급보다 앞서면 인과 위반이고 고아로 잡히지 않는다`() {
        val rows = listOf(cancel(1, "a"), grant(2, "a"))

        assertEquals(setOf(LedgerViolation.CANCELLATION_BEFORE_GRANT), LedgerIntegrity.check(rows))
    }

    /**
     * `xp`만 보면 상쇄가 맞는데 총합은 안 맞는 유일한 위반 — 상한이 **일 단위**로 걸리기 때문이다.
     * 지급일은 상한이 그대로 남고 취소일만 음수가 되어 0으로 클램프된다.
     */
    @Test
    fun `취소가 다른 날로 들어가면 날짜 불일치`() {
        val rows = listOf(grant(1, "a", day = 20_000L), cancel(2, "a", day = 20_001L))

        assertEquals(setOf(LedgerViolation.CANCELLATION_DAY_MISMATCH), LedgerIntegrity.check(rows))
    }

    @Test
    fun `날짜 불일치는 실제로 총합을 남긴다`() {
        // 위 분류가 왜 필요한지의 근거 — 상쇄됐어야 할 XP가 남는다
        val split = listOf(grant(1, "a", day = 20_000L), cancel(2, "a", day = 20_001L))

        assertTrue(LedgerIntegrity.recomputeTotalXp(split) > 0)
        assertEquals(0, LedgerIntegrity.recomputeTotalXp(listOf(grant(1, "a"), cancel(2, "a"))))
    }

    @Test
    fun `0 이하 지급은 부호 규약 위반`() {
        assertEquals(
            setOf(LedgerViolation.GRANT_NON_POSITIVE_XP),
            LedgerIntegrity.check(listOf(grant(1, "a", xp = 0))),
        )
    }

    @Test
    fun `0 이상 취소는 부호 규약 위반`() {
        val rows = listOf(grant(1, "a"), cancel(2, "a", xp = 100))

        // 부호가 뒤집혔으니 상쇄도 안 된다 — 두 위반이 같이 나오는 게 맞다
        assertEquals(
            setOf(LedgerViolation.CANCELLATION_NON_NEGATIVE_XP, LedgerViolation.CANCELLATION_SIGN_MISMATCH),
            LedgerIntegrity.check(rows),
        )
    }

    /**
     * **같은 날 산식 버전 혼재는 위반이 아니다.** 처음엔 위반으로 넣었는데, 그러면 산식을 한 번
     * 올린 뒤 대부분의 설치가 영구히 위반 상태가 된다 — `grantSessions`는 지급 시점 버전을 박제하고
     * 원장은 재기록되지 않으므로, 산식을 올린 날 뒤늦게 도착한 과거 세션(HC 지연 30~60분, 워치는
     * 며칠)이 옛 날짜에 새 버전으로 들어온다. 흔한 정상 경로다.
     *
     * 계산도 틀리지 않는다 — 상한은 합산 시점에 걸린다. 정보로는 필요하므로 진단 스냅샷의
     * `SINGLE_FORMULA_VERSION` 플래그로만 남긴다(경보 아님).
     */
    @Test
    fun `같은 날 산식 버전이 섞여도 위반이 아니다`() {
        val rows = listOf(grant(1, "a", version = 1), grant(2, "b", version = 2))

        assertEquals(emptySet(), LedgerIntegrity.check(rows))
    }

    @Test
    fun `산식을 올린 날 뒤늦게 도착한 과거 세션도 위반이 아니다`() {
        // 실제로 가장 흔한 경로 — 이걸 위반으로 잡으면 되돌릴 방법이 없다(원장은 불변)
        val rows = listOf(
            grant(1, "어제-v1", day = 20_000L, version = 1),
            grant(2, "어제-지연도착-v2", day = 20_000L, version = 2),
        )

        assertEquals(emptySet(), LedgerIntegrity.check(rows))
    }

    @Test
    fun `재계산은 일 상한을 적용한다`() {
        val huge = (1..50L).map { grant(it, "k$it", xp = 1_000) }

        assertEquals(XpEngine.applyDailyCap(50_000.0), LedgerIntegrity.recomputeTotalXp(huge))
    }

    @Test
    fun `과취소로 일합이 음수여도 총합은 음수로 새지 않는다`() {
        val rows = listOf(grant(1, "a", xp = 100), cancel(2, "a"), cancel(3, "a"))

        assertEquals(0, LedgerIntegrity.recomputeTotalXp(rows))
    }

    @Test
    fun `여러 종류가 동시에 깨지면 전부 보고한다`() {
        val rows = listOf(grant(1, "a"), grant(1, "a"))

        assertEquals(
            setOf(LedgerViolation.DUPLICATE_SEQUENCE, LedgerViolation.DUPLICATE_GRANT),
            LedgerIntegrity.check(rows),
        )
    }

    /**
     * 중복 지급이 있을 때 취소는 **첫** 지급과 대조돼야 한다. 마지막 것을 남기면 효력 없는 행과
     * 비교돼 부호 판정이 뒤집힌다 — 유니크 인덱스가 IGNORE로 살려두는 건 첫 행이다.
     */
    @Test
    fun `중복 지급이 있어도 취소는 첫 지급과 대조된다`() {
        val rows = listOf(grant(1, "a", xp = 100), grant(2, "a", xp = 40), cancel(3, "a", xp = -100))

        assertEquals(setOf(LedgerViolation.DUPLICATE_GRANT), LedgerIntegrity.check(rows))
    }

    @Test
    fun `같은 위반이 여러 건이어도 종류 하나로 접힌다`() {
        // 개수는 원격으로 못 보내는 값이라(불변식 ②) 애초에 세지 않는다 — 원인은 하나다
        val rows = listOf(grant(1, "a"), grant(2, "a"), grant(3, "a"))

        assertEquals(setOf(LedgerViolation.DUPLICATE_GRANT), LedgerIntegrity.check(rows))
    }

    @Test
    fun `위반 이름은 건강 용어를 담지 않는다`() {
        // 이름이 곧 원격 페이로드 — FailureCategory와 같은 계약
        LedgerViolation.entries.forEach {
            assertTrue(!HealthTermDenylist.isRawHealthTerm(it.name), "위반 이름에 건강 용어: ${it.name}")
        }
    }
}
