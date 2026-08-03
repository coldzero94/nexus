package com.nexus.app.growth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.loadStoryFragments
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이야기 조각 수집 (#112, E5-14).
 *
 * ## 이 티켓이 깨지는 방식
 *
 * 조각은 **워커가 굴린다**. 워커는 15분마다 같은 세션을 다시 읽으므로, 드롭이 조금이라도
 * 시간·난수에 기대면 사용자가 아무것도 안 해도 조각이 계속 늘어난다 — 며칠이면 도감이 다 차고,
 * 그 시점엔 "운동해서 얻었다"는 서사가 이미 거짓이 된 뒤다. 화면만 봐서는 구분되지 않는다.
 *
 * 그래서 여기서 고정하는 건 카드 모양이 아니라 **재읽기 불변식**이다.
 */
@RunWith(RobolectricTestRunner::class)
class StoryCodexTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val table get() = CharacterAssets(context).loadStoryFragments()

    private fun sessions(count: Int) = List(count) { "session-$it" }

    private fun load(ids: List<String>) = runBlocking { loadStoryCodex(context, ids) }

    @Test
    fun `조각 표가 파싱되고 비어 있지 않다`() {
        assertTrue(table.fragments.isNotEmpty())
    }

    /** 제목이나 본문이 비면 카드에 빈 줄이 그려진다 — 표만 고치면 되는 계약이라 여기서 막는다. */
    @Test
    fun `모든 조각에 제목과 본문이 있다`() {
        val empty = table.fragments.filter { it.title.isBlank() || it.body.isBlank() }

        assertTrue(empty.isEmpty(), "제목/본문이 빈 조각: ${empty.map { it.id }}")
    }

    /**
     * 이 기능이 성립하는 조건. 워커 재실행 = 같은 세션 재읽기이므로, 두 번째 로드는 아무것도
     * 새로 주지 않아야 한다.
     */
    @Test
    fun `같은 세션을 다시 읽어도 조각이 늘지 않는다`() {
        val ids = sessions(SAMPLE_SESSIONS)

        val first = load(ids)
        val second = load(ids)

        assertEquals(first!!.collected.size, second!!.collected.size, "재읽기로 조각이 늘었다")
        assertTrue(second.newlyFound.isEmpty(), "재읽기가 새 조각을 알렸다: ${second.newlyFound.map { it.id }}")
    }

    /** 양성 대조 — 위 단언은 드롭이 **아예 안 일어나도** 통과한다. */
    @Test
    fun `운동을 하면 조각을 얻는다`() {
        val state = load(sessions(SAMPLE_SESSIONS))

        assertTrue(state!!.newlyFound.isNotEmpty(), "세션 $SAMPLE_SESSIONS 개에서 조각이 하나도 안 나왔다")
    }

    /**
     * 가중치가 0에 수렴하거나 id가 특정 구간에만 몰리면 **영영 안 나오는 조각**이 생긴다.
     * 표만 고치는 계약이라 새 조각을 잘못 넣어도 화면은 멀쩡해 보인다.
     */
    @Test
    fun `충분히 오래 하면 모든 조각이 나온다`() {
        val state = load(sessions(MANY_SESSIONS))

        assertEquals(table.fragments.size, state!!.collected.size, "안 나오는 조각이 있다")
    }

    /** 반대쪽 — 첫 몇 번에 다 나오면 발견이 아니라 배급이다. */
    @Test
    fun `초반 몇 번으로는 도감이 안 찬다`() {
        val state = load(sessions(FEW_SESSIONS))

        assertTrue(
            state!!.collected.size < table.fragments.size,
            "$FEW_SESSIONS 번 만에 도감이 다 찼다 — 드롭 확률이 너무 높다",
        )
    }

    /** 저장소는 집합이라 두 번 넣어도 같다 — 축하는 **새로 들어온 것**에만 붙어야 한다. */
    @Test
    fun `수집 저장소는 새로 들어온 것만 돌려준다`() {
        val store = StoryCollectionStore(context)

        val first = store.collect(setOf("a", "b"))
        val second = store.collect(setOf("b", "c"))

        assertEquals(setOf("a", "b"), first)
        assertEquals(setOf("c"), second)
        assertEquals(setOf("a", "b", "c"), store.collected)
    }
}

/** 도감이 다 차지 않을 만큼 적고, 드롭이 확실히 일어날 만큼 많은 수. */
private const val SAMPLE_SESSIONS = 20

private const val FEW_SESSIONS = 3

/** 알파 사용자가 몇 달에 걸쳐 쌓을 세션 수 — 이만큼이면 모든 조각이 나와야 한다. */
private const val MANY_SESSIONS = 400
