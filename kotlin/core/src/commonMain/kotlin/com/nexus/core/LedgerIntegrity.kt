package com.nexus.core

/**
 * 원장 불변식 위반 종류 (#245, E15-12).
 *
 * 이름만 원격으로 나간다 — 위반 개수·시퀀스·XP는 싣지 않는다(불변식 ②). 그래서 분류가 곧
 * 진단 해상도이고, 새 분류를 늘리는 건 [FailureCategory]와 같은 무게의 결정이다.
 */
enum class LedgerViolation {
    /**
     * 같은 시퀀스가 두 번 — 전역 순서(BACKEND §1 계약 3)가 깨졌다.
     *
     * **현재 로컬 원장에서는 발생할 수 없다**: `sequence`가 `autoGenerate` 기본키라 SQLite가 막는다.
     * 그래도 두는 이유는 시퀀스가 **밖에서 들어오는 경로**가 예정돼 있기 때문이다 — S9 서버 임포트와
     * 백업 병합은 남이 매긴 번호를 싣고 온다. 그때가 이 검사가 실제로 일하는 시점이고, 그 코드를
     * 쓰는 사람이 검사를 새로 발명하지 않아도 되게 미리 둔다.
     *
     * 로컬에서 이게 뜨면 기본키 인덱스가 마이그레이션에서 유실됐다는 뜻이다.
     */
    DUPLICATE_SEQUENCE,

    /**
     * 같은 멱등성 키로 GRANT가 두 번 — XP 이중 지급.
     *
     * [DUPLICATE_SEQUENCE]와 마찬가지로 **지금은 DB가 막는다**: `RewardEventEntity`의
     * `(idempotencyKey, type)` 유니크 인덱스 + `OnConflictStrategy.IGNORE`가 멱등 방어의 본체다.
     * 즉 이 검사는 그 인덱스에 **동의만** 할 수 있고, 뚫린 멱등성을 새로 발견하지는 못한다.
     *
     * 진짜 위험은 마이그레이션이 그 인덱스를 떨어뜨리는 것이고, 그건 `NexusDatabaseMigrationTest`가
     * 지킨다. 여기 남기는 건 그 인덱스가 사라진 뒤의 2차 방어다.
     */
    DUPLICATE_GRANT,

    /** 대응 GRANT가 없는 CANCELLATION — 고아 취소. 총합을 음수 방향으로 갉는다. */
    ORPHAN_CANCELLATION,

    /** 같은 키를 두 번 취소 — 한 번의 삭제가 두 배로 상쇄된다. [DUPLICATE_GRANT]와 같은 인덱스가 막는다. */
    DUPLICATE_CANCELLATION,

    /** CANCELLATION의 xp가 GRANT의 정확한 반대값이 아니다 — 부분 상쇄가 조용히 남는다. */
    CANCELLATION_SIGN_MISMATCH,

    /** CANCELLATION이 GRANT보다 앞선다 — 인과 위반. 순서 기반 재생이 불가능해진다. */
    CANCELLATION_BEFORE_GRANT,

    /**
     * CANCELLATION의 지급기준일이 GRANT와 다르다.
     *
     * 상한은 **일 단위 합**에 걸리므로(MVP §5) 취소가 다른 날에 들어가면 지급일은 상한이 그대로고
     * 취소일만 음수가 되어 클램프로 사라진다 — 삭제한 활동의 XP가 남는다. `xp`만 봐서는 안 보이는
     * 유일한 위반이라 별 분류를 뒀다.
     */
    CANCELLATION_DAY_MISMATCH,

    /** GRANT의 xp가 0 이하 — 지급이 아니다. 부호 규약이 뒤집힌 코드 경로가 있다. */
    GRANT_NON_POSITIVE_XP,

    /** CANCELLATION의 xp가 0 이상 — 취소가 아니다. */
    CANCELLATION_NON_NEGATIVE_XP,
}

/**
 * [LedgerIntegrity]가 보는 원장 행 — 무결성 검사에 **필요한 것만** 담은 투영.
 *
 * `RewardEventEntity`(Room)를 그대로 못 받는 이유는 core가 Android에 의존하지 않기 때문이고,
 * [RewardEvent]를 못 쓰는 이유는 거기 `epochDay`가 없기 때문이다(일 상한·취소 상쇄의 키).
 * provenance는 무결성과 무관하므로 일부러 뺐다.
 */
data class LedgerRow(
    val sequence: Long,
    val idempotencyKey: String,
    val xp: Int,
    val type: RewardEventType,
    val formulaVersion: Int,
    val epochDay: Long,
)

