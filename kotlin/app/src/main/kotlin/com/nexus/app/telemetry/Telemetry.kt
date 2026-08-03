package com.nexus.app.telemetry

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nexus.app.BuildConfig
import com.telemetrydeck.sdk.TelemetryDeck

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
        if (appId.isBlank()) {
            Log.i(TAG, "app ID absent — telemetry off")
            return
        }
        val builder = TelemetryDeck.Builder()
            .appID(appId)
            .testMode(BuildConfig.DEBUG)
            .showDebugLogs(BuildConfig.DEBUG)
        TelemetryDeck.start(context.applicationContext, builder)
        enabled = true
    }

    /**
     * 계측 중단 (#349) — 동의를 끈 순간부터 아무것도 안 나간다.
     *
     * 플래그만 내리지 않고 SDK까지 멈추는 이유: 우리 래퍼를 안 거치는 신호(세션·수명주기)가
     * SDK 안에 있다. 플래그만 내리면 "껐는데 세션 신호는 계속 나가는" 상태가 된다.
     */
    fun stop() {
        enabled = false
        runCatching { TelemetryDeck.stop() }
            .onFailure { Log.w(TAG, "telemetry stop failed", it) }
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
}
