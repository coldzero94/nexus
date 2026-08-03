package com.nexus.app.growth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.loadStoryFragments
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.ExerciseSummary
import com.nexus.core.ActivityType
import com.nexus.core.RecordingMethod
import com.nexus.core.StoryDropPicker
import com.nexus.core.TrustReason
import com.nexus.core.TrustTier
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
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
        // 축하를 확인한 뒤 또 읽어도 새 조각이 없어야 한다 — 대기 집합이 다시 차면 축하가 무한 반복된다
        StoryCollectionStore(context).acknowledge()
        val third = load(ids)

        assertEquals(first!!.collected.size, second!!.collected.size, "재읽기로 조각이 늘었다")
        assertEquals(first.newlyFound, second.newlyFound, "재읽기가 축하 대상을 바꿨다")
        assertEquals(first.collected.size, third!!.collected.size, "확인 뒤 재읽기로 조각이 늘었다")
        assertTrue(third.newlyFound.isEmpty(), "확인한 조각을 다시 축하한다: ${third.newlyFound.map { it.id }}")
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

    /**
     * 반대쪽 — 매 운동마다 나오면 배급이다. 도감 상태로는 잴 수 없어(조각 8개에서 포화한다)
     * 드롭 자체의 빈도를 센다.
     */
    @Test
    fun `모든 운동이 조각을 주지는 않는다`() {
        val hits = sessions(MANY_SESSIONS).count { StoryDropPicker.drop(it, table, DROP_PERCENT) != null }

        // 20%의 ±10%p — 리듬이 실제로 이 근처인지 고정한다. 폭이 넓으면 확률을 바꿔도 안 걸린다
        assertTrue(
            hits in MANY_SESSIONS / 10..MANY_SESSIONS * 3 / 10,
            "운동 $MANY_SESSIONS 번 중 $hits 번 나왔다 — 목표는 ${DROP_PERCENT}%",
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

    /**
     * 축하 대기는 **확인할 때까지** 살아남는다. `collect`의 반환값만으로 축하하면 회전 한 번,
     * 프로세스 사망 한 번에 축하가 영영 사라진다(#61·#218이 같은 함정을 밟았다).
     */
    @Test
    fun `축하 대기는 확인 전까지 남는다`() {
        val store = StoryCollectionStore(context)
        store.collect(setOf("a"))

        // 다시 로드해도(= 새 인스턴스) 대기 집합은 그대로
        assertEquals(setOf("a"), StoryCollectionStore(context).pending)

        StoryCollectionStore(context).acknowledge()

        assertEquals(emptySet(), StoryCollectionStore(context).pending)
        assertEquals(setOf("a"), StoryCollectionStore(context).collected, "확인이 획득까지 지웠다")
    }

    /** 워커가 백그라운드로 채워둔 대기 집합을 화면이 집어야 한다 — 이번 로드의 신규만 보면 놓친다. */
    @Test
    fun `앞선 획득도 축하 대상으로 올라온다`() {
        val id = table.fragments.first().id
        StoryCollectionStore(context).collect(setOf(id))

        val state = load(emptyList())

        assertEquals(listOf(id), state!!.newlyFound.map { it.id })
    }

    /**
     * 신뢰 등급 우회 방지 (#112 리뷰) — 드롭을 굴리는 **두 곳**이 원장 지급과 같은 문
     * ([RewardLedgerRepository.isRewardable])을 통과한 세션만 굴리는지.
     *
     * 행위로 잡지 못하는 이유: 두 진입점 모두 실제 Health Connect 클라이언트를 만들어야 하고
     * 로보렉트릭에선 세션이 항상 비어, "수기 입력은 조각을 안 준다"가 **세션이 없어서** 통과한다.
     * 소스 가드는 필터의 삭제만 막는다 — 필터가 옳은지는 아래 등급 단언이 맡는다.
     */
    @Test
    fun `드롭 대상 세션은 원장과 같은 필터를 통과한다`() {
        val mainRoot = File(File("..").canonicalFile, "app/src/main/kotlin/com/nexus/app")

        listOf("growth/GrowthLoader.kt", "health/HealthSyncWorker.kt").forEach { path ->
            val text = File(mainRoot, path).readText()

            assertTrue(
                "isRewardable" in text,
                "$path 이 신뢰 필터 없이 세션을 굴린다 — 수기 입력(Tier C)이 조각을 받는다",
            )
        }
    }

    /** 위 가드가 지키는 명제 자체 — 수기·미상은 보상 대상이 아니다. */
    @Test
    fun `수기 입력과 미매핑 종목은 보상 대상이 아니다`() {
        val ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao())

        assertTrue(ledger.isRewardable(session()), "정상 세션이 보상 대상이 아니다")
        assertTrue(!ledger.isRewardable(session(tier = TrustTier.C)), "수기 입력이 보상 대상이다")
        assertTrue(!ledger.isRewardable(session(type = null)), "미매핑 종목이 보상 대상이다")
    }

    private fun session(type: ActivityType? = ActivityType.WALKING, tier: TrustTier = TrustTier.B) = ExerciseSummary(
        id = "s",
        type = type,
        exerciseTypeRaw = 0,
        start = Instant.EPOCH,
        end = Instant.EPOCH.plusSeconds(600),
        durationMinutes = 10,
        avgHeartRate = null,
        dataOrigin = "com.sec.android.app.shealth",
        recordingMethod = RecordingMethod.AUTO_RECORDED,
        trustTier = tier,
        trustReason = TrustReason.PHONE_RECORDED,
    )
}

/** 도감이 다 차지 않을 만큼 적고, 드롭이 확실히 일어날 만큼 많은 수. */
private const val SAMPLE_SESSIONS = 20

/** 알파 사용자가 몇 달에 걸쳐 쌓을 세션 수 — 이만큼이면 모든 조각이 나와야 한다. */
private const val MANY_SESSIONS = 400
