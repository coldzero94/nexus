package com.nexus.app.crash

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sentry 옵션 계약 (#48·#349·#352).
 *
 * ## 왜 값을 하나하나 박는가
 *
 * 여기 있는 값은 전부 "SDK 기본값이지만 계약이므로 명시"에 해당한다. 즉 **지금 안 박아도 동작은
 * 같고**, 그래서 누가 지워도 아무 일도 안 일어난다 — SDK가 기본값을 바꾸는 그날까지는. 그날
 * 바뀌는 건 화면 캡처·리플레이처럼 **건강 파생 표시값이 담긴 화면**이 서버로 나가는지 여부다.
 *
 * 초기화 콜백 안에 있으면 SDK를 실제로 띄우지 않고는 무엇이 설정됐는지 볼 수 없어서,
 * [CrashReporting.configure]를 순수 함수로 빼고 여기서 값을 직접 읽는다.
 */
@RunWith(RobolectricTestRunner::class)
class SentryOptionsContractTest {

    private fun configured(): SentryAndroidOptions = SentryAndroidOptions().also {
        CrashReporting.configure(it, dsn = TEST_DSN)
    }

    /** 화면에 건강 파생 표시값이 있다 — 화면을 담는 기능은 전부 꺼져 있어야 한다. */
    @Test
    fun `화면을 담는 기능이 모두 꺼져 있다`() {
        val options = configured()

        assertFalse(options.isAttachScreenshot, "스크린샷 첨부가 켜졌다")
        assertFalse(options.isAttachViewHierarchy, "뷰 계층 첨부가 켜졌다")
    }

    /**
     * 세션 리플레이는 스크린샷보다 **더 많이** 담는다(연속 프레임). 의존에서 뺐지만
     * 우산 의존이 다시 붙을 수 있어 값으로도 박는다.
     */
    @Test
    fun `세션 리플레이 샘플레이트가 0이다`() {
        val options = configured()

        assertEquals(0.0, options.sessionReplay.sessionSampleRate)
        assertEquals(0.0, options.sessionReplay.onErrorSampleRate)
        assertFalse(options.sessionReplay.isSessionReplayEnabled, "세션 리플레이가 켜졌다")
        assertFalse(options.sessionReplay.isSessionReplayForErrorsEnabled, "오류 시 리플레이가 켜졌다")
    }

    /**
     * 값으로 막는 것보다 **의존에서 빼는 것**이 강하다 — 코드가 APK에 아예 안 실린다.
     *
     * 클래스 존재로 확인하는 이유: 단위 테스트는 디버그 변형의 런타임 클래스패스로 돌고,
     * `exclude`는 변형과 무관하게 걸린다. 우산 의존을 그대로 되돌리면 이 단언이 빨개진다.
     */
    @Test
    fun `세션 리플레이가 클래스패스에 없다`() {
        val present = runCatching { Class.forName("io.sentry.android.replay.ReplayIntegration") }.isSuccess

        assertFalse(present, "sentry-android-replay가 다시 들어왔다 — app/build.gradle.kts의 exclude 확인")
    }

    /** 양성 대조 — 위 단언이 'Sentry 자체가 없어서' 통과하는 게 아님을 보인다. */
    @Test
    fun `Sentry 코어는 클래스패스에 있다`() {
        val present = runCatching { Class.forName("io.sentry.android.core.SentryAndroid") }.isSuccess

        assertTrue(present, "Sentry 코어가 없다 — 이 파일의 다른 단언이 전부 무의미해진다")
    }

    /**
     * **수치 세탁이 실제로 걸려 있는가.** `CrashScrubber`가 옳은지는 `CrashScrubberTest`가 보지만,
     * 그게 파이프라인에 **붙어 있는지**는 아무도 안 봤다 — 콜백을 통째로 지워도 전 스위트가
     * 초록이었다(#352 리뷰가 재현). "부를 수 있다"와 "부른다"는 다른 명제다.
     *
     * 이건 이 파일에서 유일하게 불리언이 아니라 **로직**인 계약이고, 지키는 불변식이
     * "건강 파생 수치는 기기 밖으로 안 나간다"라 가장 값어치가 크다.
     */
    @Test
    fun `예외 메시지의 수치가 전송 전에 지워진다`() {
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException().apply { value = "steps=8432" })
        }

        val sent = configured().beforeSend?.execute(event, Hint())

        val scrubbed = sent?.exceptions?.first()?.value
        assertNotNull(scrubbed, "beforeSend가 안 걸렸다 — 예외 메시지가 그대로 나간다")
        assertFalse("8432" in scrubbed, "예외 메시지에 수치가 남았다: $scrubbed")
    }

    /** 메시지 채널도 같은 통로다 — 한쪽만 세탁하면 다른 쪽으로 샌다. */
    @Test
    fun `메시지의 수치도 지워진다`() {
        val event = SentryEvent().apply { message = Message().apply { formatted = "hr=143" } }

        val sent = configured().beforeSend?.execute(event, Hint())

        val scrubbed = sent?.message?.formatted
        assertNotNull(scrubbed, "beforeSend가 안 걸렸다")
        assertFalse("143" in scrubbed, "메시지에 수치가 남았다: $scrubbed")
    }

    /** 무료 티어는 에러만 받는다(월 5k) — tracing이 켜지면 표본이 트레이스로 잠식된다. */
    @Test
    fun `tracing이 꺼져 있다`() {
        assertNull(configured().tracesSampleRate)
    }

    /** 개인 식별 정보는 보내지 않는다 — 정책 페이지 3절이 그렇게 적혀 있다. */
    @Test
    fun `PII 전송이 꺼져 있다`() {
        assertFalse(configured().isSendDefaultPii)
    }

    /**
     * 끄는 행위 자체가 전송이 되면 안 된다 (#349) — `Sentry.close()`는 이 값만큼 동기 flush를 한다.
     */
    @Test
    fun `종료 flush 대기가 0이다`() {
        assertEquals(0L, configured().shutdownTimeoutMillis)
    }
}

private const val TEST_DSN = "https://0123456789abcdef0123456789abcdef@o0.ingest.sentry.io/0"
