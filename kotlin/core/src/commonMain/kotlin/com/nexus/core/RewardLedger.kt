package com.nexus.core

/** 원장 이벤트 종류. */
enum class RewardEventType { GRANT, CANCELLATION }

/**
 * XP 지급의 불변 원장 이벤트 (#19, BACKEND.md §1). **절대 수정하지 않는다** — 취소는 보상 이벤트 append.
 * 서버가 "당시 산식"으로 재검산할 수 있도록 provenance·formulaVersion을 박제한다.
 */
data class RewardEvent(
    val sequence: Long, // ③ 단조 증가 전역 순서
    val idempotencyKey: String, // ① HC 레코드 id 기반 멱등성 키
    val xp: Int, // 지급(+) / 취소(-)
    val type: RewardEventType,
    val dataOrigin: String, // ② provenance — 패키지명
    val recordingMethod: RecordingMethod, // ② provenance — 기록 방식
    val formulaVersion: Int, // 재검산용 산식 버전
    val epochMillis: Long, // 발생 시각(호출자 주입 — core는 시계 비의존)
)

/**
 * Append-only 원장 (#19). 저장된 이벤트 불변 · 멱등 지급 · 취소는 보상 이벤트.
 * 영속화(Room)는 후속 — core는 순수 로직만.
 */
class RewardLedger {
    private val entries = mutableListOf<RewardEvent>()
    private val grantedKeys = mutableSetOf<String>()
    private val cancelledKeys = mutableSetOf<String>()
    private var nextSequence = 0L

    /** 불변 뷰(시퀀스 순). */
    fun events(): List<RewardEvent> = entries.toList()

    /** 지급 append. 같은 [idempotencyKey]는 **중복 무시**(멱등). 무시 시 null. */
    fun grant(
        idempotencyKey: String,
        xp: Int,
        dataOrigin: String,
        recordingMethod: RecordingMethod,
        formulaVersion: Int = XpEngine.FORMULA_VERSION,
        epochMillis: Long,
    ): RewardEvent? {
        if (idempotencyKey in grantedKeys) return null
        val event =
            RewardEvent(
                sequence = nextSequence++,
                idempotencyKey = idempotencyKey,
                xp = xp,
                type = RewardEventType.GRANT,
                dataOrigin = dataOrigin,
                recordingMethod = recordingMethod,
                formulaVersion = formulaVersion,
                epochMillis = epochMillis,
            )
        entries.add(event)
        grantedKeys.add(idempotencyKey)
        return event
    }

    /**
     * 취소(레코드 삭제 감지 — #8 DeletionChange). 기존 이벤트를 **수정하지 않고** 보상 이벤트 append.
     * 미지급·이미 취소된 키면 null.
     */
    fun cancel(
        idempotencyKey: String,
        epochMillis: Long,
    ): RewardEvent? {
        if (idempotencyKey !in grantedKeys || idempotencyKey in cancelledKeys) return null
        val grant = entries.first { it.idempotencyKey == idempotencyKey && it.type == RewardEventType.GRANT }
        val event =
            grant.copy(
                sequence = nextSequence++,
                xp = -grant.xp,
                type = RewardEventType.CANCELLATION,
                epochMillis = epochMillis,
            )
        entries.add(event)
        cancelledKeys.add(idempotencyKey)
        return event
    }

    /** 현재 누적 XP = 모든 이벤트 합(지급 − 취소). */
    fun totalXp(): Int = entries.sumOf { it.xp }
}
