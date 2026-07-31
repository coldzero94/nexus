package com.nexus.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.core.ExpeditionEngine
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 위젯 무변화 갱신 스킵 (#246 AC ③) — 15분 워커가 값 변화와 무관하게 매번 168px ARGB(~113KB)를
 * 새로 래스터화하고 RemoteViews를 밀어 넣던 낭비를 없앤다.
 *
 * ## 여기서 지키는 함정
 *
 * 스냅샷만 비교하면 **진행 중 원정의 위젯이 얼어붙는다.** 스냅샷은 원정 *시작 시각*만 담고 잔여
 * 시간은 렌더 시점 시계 산술이라, 스냅샷이 한 글자도 안 바뀌는 동안 화면은 "약 6시간 남음" →
 * "약 5시간" → "개봉 대기"로 바뀌어야 한다. 스킵 판정이 그걸 못 보면 개봉 대기 전환이 영영
 * 올라오지 않아 4대 장치 ②(#72)가 죽는다. 그래서 렌더 키가 표시까지 포함한다.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetUpdateSkipTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun snapshot(
        level: Int = 3,
        todayXp: Int = 120,
        spriteState: String = "idle",
        expeditionStartedAt: Long = 0L,
    ) = WidgetSnapshot(
        level = level,
        condition = 80,
        todayXp = todayXp,
        spriteState = spriteState,
        expeditionStartedAt = expeditionStartedAt,
    )

    @Before
    fun clearStore() {
        context.getSharedPreferences("nexus_widget_snapshot", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ── 렌더 키: 무엇이 갱신을 부르는가 ──

    @Test
    fun `같은 값 같은 시각이면 렌더 키가 같다`() {
        val snap = snapshot()

        assertEquals(WidgetUpdater.renderKey(snap, NOW), WidgetUpdater.renderKey(snap, NOW))
    }

    @Test
    fun `표시값이 바뀌면 렌더 키가 달라진다`() {
        assertNotEquals(
            WidgetUpdater.renderKey(snapshot(level = 3), NOW),
            WidgetUpdater.renderKey(snapshot(level = 4), NOW),
        )
        assertNotEquals(
            WidgetUpdater.renderKey(snapshot(todayXp = 120), NOW),
            WidgetUpdater.renderKey(snapshot(todayXp = 130), NOW),
        )
        assertNotEquals(
            WidgetUpdater.renderKey(snapshot(spriteState = "idle"), NOW),
            WidgetUpdater.renderKey(snapshot(spriteState = "walk"), NOW),
        )
    }

    /**
     * 이 티켓에서 가장 놓치기 쉬운 것. 스냅샷은 동일한데 시계만 흘렀다 — 위젯 화면은 바뀐다.
     */
    @Test
    fun `원정 잔여가 한 시간 줄면 스냅샷이 같아도 렌더 키가 달라진다`() {
        val snap = snapshot(expeditionStartedAt = NOW)

        assertNotEquals(
            WidgetUpdater.renderKey(snap, NOW),
            WidgetUpdater.renderKey(snap, NOW + HOUR),
            "잔여 시간이 바뀌었는데 같은 키다 — 위젯이 '약 N시간 남음'에 얼어붙는다",
        )
    }

    @Test
    fun `원정이 개봉 대기로 넘어가면 렌더 키가 달라진다`() {
        val snap = snapshot(expeditionStartedAt = NOW)
        val justBefore = NOW + ExpeditionEngine.DURATION_MILLIS - 1

        assertNotEquals(
            WidgetUpdater.renderKey(snap, justBefore),
            WidgetUpdater.renderKey(snap, justBefore + 1),
            "개봉 대기 전환이 갱신을 부르지 않는다 — 4대 장치 ②가 죽는다",
        )
    }

    /**
     * 같은 시간대(같은 반올림 시각) 안에서는 스킵돼야 실익이 있다. 15분 워커가 원정 중
     * 매번 갱신하면 이 티켓이 잡으려던 낭비가 그대로 남는다.
     */
    @Test
    fun `원정 중이라도 표시가 같은 구간에서는 렌더 키가 같다`() {
        val snap = snapshot(expeditionStartedAt = NOW)

        assertEquals(
            WidgetUpdater.renderKey(snap, NOW + MINUTE),
            WidgetUpdater.renderKey(snap, NOW + 2 * MINUTE),
        )
    }

    // ── 실제 갱신 경로: 스킵과 통과 ──

    private suspend fun update(todayXp: Int) =
        WidgetUpdater.update(context, cappedTotalXp = 500, todayXp = todayXp, spriteState = "idle", condition = 80)

    @Test
    fun `무변화 두 번째 호출은 스냅샷을 다시 쓰지 않는다`() = kotlinx.coroutines.test.runTest {
        update(todayXp = 120)
        val store = WidgetSnapshotStore(context)
        val keyAfterFirst = store.lastRenderKey
        // 스토어를 직접 오염시킨다 — 두 번째 호출이 쓰기를 건너뛰면 이 값이 남는다
        store.write(store.read().copy(todayXp = SENTINEL))

        update(todayXp = 120)

        assertEquals(SENTINEL, WidgetSnapshotStore(context).read().todayXp, "무변화인데 다시 썼다")
        assertEquals(keyAfterFirst, WidgetSnapshotStore(context).lastRenderKey)
    }

    /** AC ⑤ — 값이 실제로 바뀌면 정상 갱신돼야 한다. 스킵이 과하면 위젯이 죽은 채로 남는다. */
    @Test
    fun `값이 바뀌면 갱신된다`() = kotlinx.coroutines.test.runTest {
        update(todayXp = 120)

        update(todayXp = 999)

        assertEquals(999, WidgetSnapshotStore(context).read().todayXp)
    }

    @Test
    fun `첫 호출은 언제나 갱신된다`() = kotlinx.coroutines.test.runTest {
        // 콜드 스타트의 기본 스냅샷과 첫 실측이 우연히 같아도 위젯은 한 번 그려져야 한다
        assertEquals("", WidgetSnapshotStore(context).lastRenderKey)

        update(todayXp = 0)

        assertNotEquals("", WidgetSnapshotStore(context).lastRenderKey)
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L
        const val SENTINEL = 7777
    }
}
