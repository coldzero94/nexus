package com.nexus.core

/**
 * 진단 스냅샷이 담을 수 있는 값의 **종류** (#245).
 *
 * 숫자 타입이 **없는 게 핵심**이다. 원장 XP 합·걸음·세션 시간은 전부 수치이므로, 수치를 담을
 * 그릇을 아예 안 만들면 그 값들은 구조적으로 들어올 수 없다(불변식 ②·③). "안 넣기로 한다"가
 * 아니라 "넣을 자리가 없다" — 리뷰가 놓쳐도 타입이 막는다.
 *
 * 연속 실패 횟수처럼 정말 필요한 수치는 [Choice]로 **구간화**해서 넣는다. 구간화가 원래
 * 식별 위험을 없애는 방법이기도 하다.
 */
sealed interface DiagnosticsValue {
    /** 켜짐/꺼짐 — 권한·연결·기능 플래그. */
    data class Flag(val on: Boolean) : DiagnosticsValue

    /** enum 이름 — 상태·구간. 이름 자체가 페이로드라 건강 용어 검사를 받는다. */
    data class Choice(val name: String) : DiagnosticsValue

    /** 불투명 식별자 — 빌드 해시·설치 ID. 사람이 읽는 의미가 없어 값 검사는 하지 않는다. */
    data class Id(val value: String) : DiagnosticsValue
}

/**
 * 스냅샷에 실릴 수 있는 키의 전부 (#245).
 *
 * enum이라 **정의되지 않은 키는 애초에 넣을 수 없다.** 키를 늘리면 [DiagnosticsSnapshotTest]의
 * 고정 목록도 갱신해야 하므로 리뷰를 강제한다 — [FailureCategory]와 같은 장치다.
 *
 * @property kind 이 키가 받는 값의 종류. [DiagnosticsSnapshot.assemble]이 대조한다.
 */
enum class DiagnosticsKey(val kind: DiagnosticsKind) {
    /** 앱 버전·빌드 식별자 — 어느 빌드에서 난 일인지. */
    BUILD_ID(DiagnosticsKind.ID),

    /** 익명 설치 식별자(#240) — 같은 기기의 여러 제보를 묶는다. */
    INSTALL_ID(DiagnosticsKind.ID),

    /** Health Connect 가용성 ([HealthAvailability]). */
    HEALTH_AVAILABILITY(DiagnosticsKind.CHOICE),

    /** 필수 권한이 전부 있는가 ([ConnectGate.isConnected]). */
    REQUIRED_PERMISSIONS(DiagnosticsKind.FLAG),

    /** 선택 권한이 하나라도 빠졌는가 — "꺼진 능력"의 존재 여부만. */
    OPTIONAL_PERMISSIONS_MISSING(DiagnosticsKind.FLAG),

    /** 동기화 최신성 구간 ([SyncFreshnessBucket]) — 분 단위 값은 싣지 않는다. */
    SYNC_FRESHNESS(DiagnosticsKind.CHOICE),

    /** 마지막 처리된-실패 분류 ([FailureCategory]). */
    LAST_FAILURE(DiagnosticsKind.CHOICE),

    /** 연속 실패 구간 ([FailureStreak]) — 횟수 대신 구간. */
    FAILURE_STREAK(DiagnosticsKind.CHOICE),

    /** HC 변경 토큰을 갖고 있는가 — 없으면 다음 동기화가 전체 재조회다(#141). */
    HAS_CHANGES_TOKEN(DiagnosticsKind.FLAG),

    /** 원장이 비어 있는가 — 첫 데이터 대기(#213)와 로드 실패를 구분한다. */
    LEDGER_EMPTY(DiagnosticsKind.FLAG),

    /** 원장 불변식 위반이 있는가 ([LedgerIntegrity]). 어떤 위반인지는 싣지 않는다. */
    LEDGER_INTEGRITY_OK(DiagnosticsKind.FLAG),

    /** 원장에 박제된 산식 버전이 하나뿐인가 — 섞였으면 재검산 경계를 봐야 한다. */
    SINGLE_FORMULA_VERSION(DiagnosticsKind.FLAG),

    /** 배터리 최적화 예외를 받았는가 — 백그라운드 동기화 실패의 최대 원인. */
    BATTERY_UNRESTRICTED(DiagnosticsKind.FLAG),

    /** 시스템 애니메이션이 꺼져 있는가(#228) — 모션 관련 제보의 재현 조건. */
    REDUCE_MOTION(DiagnosticsKind.FLAG),
}

/** [DiagnosticsKey]가 받는 값의 종류 — [DiagnosticsValue] 변종과 1:1. */
enum class DiagnosticsKind { FLAG, CHOICE, ID }

/**
 * 동기화 최신성 **구간** (#245) — 진단에는 분 단위 수치 대신 이걸 싣는다.
 *
 * `minutesAgo`를 그대로 보내면 그 값이 "언제 걸었는지"를 말해준다 — 활동 시각은 건강 파생
 * 정보다. 구간은 운영 판단(정상/지연/방치)에 필요한 해상도만 남긴다.
 */
enum class SyncFreshnessBucket {
    /** 한 번도 동기화되지 않음. */
    NEVER,

    /** 지연 고지 임계 이내 — 정상. */
    FRESH,

    /** [SyncFreshness.DELAY_NOTICE_MINUTES] 초과 — 사용자에게 고지 중. */
    DELAYED,

