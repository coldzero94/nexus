package com.nexus.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.nexus.core.ConnectGate
import com.nexus.core.HealthAvailability

/**
 * 온보딩(#6)이 요청하는 Health Connect 권한: 읽기 4(걸음·운동·심박·수면) + 백그라운드 + 과거 이력.
 * 수면(#180)은 컨디션 회복 보정용. 데이터 읽기(집계·세션)는 #7·#8·#180에서 이 위에 쌓는다.
 */
object HealthPermissions {
    /**
     * **필수** — 없으면 XP를 만들 수 없어 앱의 존재 이유가 사라진다 (#236).
     * 코어 루프 진입은 이 둘만 요구한다.
     */
    val REQUIRED: Set<String> =
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

    /**
     * **선택** — 없으면 기능이 일부 줄어들 뿐 코어 루프는 돈다 (#236).
     *
     * 백그라운드 읽기·과거 이력은 안드로이드가 **별도로 게이팅해 자주 거부되는** 권한이다. 예전엔
     * 이걸 전부 요구해서, 걸음·운동을 다 승인한 사용자가 하나만 거부해도 영구 데모 모드에 갇혔다.
     */
    val OPTIONAL: Set<String> =
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        )

    /** 권한 요청 화면에 한 번에 올리는 전체 — 요청은 전부, 판정은 [REQUIRED]만. */
    val ALL: Set<String> = REQUIRED + OPTIONAL
}

/**
 * Health Connect 접근 래퍼. #6은 '가용성 확인 + 권한 요청'까지만 담당한다.
 * Health Connect는 안드로이드 전용 API라 core(KMP commonMain)가 아니라 app 모듈에 둔다.
 */
class HealthConnectManager(private val context: Context) {
    /**
     * 가용성 3상태 (#236) — 이진으로 뭉개면 업데이트로 해결되는 상태가 막다른 길로 보인다.
     * 갤럭시 프리인스톨 HC는 구버전인 경우가 흔하다.
     */
    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UpdateRequired
        else -> HealthAvailability.Unavailable
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability() == HealthAvailability.Available) HealthConnectClient.getOrCreate(context) else null

    /** 실제 승인된 권한 — 미가용/실패 시 빈 집합. 라이브 재파생(#236)의 입력. */
    suspend fun grantedPermissions(): Set<String> =
        runCatching { clientOrNull()?.permissionController?.getGrantedPermissions() }
            .getOrNull()
            .orEmpty()

    /**
     * 코어 루프 진입 자격 (#236) — **필수 권한만** 본다. 판정 규칙은 core [ConnectGate].
     *
     * 예전 이름(`hasAllPermissions`)이 곧 버그였다: 전부를 요구해 선택 권한 하나가 오탈락을 만들었다.
     */
    suspend fun hasRequiredPermissions(): Boolean =
        ConnectGate.isConnected(grantedPermissions(), HealthPermissions.REQUIRED)

    /** 승인되지 않은 선택 권한 — '꺼진 능력' 표시·비차단 재요청 대상 (#236). */
    suspend fun missingOptionalPermissions(): Set<String> =
        ConnectGate.missingOptional(grantedPermissions(), HealthPermissions.OPTIONAL)

    /** rememberLauncherForActivityResult에 넘길 권한 요청 컨트랙트. */
    fun requestPermissionsContract() = PermissionController.createRequestPermissionResultContract()

    /** 걸음 읽기용 리포지토리 (#7). HC 미가용 시 null → 데모 모드. */
    fun stepRepositoryOrNull(): StepRepository? = clientOrNull()?.let { StepRepository(it) }

    /** 운동 세션 읽기용 리포지토리 (#8). HC 미가용 시 null. */
    fun exerciseRepositoryOrNull(): ExerciseRepository? =
        clientOrNull()?.let { ExerciseRepository(it, DeviceSourceStore(context)) }

    /** 수면 읽기용 리포지토리 (#180) — 컨디션 회복 보정. HC 미가용 시 null. */
    fun sleepRepositoryOrNull(): SleepRepository? = clientOrNull()?.let { SleepRepository(it) }

    /** 성장 데이터(롤업·성향) 리포지토리 (#170). HC 미가용 시 null. */
    fun growthRepositoryOrNull(): GrowthRepository? =
        clientOrNull()?.let { GrowthRepository(StepRepository(it), ExerciseRepository(it, DeviceSourceStore(context))) }
}
