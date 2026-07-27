package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer
import com.nexus.app.settings.IdentityStore
import com.nexus.app.ui.NexusSpacing

/**
 * 복귀 환영 씬 (#30) — 3일+ 공백 후 첫 실행. 죄책감 없는 환영(무처벌 원칙):
 * 공백을 꾸짖지 않고 "기다렸어, 다시 시작하자"로. 확인하면 홈으로.
 */
@Composable
fun WelcomeBackScene(gapDays: Long, onContinue: () -> Unit) {
    val context = LocalContext.current
    val name = remember { IdentityStore(context).name }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CharacterComposer.CharacterSprite(state = "walk", modifier = Modifier.size(160.dp))
        Spacer(Modifier.height(NexusSpacing.xl))
        Text(
            text = stringResource(R.string.welcome_back_title, gapDays),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexusSpacing.md))
        Text(
            // 이름을 지었으면 그 이름으로 호명, 아니면 무명 카피 폴백 (#216)
            text = name?.let { stringResource(R.string.welcome_back_body_named, it) }
                ?: stringResource(R.string.welcome_back_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(NexusSpacing.xxl))
        Button(onClick = onContinue) {
            Text(stringResource(R.string.welcome_back_continue))
        }
    }
}
