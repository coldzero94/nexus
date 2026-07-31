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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nexus.app.R
import com.nexus.app.ui.reduceMotion
import com.nexus.core.AnimationState
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
        val assets = remember(context) { CharacterAssets(context) }
        // 장비는 정적 1프레임이라 목록이 바뀔 때만 해석하면 된다 — 재컴포지션마다 조회할 이유가 없다 (#246)
        val equipResIds = remember(assets, equipLayers) {
            equipLayers.mapNotNull { assets.frameResIdOrNull(it, 0) }
        }
        Box(modifier) {
            BaseSprite(state, Modifier.matchParentSize(), assets)
            equipResIds.forEach { resId ->
                Image(
                    painter = painterResource(resId),
                    contentDescription = null, // 장식 레이어 — 본체가 접근성 설명을 담당
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }

    /**
     * 본체 애니메이션 프레임 — 상태별 프레임 티커(2~4프레임).
     *
     * 홈·성장·초기 레벨 씬이 모두 이 한 곳을 지나므로 여기서 막으면 앱 전체가 함께 멈춘다.
     */
    @Composable
    private fun BaseSprite(state: String, modifier: Modifier, assets: CharacterAssets) {
        var set by remember(assets) { mutableStateOf<CharacterAnimationSet?>(null) }
        val placeholderDesc = stringResource(R.string.character_content_desc)

        LaunchedEffect(assets) {
            if (set == null) set = withContext(Dispatchers.IO) { assets.loadAnimationSet() }
        }

        val loaded = set
        if (loaded == null) {
            // 로드 전 자리 예약 — 레이아웃 점프 방지(#26). 이 순간에도 설명은 있어야 한다: 없으면
            // 탭 가능한 캐릭터(#217)가 TalkBack에 "버튼"으로만 읽힌다 (#224).
            Box(modifier.semantics { contentDescription = placeholderDesc })
            return
        }
        val resolvedState = if (state in loaded.states) state else loaded.defaultState
        val anim = loaded.stateOrDefault(state)
        // 상태 진입 시 1회 해석 — 프레임 루프 안에서는 이 목록을 인덱싱만 한다 (#246 AC ①)
        val frameResIds = remember(assets, resolvedState, anim.frames) {
            assets.frameResIds(resolvedState, anim.frames)
        }
        val fallbackResId = remember(assets, loaded.defaultState) {
            assets.frameResIdOrNull(loaded.defaultState, 0)
        }
        val frame = rememberSpriteFrame(anim)
        val resId = frameResIds.getOrNull(frame) ?: fallbackResId ?: return
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
     *
     * 같은 요청은 캐시된 비트맵을 **그대로 돌려준다** (#246 AC ④). 위젯은 15분마다, 그리고 배치된
     * 인스턴스마다 갱신되는데 스프라이트 상태는 idle/walk 2종뿐이라 매번 168px ARGB(~113KB)를
     * 새로 래스터화하는 건 순수한 낭비였다.
     *
     * **반환된 비트맵은 읽기 전용으로 다뤄야 한다** — 호출자가 여기에 덧그리면 캐시가 오염된다.
     * 캐시가 안전한 이유는 캐릭터 드로어블이 설정 비의존(테마 속성·`drawable-night` 변형 없음)이기
     * 때문이다. 변형 리소스가 생기면 다크 모드에서 낡은 스프라이트가 남으므로,
     * `SpriteRenderWasteTest`가 그 전제를 리소스 스캔으로 고정한다.
     */
    fun composeFrameBitmap(context: Context, layers: List<Pair<String, Int>>, sizePx: Int): Bitmap {
        require(sizePx > 0) { "sizePx must be > 0" }
        val key = layers to sizePx
        synchronized(bitmapCache) { bitmapCache[key] }?.let { return it }
        val bitmap = renderFrameBitmap(context, layers, sizePx)
        synchronized(bitmapCache) {
            // 상태가 늘어도 메모리가 새지 않게 상한 — 항목이 2~3개라 LRU를 둘 가치가 없다
            if (bitmapCache.size >= MAX_CACHED_BITMAPS) bitmapCache.clear()
            bitmapCache[key] = bitmap
        }
        return bitmap
    }

    private fun renderFrameBitmap(context: Context, layers: List<Pair<String, Int>>, sizePx: Int): Bitmap {
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

    /** 테스트 전용 — 캐시를 비운다. 프로덕션엔 무효화 시점이 없다(리소스는 프로세스 내 불변). */
    internal fun clearBitmapCache() = synchronized(bitmapCache) { bitmapCache.clear() }

    private val bitmapCache = mutableMapOf<Pair<List<Pair<String, Int>>, Int>, Bitmap>()

    private const val MAX_CACHED_BITMAPS = 4
}

/**
 * 프레임 티커 — 현재 프레임 인덱스 (#26).
 *
 * 티커가 멈추는 조건은 **두 개이고 서로 무관하다.**
 *
 * ① 시스템 '애니메이션 제거'(#228) — 사용자가 움직임 자체를 원하지 않는다. 표는 읽되 티커를 아예
 *    기동하지 않아 프레임 0에 정지한다. `reduced`를 remember 키에 포함해야 토글 순간의 프레임에
 *    굳지 않는다 — 그러면 걷는 중간 자세로 멈추고 프레임 0을 쓰는 위젯과도 어긋난다(#228 리뷰).
 *
 * ② 화면 비가시(#246 AC ②) — 볼 사람이 없다. 홈을 열어둔 채 앱을 내리면 컴포지션은 그대로 남고
 *    `LaunchedEffect`도 살아 있어 **백그라운드에서 계속 프레임을 갈아치운다**. RESUMED 아래로
 *    내려가면 이펙트 키가 바뀌어 티커 코루틴이 취소되고, 돌아오면 다시 기동한다.
 *
 * 둘은 별개 트리거라 한쪽이 꺼져도 다른 쪽은 그대로 작동해야 한다.
 *
 * 가시성을 `repeatOnLifecycle`이 아니라 상태로 읽는 이유: 그쪽은 블록을 `Dispatchers.Main.immediate`로
 * 옮겨 실행해 티커가 **컴포지션의 코루틴 컨텍스트를 벗어난다**. 키로 게이트하면 프레임 클럭 위에
 * 그대로 남아 취소·재개가 컴포지션과 한 몸으로 움직인다.
 *
 * 경과 시간은 가시성 토글을 넘겨 이어진다 — 루프 애니메이션
 * (2~4프레임)에서는 어차피 보이지 않고, 비루프는 마지막 프레임에 멈춘 뒤 복귀해도 되감기지 않는다
 * (일회성 연출이 복귀마다 재생되면 안 된다).
 */
@Composable
internal fun rememberSpriteFrame(anim: AnimationState): Int {
    val reduced = reduceMotion()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var frame by remember(anim, reduced) { mutableIntStateOf(0) }
    val elapsed = remember(anim, reduced) { mutableLongStateOf(0L) }

    LaunchedEffect(anim, reduced, visible) {
        if (reduced || !visible || anim.frames <= 1) return@LaunchedEffect
        while (anim.loop || frame < anim.frames - 1) {
            delay(anim.frameDurationMs)
            elapsed.longValue += anim.frameDurationMs
            frame = anim.frameAt(elapsed.longValue)
        }
    }
    return frame
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
