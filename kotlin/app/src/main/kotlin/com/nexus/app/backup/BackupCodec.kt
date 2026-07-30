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
    /**
     * 지어준 캐릭터 이름 (#216) — 설정값(건강 파생 아님)이라 백업에 포함한다. 기기 이전 시 애착의
     * 산물이 사라지지 않게. 미설정이면 null이라 복원 후에도 무명 카피 폴백.
     */
    val characterName: String? = null,
    /**
     * 완료 원정 수 (#204) — 앱이 센 계산값(건강 파생 아님). 탐험가 배지의 유일한 근거라 빠지면
     * 복원 후 그 배지만 소리 없이 사라진다(다른 배지는 원장·HC에서 재계산된다).
     */
    val expeditionsCompleted: Int = 0,
    /**
     * 설치 식별자 (#240) — **승계 앵커**. 복원 시 갓 생성된 로컬 UUID를 대체한다.
     *
     * 백업에 담는 이유: 백업의 목적이 승계이므로 앵커가 함께 넘어가야 의미가 있다. 본인 통제 표면이라
     * §2의 서버·제3자 경계에 걸리지 않는다(#51 결정과 같은 근거). 구버전 백업엔 없으므로 null 허용.
     */
    val installId: String? = null,
)

object BackupCodec {
    /**
     * 백업 스키마 버전.
     *
     * v2 (#240): `BackupSnapshot.installId` 추가. v1 파일은 그대로 읽힌다(필드 null → 로컬 UUID 유지).
     */
    const val VERSION = 2

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
