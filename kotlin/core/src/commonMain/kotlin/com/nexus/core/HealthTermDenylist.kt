package com.nexus.core

/**
 * 원시 건강 항목을 가리키는 용어 사전 (#238) — 기기를 떠나는 모든 표면이 **같은 목록**을 본다.
 *
 * 불변식: 원시 건강 수치(걸음·운동 시간·심박 등)는 기기를 떠나지 않는다. 나가는 표면은 셋이고
 * 각각 강제 장치가 있어야 한다 — 계측([com.nexus.app.telemetry.TelemetryPolicy]), 크래시(스크러버),
 * 백업(#51 `BackupCodec`). 목록이 표면마다 따로 있으면 한쪽만 갱신되는 순간 구멍이 생긴다.
 *
 * ## 계산값은 금지 대상이 아니다
 *
 * `xp`·`formulaVersion`·`level`은 원시 수치가 아니라 앱이 계산한 결과다. 백업·서버 페이로드가
 * 나르는 건 이것들이고(BACKEND §1), 이게 금지되면 백업 자체가 불가능해진다. 그래서 [isRawHealthTerm]은
 * **원시 항목만** 잡고, 계산값 이름은 [COMPUTED_ALLOWED]로 명시해 둔다.
 */
object HealthTermDenylist {
    /** 원시 건강 항목 어휘 — 필드·키 이름에 부분 문자열로 들어가면 잡는다. */
    val RAW_TERMS: List<String> = listOf(
        "step", "walk", "run", "workout", "exercise", "duration", "minute",
        "heart", "bpm", "hr", "sleep", "calorie", "distance",
    )

    /**
     * 계산값이라 허용되는 이름 — 원시 항목이 아니다.
     *
     * `dataOrigin`·`recordingMethod`는 신뢰 등급 판정의 근거(패키지명·기록 방식)로 수치가 아니고,
     * `idempotencyKey`는 중복 지급 방지용 참조다(#51에서 본인 계정 백업 한정으로 허용, 해시화는 #198).
     */
    val COMPUTED_ALLOWED: Set<String> = setOf(
        "xp",
        "formulaVersion",
        "level",
        "dataOrigin",
        "recordingMethod",
        "idempotencyKey",
    )

    /**
     * 이 이름이 원시 건강 항목을 가리키는가 — 대소문자 무시 부분 일치.
     *
     * [COMPUTED_ALLOWED]에 정확히 일치하는 이름은 먼저 통과시킨다: 그러지 않으면 `hr`가
     * 여러 단어에 우연히 포함돼(예: `t**hr**eshold`) 계산값까지 잡힌다.
     */
    fun isRawHealthTerm(name: String): Boolean {
        if (name in COMPUTED_ALLOWED) return false
        val lower = name.lowercase()
        return RAW_TERMS.any { lower.contains(it) }
    }
}
