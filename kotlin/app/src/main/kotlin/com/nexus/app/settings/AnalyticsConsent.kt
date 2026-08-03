package com.nexus.app.settings

import android.content.Context
import com.nexus.app.crash.CrashReporting
import com.nexus.app.telemetry.Telemetry

/**
 * 계측·오류 보고 동의 (#349, E8-11) — **끄면 아무것도 안 나간다.**
 *
 * ## 왜 필요했나
 *
 * 두 SDK 모두 건강 수치는 안 받는다(정책 allowlist가 막는다). 그런데 **설치별 식별자**는 기본으로
 * 보낸다 — TelemetryDeck은 `telemetrydeckid` 파일의 안정 식별자와 기기 종류·OS·화면 크기·시간대를
 * 시그널마다 싣고, Sentry는 `user.id = Installation.id(context)`를 **`isSendDefaultPii=false`로도
 * 막히지 않게** 세팅한다(#52 리뷰가 바이트코드로 확인). 개인정보처리방침에 적는 것만으로는
 * "권리와 문의" 절이 약속한 통제 수단이 없었다.
 *
 * ## 기본은 켜짐
 *
 * 끄는 게 기본이면 알파의 종료 KPI(크래시프리 세션율, #241)를 잴 표본이 안 모인다. 대신
 * ① 무엇이 나가는지 정책 페이지에 문장으로 적고 ② 설정 첫 화면에서 한 번에 끌 수 있게 한다.
 * 건강 수치는 애초에 안 나가므로, 켜짐 기본으로 감수하는 것은 익명 사용 패턴뿐이다.
 *
 * ## 끄면 즉시 멈춘다
 *
 * 저장만 하고 다음 실행부터 적용하면 "껐는데 아직 보내는" 창이 생긴다. 두 SDK 모두 런타임 종료
 * API가 있어(`TelemetryDeck.stop()` · `Sentry.close()`) 그 창을 없앨 수 있다 — 래퍼가 그걸 감싼다.
 */
object AnalyticsConsent {

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    /**
     * 동의 변경 — 저장과 **즉시 적용**을 한 진입점에서 한다.
     *
     * 둘을 나누면 호출부가 하나를 잊는다: 저장만 하면 이번 실행에서 계속 나가고, 적용만 하면
     * 재실행에 되살아난다. 둘 다 조용한 실패라 화면으로는 구분이 안 된다.
     */
    fun set(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context, enabled)
    }

    /** 앱 시작 시 1회 — 저장된 동의를 그대로 적용한다. */
    fun applyStored(context: Context) {
        apply(context, isEnabled(context))
    }

    private fun apply(context: Context, enabled: Boolean) {
        if (enabled) {
            Telemetry.init(context)
            CrashReporting.init(context)
        } else {
            Telemetry.stop()
            CrashReporting.stop()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal const val PREFS = "nexus_analytics_consent"

    /** 기본 켜짐 — 위 KDoc의 근거(알파 종료 KPI). 바꾸려면 #241을 먼저 보라. */
    internal const val DEFAULT_ENABLED = true

    private const val KEY_ENABLED = "enabled"
}
