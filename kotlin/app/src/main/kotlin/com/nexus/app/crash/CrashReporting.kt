package com.nexus.app.crash

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nexus.app.BuildConfig
import com.nexus.core.FailureCategory
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

private const val TAG = "CrashReporting"

/**
 * Sentry 래퍼 (#48, E8-3) — 앱 코드는 SDK를 직접 만지지 않는다(detekt ForbiddenImport 강제,
 * #46 Telemetry와 동일 패턴). 무료 티어 계약(STACK.md): **tracing off · PII off**.
 *
 * - DSN 미설정(빈 값)이면 초기화 자체를 안 한다 — 동의·설정 전 자동 수집 없음(Crashlytics 배제 사유).
 * - 건강 파생 수치는 크래시 페이로드에도 실리지 않는다: 앱 로그가 수치를 안 담는 것이 1차 방어
 *   (#46 정책), 스크린샷·뷰 계층 첨부는 여기서 명시적으로 꺼서 2차 방어.
 * - 수동 **예외** 캡처 API는 노출하지 않는다. 대신 처리된-실패는 [recordHandledFailure]로 분류
 *   이름만 보낸다 (#239) — 예외 객체를 그대로 올리면 메시지에 값이 섞일 수 있다.
 */
object CrashReporting {

    /**
     * 처리된-실패 1건 기록 (#239) — **분류 이름만** 보낸다.
     *
     * 예외 메시지·스택·수치는 싣지 않는다: 메시지엔 `"steps=8432"` 같은 값이 섞일 수 있고(불변식 ②),
     * 스택은 이미 처리된 실패엔 과하다. 보낼 수 있는 신호 전부는 [FailureCategory]이고 그 목록은
     * `FailureCategoryTest`가 고정한다.
     *
     * DSN이 없으면(알파 초기·디버그) 조용히 아무것도 하지 않는다 — 호출부가 분기하지 않게.
     */
    fun recordHandledFailure(category: FailureCategory) {
        if (BuildConfig.SENTRY_DSN.isBlank() || !enabled) return
        // 메시지가 아니라 breadcrumb·level로 — 검색 가능한 분류 하나면 운영 판단에 충분하다.
        Sentry.captureMessage(HANDLED_PREFIX + category.name, SentryLevel.WARNING)
    }

    /** 지금 오류를 보내는 상태인가 (#349) — 동의 토글 테스트가 읽는다. */
    @get:VisibleForTesting
    val isActive: Boolean get() = enabled

    /**
     * [com.nexus.app.settings.AnalyticsConsent]가 부른다. 두 번 불러도 안전해야 한다 —
     * 동의를 껐다 켜면 다시 온다.
     */
    fun init(context: Context, dsn: String = BuildConfig.SENTRY_DSN) {
        if (dsn.isBlank()) {
            Log.i(TAG, "DSN absent — crash reporting off")
            return
        }
        enabled = true
        SentryAndroid.init(context.applicationContext) { options ->
            options.dsn = dsn
            options.isSendDefaultPii = false // 기본값이지만 계약이므로 명시
            options.tracesSampleRate = null // tracing off — 무료 티어 5k/월은 에러만
            options.isAttachScreenshot = false // 화면에 건강 파생 표시값이 있다 — 첨부 금지
            options.isAttachViewHierarchy = false
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            // 방어 심화 — 미래에 값 보간 예외 메시지("steps=8432")가 생겨도 수치는 안 나간다([CrashScrubber])
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.exceptions?.forEach { ex -> ex.value = CrashScrubber.scrub(ex.value) }
                event.message?.let { it.formatted = CrashScrubber.scrub(it.formatted) }
                event
            }
        }
    }

    /**
     * 오류 보고 중단 (#349) — 동의를 끈 순간부터 아무것도 안 나간다.
     *
     * `Sentry.close()`까지 부르는 이유는 [com.nexus.app.telemetry.Telemetry.stop]과 같다:
     * 미처리 크래시 핸들러는 우리 래퍼를 안 거치므로, 플래그만 내리면 다음 크래시가 그대로 나간다.
     */
    fun stop() {
        enabled = false
        runCatching { Sentry.close() }
            .onFailure { Log.w(TAG, "crash reporting stop failed", it) }
    }

    @Volatile
    private var enabled = false

    /** 처리된-실패 메시지 접두어 — Sentry에서 미처리 크래시와 구분해 필터링한다. */
    private const val HANDLED_PREFIX = "handled:"
}
