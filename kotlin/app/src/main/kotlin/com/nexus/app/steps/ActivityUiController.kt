package com.nexus.app.steps

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.StepRepository

/**
 * 활동 탭 상태·행위 홀더 (#311) — 홈·성장과 같은 모양.
 *
 * Compose 상태를 여기서 소유하고 사용자 행위는 메서드로 노출한다. 화면은 렌더만 한다.
 */
internal class ActivityUiController(
    private val context: Context,
    private val stepRepo: StepRepository?,
    private val exerciseRepo: ExerciseRepository?,
    /**
     * 테스트가 세우는 초기 로드 상태 (#320) — **프로덕션은 항상 null**이다.
     *
     * Robolectric엔 Health Connect가 없어 repo가 늘 null이므로, 주입 없이는 미연결 분기 하나만
     * 렌더된다. 성공·실패·빈 상태를 그리는 테스트를 쓸 방법이 아예 없었다(#213 리뷰가 지적한 잠금).
     * 시임을 두는 선례는 [com.nexus.app.health.HealthSyncWorker.Seam](#234).
     */
    private val initialLoad: ActivityLoad? = null,
) {
    var load by mutableStateOf(initialLoad)
        private set

    /** 로드 재시도 트리거 (#227) — 키가 바뀌면 화면의 LaunchedEffect가 다시 돈다. */
    var reloadKey by mutableIntStateOf(0)
        private set

    /** 성공 로드의 데이터 — 없으면 null(로딩·미연결·실패). */
    val data: ActivityData? get() = (load as? ActivityLoad.Success)?.data

    /**
     * 권한 문제는 실패가 아닌 미연결 안내로 (#152, #144 패턴).
     *
     * 테스트가 상태를 세웠으면 덮어쓰지 않는다 — 화면의 LaunchedEffect가 즉시 이걸 불러
     * 주입한 분기를 미연결로 되돌리기 때문이다.
     */
    suspend fun load() {
        if (initialLoad != null) return
        load = if (stepRepo == null || exerciseRepo == null) {
            ActivityLoad.PermissionDenied
        } else {
            loadActivity(context, stepRepo, exerciseRepo)
        }
    }

    /** 실패 화면의 '다시 시도' (#227) — 로딩 표시로 되돌려 눌렸다는 피드백을 준다. */
    fun retry() {
        load = null
        reloadKey++
    }

    /** 빈 상태에서 수동 동기화가 끝났을 때 (#213·#221). */
    fun refreshAfterSync() {
        reloadKey++
    }
}
