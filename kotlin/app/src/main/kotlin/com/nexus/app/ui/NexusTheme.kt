package com.nexus.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 앱 테마 (#64, E4-11) — 시스템 다크 모드 추종. 전 화면이 MaterialTheme 토큰만 쓰므로
 * (하드코딩 색 금지 규칙) 스킴 교체만으로 다크 렌더가 성립한다.
 * Android 12+는 다이내믹 컬러(배경화면 팔레트) — 갤럭시 One UI 톤과 자연 조화.
 * 정적 스킴 분기는 minSdk(34)에선 도달 불가 — 향후 minSdk 하향 대비로 보존(#64 리뷰).
 */
@Composable
fun NexusTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        useDarkTheme -> darkColorScheme()

        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
