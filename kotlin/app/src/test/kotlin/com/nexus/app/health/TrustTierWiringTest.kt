package com.nexus.app.health

import android.content.Context
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.test.core.app.ApplicationProvider
import com.nexus.core.TrustReason
import com.nexus.core.TrustTier
import com.nexus.core.tier
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 신뢰 등급 배선 (#205) — **현재 기기 소스 병합이 실제로 등급에 반영되는지**.
 *
 * ## 왜 이 파일이 필요했나
 *
 * core에 병합 로직을 만들고 `DeviceSourceResolverTest`로 전수 검증했는데, 배선을 `DEFAULT`로
 * 되돌려도 **아무 테스트가 깨지지 않았다**. 즉 검증된 로직과 프로덕션을 잇는 한 줄이 무검증이었고,
 * #205가 고치는 결함이 정확히 그 한 줄이 빠져 있던 것이었다. 같은 결함이 다시 들어오면 여기서 깨진다.
 *
 * `dataOrigin`을 세우려면 라이브러리 internal 생성자가 필요하다 — 근거는 `HealthFakes` 헬퍼 KDoc.
 */
@RunWith(RobolectricTestRunner::class)
class TrustTierWiringTest {
    private val t0: Instant = Instant.parse("2026-07-15T10:00:00Z")

    /** 2026-06 SPN 변경 후 온디바이스 소스로 가정한 패키지 — 하드코딩 allowlist에 없다. */
    private val postSpnPackage = "com.samsung.android.shealth.ondevice"

    private fun session(metadata: Metadata) = ExerciseSessionRecord(
        startTime = t0,
        startZoneOffset = null,
        endTime = t0.plusSeconds(1800),
        endZoneOffset = null,
        metadata = metadata,
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    )

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearObservations() {
        context.getSharedPreferences(DeviceSourceStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun repo(vararg sessions: ExerciseSessionRecord, store: DeviceSourceStore? = null) = ExerciseRepository(
        FakeHealthConnectClient().apply {
            readPagesByType[ExerciseSessionRecord::class] =
                mapOf<String?, ReadRecordsResponse<out androidx.health.connect.client.records.Record>>(
                    null to ReadRecordsResponse(sessions.toList(), null),
                )
            readPagesByType[HeartRateRecord::class] =
                mapOf<String?, ReadRecordsResponse<out androidx.health.connect.client.records.Record>>(
                    null to ReadRecordsResponse(emptyList<HeartRateRecord>(), null),
                )
        },
        deviceSources = store,
    )

    /**
     * 이 티켓의 결함 그대로. 병합이 없으면 사용자가 **자기 폰으로 자동 기록한 진짜 운동**이
     * Tier C로 떨어져 XP에서 제외되고, 원장은 append-only라 그대로 박제된다.
     */
    @Test
    fun `이 기기의 SPN 변경 후 소스가 Tier B로 분류된다`() = runTest {
        val record = session(
            metadataWithOrigin(id = "s1", packageName = postSpnPackage, device = thisPhoneDevice()),
        )

        val summaries = repo(record).readRecentSessions(days = 7)

        assertEquals(TrustTier.B, summaries.single().trustTier, "병합 배선이 빠졌다 — 온디바이스 기록이 XP에서 제외된다")
    }

    @Test
    fun `다른 기기에서 온 미지 소스는 여전히 Tier C다`() = runTest {
        // 병합이 allowlist를 무력화하지 않는다 — 워치 기록은 이 폰의 온디바이스 근거가 아니다
        val record = session(
            metadataWithOrigin(id = "s2", packageName = "com.stranger.fitness", device = watchDevice()),
        )

        assertEquals(TrustTier.C, repo(record).readRecentSessions(days = 7).single().trustTier)
    }

    @Test
    fun `이 기기 관측이 같은 배치의 다른 미지 소스를 승격시키지 않는다`() = runTest {
        // 병합은 관측된 패키지만 올린다 — '이 기기에서 뭔가 관측됐다'가 통과권이 되면 안 된다
        val mine = session(metadataWithOrigin("s3", postSpnPackage, thisPhoneDevice()))
        val stranger = session(metadataWithOrigin("s4", "com.stranger.fitness", watchDevice()))

        val byOrigin = repo(mine, stranger).readRecentSessions(days = 7).associateBy { it.dataOrigin }

        assertEquals(TrustTier.B, byOrigin.getValue(postSpnPackage).trustTier)
        assertEquals(TrustTier.C, byOrigin.getValue("com.stranger.fitness").trustTier)
    }

    @Test
    fun `이 기기의 수기 입력은 병합돼도 Tier C다`() = runTest {
        // 수기 제외는 anti-abuse 1차 방어 — 병합이 이걸 뚫으면 안 된다
        val record = session(
            metadataWithOrigin(
                id = "s5",
                packageName = postSpnPackage,
                device = thisPhoneDevice(),
                recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY,
            ),
        )

        assertEquals(TrustTier.C, repo(record).readRecentSessions(days = 7).single().trustTier)
    }

    /**
     * 등급과 **근거**가 같은 allowlist를 봐야 한다. 화면이 근거를 다시 계산하면 병합된 allowlist가
     * 없어 기본값으로 떨어지고, "Tier B인데 근거는 미등록 소스"라는 모순이 사용자에게 보인다.
     */
    @Test
    fun `근거가 등급과 일치한다`() = runTest {
        val record = session(metadataWithOrigin("s7", postSpnPackage, thisPhoneDevice()))

        val summary = repo(record).readRecentSessions(days = 7).single()

        assertEquals(summary.trustTier, summary.trustReason.tier, "등급과 근거가 어긋난다")
        assertEquals(TrustReason.PHONE_RECORDED, summary.trustReason)
    }

    /**
     * 관측이 **누적**돼야 한다. 배치 안에서만 모으면 같은 세션이 7일 창에서 C, 28일 창에서 B가 되고
     * 그중 셋이 원장에 지급한다 — 7일 경로만 도는 사용자는 영구히 지급받지 못한다.
     */
    @Test
    fun `이전 배치의 관측이 다음 배치에도 적용된다`() = runTest {
        val store = DeviceSourceStore(context)
        // 1회차: 자기 메타로 스스로를 증명하는 세션이 관측을 남긴다
        repo(session(metadataWithOrigin("s8", postSpnPackage, thisPhoneDevice())), store = store)
            .readRecentSessions(days = 28)

        // 2회차: 기기 메타가 비어 스스로 증명하지 못하는 같은 패키지 세션만 있는 좁은 창
        val orphan = session(
            metadataWithOrigin("s9", postSpnPackage, Device(type = Device.TYPE_UNKNOWN)),
        )
        val tier = repo(orphan, store = store).readRecentSessions(days = 7).single().trustTier

        assertEquals(TrustTier.B, tier, "누적 관측이 안 쓰인다 — 등급이 읽기 창에 따라 달라진다")
    }

    @Test
    fun `누적 관측도 미지 소스를 승격시키지 않는다`() = runTest {
        val store = DeviceSourceStore(context)
        repo(session(metadataWithOrigin("s10", postSpnPackage, thisPhoneDevice())), store = store)
            .readRecentSessions(days = 28)

        val stranger = session(metadataWithOrigin("s11", "com.stranger.fitness", watchDevice()))

        assertEquals(TrustTier.C, repo(stranger, store = store).readRecentSessions(days = 7).single().trustTier)
    }

    @Test
    fun `기존 하드코딩 소스는 그대로 Tier B다`() = runTest {
        // 병합이 기본값을 빼앗지 않는다 — 관측이 없는 배치에서도 삼성헬스는 B여야 한다
        val record = session(
            metadataWithOrigin("s6", "com.sec.android.app.shealth", watchDevice()),
        )

        assertEquals(TrustTier.B, repo(record).readRecentSessions(days = 7).single().trustTier)
    }
}
