package com.nexus.app.growth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.ExerciseRepository
import com.nexus.app.health.HealthConnectManager

/**
 * 성장 탭 상태·행위 홀더 (#311) — 홈([com.nexus.app.home.HomeScreen]의 `HomeUiController`)과 같은 모양.
 *
 * 카드가 늘 때 화면 컴포저블이 길어지지 않게, Compose 상태는 프로퍼티 델리게이트로 여기서 소유하고
 * 사용자 행위는 메서드로 노출한다. 화면은 **렌더만** 한다.
 *
 * 상태를 컴포저블 본문에 흩어두면 카드를 하나 붙일 때마다 `var … by remember`가 늘어 화면 함수가
 * detekt 임계(60줄·파일당 11함수)를 향해 밀린다. S7에서 다섯 번 걸린 게 그 결과였다.
 */
internal class GrowthUiController(
    private val context: Context,
    private val manager: HealthConnectManager,
    private val exerciseRepo: ExerciseRepository?,
    private val ledger: RewardLedgerRepository,
    private val stateStore: GrowthStateStore,
    /** 테스트가 세우는 초기 상태 (#320·#263) — 프로덕션은 항상 null. */
    private val seed: GrowthSeed? = null,
) {
    var load by mutableStateOf(seed?.load)
        private set

    /** 기준점 대비 변화 (#61) — 레벨업·성향 변화. 없으면 null. */
    var change by mutableStateOf(seed?.change)
        private set

    /** 상시 배지(#175) + 이달의 배지(#206) — 요약보다 늦게 도착한다. */
    var badgeSections by mutableStateOf(BadgeSectionsState())
        private set

    /** 축하 카드 가시성 — dismiss는 토글(노드를 즉시 빼면 exit 연출이 생략된다, #61). */
    var celebrationVisible by mutableStateOf(true)
        private set

    /** 로드 재시도 트리거 (#227) — 키가 바뀌면 화면의 LaunchedEffect가 다시 돈다. */
    var reloadKey by mutableIntStateOf(0)
        private set

    /** 한 번의 로드 — 요약이 먼저 반영되고 배지는 뒤이어 채워진다 (#206). */
    suspend fun load() {
        if (seed != null) return // 테스트가 세운 상태 유지 (#320)
        badgeSections = loadGrowthScreen(context, manager, exerciseRepo, ledger, stateStore) { loaded, detected ->
            load = loaded
            change = detected
        }
    }

    /** 실패 화면의 '다시 시도' (#227) — 로딩 표시로 되돌려 눌렸다는 피드백을 준다. */
    fun retry() {
        load = null
        reloadKey++
    }

    /** 빈 상태에서 수동 동기화가 끝났을 때 (#213·#221) — 값만 다시 읽는다. */
    fun refreshAfterSync() {
        reloadKey++
    }

    /**
     * 변화 카드 확인 — 그 순간이 기준점이다. 재진입 시 같은 변화를 다시 축하하지 않는다.
     *
     * 기준점 소비를 감지 시점이 아니라 여기서 하는 이유: 감지 때 갱신하면 회전·프로세스 사망으로
     * 카드가 영영 소실된다(#61 리뷰).
     */
    fun dismissCelebration(state: GrowthUiState) {
        celebrationVisible = false
        stateStore.recordSeen(state.summary.level, state.summary.affinity)
    }
}
