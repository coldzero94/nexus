package com.nexus.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord

/**
 * 온보딩(#6)이 요청하는 Health Connect 권한 5종: 읽기 3 + 백그라운드 + 과거 이력.
 * 데이터 읽기(집계·세션)는 #7·#8에서 이 위에 쌓는다.
 */
object HealthPermissions {
    val ALL: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )
}

/**
 * Health Connect 접근 래퍼. #6은 '가용성 확인 + 권한 요청'까지만 담당한다.
 * Health Connect는 안드로이드 전용 API라 core(KMP commonMain)가 아니라 app 모듈에 둔다.
 */
class HealthConnectManager(private val context: Context) {

    /** SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    /** 요청 권한이 모두 승인됐는지. 미가용/실패 시 false. */
    suspend fun hasAllPermissions(): Boolean {
        val granted = clientOrNull()?.permissionController?.getGrantedPermissions() ?: return false
        return granted.containsAll(HealthPermissions.ALL)
    }

    /** rememberLauncherForActivityResult에 넘길 권한 요청 컨트랙트. */
    fun requestPermissionsContract() =
        PermissionController.createRequestPermissionResultContract()

    /** 걸음 읽기용 리포지토리 (#7). HC 미가용 시 null → 데모 모드. */
    fun stepRepositoryOrNull(): StepRepository? = clientOrNull()?.let { StepRepository(it) }
}