/**
 * 원장 자체 불변식의 런타임 검증 (#245, E15-12).
 *
 * ## 왜 필요했나
 *
 * 원장은 이 앱의 crown jewel이다 — 캐릭터의 모든 성장이 여기서 재계산된다. 그런데 이 원장이
 * **스스로 깨졌는지 확인하는 코드가 없었다.** 알파 테스터가 이미 원장을 쌓고 있는 상태에서
 * 이중 append나 고아 취소가 생기면, S9 서버 임포트로 남의 눈에 띌 때까지 무탐지다. 그때는
 * 이미 몇 주치 원장이 오염돼 있고, 원장은 불변이라 되돌릴 수 없다.
 *
 * ## 검사는 순수함수, 대응은 app이 정한다
 *
 * [check]는 판정만 한다 — 로그도 크래시도 없다. 무엇을 할지는 app 계층의 `LedgerIntegrityGuard`가
 * 호출 지점별로 결정한다(자동 경로는 보고, 개발자 요청은 크래시). 릴리스로 나가는 건
 * [FailureCategory.LEDGER_INTEGRITY] **발생 사실뿐**이고 위반 종류·개수·시퀀스는 싣지 않는다.
 *
 * ## 자동 수정하지 않는다
 *
 * 위반을 발견해도 행을 지우거나 고치지 않는다. append-only가 이 원장의 유일한 안전 장치이고,
 * "고치는 코드"가 곧 다음 오염의 경로가 된다. 진단은 보고까지만 한다.
 *
 * ## 지금 DB가 이미 막는 것과 그렇지 않은 것
 *
 * [LedgerViolation.DUPLICATE_SEQUENCE]·[LedgerViolation.DUPLICATE_GRANT]·
 * [LedgerViolation.DUPLICATE_CANCELLATION]은 로컬 원장에서 **발생할 수 없다** — 기본키와 유니크
 * 인덱스가 막는다. 즉 이 셋은 새로운 사실을 발견하지 못하고, 인덱스가 마이그레이션에서 유실됐을 때와
 * 시퀀스가 밖에서 들어올 때(S9 임포트·백업 병합)의 2차 방어다. 각 상수 KDoc에 그 상태를 적어둔 이유는
 * 나중에 읽는 사람이 "이건 검사되고 있다"고 오해하지 않게 하기 위해서다.
 *
 * 실제로 지금 일하는 검사는 취소 짝(고아·부호·**지급일 보존**·인과 순서)과 부호 규약이다. 그중
 * [LedgerViolation.CANCELLATION_DAY_MISMATCH]가 가장 미묘하다 — 이벤트를 눈으로 봐서는 정상인데
 * 총합만 틀린다.
 *
 * ## 산식 버전 혼재는 위반이 아니다
 *
 * 한 날짜에 여러 `formulaVersion`이 섞이는 건 **정상**이다. `grantSessions`는 지급 시점 버전을 박제하고
 * 원장은 재기록하지 않으므로, 산식을 올린 날 뒤늦게 도착한 과거 세션(HC 지연은 30~60분, 워치는 며칠)이
 * 옛 `epochDay`에 새 버전으로 들어온다 — 흔한 정상 경로다. 상한은 어차피 **합산 시점**에 걸리므로
 * ([LedgerMath]) 계산도 틀리지 않는다. 이걸 위반으로 잡으면 산식을 한 번 올린 뒤 대부분의 설치가
 * 영구히 위반 상태가 되고, 되돌릴 방법이 없다(원장은 불변). 정보로서는 필요하므로 진단 스냅샷의
 * [DiagnosticsKey.SINGLE_FORMULA_VERSION] 플래그로만 남긴다 — 경보가 아니라 참고값.
 */
object LedgerIntegrity {

    /**
     * 전수 검사. 위반 **종류**의 집합을 반환한다(빈 집합 = 정상).
     *
     * 개수가 아니라 종류를 돌려주는 건 의도다. 같은 종류가 40건이면 원인은 하나이고, 개수는
     * 원격으로 보낼 수 없는 값이라(불변식 ②) 종류만 있으면 진단에 충분하다.
     *
     * @param rows 원장 전체. 입력 순서는 무관하다 — 짝 검사는 [LedgerRow.sequence]로 정렬해서 본다.
     *   다만 시퀀스 중복 검사는 **정렬 전 입력**을 본다(정렬하면 중복만 남아 검사가 무의미해진다).
     */
    fun check(rows: List<LedgerRow>): Set<LedgerViolation> {
        val violations = mutableSetOf<LedgerViolation>()

        checkSequences(rows, violations)
        checkPairing(rows.sortedBy { it.sequence }, violations)
        return violations
    }

