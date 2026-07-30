package com.nexus.app.diag

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexus.app.health.HealthConnectManager

/**
 * 릴리스 빌드의 개발자 도구 — **아무것도 그리지 않는다** (#245).
 *
 * 디버그 짝(`src/debug`)과 같은 시그니처를 릴리스 소스셋에도 두는 이유는 `SettingsScreen`이
 * 조건 없이 호출할 수 있게 하려는 것이다. 그래야 릴리스에는 도구 코드도 문자열도 **컴파일되지
 * 않는다** — `BuildConfig.DEBUG` 분기로 숨기면 원장 삭제 코드와 버튼 문자열이 릴리스 APK에
 * 그대로 실린다.
 *
 * 이 함수가 지워지면 릴리스 빌드가 깨지므로, 게이트가 조용히 사라지지 않는다.
 */
// 파라미터가 안 쓰이는 게 이 함수의 요점이다 — 디버그 짝과 시그니처만 맞추고 아무것도 하지 않는다.
@Suppress("UnusedParameter")
@Composable
internal fun DeveloperToolsCard(manager: HealthConnectManager, modifier: Modifier = Modifier) = Unit
