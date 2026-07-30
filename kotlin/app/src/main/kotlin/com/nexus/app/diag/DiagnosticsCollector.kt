package com.nexus.app.diag

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.nexus.app.BuildConfig
import com.nexus.app.data.InstallIdStore
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardEventEntity
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.health.HealthPermissions
import com.nexus.app.health.TokenStore
import com.nexus.core.ConnectGate
import com.nexus.core.DiagnosticsKey
import com.nexus.core.DiagnosticsSnapshot
import com.nexus.core.DiagnosticsValue
import com.nexus.core.FailureStreak
import com.nexus.core.LedgerIntegrity
import com.nexus.core.LedgerRow
import com.nexus.core.RewardEventType
import com.nexus.core.SyncFreshness
import com.nexus.core.SyncFreshnessBucket

/**
 * 진단 스냅샷 수집 (#245, E15-12) — 폰에 이미 영속된 상태를 [DiagnosticsSnapshot]으로 꺼낸다.
 *
 * ## 왜 이 파일이 필요했나
 *
 * 여기 담기는 값은 **하나도 새로 만들지 않는다.** HC 가용성·권한·토큰 보유·마지막 실패 분류는
 * 전부 이미 저장돼 있었다. 없던 건 그걸 **꺼내는 길**이다 — 원격 테스터에게 logcat은 존재하지
 * 않는 정보와 같아서, "걸음이 안 올라와요" 제보에 우리가 볼 수 있는 게 0이었다.
 *
 * ## 수치는 구조적으로 못 들어온다
 *
 * [DiagnosticsValue]에 수치 그릇이 없다. 그래서 걸음 수·XP 합·세션 시간은 담기지 않고, 대신
 * "원장이 비었는가" 같은 **판정 결과**만 싣는다. 원장 행도 [LedgerRow]로 투영해서 넘기므로
 * provenance·XP 값이 스냅샷 경로에 흘러들 자리가 없다.
 */
object DiagnosticsCollector {

    /**
     * 현재 상태를 수집한다. Room·HC 권한 조회 때문에 suspend다.
     *
     * @param nowMillis 최신성 계산 기준 시각 — 호출자가 주입한다(테스트 결정성).
     */
    suspend fun collect(
        context: Context,
        manager: HealthConnectManager,
        nowMillis: Long,
    ): Map<DiagnosticsKey, DiagnosticsValue> {
        val tokens = TokenStore(context)
        // 실패를 emptySet으로 접으면 "권한 없음"과 구분되지 않는다 — 진단 순서상 두 번째로 보는
        // 값이라, 권한이 멀쩡한 테스터에게 재승인을 안내하게 된다. 못 읽었으면 키를 빼는 게 맞다.
        val granted = runCatching { manager.grantedPermissions() }.getOrNull()
        val rows = runCatching { ledgerRows(context) }.getOrNull()

        return buildMap {
            put(DiagnosticsKey.BUILD_ID, DiagnosticsValue.Id(buildId()))
            put(DiagnosticsKey.INSTALL_ID, DiagnosticsValue.Id(InstallIdStore(context).installId))
            put(DiagnosticsKey.HEALTH_AVAILABILITY, DiagnosticsValue.Choice(manager.availability().name))
            putPermissions(granted)
            put(
                DiagnosticsKey.SYNC_FRESHNESS,
                DiagnosticsValue.Choice(freshnessBucket(tokens.lastSyncEpochMillis, nowMillis)),
            )
            tokens.lastFailureCategory?.let { put(DiagnosticsKey.LAST_FAILURE, DiagnosticsValue.Choice(it)) }
            put(
                DiagnosticsKey.FAILURE_STREAK,
                DiagnosticsValue.Choice(FailureStreak.of(tokens.consecutiveFailures).name),
            )
            put(DiagnosticsKey.HAS_CHANGES_TOKEN, DiagnosticsValue.Flag(tokens.changesToken != null))
            putLedger(rows)
            put(DiagnosticsKey.BATTERY_UNRESTRICTED, DiagnosticsValue.Flag(batteryUnrestricted(context)))
            put(DiagnosticsKey.REDUCE_MOTION, DiagnosticsValue.Flag(reduceMotion(context)))
        }
    }

