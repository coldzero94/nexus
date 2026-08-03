package com.nexus.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 스켈레톤이 **실제 화면에 배선돼 있는지** (#268).
 *
 * 컴포넌트 테스트만으로는 이 기능 전체를 되돌려도 초록이 유지된다 — 리뷰가 실증했다. 세 화면의
 * 로딩 분기를 스피너로 되돌리고 `StaggerItem`을 전부 걷어내도 `LoadingSkeletonTest`는 하나도 깨지지
 * 않았다. 스켈레톤을 **부를 수 있다**와 **부른다**는 다른 명제이고, 이 티켓이 지키려는 건 후자다.
 *
 * `ColorTokenGuardTest`·`TabRhythmGuardTest`와 같은 소스 스캔 방식이다 — 화면 조립은 시맨틱으로
 * 관측하기 어렵고(로딩 분기를 실기기 조건 없이 세우기 어렵다) 규칙 자체는 텍스트로 확인 가능하다.
 */
class SkeletonWiringTest {

    private val repo = File("..").canonicalFile

    private fun source(path: String): String {
        val file = File(repo, "app/src/main/kotlin/com/nexus/app/$path")
        assertTrue(file.isFile, "$path 가 없다 — 경로가 어긋나면 이 가드가 조용히 무력해진다")
        return file.readText()
    }

    @Test
    fun `세 탭의 로딩 분기가 스켈레톤을 쓴다`() {
        LOADING_BRANCHES.forEach { (path, skeleton) ->
            assertTrue(
                source(path).contains("$skeleton()"),
                "$path 의 로딩 분기가 $skeleton 을 쓰지 않는다 — 중앙 스피너로 되돌아갔을 수 있다",
            )
        }
    }

    /**
     * 되돌림을 잡는 쪽. 스켈레톤 호출을 남겨둔 채 스피너를 **함께** 두는 어중간한 상태도 막는다.
     */
    @Test
    fun `탭 화면에 맨 진행 인디케이터가 없다`() {
        LOADING_BRANCHES.keys.forEach { path ->
            assertTrue(
                !source(path).contains("CircularProgressIndicator"),
                "$path 에 CircularProgressIndicator가 남아 있다 — 로딩은 콘텐츠 형태로 (#268)",
            )
        }
    }

    /**
     * 등장 스태거가 본문 카드에 실제로 걸려 있는지. 컴포넌트만 있고 아무도 안 쓰면 AC ②는 없는 것이다.
     */
    @Test
    fun `본문 카드 스택이 스태거를 탄다`() {
        STAGGERED.forEach { (path, minCount) ->
            val count = Regex("StaggerItem\\(").findAll(source(path)).count()
            assertTrue(count >= minCount, "$path 의 StaggerItem이 ${count}개 — 최소 ${minCount}개여야 한다")
        }
    }

    /**
     * AC ②는 "fade **+** slide-up"이다. alpha는 시맨틱에도 픽셀에도 드러나지 않아(이 하네스에서
     * 컴포즈 노드 캡처가 불가능하다) 위치처럼 관측할 수 없다 — 둘 중 하나가 빠지는 회귀는 소스로만
     * 잡을 수 있다. 지금은 slide만 테스트로 고정돼 있고, `alpha = 1f`로 굳혀도 통과했다(리뷰 지적).
     */
    @Test
    fun `등장 연출이 투명도와 이동을 함께 건다`() {
        val text = File(repo, "app/src/main/kotlin/com/nexus/app/ui/StaggeredAppearance.kt").readText()
        val layer = text.substringAfter("return graphicsLayer {").substringBefore("}")

        assertTrue(layer.contains("alpha ="), "등장에서 페이드가 빠졌다")
        assertTrue(layer.contains("translationY ="), "등장에서 슬라이드가 빠졌다")
    }

    /**
     * 진행값이 재구성을 넘겨 살아남는지 — `remember`가 빠지면 재구성마다 새 `Animatable(0f)`이 생겨
     * **카드가 alpha 0·12dp 아래에 영구히 굳는다**(화면에서 영영 안 보인다).
     *
     * 소스로 잡는 이유: 이 하네스에서 관측이 불가능하다. `fetchSemanticsNode().positionInRoot`는
     * `setContent` 직후 첫 프레임 이후로는 낡은 값을 계속 주고(실측), `onGloballyPositioned` 궤적은
     * 레이아웃이 다시 돌 때만 갱신돼 그리기 전용 변화를 놓친다. 관측 불가를 인정하고 삭제를 막는다.
     */
    @Test
    fun `등장 진행값이 재구성을 넘겨 보존된다`() {
        val text = File(repo, "app/src/main/kotlin/com/nexus/app/ui/StaggeredAppearance.kt").readText()

        assertTrue(
            text.contains("remember { Animatable("),
            "Animatable이 remember 밖에 있다 — 재구성마다 초기화돼 카드가 영구히 안 보이게 된다",
        )
    }

    private companion object {
        val LOADING_BRANCHES = mapOf(
            "home/HomeScreen.kt" to "HomeSkeleton",
            "growth/GrowthScreen.kt" to "GrowthSkeleton",
            "steps/ActivityScreen.kt" to "ActivitySkeleton",
        )

        /** 각 화면이 감싸야 하는 최소 카드 수 — 실제 스택보다 보수적으로 잡는다. */
        val STAGGERED = mapOf(
            "home/HomeScreen.kt" to 5,
            "growth/GrowthCards.kt" to 3,
            "steps/ActivityScreen.kt" to 3,
        )
    }
}
