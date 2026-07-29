package com.nexus.app.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.growth.labelRes
import com.nexus.app.ui.CardEmphasis
import com.nexus.app.ui.NexusCard
import com.nexus.app.ui.celebrationEnter
import com.nexus.core.Stat

/**
 * 레벨업 축하 (#219, E14-9) — 홈에서도, 그리고 **무엇이 올랐는지**까지.
 *
 * 기존 연출(#61)은 성장 탭에서만 뜨고 '레벨 N 달성' 텍스트뿐이었다. 대다수 세션이 홈에 착지하므로
 * 축하가 사용자를 만나지 못했고, "데이터가 캐릭터에 새겨진다"는 핵심 약속의 증거(어느 스탯이
 * 올랐나)가 빠져 있었다(BENCHMARK §2 '숫자만 오르는 성장 금지').
 *
 * 상승분이 비어 있어도 카드는 뜬다 — 레벨업 자체가 축하할 일이고, "무엇이 올랐는지"는 있으면
 * 더 좋은 증거일 뿐 없다고 침묵할 이유는 아니다(최초 방문·창 이탈 등).
 *
 * dismiss는 visible 토글 — 노드를 즉시 제거하면 exit 연출이 생략된다(#61 패턴).
 */
@Composable
internal fun LevelUpCard(level: Int, risenStats: Map<Stat, Int>, visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = celebrationEnter(), exit = fadeOut()) {
        NexusCard(emphasis = CardEmphasis.Highlight, title = stringResource(R.string.levelup_title, level)) {
            Text(stringResource(R.string.levelup_body), style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(com.nexus.app.ui.NexusSpacing.xs)) {
                risenStats.forEach { (stat, delta) ->
                    Text(
                        stringResource(R.string.levelup_stat_delta, stringResource(stat.labelRes()), delta),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.levelup_dismiss)) }
        }
    }
}
