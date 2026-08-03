package com.nexus.app.character

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.core.ActivityType
import com.nexus.core.MoodEvaluator
import com.nexus.core.SessionInput
import com.nexus.core.TrustTier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 운동 종류별 특화 반응 (#114, E5-15).
 *
 * ## 이 기능이 조용히 죽는 방식
 *
 * 반응이 **안 뜨는 게 아니라 늘 같은 게 뜬다**. 표에 규칙을 넣어도 우선순위가 잘못 걸리면
 * 앞선 규칙이 다 먹고, 아트가 없으면 `renderState`가 idle/walk로 되돌리고, `animations.json`에
 * 상태가 없으면 컴포저가 기본 상태로 되돌린다 — 셋 다 크래시도 로그도 없다(#66에서 실제로 겪었다).
 *
 * 그래서 **세션 → 기분 → 표정 → 아트**의 사슬을 끝까지 태워서 3종이 서로 다른 그림으로
 * 끝나는지 확인한다. 표만 보는 테스트는 화면이 같은 얼굴이어도 통과한다.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityReactionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val assets get() = CharacterAssets(context)

    private val today = LocalDate.of(2026, 8, 3)

    private fun session(type: ActivityType, minutes: Int, dayOffset: Long = 0) = SessionInput(
        type = type,
        minutes = minutes,
        tier = TrustTier.B,
        epochDay = today.toEpochDay() + dayOffset,
    )

    /** 세션 → 렌더 상태까지 실제 배선을 그대로 태운다. */
    private fun renderState(vararg sessions: SessionInput): String {
        val moodContext = MoodResolver.contextFromSessions(
            sessions.toList(),
            today,
            restMode = false,
            // 주간 목표 달성은 p1(뿌듯)이 다 먹으므로 도달 불가능하게 크게 잡는다
            goalDays = 99,
            condition = 70,
        )
        val result = MoodEvaluator.evaluate(assets.loadMoodTable(), moodContext)
        assertNotNull(result, "기분이 하나도 안 잡혔다")
        return MoodResolver.renderState(assets, result.face, moodContext.todayActiveMin)
    }

    @Test
    fun `걷기는 산책 반응이 된다`() {
        assertEquals("walk_along", renderState(session(ActivityType.WALKING, 30)))
    }

    @Test
    fun `러닝은 동행 반응이 된다`() {
        assertEquals("run_along", renderState(session(ActivityType.RUNNING, 30)))
    }

    @Test
    fun `근력은 알통 반응이 된다`() {
        assertEquals("flex", renderState(session(ActivityType.STRENGTH, 30)))
    }

    /**
     * 반응은 **가장 많이 한 운동**을 따른다. 러닝 40분 뒤 근력 5분을 했는데 알통이 뜨면
     * "내가 한 운동을 얘가 안다"가 오히려 깨진다.
     */
    @Test
    fun `여러 종류를 했으면 가장 많이 한 쪽이 이긴다`() {
        assertEquals(
            "run_along",
            renderState(session(ActivityType.RUNNING, 40), session(ActivityType.STRENGTH, 5)),
        )
        assertEquals(
            "walk_along",
            renderState(session(ActivityType.WALKING, 60), session(ActivityType.STRENGTH, 30)),
        )
    }

    /** 오늘 것만 본다 — 어제 뛴 걸로 오늘 뛴 척하면 반응이 거짓말이 된다. */
    @Test
    fun `어제 운동은 오늘 반응에 안 섞인다`() {
        assertEquals(
            "walk_along",
            renderState(session(ActivityType.WALKING, 30), session(ActivityType.RUNNING, 90, dayOffset = -1)),
        )
    }

    /**
     * 10분 미만은 평온이 받는다. 3분 걷고 '따라 산책'이 뜨면 연출이 과장이 되고, 무엇보다
     * 특화 반응이 **늘 뜨는 반응**이 돼 다양성이라는 목적 자체가 사라진다.
     */
    @Test
    fun `아주 짧은 활동은 평온이 받는다`() {
        assertEquals("calm_smile", renderState(session(ActivityType.WALKING, 5)))
    }

    /** 양성 대조 — 위 단언이 '항상 calm_smile'로 통과하지 않는지. */
    @Test
    fun `활동이 없으면 특화 반응이 안 뜬다`() {
        val state = renderState(session(ActivityType.WALKING, 30, dayOffset = -1))

        assertTrue(state !in SPECIAL_STATES, "활동 없는 날에 특화 반응이 떴다: $state")
    }

    /**
     * 세 반응이 **서로 다른 그림**인지. 같은 파일을 가리키면 규칙은 다 도는데 화면은 그대로라,
     * 위 단언들이 전부 초록인 채로 사용자는 아무 변화를 못 본다.
     */
    @Test
    fun `세 반응의 아트가 서로 다르다`() {
        val ids = SPECIAL_STATES.map { assets.frameResIdOrNull(it, 0) }

        assertTrue(ids.none { it == null }, "특화 반응 아트가 없다: $SPECIAL_STATES")
        assertEquals(ids.size, ids.distinct().size, "특화 반응 아트가 겹친다")
    }

    /** 기존 표정 5종과도 겹치면 안 된다 — 러닝인데 '신남'과 같은 그림이면 구분이 아니다. */
    @Test
    fun `특화 반응이 기존 표정과 겹치지 않는다`() {
        val existing = listOf("calm_smile", "jump_hyped", "proud_sparkle", "bored_lookaround", "cozy_roll")
            .mapNotNull { assets.frameResIdOrNull(it, 0) }

        val overlap = SPECIAL_STATES.mapNotNull { assets.frameResIdOrNull(it, 0) }.filter { it in existing }

        assertTrue(overlap.isEmpty(), "기존 표정과 같은 아트를 쓰는 특화 반응이 있다")
    }

    /** 대사도 종류마다 달라야 한다 — 그림만 바뀌고 말이 같으면 반쯤만 반응한 것이다. */
    @Test
    fun `종류마다 다른 대사를 준다`() {
        val table = assets.loadMoodTable()

        val lines = table.rules.filter { it.face in SPECIAL_STATES }.map { it.lines }

        assertEquals(SPECIAL_STATES.size, lines.size, "특화 반응 규칙 수가 안 맞는다")
        assertTrue(lines.none { it.isEmpty() }, "대사가 빈 특화 반응이 있다")
        assertEquals(lines.flatten().size, lines.flatten().distinct().size, "특화 반응 대사가 겹친다")
    }
}

private val SPECIAL_STATES = listOf("walk_along", "run_along", "flex")
