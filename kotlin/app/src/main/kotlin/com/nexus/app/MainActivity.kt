package com.nexus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexus.core.ActivityType
import com.nexus.core.XpEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HelloNexus()
            }
        }
    }
}

@Composable
fun HelloNexus() {
    // core 모듈 연결 증명: 30분 러닝의 기본 점수를 core가 계산한다.
    val sampleXp = XpEngine.baseScore(ActivityType.RUNNING, minutes = 30)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Hello NEXUS", style = MaterialTheme.typography.headlineLarge)
        Text(text = "30분 러닝 = $sampleXp XP (산식 v${XpEngine.FORMULA_VERSION})")
    }
}
