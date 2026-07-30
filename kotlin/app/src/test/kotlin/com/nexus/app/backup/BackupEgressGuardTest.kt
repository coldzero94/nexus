package com.nexus.app.backup

import com.nexus.core.HealthTermDenylist
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 백업 egress 경계 (#238, E15-10) — **기기 밖으로 나가는 유일한 앱-생성 페이로드**에 이빨을 붙인다.
 *
 * 유출 표면 셋 중 계측(`TelemetryPolicyTest`)·크래시(`CrashScrubberTest`)는 CI로 강제되는데, 실제로
 * 기기를 떠나는 백업([BackupCodec])에는 주석뿐이었다. `BackupEvent`에 `steps: Int`를 추가해도 CI가
 * 초록이었다 — 불변식 ③("원시 건강 값은 기기를 떠나지 않는다")의 egress 표면이 무방비였다.
 *
 * 이 테스트는 두 방향으로 잠근다:
 * 1. **정확 고정** — 필드 집합을 그대로 못 박아, 새 필드가 붙으면 무조건 빨강이 된다. 리뷰어가
 *    "이게 나가도 되는 값인가"를 반드시 한 번 생각하게 만드는 장치다.
 * 2. **의미 검사** — 이름이 원시 건강 항목을 가리키면 실패한다(core [HealthTermDenylist] 공유 목록).
 *    ①만 있으면 목록을 기계적으로 갱신하고 넘어갈 수 있는데, ②는 그때도 걸린다.
 */
class BackupEgressGuardTest {
    /**
     * `BackupCodec.VERSION` = 2의 스키마. **여기를 고칠 때는 VERSION도 올려야 한다** —
     * 구버전 파일을 읽는 계약이 달라지므로(`decode`가 버전 범위를 검사한다).
     */
    private val v2Payload = setOf("backupVersion", "exportedAtEpochMillis", "events", "snapshot")

    private val v2Event = setOf(
        "idempotencyKey",
        "xp",
        "type",
        "dataOrigin",
        "recordingMethod",
        "formulaVersion",
        "epochMillis",
        "epochDay",
    )

    private val v2Snapshot = setOf(
        "energyTotalSpent", "expeditionStartedAtMillis", "settlementLastSeenXp",
        "morningLastShownEpochDay", "journalLastShownEpochDay", "weeklyGoalDays",
        "restModeEnabled", "restModeSinceEpochDay", "characterName", "expeditionsCompleted",
        // #240 승계 앵커 — 본인 통제 백업 표면에만 담긴다(서버·계측 아님)
        "installId",
    )

    /**
     * **직렬화되는** 필드 이름 — 프로퍼티 리플렉션이 아니라 직렬화 디스크립터를 본다.
     *
     * 실제로 기기를 떠나는 건 직렬화 대상뿐이다. `@Transient`나 파생 프로퍼티까지 세면 egress가
     * 아닌 것에 발이 묶이고, 반대로 커스텀 SerialName을 놓칠 수도 있다 — 디스크립터가 진실이다.
     */
    private fun serializedFields(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    @Test
    fun `페이로드 필드가 고정 집합과 정확히 일치한다`() {
        assertEquals(v2Payload, serializedFields(BackupPayload.serializer().descriptor), VERSION_HINT)
        assertEquals(v2Event, serializedFields(BackupEvent.serializer().descriptor), VERSION_HINT)
        assertEquals(v2Snapshot, serializedFields(BackupSnapshot.serializer().descriptor), VERSION_HINT)
    }

    @Test
    fun `고정 집합은 현재 VERSION의 것이다`() {
        // 스키마를 바꾸고 VERSION을 안 올리면 여기서 잡힌다 — 위 집합은 v1을 기술한다
        assertEquals(2, BackupCodec.VERSION, "VERSION이 올랐다 — 위 v2* 집합을 새 버전 기준으로 갱신하세요")
    }

    @Test
    fun `어떤 필드 이름도 원시 건강 항목을 가리키지 않는다`() {
        val all =
            serializedFields(BackupPayload.serializer().descriptor) +
                serializedFields(BackupEvent.serializer().descriptor) +
                serializedFields(BackupSnapshot.serializer().descriptor)
        val offenders = all.filter { HealthTermDenylist.isRawHealthTerm(it) }

        assertEquals(
            emptyList(),
            offenders,
            "백업은 기기를 떠난다 — 원시 건강 값(걸음·운동 시간·심박 등)은 담을 수 없다. " +
                "계산된 XP와 산식 버전만 나간다(BACKEND §1).",
        )
    }

    @Test
    fun `계산값은 금지 목록에 걸리지 않는다`() {
        // 이 가드가 xp·formulaVersion까지 잡으면 백업 자체가 불가능해진다 — 과잉 차단 방지 검사
        listOf("xp", "formulaVersion", "level", "dataOrigin", "recordingMethod", "idempotencyKey").forEach {
            assertTrue(!HealthTermDenylist.isRawHealthTerm(it), "$it 은 계산값인데 금지로 잡혔다")
        }
    }

    @Test
    fun `denylist가 실제로 원시 항목을 잡는다`() {
        // 목록이 비거나 로직이 뒤집히면 위 테스트들이 전부 조용히 통과한다 — 그 침묵을 막는 카나리
        listOf("steps", "todaySteps", "workoutMinutes", "heartRate", "sleepHours", "calories")
            .forEach { assertTrue(HealthTermDenylist.isRawHealthTerm(it), "$it 을 잡지 못했다") }
    }

    private companion object {
        const val VERSION_HINT =
            "백업 스키마가 바뀌었다 — 이 값이 기기 밖으로 나가도 되는지 확인하고, " +
                "괜찮다면 BackupCodec.VERSION을 올리고 이 테스트의 고정 집합을 갱신하세요."
    }
}
