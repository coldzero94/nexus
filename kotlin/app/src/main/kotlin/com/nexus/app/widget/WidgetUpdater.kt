package com.nexus.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.nexus.core.LevelCurve

/**
 * 위젯 갱신 진입점 (#40, E6-2) — 스냅샷 쓰기 + Glance updateAll을 한 곳으로.
 * 호출원: ① 동기화 워커(15분 — "준실시간"의 실제 한계, 위젯 문구도 이 전제) ② 홈 로드(앱 사용 시 즉시).
 * 컨디션은 28일 폴드가 필요해 홈 로드가 실계산 값을 주고, 워커는 마지막 값을 유지한다
 * (일 단위 변화라 15분 워커가 재계산할 가치가 없음 — 매일 첫 앱 진입이 갱신).
 */
object WidgetUpdater {

    suspend fun update(
        context: Context,
        cappedTotalXp: Int,
        todayXp: Int,
        todayActive: Boolean,
        condition: Int? = null,
    ) {
        val store = WidgetSnapshotStore(context)
        val previous = store.read()
        store.write(
            WidgetSnapshot(
                level = LevelCurve.displayLevel(cappedTotalXp),
                condition = condition ?: previous.condition,
                todayXp = todayXp,
                spriteState = if (todayActive) "walk" else "idle",
            ),
        )
        NexusWidget().updateAll(context)
    }
}
