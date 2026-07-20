package com.nexus.app.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 백업 파일 스키마 (#51, E8-6 — BACKEND.md §3 방안 1) — **계산된 값만** 담는다:
 * RewardEvent 원장(XP 산출값·산식 버전·provenance)과 소비 기준점 스냅샷.
 * 원본 건강 수치(걸음·시간·심박·수면)는 어떤 필드에도 싣지 않는다 — Health Connect가
 * 원본의 단일 진실 소스라 백업 대상 자체가 아니다. 스키마 변경 = [BackupCodec.VERSION]
 * 증가 + BackupCodecTest 키 고정 갱신.
 */
@Serializable
data class BackupPayload(
    val backupVersion: Int,
    val exportedAtEpochMillis: Long,
    val events: List<BackupEvent>,
    val snapshot: BackupSnapshot,
)

/** 원장 이벤트 — [com.nexus.app.data.RewardEventEntity]와 1:1(sequence 제외 — 복원 시 재부여). */
@Serializable
data class BackupEvent(
    val idempotencyKey: String,
    val xp: Int,
    val type: String,
    val dataOrigin: String,
    val recordingMethod: String,
    val formulaVersion: Int,
    val epochMillis: Long,
    val epochDay: Long,
)

/** 원장 밖 스칼라 상태 — 소비 기준점·설정. 전부 계산값/설정값이다. */
@Serializable
data class BackupSnapshot(
    val energyTotalSpent: Int = 0,
    val expeditionStartedAtMillis: Long? = null,
    val settlementLastSeenXp: Int? = null,
    val morningLastShownEpochDay: Long? = null,
    val journalLastShownEpochDay: Long? = null,
    val weeklyGoalDays: Int? = null,
    val restModeEnabled: Boolean = false,
    /** 휴식 시작일 — 없으면 복원일로 폴백. 소급 면제 방지 계약(#31)을 복원 후에도 지킨다(#51 리뷰 F2). */
    val restModeSinceEpochDay: Long? = null,
)

object BackupCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true // 미래 마이너 확장 필드는 무시하고 읽는다
        encodeDefaults = true
    }

    fun encode(payload: BackupPayload): String = json.encodeToString(BackupPayload.serializer(), payload)

    /** @throws IllegalArgumentException 손상된 JSON(SerializationException 포함)·미지원 버전. */
    fun decode(text: String): BackupPayload {
        val payload = json.decodeFromString(BackupPayload.serializer(), text)
        require(payload.backupVersion in 1..VERSION) { "지원하지 않는 백업 버전: ${payload.backupVersion}" }
        return payload
    }
}
