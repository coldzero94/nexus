package com.nexus.app.data

import com.nexus.core.LedgerMath
import com.nexus.core.RecordingMethod
import com.nexus.core.RewardEventType
import com.nexus.core.XpEngine

/**
 * 원장 영속 저장소 (#162) — core [com.nexus.core.RewardLedger]와 같은 계약
 * (append-only·멱등·취소는 보상 행)을 Room 위에서 제공한다. 멱등성은 DB 유니크
 * 제약(INSERT IGNORE)이 강제 — 프로세스가 여럿이어도 안전.
 */
class RewardLedgerRepository(private val dao: RewardEventDao) {

    /** 지급 append — 이미 지급된 키면 false(멱등 무시). */
    suspend fun grant(
        idempotencyKey: String,
        xp: Int,
        dataOrigin: String,
        recordingMethod: RecordingMethod,
        epochMillis: Long,
        epochDay: Long,
        formulaVersion: Int = XpEngine.FORMULA_VERSION,
    ): Boolean = dao.insert(
        RewardEventEntity(
            idempotencyKey = idempotencyKey,
            xp = xp,
            type = RewardEventType.GRANT.name,
            dataOrigin = dataOrigin,
            recordingMethod = recordingMethod.name,
            formulaVersion = formulaVersion,
            epochMillis = epochMillis,
            epochDay = epochDay,
        ),
    ) != -1L

    /**
     * 취소 append(삭제 감지 #133) — 지급이 없으면 false. 취소 행은 지급의 XP 부호 반전과
     * **지급일**을 보존해 일 상한 합산에서 정확히 상쇄된다. 중복 취소는 유니크 제약이 무시.
     */
    suspend fun cancel(idempotencyKey: String, epochMillis: Long): Boolean {
        val grant = dao.grantOf(idempotencyKey) ?: return false
        return dao.insert(
            grant.copy(
                sequence = 0, // autoGenerate
                xp = -grant.xp,
                type = RewardEventType.CANCELLATION.name,
                epochMillis = epochMillis,
            ),
        ) != -1L
    }

    /** 표시용 누적 XP — 일 상한 적용(core LedgerMath). */
    suspend fun cappedTotalXp(): Int = LedgerMath.cappedTotalXp(dao.xpByDay().associate { it.epochDay to it.xp })
}
