package com.nexus.app.data

import com.nexus.app.health.ExerciseSummary
import com.nexus.core.LedgerMath
import com.nexus.core.RecordingMethod
import com.nexus.core.RewardEventType
import com.nexus.core.TrustPolicy
import com.nexus.core.XpEngine
import java.time.ZoneId

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

    /**
     * 세션 목록 멱등 지급 (#163) — 워커(백그라운드)와 화면(로드 시)이 같은 진입점을 쓴다.
     * 화면이 호출해도 안전: 같은 키는 DB가 무시하므로 원장은 항상 "본 세션까지" 상태.
     * 지급 XP = 기본점수 × 개인 신뢰 계수(무상한 — 상한은 합산 시점, LedgerMath).
     */
    suspend fun grantSessions(sessions: List<ExerciseSummary>, zone: ZoneId, epochMillis: Long) {
        sessions.forEach { session ->
            val type = session.type ?: return@forEach
            if (!TrustPolicy.isXpEligible(session.trustTier)) return@forEach
            grant(
                idempotencyKey = session.id,
                xp = (
                    XpEngine.baseScore(type, session.durationMinutes.toInt()) *
                        session.trustTier.personalXpMultiplier
                    ).toInt(),
                dataOrigin = session.dataOrigin,
                recordingMethod = session.recordingMethod,
                epochMillis = epochMillis,
                epochDay = session.start.atZone(zone).toLocalDate().toEpochDay(),
            )
        }
    }

    /** 특정 일자의 상한 적용 XP (#36 아침 카드 "어제의 성장"). */
    suspend fun cappedXpOn(epochDay: Long): Int =
        LedgerMath.cappedTotalXp(dao.xpByDay().filter { it.epochDay == epochDay }.associate { it.epochDay to it.xp })

    /** 표시용 누적 XP — 일 상한 적용(core LedgerMath). */
    suspend fun cappedTotalXp(): Int = LedgerMath.cappedTotalXp(dao.xpByDay().associate { it.epochDay to it.xp })

    /** 일별 순 XP 맵 (#214 기세) — 그날 순 XP>0이면 활동일. 취소로 순합≤0인 날은 비활동. */
    suspend fun dailyXpMap(): Map<Long, Double> = dao.xpByDay().associate { it.epochDay to it.xp }
}
