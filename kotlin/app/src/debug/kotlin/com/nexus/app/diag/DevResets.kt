package com.nexus.app.diag

import android.content.Context
import com.nexus.app.data.NexusDatabase
import com.nexus.app.health.TokenStore
import com.nexus.app.telemetry.Telemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 디버그 클린 리셋 (#245) — 재현을 위해 "처음 상태"로 되돌린다.
 *
 * ## 왜 디버그 소스셋에만 두나
 *
 * 여기 있는 건 전부 **되돌릴 수 없는 삭제**다. 릴리스에 존재하지 않아야 하는 코드의 정의에 정확히
 * 맞는다. main에 두고 `BuildConfig.DEBUG`로 감싸면 원장 삭제 코드가 릴리스 APK에 실린다 —
 * crown jewel 옆에 둘 물건이 아니다.
 *
 * Room의 `clearAllTables()`를 쓰고 DAO에 삭제 쿼리를 추가하지 않는다. DAO는 main 소스셋이라
 * 삭제 메서드를 넣는 순간 프로덕션 코드가 원장을 지울 수 있게 된다.
 */
internal object DevResets {

    /**
     * Changes 토큰을 지우고 리셋 마커를 기록한다 — #141(토큰 만료 델타 유실) 재현.
     *
     * 토큰만 지우는 게 핵심이다. `lastSync`를 함께 지우면 "한 번도 동기화 안 함"이 되어 다른
     * 경로(#213 첫 데이터 대기)가 타므로 재현하려던 상황이 아니게 된다.
     */
    fun resetChangesToken(context: Context) {
        val store = TokenStore(context)
        store.changesToken = null
        store.recordTokenReset(System.currentTimeMillis())
    }

    /**
     * 원장 전체 삭제 — DB의 모든 테이블. 되돌릴 수 없다.
     *
     * `clearAllTables()`는 suspend가 아닌 블로킹 호출이라 IO로 넘긴다. 카드가 `rememberCoroutineScope`
     * (= Main)에서 부르므로 그대로 두면 Room이 "Cannot access database on the main thread"로 던진다.
     *
     * 무결성 마커도 함께 지운다 — 원장이 사라지면 그 관측은 무의미하고, 남겨두면 깨끗한 원장에
     * 옛 위반이 붙어 있어 다음 재현을 오독한다.
     */
    suspend fun clearLedger(context: Context) = withContext(Dispatchers.IO) {
        NexusDatabase.get(context).clearAllTables()
        IntegrityMarkerStore(context).clear()
    }

    /** 동기화 상태(토큰·마지막 시각·실패 카운터) 전체 초기화 — 신규 설치와 같은 상태. */
    fun clearSyncState(context: Context) {
        context.getSharedPreferences(TokenStore.PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * 계측 첫-발화 기록 초기화 — `recordOnce` 이벤트를 다시 발화시킨다.
     *
     * 온보딩 퍼널(#226)을 다시 태우려면 이게 필요하다. 앱 재설치로도 되지만 그러면 원장까지
     * 날아가서 "원장은 있고 퍼널만 처음" 상태를 만들 수 없다.
     */
    fun clearTelemetryFirsts(context: Context) {
        context.getSharedPreferences(Telemetry.FIRSTS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
