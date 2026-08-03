package com.nexus.app.character

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.core.MoodTriggerTable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 기분 표정 아트 배선 (#66, P-3).
 *
 * ## 왜 이 가드가 필요한가
 *
 * 표정은 **조용히 사라진다.** `MoodResolver.renderState`는 아트가 없으면 `idle`/`walk`로 폴백하고,
 * `CharacterComposer.BaseSprite`는 `animations.json`에 없는 상태를 `defaultState`로 되돌린다.
 * 둘 다 정상 경로라 로그도 크래시도 없다 — 화면엔 그냥 늘 같은 얼굴이 나온다.
 *
 * 실제로 이 티켓에서 드로어블 다섯 장을 넣고 JSON 항목을 빠뜨렸더니 아홉 칸이 전부 같은 얼굴로
 * 렌더됐다. 에뮬레이터에 띄워보기 전까지 아무것도 알려주지 않았다.
 *
 * 그래서 **표 → 아트 → 애니메이션 메타** 세 곳이 맞물리는지를 여기서 고정한다. 기분 표(#28·#65)는
 * "JSON만 고치면 코드 무수정"이 계약이므로, 새 기분을 추가한 사람이 아트를 잊으면 여기서 걸려야 한다.
 */
@RunWith(RobolectricTestRunner::class)
class MoodFaceAssetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val assets get() = CharacterAssets(context)

    private fun faces(): List<String> = MoodTriggerTable.parse(
        context.assets.open("character/mood_triggers.json").bufferedReader().use {
            it.readText()
        },
    )
        .rules
        .map { it.face }

    @Test
    fun `기분 표의 모든 표정에 아트가 있다`() {
        val missing = faces().filter { assets.frameResIdOrNull(it, 0) == null }

        assertTrue(missing.isEmpty(), "표정 아트가 없다 — 기분이 화면에 안 나타나고 idle로 폴백된다: $missing")
    }

    /**
     * 드로어블만 있고 `animations.json` 항목이 없으면 [CharacterComposer]가 기본 상태로 되돌린다 —
     * 파일은 있는데 화면엔 안 나오는, 가장 헷갈리는 실패 모양이다.
     */
    @Test
    fun `기분 표의 모든 표정이 애니메이션 메타에 있다`() {
        val states = assets.loadAnimationSet().states

        val missing = faces().filterNot { it in states }

        assertTrue(missing.isEmpty(), "animations.json에 상태가 없다 — 드로어블이 있어도 기본 상태로 폴백된다: $missing")
    }

    /** 표정은 정지 포즈다 — 프레임이 둘 이상이면 티커가 돌면서 없는 프레임을 찾는다. */
    @Test
    fun `표정은 단일 프레임이다`() {
        val states = assets.loadAnimationSet().states

        faces().forEach { face ->
            assertEquals(1, states.getValue(face).frames, "$face 가 여러 프레임이다 — 정지 포즈여야 한다")
        }
    }

    /**
     * 기분 다섯 종이 **서로 다른** 아트를 가리키는지. 같은 파일을 재사용하면 표정이 바뀌어도
     * 화면은 그대로여서, 기분 시스템이 도는데도 사용자는 아무 변화를 못 본다.
     */
    @Test
    fun `기분마다 다른 표정을 쓴다`() {
        val ids = faces().map { assets.frameResIdOrNull(it, 0) }

        assertEquals(ids.size, ids.distinct().size, "표정 아트가 겹친다: ${faces()}")
    }

    /** 기본 프레임도 함께 — 폴백 경로가 비면 캐릭터 자체가 안 그려진다. */
    @Test
    fun `기본 상태 프레임이 모두 있다`() {
        val set = assets.loadAnimationSet()

        set.states.forEach { (state, anim) ->
            repeat(anim.frames) { frame ->
                assertNotNull(assets.frameResIdOrNull(state, frame), "character_${state}_$frame 이 없다")
            }
        }
    }
}