    /** 평문 스냅샷 — 공유 시트·파일로 그대로 나가는 문자열. */
    suspend fun renderText(context: Context, manager: HealthConnectManager, nowMillis: Long): String =
        DiagnosticsSnapshot.render(collect(context, manager, nowMillis))

    /**
     * 원장을 무결성 검사용 투영으로 읽는다 — `epochDay`·부호·시퀀스만.
     *
     * 알 수 없는 `type` 문자열은 GRANT로 접지 않고 예외를 낸다. 조용히 접으면 저장된 쓰레기값이
     * "정상 지급"으로 세어져 무결성 검사가 통과해버린다 — 이 검사가 막으려던 바로 그 상황이다.
     */
    suspend fun ledgerRows(context: Context): List<LedgerRow> =
        NexusDatabase.get(context).rewardEventDao().all().map { it.toLedgerRow() }

    private fun RewardEventEntity.toLedgerRow() = LedgerRow(
        sequence = sequence,
        idempotencyKey = idempotencyKey,
        xp = xp,
        type = RewardEventType.valueOf(type),
        formulaVersion = formulaVersion,
        epochDay = epochDay,
    )

    /** 권한 플래그 — 못 읽었으면(null) 두 키를 모두 뺀다. [putLedger]와 같은 정책. */
    private fun MutableMap<DiagnosticsKey, DiagnosticsValue>.putPermissions(granted: Set<String>?) {
        if (granted == null) return
        put(
            DiagnosticsKey.REQUIRED_PERMISSIONS,
            DiagnosticsValue.Flag(ConnectGate.isConnected(granted, HealthPermissions.REQUIRED)),
        )
        put(
            DiagnosticsKey.OPTIONAL_PERMISSIONS_MISSING,
            DiagnosticsValue.Flag(ConnectGate.missingOptional(granted, HealthPermissions.OPTIONAL).isNotEmpty()),
        )
    }

    private fun MutableMap<DiagnosticsKey, DiagnosticsValue>.putLedger(rows: List<LedgerRow>?) {
        // 원장을 못 읽었으면 플래그를 아예 넣지 않는다 — false로 채우면 "정상"으로 오독된다
        if (rows == null) return
        put(DiagnosticsKey.LEDGER_EMPTY, DiagnosticsValue.Flag(rows.isEmpty()))
        put(DiagnosticsKey.LEDGER_INTEGRITY_OK, DiagnosticsValue.Flag(LedgerIntegrity.check(rows).isEmpty()))
        put(
            DiagnosticsKey.SINGLE_FORMULA_VERSION,
            DiagnosticsValue.Flag(rows.distinctBy { it.formulaVersion }.size <= 1),
        )
    }

    private fun freshnessBucket(lastSyncMillis: Long, nowMillis: Long): String {
        val freshness = if (lastSyncMillis <= 0L) {
            SyncFreshness.Never
        } else {
            SyncFreshness.Synced(((nowMillis - lastSyncMillis).coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt())
        }
        return SyncFreshnessBucket.of(freshness).name
    }

    /**
     * 빌드 식별자.
     *
     * `CrashScrubber`를 통과시키지 **않는다** — 스크러버는 연속 숫자를 `#`으로 지우므로
     * `0.1.0-42`가 `#.#.#-#`이 되어 식별자로서 쓸모가 사라진다. 여기 들어가는 값은 우리가
     * `BuildConfig`에서 만드는 상수라 건강 데이터가 닿는 경로가 없다.
     */
    private fun buildId(): String = "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"

    private const val MILLIS_PER_MINUTE = 60_000L

    private fun batteryUnrestricted(context: Context): Boolean = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun reduceMotion(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
}
