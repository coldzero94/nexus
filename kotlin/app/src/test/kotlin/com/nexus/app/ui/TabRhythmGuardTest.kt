package com.nexus.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 4탭 세로 리듬 회귀 방지 (#260, E16-10) — 탭 화면의 간격은 **공통 스케일에서만** 나온다.
 *
 * ## 이 테스트가 막는 것
 *
 * 활동 탭이 유일하게 수동 `Spacer(Modifier.height(...))`로 섹션을 벌려서, 같은 앱 안에서 탭을
 * 옮길 때 밀도와 리듬이 튀었다. 고쳐놓아도 다음에 섹션을 추가하는 사람이 손에 익은 `Spacer`를
 * 다시 넣으면 원래대로 돌아간다 — 눈으로는 잘 안 보이는 회귀라 테스트로 못박는다.
 *
 * ## 루트 파일만 보면 안 된다
 *
 * 처음엔 4개 루트 화면 파일만 스캔했는데, 리뷰에서 **이미 통과하면서 놓친 사례**가 나왔다:
 * `growth/TodayXpCard.kt`에 수동 Spacer가 있었고 그건 성장 탭 안에서 렌더된다. 이 저장소는 섹션·카드를
 * 화면당 별 파일로 쪼개 두므로(#311), 정작 Spacer가 들어갈 자리는 전부 루트 밖이다. 그래서
 * **탭 패키지 전체**를 훑는다.
 *
 * 리터럴 문자열이 아니라 정규식을 쓰는 이유도 같다 — `Spacer(modifier = Modifier.height(…))`,
 * 줄바꿈된 형태, `Modifier.size(…)`가 전부 리터럴 검사를 빠져나간다.
 *
 * ## 왜 전체 화면 연출은 제외인가
 *
 * `OnboardingScreen`·`WelcomeBackScene`·`InitialLevelScene`·`MainActivity`는 **탭이 아니라 전체
 * 화면 연출**이다. 요소가 몇 개뿐이고 `Arrangement.Center`로 화면 중앙에 세우며, 위아래 비대칭
 * 여백이 연출의 일부라(로고를 위로, 버튼을 아래로) 균일 스케일을 강제하면 오히려 나빠진다.
 * 탭 안 스크롤 목록과 성질이 다르다.
 *
 * [sceneExclusions]가 그 목록이고, **파일명이 아니라 경로로** 적어 다른 파일이 우연히 빠져나가지
 * 않게 한다. 목록에 없는 파일이 늘면 위 검사가 잡는다 — 예외를 늘리려면 여기 손을 대야 한다.
 *
 * `ColorTokenGuardTest`와 같은 방식 — 이 저장소에는 디자인 규약을 소스 스캔으로 강제하는 선례가 있다.
 */
class TabRhythmGuardTest {
    private val repo = File("..").canonicalFile

    /** 탭 화면과 그 섹션·카드가 사는 패키지 — 세로 리듬을 서로 맞춰야 하는 범위 전체. */
    private val tabPackages = listOf("home", "steps", "growth", "settings")

    /** 4탭의 루트 화면 — 공통 리듬 선언을 직접 확인할 대상. */
    private val tabScreens = listOf(
        "home/HomeScreen.kt",
        "steps/ActivityScreen.kt",
        "growth/GrowthScreen.kt",
        "settings/SettingsScreen.kt",
    )

    /** 손으로 준 세로 간격. 이름 붙인 인자·줄바꿈·`size(` 변형까지 잡는다. */
    private val manualVerticalSpacer =
        Regex("""Spacer\(\s*(modifier\s*=\s*)?Modifier\s*\.\s*(height|size)\(""")

    private fun packageDir(name: String) = File(repo, "app/src/main/kotlin/com/nexus/app/$name")

    private fun ktFiles(dir: File): List<File> =
        if (!dir.isDirectory) emptyList() else dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * 탭 패키지 안에 있지만 전체 화면 연출인 파일 — 위 KDoc의 이유로 균일 리듬 대상이 아니다.
     * 늘릴 때는 "정말 전체 화면 연출인가"를 확인한다(스크롤되는 목록이면 아니다).
     */
    private val sceneExclusions = setOf("home/WelcomeBack.kt")

    private fun scanned() = tabPackages
        .flatMap { ktFiles(packageDir(it)) }
        .filterNot { file -> sceneExclusions.any { file.path.endsWith(it) } }

    @Test
    fun `연출 예외 목록이 실제 파일을 가리킨다`() {
        // 파일을 옮기면 예외가 아무것도 제외하지 않으면서 조용히 남는다 — 그러면 검사가 오작동한다
        val missing = sceneExclusions.filterNot { File(repo, "app/src/main/kotlin/com/nexus/app/$it").isFile }

        assertEquals(emptyList(), missing, "연출 예외 경로가 바뀌었다 — 목록을 갱신하세요")
    }

    @Test
    fun `가드가 실제로 소스를 읽고 있다`() {
        // 경로가 바뀌면 이 테스트가 조용히 아무것도 검사하지 않게 된다 — 그게 최악의 실패 모드다
        val files = scanned()

        assertTrue(files.size >= tabScreens.size, "탭 패키지에서 읽은 파일이 ${files.size}개뿐이다 — 경로 확인")
        tabScreens.forEach { path ->
            assertTrue(File(repo, "app/src/main/kotlin/com/nexus/app/$path").isFile, "$path 가 없다 — 목록을 갱신하세요")
        }
    }

    @Test
    fun `탭 패키지에 수동 세로 Spacer가 없다`() {
        val offenders = scanned()
            .filter { manualVerticalSpacer.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repo).path }

        assertEquals(
            emptyList(),
            offenders,
            "탭 화면의 세로 간격은 Arrangement.spacedBy(NexusSpacing…)로만 준다 — " +
                "손으로 Spacer를 넣으면 탭마다 리듬이 갈린다 (#260)",
        )
    }

    @Test
    fun `정규식이 Spacer 변형을 전부 잡는다`() {
        // 이 테스트가 없으면 위 검사가 통과하는 이유가 "위반이 없어서"인지 "정규식이 못 잡아서"인지 모른다
        listOf(
            "Spacer(Modifier.height(NexusSpacing.md))",
            "Spacer(modifier = Modifier.height(NexusSpacing.md))",
            "Spacer(\n    Modifier.height(NexusSpacing.md),\n)",
            "Spacer(Modifier . height(NexusSpacing.md))",
            "Spacer(Modifier.size(NexusSpacing.md))",
        ).forEach { form ->
            assertTrue(manualVerticalSpacer.containsMatchIn(form), "이 형태를 못 잡는다: $form")
        }
        // 가로 여백·weight는 대상이 아니다 — 세로 리듬과 무관하다
        listOf("Spacer(Modifier.width(NexusSpacing.md))", "Spacer(Modifier.weight(1f))").forEach { form ->
            assertTrue(!manualVerticalSpacer.containsMatchIn(form), "이 형태는 잡으면 안 된다: $form")
        }
    }

    @Test
    fun `네 탭이 같은 세로 리듬과 화면 패딩을 쓴다`() {
        val rhythm = "Arrangement.spacedBy(NexusSpacing.lg)"
        val screenPadding = "padding(NexusSpacing.screen)"

        tabScreens.forEach { path ->
            val text = File(repo, "app/src/main/kotlin/com/nexus/app/$path").readText()
            assertTrue(rhythm in text, "$path 가 공통 세로 리듬($rhythm)을 쓰지 않는다")
            assertTrue(screenPadding in text, "$path 가 공통 화면 패딩($screenPadding)을 쓰지 않는다")
        }
    }
}
