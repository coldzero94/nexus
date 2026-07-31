package com.nexus.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.nexus.app.data.ExpeditionStore
import com.nexus.app.home.EveningJournalStore
import com.nexus.app.home.MorningCardStore
import com.nexus.core.LevelCurve

/**
 * 위젯 갱신 진입점 (#40, E6-2) — 스냅샷 쓰기 + Glance updateAll을 한 곳으로.
 * 호출원: ① 동기화 워커(15분 — "준실시간"의 실제 한계, 위젯 문구도 이 전제) ② 홈 로드(앱 사용 시 즉시).
 * 컨디션은 28일 폴드가 필요해 홈 로드가 실계산 값을 주고, 워커는 마지막 값을 유지한다
 * (일 단위 변화라 15분 워커가 재계산할 가치가 없음 — 매일 첫 앱 진입이 갱신).
 */
object WidgetUpdater {

    @Suppress("TooGenericExceptionCaught")
    suspend fun update(
        context: Context,
        cappedTotalXp: Int,
        todayXp: Int,
        // 기분 배선 (#212): 홈이 평가한 렌더 상태를 전달. null이면 마지막 값 보존(워커는 기분 미평가).
        spriteState: String? = null,
        condition: Int? = null,
    ) {
        // best-effort 격리 (#40 리뷰 F1): 위젯은 코스메틱 부수효과 — 갱신 실패가
        // 홈 컴포지션을 죽이거나 성공한 동기화를 실패로 뒤집으면 안 된다. 광범위 catch는
        // 이 경계에서만 의도적(#130의 "삼킴 금지"는 임계 경로 대상 — 여기서는 로그로 드러냄).
        try {
            val store = WidgetSnapshotStore(context)
            val previous = store.read()
            val now = java.time.LocalDateTime.now()
            val today = now.toLocalDate().toEpochDay()
            val next = WidgetSnapshot(
                level = LevelCurve.displayLevel(cappedTotalXp),
                condition = condition ?: previous.condition,
                todayXp = todayXp,
                spriteState = spriteState ?: previous.spriteState,
                // 4대 장치 (#72): 원정 시각·아침/저녁 이벤트 — 프리퍼런스 소량 읽기라 계약 내
                expeditionStartedAt = ExpeditionStore(context).startedAtMillis ?: 0L,
                morningPending = MorningCardStore(context).lastShownEpochDay
                    .let { it != MorningCardStore.UNSET && it != today },
                journalPending = EveningJournalStore(context).lastShownEpochDay
                    .let { it != EveningJournalStore.UNSET && it != today } &&
                    now.hour >= EveningJournalStore.OPEN_HOUR,
            )
            val renderKey = renderKey(next)
            // 그릴 게 그대로면 쓰기도 updateAll도 건너뛴다 (#246 AC ③). 15분 워커가 값 변화와
            // 무관하게 매번 168px 비트맵을 새로 래스터화하고 RemoteViews를 밀어 넣던 낭비를 없앤다.
            if (renderKey == store.lastRenderKey) return
            store.write(next)
            store.lastRenderKey = renderKey
            NexusWidget().updateAll(context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "widget update failed — cosmetic, skipping", e)
        }
    }

    /**
     * 위젯이 그릴 것 전체를 한 값으로 (#246).
     *
     * 스냅샷만으로는 부족하다 — 원정 잔여 시간은 렌더 시점에 시계로 계산되므로 스냅샷이 그대로여도
     * 표시가 바뀐다([expeditionDisplay] KDoc). 스냅샷과 그 표시를 함께 넣어야 "약 6시간 남음"에
     * 얼어붙지 않고, 개봉 대기 전환이 다음 갱신(≤15분) 안에 올라간다.
     */
    internal fun renderKey(snapshot: WidgetSnapshot, nowMillis: Long = System.currentTimeMillis()): String =
        "$snapshot|${expeditionDisplay(snapshot.expeditionStartedAt, nowMillis)}"

    private const val TAG = "WidgetUpdater"
}
