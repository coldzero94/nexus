package com.nexus.app.diag

import android.content.Context
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardEventEntity
import com.nexus.core.RecordingMethod
import com.nexus.core.RewardEventType
import com.nexus.core.XpEngine
import java.time.LocalDate
import java.time.ZoneId

/**
 * 디버그 원장 시더 (#245) — **합성 수치만** 넣는다.
 *
 * ## 왜 Health Connect를 안 건드리나
 *
 * 걸음·세션을 실제로 만들어 동기화를 태우면 그 값이 원장에 박제되고, 테스터 기기에서 실제 활동과
 * 구분되지 않는다. 원장은 append-only라 나중에 골라낼 수도 없다. 그래서 시드는 원장에 직접
 * 쓰고, 키에 [SYNTHETIC_PREFIX]를 박아 언제든 식별되게 한다.
 *
 * ## 왜 위반까지 심는가
 *
 * [seedMixedFormulaDay]·[seedOrphanCancellation]은 **일부러 원장을 깨뜨린다.** [LedgerIntegrityGuard]가
 * 실제로 잡는지 확인할 방법이 이것밖에 없다 — 검사기를 만들어놓고 검사기가 도는지 못 보면
 * 그 검사기는 없는 것과 같다. 디버그 빌드에서만 존재하므로 사용자 원장에는 닿지 않는다.
 *
 * 확인 방법: 심고 나서 카드의 '다시 읽기'로 위반이 뜨는지 보고, '무결성 검사(위반 시 크래시)'로
 * [LedgerIntegrityGuard.verifyOrCrash]의 **의도된 크래시**까지 받는다. 시작·동기화 경로는 보고만
 * 하므로(그래야 이 시드를 되돌릴 '원장 전체 삭제' 버튼에 닿을 수 있다) 크래시는 여기서만 난다.
 */
internal object DevLedgerSeeder {
    /** 합성 행 식별 접두어 — 실제 HC 레코드 id와 절대 겹치지 않는다. */
    const val SYNTHETIC_PREFIX = "synthetic-"

    /** [seedWeek]가 채우는 일수. */
    const val SEED_DAYS = 7

    private const val SYNTHETIC_ORIGIN = "com.nexus.app.debug.synthetic"
    private const val SYNTHETIC_XP = 40

    /** 최근 [SEED_DAYS]일에 하루 한 건씩 합성 지급. 같은 날 두 번 눌러도 멱등(키가 날짜로 결정). */
    suspend fun seedWeek(context: Context) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toEpochDay()
        val dao = NexusDatabase.get(context).rewardEventDao()

        for (back in 0 until SEED_DAYS) {
            val day = today - back
            dao.insert(row(key = "${SYNTHETIC_PREFIX}day-$day", day = day))
        }
    }

    /**
     * 같은 날에 다른 산식 버전을 섞는다 — [com.nexus.core.LedgerViolation.MIXED_FORMULA_VERSION_IN_DAY] 재현.
     *
     * 마이그레이션이 버전 경계를 자정에 맞추지 않으면 실제로 이 상태가 된다.
     */
    suspend fun seedMixedFormulaDay(context: Context) {
        val day = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val dao = NexusDatabase.get(context).rewardEventDao()

        dao.insert(row(key = "${SYNTHETIC_PREFIX}mixed-current", day = day))
        dao.insert(
            row(key = "${SYNTHETIC_PREFIX}mixed-next", day = day, formulaVersion = XpEngine.FORMULA_VERSION + 1),
        )
    }

    /**
     * 지급 없는 취소를 넣는다 — [com.nexus.core.LedgerViolation.ORPHAN_CANCELLATION] 재현.
     *
     * 백업 병합이나 부분 복원이 지급 행만 빠뜨리면 이 상태가 된다(#133 경로).
     */
    suspend fun seedOrphanCancellation(context: Context) {
        val day = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        NexusDatabase.get(context).rewardEventDao().insert(
            row(
                key = "${SYNTHETIC_PREFIX}orphan",
                day = day,
                xp = -SYNTHETIC_XP,
                type = RewardEventType.CANCELLATION,
            ),
        )
    }

    private fun row(
        key: String,
        day: Long,
        xp: Int = SYNTHETIC_XP,
        type: RewardEventType = RewardEventType.GRANT,
        formulaVersion: Int = XpEngine.FORMULA_VERSION,
    ) = RewardEventEntity(
        idempotencyKey = key,
        xp = xp,
        type = type.name,
        dataOrigin = SYNTHETIC_ORIGIN,
        recordingMethod = RecordingMethod.MANUAL_ENTRY.name,
        formulaVersion = formulaVersion,
        epochMillis = day * MILLIS_PER_DAY,
        epochDay = day,
    )

    private const val MILLIS_PER_DAY = 86_400_000L
}
