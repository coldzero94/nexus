package com.nexus.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 색 토큰 회귀 방지 (#267, E16-17) — 하드코딩 색 리터럴이 **토큰 파일 밖으로 새지 않게** 못 박는다.
 *
 * CLAUDE.md·`rules/kotlin.md`의 "색/치수는 토큰 외 금지"는 문구만 있고 자동 검증이 없었다. 지금은
 * 위반이 0건이라 못 박기에 가장 좋은 시점이다 — 토큰을 승격해 놓고도 새 코드가 다시 하드코딩으로
 * 새면 디자인 시스템이 무의미해진다.
 *
 * ## detekt 커스텀 규칙 대신 소스 스캔인 이유
 *
 * 완료 기준은 detekt 규칙을 제안했지만, 실측하니 `Color(0x…)` 리터럴은 토큰 파일 2개에만 있고
 * `Color` 타입 import는 3개 파일뿐인데 그중 하나는 **파라미터 타입으로 정당하게** 쓴다. 그래서
 * `ForbiddenImport`는 오탐이 나고, 생성자 호출만 잡는 `ForbiddenMethodCall`은 타입 해석이 필요해
 * 이 프로젝트의 루트 detekt 태스크에서 조용히 실행되지 않는다(`detekt.yml` 머리말에 기록됨).
 *
 * 커스텀 규칙 모듈은 JVM 모듈·플러그인 별칭·서비스 등록이 필요한데, AGP 9 내장 Kotlin 때문에
 * 플러그인 배선이 민감한 저장소다(CLAUDE.md 경고). 같은 보장을 훨씬 적은 표면으로 얻는 쪽을 택했다 —
 * 이 저장소엔 정책을 테스트로 강제하는 선례가 이미 있다(`TelemetryPolicyTest`의 이벤트 allowlist).
 */
class ColorTokenGuardTest {
    /** 색 토큰 **정의** 파일 — 여기서만 리터럴을 쓴다. */
    private val tokenFiles = setOf("NexusColors.kt", "VizColors.kt")

    /** `Color(0xFF…)` 형태의 하드코딩 색. 토큰을 거치지 않은 색이 바로 이 모양으로 들어온다. */
    private val colorLiteral = Regex("""Color\(\s*0x""")

    private fun sourceRoots(): List<File> {
        // 테스트 작업 디렉터리는 모듈 루트(app/) — 저장소 루트로 올라가 두 소스셋을 본다
        val repo = File("..").canonicalFile
        return listOf(File(repo, "app/src/main"), File(repo, "core/src"))
    }

    @Test
    fun `토큰 파일 밖에는 하드코딩 색 리터럴이 없다`() {
        val offenders = sourceRoots()
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
            .filterNot { it.name in tokenFiles }
            .filter { colorLiteral.containsMatchIn(it.readText()) }
            .map { it.relativeTo(File("..").canonicalFile).path }

        assertEquals(
            emptyList(),
            offenders,
            "색은 NexusColors·VizColors 토큰으로만 정의한다 — 화면에서 Color(0x…)를 쓰면 " +
                "다크 테마·팔레트 변경이 그 지점만 비켜간다. 새 색이 필요하면 토큰에 추가하고 이름으로 참조하세요.",
        )
    }

    @Test
    fun `가드가 실제로 소스를 읽고 있다`() {
        // 스캔 경로가 틀어져 0개 파일을 훑으면 위 테스트는 영원히 통과한다 — 그 침묵을 막는 카나리
        val scanned = sourceRoots()
            .filter { it.isDirectory }
            .sumOf { it.walkTopDown().count { f -> f.isFile && f.extension == "kt" } }
        assertEquals(true, scanned > 50, "스캔한 Kotlin 파일이 $scanned 개뿐 — 경로가 틀어졌다")
    }
}
