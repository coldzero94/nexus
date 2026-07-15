package com.nexus.app.growth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.core.ClassAffinity

/**
 * 레벨업·성향 변화 축하 카드 (#61, E3-15). 스케일+페이드 등장 연출, 확인으로 닫음.
 * 캐릭터 애니메이션 에셋은 이후 스프린트 — v1은 카드 연출로 완료 기준 충족.
 */
@Composable
internal fun CelebrationCard(change: GrowthChange, visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = spring(dampingRatio = 0.55f)) + fadeIn(),
        exit = fadeOut(),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                change.levelUpTo?.let { level ->
                    Text(
                        stringResource(R.string.celebrate_level_up, level),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                change.affinityChangedTo?.let { affinity ->
                    Text(
                        stringResource(R.string.celebrate_affinity_change, stringResource(affinity.labelRes())),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.celebrate_dismiss)) }
                }
            }
        }
    }
}