    /** 하루 이상 — 백그라운드 작업이 죽었을 가능성. */
    STALE,
    ;

    companion object {
        private const val STALE_MINUTES = 24 * 60

        /** [SyncFreshness]를 구간으로 접는다. */
        fun of(freshness: SyncFreshness): SyncFreshnessBucket = when (freshness) {
            SyncFreshness.Never -> NEVER

            is SyncFreshness.Synced -> when {
                freshness.minutesAgo >= STALE_MINUTES -> STALE
                freshness.minutesAgo > SyncFreshness.DELAY_NOTICE_MINUTES -> DELAYED
                else -> FRESH
            }
        }
    }
}

/**
 * 연속 실패 **구간** (#245) — 횟수 대신 이걸 싣는다.
 *
 * 1회는 일시적 잡음이고, 3회 이상은 우리가 봐야 하는 신호다. 그 사이의 정확한 숫자로 달라지는
 * 운영 판단이 없어 구간이면 충분하다.
 */
enum class FailureStreak {
    /** 실패 없음. */
    NONE,

    /** 1~2회 — 재시도로 풀릴 수 있는 범위. */
    TRANSIENT,

    /** 3회 이상 — 지속 실패. 사람이 봐야 한다. */
    PERSISTENT,
    ;

    companion object {
        /** 지속 실패로 보는 최소 연속 횟수. */
        const val PERSISTENT_THRESHOLD = 3

        /** 연속 실패 횟수를 구간으로 접는다. 음수는 0으로 본다. */
        fun of(consecutiveFailures: Int): FailureStreak = when {
            consecutiveFailures <= 0 -> NONE
            consecutiveFailures < PERSISTENT_THRESHOLD -> TRANSIENT
            else -> PERSISTENT
        }
    }
}

/**
 * 진단 스냅샷 조립 (#245, E15-12) — 테스터 제보에 붙일 **비-건강 상태 덤프**.
 *
 * ## 왜 필요했나
 *
 * 원격 알파에서 "걸음이 안 올라와요"라는 제보에 우리가 볼 수 있는 게 0이었다. HC 가용성,
 * 권한 상태, 토큰 보유, 마지막 실패 분류는 **이미 폰에 영속돼 있는데** 꺼낼 길이 없었다.
 * USB를 꽂아야 logcat이 보이는 원격 테스터에게는 존재하지 않는 정보와 같다.
 *
 * ## 무엇을 담지 않는가
 *
 * 걸음 수·세션 시간·XP 합·심박은 담지 않는다. 안 담기로 **결정**한 게 아니라 [DiagnosticsValue]에
 * 수치 그릇이 없어서 담을 수 없다. 대신 "원장이 비었는가", "위반이 있는가" 같은 **판정 결과**만
 * 싣는다 — 진단에 필요한 건 값이 아니라 상태다.
 *
 * ## 스크럽은 호출측 책임
 *
 * [DiagnosticsValue.Id]에 들어가는 빌드 문자열 같은 자유텍스트는 app 계층의 CrashScrubber(#201)를
 * 통과한 뒤 들어와야 한다. core는 스크러버를 갖지 않는다(Android 의존).
 */
object DiagnosticsSnapshot {

    /**
     * 필드를 사람이 읽는 `key=value` 맵으로 조립한다.
     *
     * @throws IllegalArgumentException 키가 선언한 종류와 값 종류가 다르거나, [DiagnosticsValue.Choice]
     *   이름이 원시 건강 용어일 때. 던지는 쪽을 택한 이유는 이 조립이 **디버그·제보 경로에서만**
     *   불리고, 조용히 필드를 버리면 없는 상태를 정상으로 오독하게 되기 때문이다.
     */
    fun assemble(fields: Map<DiagnosticsKey, DiagnosticsValue>): Map<String, String> =
        fields.entries.associate { (key, value) ->
            require(value.kind() == key.kind) {
                "진단 키 ${key.name}은 ${key.kind}를 받는데 ${value.kind()}가 들어왔다"
            }
            if (value is DiagnosticsValue.Choice) {
                require(!HealthTermDenylist.isRawHealthTerm(value.name)) {
                    "진단 값이 원시 건강 항목을 가리킨다: ${value.name} (불변식 ②)"
                }
            }
            key.name to value.render()
        }

    /** 공유·저장용 평문. 키 순서는 [DiagnosticsKey] 선언 순 — 제보끼리 눈으로 비교하기 쉽게. */
    fun render(fields: Map<DiagnosticsKey, DiagnosticsValue>): String {
        val assembled = assemble(fields)
        return DiagnosticsKey.entries.mapNotNull { key ->
            assembled[key.name]?.let { "${key.name}=$it" }
        }.joinToString("\n")
    }

    private fun DiagnosticsValue.kind(): DiagnosticsKind = when (this) {
        is DiagnosticsValue.Flag -> DiagnosticsKind.FLAG
        is DiagnosticsValue.Choice -> DiagnosticsKind.CHOICE
        is DiagnosticsValue.Id -> DiagnosticsKind.ID
    }

    private fun DiagnosticsValue.render(): String = when (this) {
        is DiagnosticsValue.Flag -> on.toString()
        is DiagnosticsValue.Choice -> name
        is DiagnosticsValue.Id -> value
    }
}
