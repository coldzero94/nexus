package com.nexus.app.crash

import android.content.Context
import android.util.Log
import com.nexus.app.BuildConfig
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

private const val TAG = "CrashReporting"

private val DIGITS = Regex("""\d+""")

/**
 * Sentry 래퍼 (#48, E8-3) — 앱 코드는 SDK를 직접 만지지 않는다(detekt ForbiddenImport 강제,
 * #46 Telemetry와 동일 패턴). 무료 티어 계약(STACK.md): **tracing off · PII off**.
 *
 * - DSN 미설정(빈 값)이면 초기화 자체를 안 한다 — 동의·설정 전 자동 수집 없음(Crashlytics 배제 사유).
 * - 건강 파생 수치는 크래시 페이로드에도 실리지 않는다: 앱 로그가 수치를 안 담는 것이 1차 방어
 *   (#46 정책), 스크린샷·뷰 계층 첨부는 여기서 명시적으로 꺼서 2차 방어.
 * - 수동 캡처 API는 의도적으로 노출하지 않는다 — MVP는 미처리 크래시 수집만.
 */
object CrashReporting {

    /** [com.nexus.app.NexusApplication]에서 1회 호출. */
    fun init(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) {
            Log.i(TAG, "DSN absent — crash reporting off")
            return
        }
        SentryAndroid.init(context.applicationContext) { options ->
            options.dsn = dsn
            options.isSendDefaultPii = false // 기본값이지만 계약이므로 명시
            options.tracesSampleRate = null // tracing off — 무료 티어 5k/월은 에러만
            options.isAttachScreenshot = false // 화면에 건강 파생 표시값이 있다 — 첨부 금지
            options.isAttachViewHierarchy = false
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            // 방어 심화 — 미래에 값 보간 예외 메시지("steps=8432")가 생겨도 수치는 안 나간다
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.exceptions?.forEach { ex -> ex.value = ex.value?.replace(DIGITS, "#") }
                event.message?.let { it.formatted = it.formatted?.replace(DIGITS, "#") }
                event
            }
        }
    }
}
