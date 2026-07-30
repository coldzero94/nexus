package com.nexus.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nexus.app.ui.NexusSpacing

/**
 * 설정 섹션 (#264, E16-14) — 라벨 + 그 아래 카드 묶음.
 *
 * ## 왜 필요했나
 *
 * 설정이 연동·휴식·리마인더·목표·위젯·백업·삭제 **7장을 동일 강조로 평평히 쌓아** 무관한 항목이
 * 섞여 있었다. "지금 바꾸려는 게 어디 있나"를 매번 위에서부터 훑어야 했고, 항목이 하나 늘 때마다
 * 그 비용이 커진다.
 *
 * ## 라벨에 표제 시맨틱을 준다
 *
 * `A11Y-TALKBACK` 6번이 "제목 단위 이동으로 카드 사이를 건너뛸 수 있는지"를 요구한다. 섹션 라벨이
 * 표제가 아니면 TalkBack 표제 이동에서 **카드 제목만** 나와, 화면에 보이는 그룹 구조가 스크린리더
 * 사용자에게는 존재하지 않는다.
 *
 * @param title 섹션 라벨.
 * @param content 이 섹션에 속한 카드들.
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 섹션 라벨은 구조 표지 — TalkBack 표제 이동에서 그룹 경계로 잡혀야 한다
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
