package com.nexus.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.crash.CrashReporting
import com.nexus.app.telemetry.Telemetry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
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
 *
 * 화면에 토글이 실제로 그려지는지는 `SettingsScreenRenderTest`가 본다 — 소스 스캔보다 강하고,
 * 그 테스트의 존재 이유가 바로 "카드를 빠뜨리는 것"이다.
 */
@RunWith(RobolectricTestRunner::class)
class AnalyticsConsentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * 시임을 같은 스레드로 — `set`이 백그라운드로 나가면 단언이 경합한다.
     * 설정값도 넣어준다: 테스트 빌드엔 앱 ID·DSN이 없어 켜는 경로가 통째로 no-op이 된다.
     *
     * 먼저 **꺼진 상태로 맞춘다.** 로보렉트릭은 클래스마다 `NexusApplication.onCreate`를 돌리고,
     * 그게 `applyStored`를 백그라운드 스레드로 던진다 — 이 클래스가 시임을 갈아끼운 뒤에 그
     * 스레드가 도착하면 테스트 도중에 SDK가 켜진다(실제로 `before=true`로 관측됐다).
     */
    @Before
    fun useSameThreadRunner() {
        AnalyticsConsent.resetForTest()
        AnalyticsConsent.runner = { it() }
        AnalyticsConsent.telemetryAppId = { TEST_APP_ID }
        AnalyticsConsent.sentryDsn = { TEST_DSN }
        Telemetry.stop(context)
        CrashReporting.stop(context)
    }

    /**
     * 전역 상태를 반드시 되돌린다 (`HealthSyncWorkerTest.restoreSeam` 선례).
     *
     * `Telemetry.enabled`·`CrashReporting.enabled`는 Kotlin `object`의 `@Volatile` 필드고,
     * 로보렉트릭은 테스트 **클래스** 사이에서도 같은 샌드박스 클래스로더를 재사용한다. 안 되돌리면
     * 켜진 채로 다음 클래스에 새어 들어가 — SDK가 실제로 기동된 상태로 — 다른 테스트의 계측
     * 콜사이트가 진짜 신호를 만든다(#246이 WorkManager로 똑같이 당했다).
     */
    @After
    fun restoreGlobals() {
        Telemetry.stop(context)
        CrashReporting.stop(context)
        AnalyticsConsent.resetForTest()
    }

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

    /** 껐다 켜면 **다시 나가야** 한다 — 저장값만 보면 이 경로가 죽어도 통과한다. */
    @Test
    fun `다시 켜면 재개된다`() {
        AnalyticsConsent.set(context, false)

        AnalyticsConsent.set(context, true)

        assertTrue(AnalyticsConsent.isEnabled(context))
        assertTrue(Telemetry.isActive, "다시 켰는데 계측이 안 돌아왔다")
        assertTrue(CrashReporting.isActive, "다시 켰는데 오류 보고가 안 돌아왔다")
    }

    /**
     * 철회는 큐를 **버린다**. `TelemetryDeck.stop()`도 `Sentry.close()`도 못 올린 기록을
     * 캐시에 남기고, 다음 init이 그걸 묶어 보낸다 — 껐다 켜면 철회 이전 데이터가 나간다.
     */
    @Test
    fun `끄면 아직 못 올린 기록을 지운다`() {
        AnalyticsConsent.set(context, true)
        val signalCache = File(context.cacheDir, "telemetrydeck.json").apply { writeText("[]") }
        val identity = File(context.filesDir, "telemetrydeckid").apply { writeText("id") }
        val sentryCache = File(context.cacheDir, "sentry").apply { mkdirs() }
        File(sentryCache, "queued.envelope").writeText("x")

        AnalyticsConsent.set(context, false)

        assertFalse(signalCache.exists(), "안 올린 신호 큐가 남았다 — 다시 켜면 나간다")
        assertFalse(identity.exists(), "설치 식별자가 남았다 — 철회 전후가 같은 id로 이어진다")
        assertFalse(sentryCache.exists(), "안 올린 오류 캐시가 남았다 — 다시 켜면 나간다")
    }

    /** init이 실패하면 '보내는 중'이라고 보고하면 안 된다 — 관측 가능한 계약이 거짓이 된다. */
    @Test
    fun `설정값이 잘못되면 켜졌다고 보고하지 않는다`() {
        CrashReporting.init(context, dsn = "not-a-dsn")
        Telemetry.init(context, appId = "not-a-uuid")

        assertFalse(CrashReporting.isActive, "DSN이 잘못됐는데 오류 보고가 켜졌다고 한다")
        assertFalse(Telemetry.isActive, "앱 ID가 잘못됐는데 계측이 켜졌다고 한다")
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

    /**
     * 배선 가드의 범위 — `NexusApplication` 말고 어디서도 SDK를 직접 켜면 안 된다.
     *
     * 처음엔 `NexusApplication.kt` 한 파일만 봤는데, 그러면 `MainActivity`에서 `Telemetry.init`을
     * 부르는 회귀가 그대로 통과한다.
     */
    @Test
    fun `동의를 거치지 않고 SDK를 켜는 곳이 없다`() {
        val mainRoot = File(File("..").canonicalFile, "app/src/main/kotlin/com/nexus/app")

        val offenders = mainRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .filterNot { it.name == "AnalyticsConsent.kt" }
            .filter { f ->
                val text = f.readText()
                "Telemetry.init(" in text || "CrashReporting.init(" in text
            }
            .map { it.name }
            .toList()

        assertTrue(offenders.isEmpty(), "동의를 건너뛰고 SDK를 켜는 파일: $offenders")
    }
}

/** TelemetryDeck 앱 ID는 UUID여야 한다 — 아무 문자열이나 주면 start가 던진다. */
private const val TEST_APP_ID = "00000000-0000-4000-8000-000000000000"

/** 형식만 맞춘 가짜 DSN — Sentry가 파싱에 실패하면 init 자체가 안 돈다. */
private const val TEST_DSN = "https://0123456789abcdef0123456789abcdef@o0.ingest.sentry.io/0"
