package com.nexus.app.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.nexus.app.BuildConfig
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

    fun isEnabled(context: Context): Boolean = cached ?: prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    /**
     * 동의 변경 — 저장과 **즉시 적용**을 한 진입점에서 한다.
     *
     * 둘을 나누면 호출부가 하나를 잊는다: 저장만 하면 이번 실행에서 계속 나가고, 적용만 하면
     * 재실행에 되살아난다. 둘 다 조용한 실패라 화면으로는 구분이 안 된다.
     */
    fun set(context: Context, enabled: Boolean) {
        // 메모리에 먼저 — 디스크 쓰기와 SDK 기동은 아래에서 백그라운드로 나가므로,
        // 토글 직후 화면이 읽는 값이 이 한 줄로 정해진다
        cached = enabled
        // 설정값은 **지금** 읽는다 — 백그라운드 블록 안에서 읽으면 그 사이에 바뀐 값을 쓴다
        val appId = telemetryAppId()
        val dsn = sentryDsn()
        runner {
            // 철회는 잃으면 안 되는 쓰기다(다시 켜진 채로 되살아나면 약속이 깨진다) — commit()
            prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
            apply(context, enabled, appId, dsn)
        }
    }

    /** 앱 시작 시 1회 — 저장된 동의를 그대로 적용한다. 콜드스타트를 막지 않도록 백그라운드로. */
    fun applyStored(context: Context) {
        val enabled = isEnabled(context)
        val appId = telemetryAppId()
        val dsn = sentryDsn()
        runner { apply(context, enabled, appId, dsn) }
    }

    /**
     * 실행 시임 (#349 리뷰) — 프로덕션은 백그라운드 스레드.
     *
     * 메인에서 돌리면 안 되는 이유가 양쪽에 있다: 끌 때 `Sentry.close()`가 동기 flush를 하고,
     * 켤 때 `SentryAndroid.init`이 캐시 디렉터리·식별자 파일을 만든다. 설정 스위치 한 번이
     * ANR 위험을 지는 건 과하다. 테스트는 같은 스레드로 바꿔 결정적으로 만든다.
     */
    @VisibleForTesting
    internal var runner: (() -> Unit) -> Unit = { block -> Thread(block).start() }

    private fun apply(context: Context, enabled: Boolean, appId: String, dsn: String) {
        if (enabled) {
            Telemetry.init(context, appId = appId)
            CrashReporting.init(context, dsn = dsn)
        } else {
            Telemetry.stop(context)
            CrashReporting.stop(context)
        }
    }

    /**
     * 설정값 시임 (#349 리뷰) — 테스트 빌드엔 앱 ID·DSN이 없어서, 이게 없으면 **다시 켜는
     * 경로가 아무것도 검증하지 못한다**(두 init이 빈 값으로 즉시 반환한다).
     */
    @VisibleForTesting
    internal var telemetryAppId: () -> String = { BuildConfig.TELEMETRYDECK_APP_ID }

    @VisibleForTesting
    internal var sentryDsn: () -> String = { BuildConfig.SENTRY_DSN }

    /**
     * 마지막으로 정한 값 — 디스크 쓰기가 백그라운드로 나가므로 화면이 즉시 읽을 값이 필요하다.
     *
     * `object`의 정적 상태라 테스트 사이에 샌다(로보렉트릭은 클래스 사이에서도 같은 샌드박스
     * 클래스로더를 재사용한다). 테스트가 [resetForTest]로 되돌린다.
     */
    @Volatile
    private var cached: Boolean? = null

    /** 테스트 전용 — 정적 상태를 초기 상태로. */
    @VisibleForTesting
    internal fun resetForTest() {
        cached = null
        runner = { block -> Thread(block).start() }
        telemetryAppId = { BuildConfig.TELEMETRYDECK_APP_ID }
        sentryDsn = { BuildConfig.SENTRY_DSN }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal const val PREFS = "nexus_analytics_consent"

    /** 기본 켜짐 — 위 KDoc의 근거(알파 종료 KPI). 바꾸려면 #241을 먼저 보라. */
    internal const val DEFAULT_ENABLED = true

    private const val KEY_ENABLED = "enabled"
}
