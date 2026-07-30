package com.nexus.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 켜기/끄기 설정 카드 (#260, E16-10) — 제목 + 설명 + 우측 스위치의 단일 규격.
 *
 * 설정 탭에 이 형태가 두 곳(휴식 모드·리마인더)에 손으로 조립돼 있었다. 같은 모양을 두 번 쓰는
 * 건 문제가 아니지만, **스위치의 위치·설명 타이포·카드 패딩을 각자 정하고 있던 것**이 문제다 —
 * 한쪽만 손대면 두 행의 리듬이 갈린다.
 *
 * `onCheckedChange`를 그대로 넘기는 이유는 리마인더가 켤 때 알림 권한 런처를 타야 하기 때문이다.
 * 그 분기까지 여기로 들이면 이 컴포넌트가 권한을 알게 되고, 다음 스위치는 또 다른 이유로 예외가
 * 된다. **모양만 통일하고 동작은 호출부에 남긴다.**
 */
@Composable
fun NexusSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    NexusCard(
        modifier = modifier,
        title = title,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    ) {
        Text(description, style = MaterialTheme.typography.bodySmall)
    }
}
