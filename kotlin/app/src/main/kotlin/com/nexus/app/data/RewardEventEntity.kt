package com.nexus.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * RewardEvent 원장 행 (#162, #19 스키마의 영속화) — **불변, UPDATE 금지**. 취소는 새 행 append.
 * (idempotencyKey, type) 유니크 인덱스가 멱등성을 DB 계약으로 강제한다 — 같은 HC 레코드의
 * 중복 지급·중복 취소는 INSERT IGNORE로 조용히 무시된다.
 * 원시 건강 수치는 저장하지 않는다 — 계산된 XP·provenance·산식 버전만(제품 불변식).
 */
@Entity(
    tableName = "reward_events",
    indices = [Index(value = ["idempotencyKey", "type"], unique = true)],
)
data class RewardEventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val idempotencyKey: String,
    val xp: Int,
    val type: String, // RewardEventType.name
    val dataOrigin: String,
    val recordingMethod: String, // RecordingMethod.name
    val formulaVersion: Int,
    val epochMillis: Long,
    /** 지급 기준일(사용자 시간대) — 일 상한 합산 키. 취소 행도 **지급일**을 보존해 정확히 상쇄. */
    val epochDay: Long,
)
