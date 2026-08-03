package com.nexus.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 모션 테스트 하네스 함정 가드 (#338).
 *
 * ## 왜 필요한가
 *
 * `NexusTheme`은 [LocalMotionScale]을 **시스템 값으로 다시 공급한다**(NexusTheme.kt). 그래서
 * 테스트가 테마 **바깥**에서 이 로컬을 주입하면 아무 효과가 없고, 리듀스드모션을 검증한다고 믿는
 * 테스트가 사실은 기본값(1f)만 계속 통과시킨다. #268에서 이 파일의 리듀스드모션 단언 전부가
 * 그렇게 무력했고, 부정 테스트를 돌리기 전까지 아무도 몰랐다.
 *
 * 지금 위반은 0건이다. 그게 이 가드가 필요한 이유다 — **재발이 조용하기 때문에** 위반이 없는
 * 상태에서 미리 못을 박아야 한다. 실패해도 화면은 멀쩡하고 CI는 초록이다.
 *
 * `ColorTokenGuardTest`·`TabRhythmGuardTest`와 같은 소스 스캔 방식이다.
 */
class MotionHarnessGuardTest {

    private val testRoot = File(File("..").canonicalFile, "app/src/test/kotlin/com/nexus/app")

    private fun testSources(): List<File> {
        assertTrue(testRoot.isDirectory, "테스트 경로가 어긋났다: $testRoot — 가드가 조용히 무력해진다")
        // 자기 자신은 뺀다 — 정규식 리터럴이 자기 패턴에 걸린다
        return testRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != SELF }
            .toList()
    }

    /**
     * 테마 바깥 주입을 잡는다.
     *
     * 판정: 한 파일 안에서 `CompositionLocalProvider(...LocalMotionScale...)`의 **여는 블록 바로
     * 다음**에 `NexusTheme`이 오면 뒤집힌 것이다. 반대로 `NexusTheme { CompositionLocalProvider(`는
     * 올바른 순서다. `NexusTheme`을 아예 안 쓰는 테스트(스프라이트 티커 등)는 덮어쓸 것이 없어 무관하다.
     */
    @Test
    fun `모션 스케일 주입은 NexusTheme 안쪽이어야 한다`() {
        val inverted = testSources().filter { file ->
            INVERTED.containsMatchIn(file.readText())
        }

        assertTrue(
            inverted.isEmpty(),
            "NexusTheme 바깥에서 LocalMotionScale을 주입한다 — 테마가 시스템 값으로 덮어써 무력해진다: " +
                inverted.map { it.name },
        )
    }

    /**
     * "안 움직인다"만 단언하고 끝내지 않게 한다.
     *
     * 리듀스드모션 테스트는 아무것도 안 그려도, 화면이 안 떠도 통과할 수 있다 — 실제로 #268에서
     * 조회 횟수를 0으로 재고 "늘지 않았다"가 공허하게 참이 된 적이 있다. 감축을 단언하는 파일에는
     * **평소 동작을 단언하는 짝**이 함께 있어야 한다.
     */
    @Test
    fun `모션 감축 테스트에는 양성 대조가 있다`() {
        val missing = testSources().filter { file ->
            val text = file.readText()
            REDUCED_ASSERTION.containsMatchIn(text) && !POSITIVE_CONTROL.containsMatchIn(text)
        }

        assertTrue(
            missing.isEmpty(),
            "감축만 단언하고 평소 동작을 확인하지 않는다 — 아무것도 안 그려도 통과한다: " +
                missing.map { it.name },
        )
    }

    private companion object {
        const val SELF = "MotionHarnessGuardTest.kt"

        /** `CompositionLocalProvider(… LocalMotionScale …) { NexusTheme` — 순서가 뒤집힌 형태. */
        val INVERTED = Regex(
            """CompositionLocalProvider\([^)]*LocalMotionScale[^)]*\)\s*\{\s*NexusTheme""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** 감축 상태를 세우는 테스트인가 — 스케일 0 주입이 신호. */
        val REDUCED_ASSERTION = Regex("""LocalMotionScale provides 0f|motionScale = 0f""")

        /**
         * 짝이 되는 평소-동작 단언이 있는가. 같은 파일에 스케일 1f(또는 기본값 렌더)로 무언가를
         * 확인하는 테스트가 있으면 통과 — 이름 규칙 대신 존재로 본다.
         */
        val POSITIVE_CONTROL = Regex("""motionScale = 1f|LocalMotionScale provides 1f|평소|양성 대조""")
    }
}
