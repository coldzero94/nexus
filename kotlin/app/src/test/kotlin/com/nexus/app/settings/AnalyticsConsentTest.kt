package com.nexus.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.crash.CrashReporting
import com.nexus.app.telemetry.Telemetry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계측·오류 보고 동의 (#349, E8-11).
 *
 * ## 이 기능이 조용히 깨지는 방식
 *
 * 껐는데 계속 나간다. 화면은 꺼진 걸로 보이고, 로그에도 아무 표시가 없다 — 신호가 나가는지는
 * 기기에서 관측할 수 없고, 서버(TelemetryDeck·Sentry)에 며칠 뒤 나타난다. 그때는 이미
 * "끄면 아무것도 안 나간다"는 약속(개인정보처리방침 3·9절)이 깨진 뒤다.
 *
 * 깨지는 경로는 셋이고 여기서 셋 다 막는다: ① 저장은 됐는데 이번 실행에 적용이 안 된 경우
 * ② 적용은 됐는데 저장이 안 돼 재실행에 되살아나는 경우 ③ 앱 시작이 동의를 안 보고
 * 그냥 켜버리는 경우.
 */
@RunWith(RobolectricTestRunner::class)
class AnalyticsConsentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `기본은 켜짐이다`() {
        assertTrue(AnalyticsConsent.isEnabled(context), "기본값을 바꾸려면 알파 종료 KPI(#241)를 먼저 볼 것")
    }

    /** 재실행에 되살아나면 안 된다 — 저장 없이 적용만 하는 실수가 여기서 걸린다. */
    @Test
    fun `끈 상태가 저장된다`() {
        AnalyticsConsent.set(context, false)

        assertFalse(AnalyticsConsent.isEnabled(context))
    }

    @Test
    fun `다시 켤 수 있다`() {
        AnalyticsConsent.set(context, false)
        AnalyticsConsent.set(context, true)

        assertTrue(AnalyticsConsent.isEnabled(context))
    }

    /**
     * 양성 대조 — 켜져 있을 수 있다는 걸 먼저 보인다.
     *
     * 이게 없으면 아래 "끄면 멈춘다"가 **애초에 켜진 적이 없어서** 통과한다. 테스트 빌드엔
     * 앱 ID·DSN이 안 들어가므로(`BuildConfig`가 빈 문자열) 여기서 직접 넣어 준다.
     */
    @Test
    fun `설정값이 있으면 켜진다`() {
        Telemetry.init(context, appId = TEST_APP_ID)
        CrashReporting.init(context, dsn = TEST_DSN)

        assertTrue(Telemetry.isActive, "앱 ID를 줬는데 계측이 안 켜졌다")
        assertTrue(CrashReporting.isActive, "DSN을 줬는데 오류 보고가 안 켜졌다")
    }

    /** **끄면 즉시 멈춘다.** 저장만 하고 다음 실행부터 적용하면 "껐는데 아직 보내는" 창이 생긴다. */
    @Test
    fun `끄면 두 래퍼가 즉시 멈춘다`() {
        Telemetry.init(context, appId = TEST_APP_ID)
        CrashReporting.init(context, dsn = TEST_DSN)

        AnalyticsConsent.set(context, false)

        assertFalse(Telemetry.isActive, "계측이 안 멈췄다")
        assertFalse(CrashReporting.isActive, "오류 보고가 안 멈췄다")
    }

    /** 앱 시작이 저장된 동의를 그대로 적용하는지 — 안 그러면 다음 실행에 되살아난다. */
    @Test
    fun `시작 시 꺼둔 동의가 유지된다`() {
        Telemetry.init(context, appId = TEST_APP_ID)
        CrashReporting.init(context, dsn = TEST_DSN)
        AnalyticsConsent.set(context, false)

        AnalyticsConsent.applyStored(context)

        assertFalse(Telemetry.isActive)
        assertFalse(CrashReporting.isActive)
        assertFalse(AnalyticsConsent.isEnabled(context))
    }

    /**
     * 배선 가드 — 앱 시작이 **동의를 거쳐서만** 두 SDK를 켜는지.
     *
     * 행위로 못 잡는 이유: `NexusApplication.onCreate`는 로보렉트릭이 테스트 전에 이미 돌려버려
     * 그 시점의 동의 상태를 테스트가 세울 수 없다. 소스 가드는 `Telemetry.init`을 직접 부르는
     * 회귀만 막는다 — 그 한 줄이 "껐다고 저장한 사용자에게 다음 실행부터 다시 켜지는" 버그다.
     */
    @Test
    fun `앱 시작이 동의를 거쳐 계측을 켠다`() {
        val source = File(File("..").canonicalFile, "app/src/main/kotlin/com/nexus/app/NexusApplication.kt")
            .readText()

        assertTrue("AnalyticsConsent.applyStored" in source, "앱 시작이 저장된 동의를 안 본다")
        assertFalse("Telemetry.init(" in source, "앱 시작이 동의를 건너뛰고 계측을 켠다")
        assertFalse("CrashReporting.init(" in source, "앱 시작이 동의를 건너뛰고 오류 보고를 켠다")
    }

    /** 설정 화면이 실제로 토글을 그리는지 — 컴포저블이 있는 것과 화면이 쓰는 건 다른 명제다. */
    @Test
    fun `설정 화면이 동의 토글을 그린다`() {
        val screen = File(File("..").canonicalFile, "app/src/main/kotlin/com/nexus/app/settings/SettingsScreen.kt")
            .readText()

        assertTrue("AnalyticsConsentCard()" in screen, "설정 화면에 동의 토글이 없다")
    }

    /** 저장소 이름은 백업·초기화 도구가 참조한다 — 조용히 바뀌면 꺼둔 동의가 백업에서 빠진다. */
    @Test
    fun `저장소 이름이 고정이다`() {
        assertEquals("nexus_analytics_consent", AnalyticsConsent.PREFS)
    }
}

/** TelemetryDeck 앱 ID는 UUID여야 한다 — 아무 문자열이나 주면 start가 던진다. */
private const val TEST_APP_ID = "00000000-0000-4000-8000-000000000000"

/** 형식만 맞춘 가짜 DSN — Sentry가 파싱에 실패하면 init 자체가 안 돈다. */
private const val TEST_DSN = "https://0123456789abcdef0123456789abcdef@o0.ingest.sentry.io/0"
