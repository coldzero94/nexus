package com.nexus.app.character

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import com.nexus.core.BadgeAssetConvention
import com.nexus.core.BadgeTable
import com.nexus.core.BadgeTableReader
import com.nexus.core.CharacterAnimationSet
import com.nexus.core.CharacterAssetConvention
import com.nexus.core.DialoguePool
import com.nexus.core.DialogueTable
import com.nexus.core.EquipCatalog
import com.nexus.core.EquipCatalogReader
import com.nexus.core.ExpeditionRewardPicker
import com.nexus.core.ExpeditionRewardTable
import com.nexus.core.MonthlyBadgeTable
import com.nexus.core.MonthlyBadgeTableReader
import com.nexus.core.MoodTable
import com.nexus.core.MoodTriggerTable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 규약 이름 → 드로어블 id 원본 조회. 없으면 0.
 *
 * `getIdentifier`는 규약 기반 동적 조회가 목적이라 의도적 사용 — 에셋 추가에 코드 수정이 없어야
 * 한다는 E4-1 완료 기준 때문이다(정적 R 참조는 상태 추가마다 코드 수정이 필요해진다).
 * 비싼 건 이 호출 자체이므로 [CharacterAssets]가 **인스턴스당** 이름 하나에 한 번만 부른다(#246).
 */
@SuppressLint("DiscouragedApi")
private fun Context.drawableIdByName(name: String): Int = resources.getIdentifier(name, "drawable", packageName)

/**
 * 캐릭터 에셋 로더 (#25, E4-1). 컴포저(E4-2)의 유일한 에셋 진입점.
 *
 * 에셋 추가 절차(코드 무수정): ① drawable에 `character_{state}_{frame}` 파일 추가
 * ② assets/character/animations.json에 상태 항목 추가 — 끝.
 *
 * @param lookup res id 원본 조회. 기본은 [Context.drawableIdByName]이고, 주입 가능한 이유는
 *   **테스트가 실제 조회 횟수를 세야 하기 때문**이다(#246 AC ①) — 메모가 도는지는 반환값으로는
 *   드러나지 않고 호출 횟수로만 드러난다.
 */
class CharacterAssets(private val context: Context, private val lookup: (String) -> Int = context::drawableIdByName) {

    /**
     * 이름 → res id 메모 (#246). 프레임 티커가 매 틱 조회하면 리플렉션(`getIdentifier`)이
     * 재컴포지션 임계경로에서 돌아 저사양 기기에서 프레임을 떨어뜨린다.
     *
     * res id는 프로세스 수명 동안 불변이다 — 설정 변경(다크 모드·로케일)은 id가 아니라 **로드 시점의
     * 자원 선택**을 바꾸므로 메모가 상하지 않는다. 없는 이름(0)도 캐시한다: 폴백 경로가 매 프레임
     * 헛조회를 반복하지 않게.
     *
     * 범위는 **인스턴스당**이다. 프로덕션은 여러 화면이 각자 [CharacterAssets]를 만들지만, 값비싼 건
     * 반복 조회가 아니라 *한 화면이 프레임마다* 조회하는 것이라 컴포저블이 `remember`한 인스턴스
     * 하나면 충분하다. 프로세스 전역으로 올리면 주입된 [lookup]을 다른 인스턴스의 캐시가 가로채
     * 조회 횟수를 세는 테스트가 성립하지 않는다.
     */
    private val resolved = ConcurrentHashMap<String, Int>()

    /** 애니메이션 메타 로드 — 잘못된 표는 여기서 즉시 실패(조용한 무애니메이션 방지, core 검증). */
    fun loadAnimationSet(): CharacterAnimationSet = context.assets.open(META_PATH).bufferedReader().use {
        CharacterAssetConvention.parse(it.readText())
    }

    /** 대사 풀 로드 (#29) — 같은 fail-fast 계약. 대사 수정 = JSON만(코드 무수정). */
    fun loadDialoguePool(): DialoguePool = context.assets.open(DIALOGUE_PATH).bufferedReader().use {
        DialogueTable.parse(it.readText())
    }

    /** 기분 트리거 표 로드 (#28) — 같은 fail-fast 계약. 임계값·규칙 수정 = JSON만(코드 무수정). */
    fun loadMoodTable(): MoodTable = context.assets.open(MOOD_PATH).bufferedReader().use {
        MoodTriggerTable.parse(it.readText())
    }

    /** 배지 해금 표 로드 (#69, 게임 데이터) — 같은 fail-fast 계약. 배지 추가·조건 수정 = JSON만. */
    fun loadBadgeTable(): BadgeTable = context.assets.open(BADGE_PATH).bufferedReader().use {
        BadgeTableReader.parse(it.readText())
    }

    /** 월 한정 배지 캘린더 로드 (#38) — 같은 fail-fast 계약. 새 달 배지 추가 = JSON만. */
    fun loadMonthlyBadgeTable(): MonthlyBadgeTable = context.assets.open(MONTHLY_BADGE_PATH).bufferedReader().use {
        MonthlyBadgeTableReader.parse(it.readText())
    }

    /** 장비 카탈로그 로드 (#37) — 같은 fail-fast 계약. 장비 추가 = JSON + 드로어블만(코드 무수정). */
    fun loadEquipCatalog(): EquipCatalog = context.assets.open(EQUIPMENT_PATH).bufferedReader().use {
        EquipCatalogReader.parse(it.readText())
    }

    /** 규약 이름 → 드로어블 id. 없으면 null(호출자가 기본 상태 프레임으로 폴백). */
    @DrawableRes
    fun frameResIdOrNull(state: String, frame: Int): Int? =
        resolveDrawable(CharacterAssetConvention.frameName(state, frame))

    /**
     * 배지 글리프 접미사 → 드로어블 id (#266). 없으면 기본 글리프, 그것도 없으면 null.
     *
     * [frameResIdOrNull]과 같은 이유로 규약 기반 조회다 — **배지 추가는 JSON만**이라는 #69 계약을
     * 지키려면 정적 R 참조를 쓸 수 없다(배지마다 코드 수정이 필요해진다). 정식 아트(#76)는 같은
     * 이름으로 드로어블을 넣으면 코드 무수정으로 갈아탄다.
     */
    @DrawableRes
    fun badgeIconResIdOrNull(icon: String?): Int? = resolveDrawable(BadgeAssetConvention.iconName(icon))
        ?: resolveDrawable(BadgeAssetConvention.iconName(null))

    private fun resolveDrawable(name: String): Int? {
        resolveCount.incrementAndGet()
        return resolved.computeIfAbsent(name, lookup).takeIf { it != 0 }
    }

    /** 원정 보상 표 (#68). 보상 추가·수정 = JSON만(코드 무수정) — 배지 표와 같은 규약. */
    fun loadExpeditionRewards(): ExpeditionRewardTable =
        context.assets.open(EXPEDITION_PATH).bufferedReader().use { ExpeditionRewardPicker.parse(it.readText()) }

    internal companion object {
        /**
         * res id **해석 시도** 횟수 (#246 AC ①) — 캐시 히트도 센다.
         *
         * 미스만 세면 안 된다. 메모가 있는 한 프레임마다 [frameResIdOrNull]을 불러도 카운터는
         * 그대로라, "프레임 루프 안에서 해석하지 않는다"는 AC의 나머지 절반이 측정되지 않는다
         * (리뷰가 실제로 그 변형을 통과시켰다). 시도를 세면 해석을 루프 안으로 되돌리는 순간 드러난다.
         *
         * 히트도 공짜가 아니다 — [frameResIdOrNull]은 메모에 닿기 전에
         * `CharacterAssetConvention.frameName`의 정규식 검증과 문자열 조립을 지난다.
         */
        @VisibleForTesting
        val resolveCount = AtomicInteger()

        const val META_PATH = "character/animations.json"
        const val DIALOGUE_PATH = "character/dialogue.json"
        const val MOOD_PATH = "character/mood_triggers.json"
        const val BADGE_PATH = "character/badges.json"
        const val MONTHLY_BADGE_PATH = "character/monthly_badges.json"
        const val EQUIPMENT_PATH = "character/equipment.json"
        const val EXPEDITION_PATH = "character/expeditions.json"
    }
}

/**
 * 한 상태의 프레임 res id를 **한 번에** 해석한다 (#246 AC ①) — 인덱스가 곧 프레임 번호,
 * 없는 프레임은 null.
 *
 * 티커는 이 목록을 인덱싱하기만 한다. 프레임마다 [CharacterAssets.frameResIdOrNull]을 부르면
 * 상태가 유지되는 내내(홈이 보이는 내내) 조회가 반복되는데, 상태당 프레임은 2~4개뿐이라
 * 진입 시 한 번이면 끝난다. 로더의 책임이 아니라 그 위의 파생이라 확장으로 둔다.
 */
internal fun CharacterAssets.frameResIds(state: String, frames: Int): List<Int?> =
    List(frames.coerceAtLeast(1)) { frameResIdOrNull(state, it) }