    /**
     * 원장에서 표시 총합을 다시 만든다 — 진단 화면이 보여주는 "원장이 말하는 값".
     *
     * 취소가 지급과 **같은 날 키**로 들어오는 계약에 기대므로([LedgerViolation.CANCELLATION_DAY_MISMATCH]가
     * 지키는 그 계약), 그 위반이 있으면 이 값도 신뢰할 수 없다.
     *
     * 이 값을 어딘가 저장된 총합과 자동으로 대조하지는 **않는다**. 앱에 영속된 총합류 값은
     * `SettlementStore.lastSeenXp`(= 사용자가 마지막으로 본 기준선, 새 XP가 오면 **당연히** 달라진다)와
     * 위젯 스냅샷(총합이 아니라 레벨을 저장한다)뿐이어서, 대조하면 정상 상태를 위반으로 신고한다.
     * 진짜 캐시가 생기면 그때 대조를 붙인다.
     */
    fun recomputeTotalXp(rows: List<LedgerRow>): Int = LedgerMath.cappedTotalXp(
        rows.groupBy { it.epochDay }.mapValues { (_, day) -> day.sumOf { it.xp }.toDouble() },
    )

    /**
     * 시퀀스 중복 — **정렬 전 입력**을 본다.
     *
     * 정렬한 목록에서 `b.sequence <= a.sequence`를 보는 흔한 형태는 사실상 동등 비교로 축약되고,
     * "단조 증가를 검사한다"는 착각만 남는다. 실제로 검사할 수 있는 건 중복이므로 그렇게 이름 붙였다.
     */
    private fun checkSequences(rows: List<LedgerRow>, into: MutableSet<LedgerViolation>) {
        if (rows.distinctBy { it.sequence }.size != rows.size) into += LedgerViolation.DUPLICATE_SEQUENCE
    }

    /** 이미 있으면 덮지 않고 true(= 중복)를 반환. `putIfAbsent`는 commonMain에 없다. */
    private fun MutableMap<String, LedgerRow>.putIfAbsentCompat(key: String, row: LedgerRow): Boolean {
        if (containsKey(key)) return true
        put(key, row)
        return false
    }

    /**
     * 지급·취소 짝 검사.
     *
     * 지급을 **먼저 전부** 모은 뒤 취소를 본다. 한 번에 훑으면 지급보다 앞선 취소가
     * "짝 없는 취소"로 잡혀 [LedgerViolation.CANCELLATION_BEFORE_GRANT]가 영원히 안 나온다 —
     * 인과 위반과 고아 취소는 원인이 다르므로(순서 섞임 vs 지급 유실) 구분해야 한다.
     */
    private fun checkPairing(ordered: List<LedgerRow>, into: MutableSet<LedgerViolation>) {
        val grants = mutableMapOf<String, LedgerRow>()
        for (row in ordered.filter { it.type == RewardEventType.GRANT }) {
            if (row.xp <= 0) into += LedgerViolation.GRANT_NON_POSITIVE_XP
            // 중복이면 **첫** 지급을 남긴다 — 유니크 인덱스가 IGNORE로 살려두는 쪽이 그것이고,
            // 마지막 것을 남기면 취소 대조가 효력 없는 행과 비교돼 부호 판정이 뒤집힌다
            if (grants.putIfAbsentCompat(row.idempotencyKey, row)) into += LedgerViolation.DUPLICATE_GRANT
        }

        val cancelled = mutableSetOf<String>()
        for (row in ordered.filter { it.type == RewardEventType.CANCELLATION }) {
            if (row.xp >= 0) into += LedgerViolation.CANCELLATION_NON_NEGATIVE_XP
            val grant = grants[row.idempotencyKey]
            if (grant == null) into += LedgerViolation.ORPHAN_CANCELLATION else checkAgainstGrant(row, grant, into)
            if (!cancelled.add(row.idempotencyKey)) into += LedgerViolation.DUPLICATE_CANCELLATION
        }
    }

    private fun checkAgainstGrant(cancellation: LedgerRow, grant: LedgerRow, into: MutableSet<LedgerViolation>) {
        if (cancellation.xp != -grant.xp) into += LedgerViolation.CANCELLATION_SIGN_MISMATCH
        if (cancellation.epochDay != grant.epochDay) into += LedgerViolation.CANCELLATION_DAY_MISMATCH
        if (cancellation.sequence < grant.sequence) into += LedgerViolation.CANCELLATION_BEFORE_GRANT
    }
}
