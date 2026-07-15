package com.nexus.app.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
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
import androidx.core.content.ContextCompat
import com.nexus.app.R
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

    /** 캐릭터 스프라이트 — [state]는 animations.json의 상태 키(미지 상태는 기본 상태 폴백). */
    @Composable
    fun CharacterSprite(state: String, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val assets = remember { CharacterAssets(context) }
        var set by remember { mutableStateOf<CharacterAnimationSet?>(null) }
        var frame by remember(state) { mutableIntStateOf(0) }

        LaunchedEffect(state) {
            val loaded = set ?: withContext(Dispatchers.IO) { assets.loadAnimationSet() }.also { set = it }
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

        val loaded = set ?: return
        val resolvedState = if (state in loaded.states) state else loaded.defaultState
        val resId = assets.frameResIdOrNull(resolvedState, frame)
            ?: assets.frameResIdOrNull(loaded.defaultState, 0)
            ?: return
        Image(
            painter = painterResource(resId),
            contentDescription = stringResource(R.string.character_content_desc),
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
