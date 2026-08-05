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
import io.sentry.android.core.SentryAndroidOptions
import java.io.File

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
        if (enabled) return
        if (dsn.isBlank()) {
            Log.i(TAG, "DSN absent — crash reporting off")
            return
        }
        runCatching { initSentry(context, dsn) }
            // 플래그는 **init 뒤에**, 그리고 SDK에게 물어서 정한다. 앞에 두면 init이 던졌을 때
            // "보내는 중"이라고 거짓 보고한다(#349 리뷰가 잘못된 DSN으로 재현 — 테스트가 고정).
            // `isEnabled()`로 정하는 건 그 위의 방어다: SDK가 던지지 않고 조용히 스스로를 끄는
            // 경우까지 덮는다(현재 테스트로는 도달 경로가 없어 단언하지 않는다).
            .onSuccess { enabled = Sentry.isEnabled() }
            .onFailure { Log.w(TAG, "crash reporting init failed", it) }
    }

    private fun initSentry(context: Context, dsn: String) {
        SentryAndroid.init(context.applicationContext) { options -> configure(options, dsn) }
    }

    /**
     * 옵션 계약 (#48·#349·#352) — **순수 함수로 빼서** 테스트가 값을 직접 검사한다.
     *
     * 초기화 콜백 안에 있으면 SDK를 실제로 띄우지 않고는 무엇이 설정됐는지 볼 수 없다. 여기 있는
     * 값들은 전부 "기본값이지만 계약이므로 명시"에 해당해서, 조용히 뒤집히는 게 정확히 위험이다.
     */
    @VisibleForTesting
    internal fun configure(options: SentryAndroidOptions, dsn: String) {
        with(options) {
            this.dsn = dsn
            isSendDefaultPii = false // 기본값이지만 계약이므로 명시
            tracesSampleRate = null // tracing off — 무료 티어 5k/월은 에러만
            isAttachScreenshot = false // 화면에 건강 파생 표시값이 있다 — 첨부 금지
            isAttachViewHierarchy = false
            // 세션 리플레이는 의존에서 이미 뺐다(app/build.gradle.kts, #352). 그래도 0을 박는 이유:
            // 우산 의존이 다시 붙거나 SDK가 기본값을 바꾸면, 화면 캡처보다 더 많이 담는 기능이
            // 조용히 켜진다. 스크린샷을 명시적으로 막아둔 앱이 리플레이를 기본값에 맡길 수는 없다.
            sessionReplay.sessionSampleRate = 0.0
            sessionReplay.onErrorSampleRate = 0.0
            environment = if (BuildConfig.DEBUG) "debug" else "release"
            // 동의를 끄는 순간 마지막 업로드가 일어나면 안 된다 — Sentry.close()는 이 값만큼
            // 동기 flush를 하고(무료 API엔 close(false)가 없다) 그 자체가 전송 행위가 된다 (#349 리뷰)
            shutdownTimeoutMillis = 0
            // 방어 심화 — 미래에 값 보간 예외 메시지("steps=8432")가 생겨도 수치는 안 나간다([CrashScrubber])
            beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
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
    fun stop(context: Context) {
        enabled = false
        runCatching { Sentry.close() }
            .onFailure { Log.w(TAG, "crash reporting stop failed", it) }
        // 아직 못 올린 envelope·세션·breadcrumb이 캐시에 남는다 — 다음 init이 그걸 묶어 보낸다.
        // 철회 이전 데이터가 재동의 순간 나가는 셈이라, 큐를 세워두지 않고 버린다 (#349 리뷰).
        runCatching { File(context.cacheDir, SENTRY_CACHE_DIR).deleteRecursively() }
            .onFailure { Log.w(TAG, "crash cache cleanup failed", it) }
    }

    @Volatile
    private var enabled = false

    /** SDK가 못 올린 envelope·세션·breadcrumb을 쌓아두는 디렉터리. */
    private const val SENTRY_CACHE_DIR = "sentry"

    /** 처리된-실패 메시지 접두어 — Sentry에서 미처리 크래시와 구분해 필터링한다. */
    private const val HANDLED_PREFIX = "handled:"
}
