package com.nexus.app.telemetry

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nexus.app.BuildConfig
import com.telemetrydeck.sdk.TelemetryDeck
import java.io.File

private const val TAG = "Telemetry"

/**
 * TelemetryDeck 래퍼 (#46, E8-1) — 앱 코드는 SDK를 직접 만지지 않고 이 객체만 쓴다
 * (무료 50k/월 초과 시 Aptabase 이전이 쉬워야 한다 — STACK.md §8).
 *
 * - 보낼 수 있는 신호는 [TelemetryEvent]뿐, 파라미터는 [TelemetryPolicy] 통과분만.
 * - SDK의 `floatValue`(숫자 채널)는 **의도적으로 노출하지 않는다** — 건강 수치가 실릴 유일한
 *   숫자 통로를 래퍼에서 봉인.
 * - 앱 ID 미설정(빈 값)이면 어떤 신호도 만들지 않는다 — 동의·설정 전 자동 수집 없음.
 */
object Telemetry {

    @Volatile
    private var enabled = false

    /** 지금 신호를 보내는 상태인가 (#349) — 동의 토글 테스트가 읽는다. */
    @get:VisibleForTesting
    val isActive: Boolean get() = enabled

    /**
     * [com.nexus.app.settings.AnalyticsConsent]가 부른다. 앱 ID가 없으면 계측 전체가 no-op.
     *
     * 두 번 불러도 안전해야 한다 — 동의를 껐다 켜면 다시 온다.
     */
    fun init(context: Context, appId: String = BuildConfig.TELEMETRYDECK_APP_ID) {
        if (enabled) return
        if (appId.isBlank()) {
            Log.i(TAG, "app ID absent — telemetry off")
            return
        }
        // 기동 전체를 감싼다 — 앱 ID가 UUID가 아니면 **빌더 단계에서** 던지고, 그게 설정 화면의
        // 토글 람다까지 올라간다. 프라이버시 스위치를 누르다 앱이 죽는 건 최악의 실패 모양이다
        runCatching {
            val builder = TelemetryDeck.Builder()
                .appID(appId)
                .testMode(BuildConfig.DEBUG)
                .showDebugLogs(BuildConfig.DEBUG)
            TelemetryDeck.start(context.applicationContext, builder)
        }
            // SDK에게 물어서 정한다 — CrashReporting과 같은 이유(#349 리뷰). 던지는 경로는
            // 테스트가 고정하고, 이 형태는 '조용히 꺼지는' 경우까지 덮는 방어다
            .onSuccess { enabled = TelemetryDeck.getInstance() != null }
            .onFailure { Log.w(TAG, "telemetry start failed", it) }
    }

    /**
     * 계측 중단 (#349) — 동의를 끈 순간부터 아무것도 안 나간다.
     *
     * 플래그만 내리지 않고 SDK까지 멈추는 이유: 우리 래퍼를 안 거치는 신호(세션·수명주기)가
     * SDK 안에 있다. 플래그만 내리면 "껐는데 세션 신호는 계속 나가는" 상태가 된다.
     *
     * **큐까지 지운다.** `TelemetryDeck.stop()`은 flush도 clear도 하지 않는다 — 아직 못 올린
     * 신호가 `cacheDir/telemetrydeck.json`에 그대로 남고, `PersistentSignalCache`는 생성자에서
     * 그 파일을 다시 읽는다. 즉 껐다가 다시 켜는 순간 **철회 이전 신호가 나간다**(#349 리뷰가
     * 파일 크기로 확인). 철회는 큐를 세워두는 게 아니라 버리는 것이다.
     *
     * 설치 식별자(`filesDir/telemetrydeckid`)도 함께 지운다. 남겨두면 껐다 켠 전후 데이터가
     * 같은 id로 이어져, 철회가 "잠깐 멈춤"이 된다.
     */
    fun stop(context: Context) {
        enabled = false
        runCatching { TelemetryDeck.stop() }
            .onFailure { Log.w(TAG, "telemetry stop failed", it) }
        runCatching {
            File(context.cacheDir, SIGNAL_CACHE_FILE).delete()
            File(context.filesDir, IDENTITY_FILE).delete()
        }.onFailure { Log.w(TAG, "telemetry queue cleanup failed", it) }
    }

    /**
     * 이벤트 기록 — 정책 위반 파라미터가 있으면 **이벤트째 버린다**(부분 전송 없음).
     * 디버그 빌드에선 즉시 크래시로 개발 중에 잡는다.
     */
    fun record(event: TelemetryEvent, params: Map<String, String> = emptyMap()) {
        val violations = TelemetryPolicy.violations(params)
        if (violations.isNotEmpty()) {
            check(!BuildConfig.DEBUG) { "telemetry policy violation: $violations" }
            Log.w(TAG, "event dropped by policy: ${event.signal} $violations")
            return
        }
        // 발생 사실만 로그 — QA가 앱 ID 없이도 콜사이트를 검증하는 통로 (#47, 디버그 한정)
        if (BuildConfig.DEBUG) Log.i(TAG, "signal: ${event.signal}")
        if (!enabled) return
        TelemetryDeck.getInstance()?.signal(event.signal, params, null, null)
    }

    /**
     * 사용자당 1회 이벤트(퍼널 전환점, #47). 마킹은 **실제 전송된 때만** — 앱 ID 없이 발생한
     * 전환이 영구 소실되지 않고, ID가 켜진 뒤 첫 재발생 때 전송된다(첫 XP처럼 재발생
     * 경로가 있는 신호에 한함 — 알파는 처음부터 ID를 켜므로 실무 영향 없음).
     */
    fun recordOnce(context: Context, event: TelemetryEvent) {
        val prefs = context.getSharedPreferences(FIRSTS_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(event.signal, false)) return
        record(event)
        if (enabled) prefs.edit().putBoolean(event.signal, true).apply()
    }

    /** 첫-발화 기록 prefs 이름 — 디버그 도구(#245)가 재현을 위해 초기화한다. */
    internal const val FIRSTS_PREFS = "nexus_telemetry_firsts"

    /** SDK가 못 올린 신호를 쌓아두는 파일 (TelemetryDeck `PersistentSignalCache`). */
    private const val SIGNAL_CACHE_FILE = "telemetrydeck.json"

    /** SDK가 만드는 설치 식별자 파일 (`FileUserIdentityProvider`). */
    private const val IDENTITY_FILE = "telemetrydeckid"
}
