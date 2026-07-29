package com.nexus.app.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.onboarding.OnboardingStore
import com.nexus.core.FirstSession
import com.nexus.core.FirstSessionCue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 첫 세션 판정의 **배선** (#211) — core 규칙이 아니라 기준선 래치와 자격 게이트를 고정한다.
 *
 * 이 얇은 래퍼가 티켓의 위험이 전부 모인 곳이다: 기준선을 언제 박느냐에 따라 소급 XP를 "네 움직임"으로
 * 축하하고, 자격 게이트가 없으면 몇 주 쓴 사용자에게 "첫 성장까지, 10분"이 뜬다.
 *
 * 에뮬 불요(#232 하네스) — 실 SharedPreferences를 Robolectric으로 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
class FirstSessionCueTest {
    private lateinit var store: OnboardingStore

    @Before
    fun setUp() {
        store = OnboardingStore(ApplicationProvider.getApplicationContext<Context>())
        store.firstSessionEligible = true
    }

    @Test
    fun `자격이 없으면 아무것도 뜨지 않는다 — 기존 설치 업데이트`() {
        // 온보딩을 다시 밟지 않는 사용자에게 '첫 세션'은 없다. 기준선도 건드리지 않는다.
        store.firstSessionEligible = false

        assertEquals(
            FirstSessionCue.None,
            resolveFirstSessionCue(store, lifetimeXp = 5000, todayXp = 0, awaitingFirstData = false),
        )
        assertEquals(FirstSession.NO_BASELINE, store.firstXpBaselineXp)
    }

    @Test
    fun `데이터 도착 전에는 기준선을 잡지 않는다`() {
        // 0으로 박아두면 30~60분 뒤 도착한 지난 28일치가 통째로 '증가분'이 된다
        val cue = resolveFirstSessionCue(store, lifetimeXp = 0, todayXp = 0, awaitingFirstData = true)

        assertEquals(FirstSessionCue.None, cue)
        assertEquals(FirstSession.NO_BASELINE, store.firstXpBaselineXp)
    }

    @Test
    fun `기준선을 세운 그 로드에서 바로 코치가 뜬다 — 첫 방문을 놓치지 않는다`() {
        val cue = resolveFirstSessionCue(store, lifetimeXp = 900, todayXp = 0, awaitingFirstData = false)

        assertEquals(FirstSessionCue.Coach, cue)
        assertEquals(900, store.firstXpBaselineXp, "이력 900이 기준선에 흡수돼야 축하로 새지 않는다")
    }

    @Test
    fun `기준선은 덮어쓰지 않는다 — 그 사이 첫 활동이 소급분으로 오인된다`() {
        resolveFirstSessionCue(store, lifetimeXp = 900, todayXp = 0, awaitingFirstData = false)
        resolveFirstSessionCue(store, lifetimeXp = 960, todayXp = 60, awaitingFirstData = false)

        assertEquals(900, store.firstXpBaselineXp)
    }

    @Test
    fun `기준선 이후 오늘 움직이면 축하`() {
        resolveFirstSessionCue(store, lifetimeXp = 900, todayXp = 0, awaitingFirstData = false)

        val cue = resolveFirstSessionCue(store, lifetimeXp = 960, todayXp = 60, awaitingFirstData = false)
        assertEquals(FirstSessionCue.FirstXp, cue)
    }

    @Test
    fun `늦게 도착한 소급분으로는 축하하지 않는다`() {
        // 이력 0건 상태로 기준선을 세운 뒤(=이미 데이터가 왔다고 판단된 시점), 지난 날짜분만 늘어난 경우
        resolveFirstSessionCue(store, lifetimeXp = 0, todayXp = 0, awaitingFirstData = false)

        val cue = resolveFirstSessionCue(store, lifetimeXp = 900, todayXp = 0, awaitingFirstData = false)
        assertEquals(FirstSessionCue.Coach, cue, "오늘 활동이 0인데 축하하면 하지도 않은 걸 축하하는 것")
    }

    @Test
    fun `플래그를 소진하면 프로세스가 죽어도 다시 뜨지 않는다`() {
        resolveFirstSessionCue(store, lifetimeXp = 0, todayXp = 0, awaitingFirstData = false)
        store.firstCoachShown = true
        store.firstXpCelebrated = true

        // 새 인스턴스 = 프로세스 재시작과 같은 조건(prefs에서 다시 읽는다)
        val reopened = OnboardingStore(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(
            FirstSessionCue.None,
            resolveFirstSessionCue(reopened, lifetimeXp = 40, todayXp = 40, awaitingFirstData = false),
        )
    }
}
