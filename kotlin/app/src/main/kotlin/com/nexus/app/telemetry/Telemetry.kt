package com.nexus.app.telemetry

import android.content.Context
import android.util.Log
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

    /** [com.nexus.app.NexusApp]에서 1회 호출. 앱 ID가 없으면 계측 전체가 no-op. */
    fun init(context: Context) {
        val appId = BuildConfig.TELEMETRYDECK_APP_ID
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
        if (!enabled) return
        TelemetryDeck.getInstance()?.signal(event.signal, params, null, null)
    }
}
