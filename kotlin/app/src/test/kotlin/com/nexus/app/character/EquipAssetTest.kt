package com.nexus.app.character

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.core.EquipSlot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 장비 레이어 아트 배선 (#76, P-4).
 *
 * [MoodFaceAssetTest]와 같은 이유로 존재한다 — 장비도 **조용히 사라진다.**
 * `CharacterComposer.CharacterSprite`는 해석 못 한 레이어를 `mapNotNull`로 그냥 버리므로,
 * 카탈로그에 아이템을 추가하고 드로어블을 빠뜨리면 목록엔 칩이 뜨는데 캐릭터엔 아무것도 안 걸린다.
 * 사용자는 "장착했는데 안 보인다"를 겪고, 로그에는 아무것도 남지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class EquipAssetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val assets get() = CharacterAssets(context)

    private fun catalog() = assets.loadEquipCatalog().items

    @Test
    fun `카탈로그의 모든 장비에 아트가 있다`() {
        val missing = catalog().filter { assets.frameResIdOrNull(it.layerState, 0) == null }

        assertTrue(missing.isEmpty(), "장비 아트가 없다 — 장착해도 캐릭터에 안 걸린다: ${missing.map { it.id }}")
    }

    /** AC: 슬롯 2 × 4종. 슬롯 하나가 비면 그 슬롯 행이 통째로 사라진다(EquipmentSection). */
    @Test
    fun `슬롯마다 네 종류가 있다`() {
        EquipSlot.entries.forEach { slot ->
            val count = catalog().count { it.slot == slot }
            assertEquals(EXPECTED_PER_SLOT, count, "$slot 슬롯이 ${count}종 — ${EXPECTED_PER_SLOT}종이어야 한다")
        }
    }

    /** 아이템마다 다른 아트여야 한다 — 같은 파일을 재사용하면 갈아입어도 화면이 그대로다. */
    @Test
    fun `장비마다 다른 아트를 쓴다`() {
        val ids = catalog().map { assets.frameResIdOrNull(it.layerState, 0) }

        assertEquals(ids.size, ids.distinct().size, "장비 아트가 겹친다: ${catalog().map { it.layerState }}")
    }

    /**
     * 장비 레이어는 **몸과 같은 상태 이름 공간**을 쓴다(`character_{layerState}_0`). 기분 표정이나
     * 기본 프레임과 이름이 겹치면 장비를 장착하는 순간 몸이 통째로 덮이거나 표정이 사라진다.
     */
    @Test
    fun `장비 레이어 이름이 몸 상태와 겹치지 않는다`() {
        val bodyStates = assets.loadAnimationSet().states.keys

        val collided = catalog().map { it.layerState }.filter { it in bodyStates }

        assertTrue(collided.isEmpty(), "장비가 몸 상태 이름을 덮어쓴다: $collided")
    }

    private companion object {
        const val EXPECTED_PER_SLOT = 4
    }
}
