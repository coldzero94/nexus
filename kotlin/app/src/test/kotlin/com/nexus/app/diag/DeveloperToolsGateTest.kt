package com.nexus.app.diag

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 개발자 도구 릴리스 부재 게이트 (#245, E15-12) — **도구가 Play로 나가지 않는다**를 못 박는다.
 *
 * ## 왜 소스 스캔인가
 *
 * 단위 테스트는 debug 변형으로 돌아서 릴리스 APK를 열어볼 수 없다. 그래서 릴리스에 없다는 걸
 * **소스셋 배치**로 증명한다: 도구 코드와 문자열이 `src/debug`에만 있고, `src/main`·`src/release`가
 * 그것을 참조하지 않으면 릴리스 컴파일 단위에 들어갈 경로가 없다.
 *
 * `ColorTokenGuardTest`와 같은 방식이다 — 이 저장소에는 정책을 소스 스캔으로 강제하는 선례가 있다.
 *
 * ## 이 게이트가 막는 실패
 *
 * `BuildConfig.DEBUG` 분기로 숨기는 흔한 방식은 **코드와 문자열을 릴리스 APK에 그대로 남긴다.**
 * "원장 전체 삭제" 버튼이 리버싱 가능한 형태로 실린다는 뜻이고, 그건 crown jewel 옆에 둘 물건이
 * 아니다. 누군가 편의를 위해 도구를 main으로 옮기면 여기서 깨진다.
 */
class DeveloperToolsGateTest {
    private val repo = File("..").canonicalFile
    private val app = File(repo, "app")

    /** 디버그 소스셋에만 존재해야 하는 도구 타입. */
    private val debugOnlyTypes = listOf("DevResets", "DevLedgerSeeder", "DevToolsReadout")

    private fun ktFiles(dir: File): List<File> =
        if (!dir.isDirectory) emptyList() else dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `디버그 도구 카드는 debug와 release 소스셋 양쪽에 있다`() {
        // 짝이 없으면 SettingsScreen이 조건 없이 호출할 수 없고, 결국 BuildConfig.DEBUG 분기로 돌아간다
        val path = "kotlin/com/nexus/app/diag/DeveloperToolsCard.kt"

        assertTrue(File(app, "src/debug/$path").isFile, "디버그 짝이 없다")
        assertTrue(File(app, "src/release/$path").isFile, "릴리스 no-op 짝이 없다 — 릴리스 빌드가 깨진다")
    }

    @Test
    fun `리셋과 시더는 debug 소스셋에만 정의된다`() {
        val defined = debugOnlyTypes.associateWith { type ->
            val declaration = Regex("""(object|class)\s+$type\b""")
            listOf("src/main", "src/release")
                .flatMap { ktFiles(File(app, it)) }
                .filter { declaration.containsMatchIn(it.readText()) }
                .map { it.relativeTo(repo).path }
        }.filterValues { it.isNotEmpty() }

        assertEquals(
            emptyMap(),
            defined,
            "원장 삭제·시드 코드가 릴리스 컴파일 단위에 있다 — src/debug로 옮기세요 (#245)",
        )
    }

    @Test
    fun `main과 release는 도구 내부를 참조하지 않는다`() {
        val offenders = listOf("src/main", "src/release")
            .flatMap { ktFiles(File(app, it)) }
            .filter { file -> debugOnlyTypes.any { it in file.readText() } }
            .map { it.relativeTo(repo).path }

        assertEquals(
            emptyList(),
            offenders,
            "릴리스에 컴파일되는 코드가 디버그 전용 타입을 참조한다 — 릴리스 빌드가 깨진다 (#245)",
        )
    }

    @Test
    fun `도구 문자열은 debug 리소스에만 있다`() {
        val devString = Regex("""name="dev_tools_""")
        val mainRes = File(app, "src/main/res")

        val offenders = (if (mainRes.isDirectory) mainRes.walkTopDown() else emptySequence())
            .filter { it.isFile && it.extension == "xml" }
            .filter { devString.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repo).path }
            .toList()

        assertEquals(
            emptyList(),
            offenders,
            "개발자 도구 문자열이 main 리소스에 있다 — 릴리스 APK에 실린다 (#245)",
        )
        assertTrue(
            File(app, "src/debug/res/values/strings.xml").readText().contains("dev_tools_title"),
            "디버그 문자열이 debug 소스셋에 없다",
        )
    }

    @Test
    fun `릴리스 짝은 아무 동작도 하지 않는다`() {
        val body = File(app, "src/release/kotlin/com/nexus/app/diag/DeveloperToolsCard.kt").readText()

        // 릴리스 짝에 배선이 생기면 그게 곧 릴리스 진입점이다
        listOf("DiagnosticsCollector", "LedgerIntegrity", "startActivity", "clearAllTables").forEach {
            assertTrue(it !in body, "릴리스 no-op 짝이 $it 를 참조한다 — 더 이상 no-op이 아니다")
        }
    }
}
