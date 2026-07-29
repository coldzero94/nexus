package com.nexus.app.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.nexus.app.R
import com.nexus.app.ui.reduceMotion
import com.nexus.core.CharacterAnimationSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 캐릭터 컴포저 (#26, E4-2) — 앱과 위젯이 공유하는 단일 렌더링 모듈.
 * - 앱: [CharacterSprite] — animations.json 메타 기반 프레임 티커(2~4프레임).
 * - 위젯: [composeFrameBitmap] — 레이어 합성 비트맵(Glance ImageProvider 입력).
 * 프레임 산술은 core [com.nexus.core.AnimationState.frameAt] 하나만 쓴다(앱·위젯 동기).
 *
 * 깨진 메타(JSON)는 로드 시점에 즉시 크래시한다(core parse 계약) — 표는 앱에 번들된
 * 저작물이라 런타임 입력이 아니고, 조용한 무애니메이션보다 개발 중 즉시 발견이 낫다.
 */
object CharacterComposer {

    /**
     * 캐릭터 스프라이트 — [state]는 animations.json의 상태 키(미지 상태는 기본 상태 폴백).
     * [equipLayers]는 본체 위에 쌓을 장비 레이어 상태들(#37, core [com.nexus.core.Loadout.renderLayers]
     * 결과의 본체 이후 원소들). 장비는 정적 1프레임이라 애니메이션 없이 본체 위에 겹쳐 그린다.
     */
    @Composable
    fun CharacterSprite(state: String, modifier: Modifier = Modifier, equipLayers: List<String> = emptyList()) {
        val context = LocalContext.current
        val assets = remember { CharacterAssets(context) }
        Box(modifier) {
            BaseSprite(state, Modifier.matchParentSize())
            equipLayers.forEach { layer ->
                assets.frameResIdOrNull(layer, 0)?.let { resId ->
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null, // 장식 레이어 — 본체가 접근성 설명을 담당
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }

    /**
     * 본체 애니메이션 프레임 — 상태별 프레임 티커(2~4프레임).
     *
     * 시스템 '애니메이션 제거'가 켜져 있으면 **티커를 아예 기동하지 않는다**(#228) — 프레임 0 정지.
     * 표를 읽어 상태를 해석하는 일은 그대로 하므로 스프라이트는 정상적으로 그려지고, 움직임만 없다.
     * 홈·성장·초기 레벨 씬이 모두 이 한 곳을 지나므로 여기서 막으면 앱 전체가 함께 멈춘다.
     */
    @Composable
    private fun BaseSprite(state: String, modifier: Modifier) {
        val context = LocalContext.current
        val assets = remember { CharacterAssets(context) }
        var set by remember { mutableStateOf<CharacterAnimationSet?>(null) }
        val reduced = reduceMotion()
        // reduced를 키에 포함해야 토글 순간의 프레임에 굳지 않는다 — 그러면 걷는 중간 자세로 멈추고
        // 프레임 0을 쓰는 위젯과도 어긋난다(#228 리뷰). 켜고 끌 때 모두 0에서 다시 시작한다.
        var frame by remember(state, reduced) { mutableIntStateOf(0) }
        val placeholderDesc = stringResource(R.string.character_content_desc)

        LaunchedEffect(state, reduced) {
            val loaded = set ?: withContext(Dispatchers.IO) { assets.loadAnimationSet() }.also { set = it }
            if (reduced) return@LaunchedEffect // 표는 읽되 티커는 돌리지 않는다 — 정지 프레임 (#228)
            val anim = loaded.stateOrDefault(state)
            var elapsed = 0L
            while (anim.frames > 1) {
                delay(anim.frameDurationMs)
                elapsed += anim.frameDurationMs
                val next = anim.frameAt(elapsed)
                frame = next
                if (!anim.loop && next == anim.frames - 1) break // 비루프는 마지막 프레임 정지
            }
        }

        val loaded = set
        if (loaded == null) {
            // 로드 전 자리 예약 — 레이아웃 점프 방지(#26). 이 순간에도 설명은 있어야 한다: 없으면
            // 탭 가능한 캐릭터(#217)가 TalkBack에 "버튼"으로만 읽힌다 (#224).
            Box(modifier.semantics { contentDescription = placeholderDesc })
            return
        }
        val resolvedState = if (state in loaded.states) state else loaded.defaultState
        val resId = assets.frameResIdOrNull(resolvedState, frame)
            ?: assets.frameResIdOrNull(loaded.defaultState, 0)
            ?: return
        Image(
            painter = painterResource(resId),
            // 고정 '내 캐릭터'만 읽으면 기분·활동 상태가 시각 채널에만 남는다 (#224).
            // 상태 키(idle·walk·표정)를 사람이 읽는 라벨로 덧붙인다.
            contentDescription = stringResource(R.string.character_content_desc) +
                spriteStateSuffix(resolvedState),
            modifier = modifier,
        )
    }

    /**
     * 위젯용 프레임 비트맵 합성 — [layers]는 아래부터 위로 쌓을 상태들(v1은 본체 1장,
     * 장비 레이어는 E5). 프레임은 호출자가 [com.nexus.core.AnimationState.frameAt]로 선택.
     * 해석 불가한 레이어는 건너뛴다(위젯은 부분 렌더가 빈 위젯보다 낫다).
     */
    fun composeFrameBitmap(context: Context, layers: List<Pair<String, Int>>, sizePx: Int): Bitmap {
        require(sizePx > 0) { "sizePx must be > 0" }
        val assets = CharacterAssets(context)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layers.forEach { (state, frame) ->
            val resId = assets.frameResIdOrNull(state, frame) ?: return@forEach
            val drawable = ContextCompat.getDrawable(context, resId) ?: return@forEach
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        }
        return bitmap
    }
}

/** 스프라이트 상태를 낭독용 접미사로 (#224) — 모르는 상태는 접미사 없이 기본 설명만. */
@Composable
private fun spriteStateSuffix(state: String): String {
    val res = when (state) {
        "walk" -> R.string.a11y_sprite_walk
        "idle" -> R.string.a11y_sprite_idle
        else -> null
    } ?: return ""
    return ", " + stringResource(res)
}
