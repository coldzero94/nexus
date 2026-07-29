package com.nexus.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * 앱 테마 (#64·#251, E16-1) — 시스템 다크 모드 추종. 전 화면이 MaterialTheme 토큰만 쓰므로
 * (하드코딩 색 금지 규칙) 스킴 교체만으로 다크 렌더가 성립한다.
 *
 * **다이내믹 컬러 정책(#251, docs/DESIGN.md)**: 기본은 **브랜드 스킴 고정**([NexusLightColors]/
 * [NexusDarkColors]) — 실기기 색이 배경화면에 100% 종속돼 앱 고유색이 사라지는 것을 막고 아이콘·
 * 스플래시·데이터 시각화 색과 일관되게 한다. [dynamicColor]=true는 옵트인(갤럭시 One UI 조화 선호
 * 시), 미설정 시 브랜드색.
 */
@Composable
fun NexusTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        // minSdk 34라 다이내믹 컬러(31+)는 항상 사용 가능 — 버전 가드 불필요(#242 lint)
        dynamicColor ->
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        useDarkTheme -> NexusDarkColors

        else -> NexusLightColors
    }
    // 데이터 시각화 팔레트(#257)는 M3 스킴 밖 토큰 — 동일 다크 판정에 묶어 주입.
    // 모션 스케일(#217)은 시스템 '애니메이션 제거'를 앱 전역에 알린다 — motionDuration()의 입력.
    // 정책 전반(캐릭터 티커·축하 연출 대체안)은 #228 소유이고, 여기선 공급 지점만 세운다.
    CompositionLocalProvider(
        LocalVizColors provides VizColors.palette(useDarkTheme),
        LocalMotionScale provides rememberSystemMotionScale(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexusTypography,
            shapes = NexusShapes,
            content = content,
        )
    }
}
