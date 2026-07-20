package com.nexus.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer
import com.nexus.core.ExpeditionEngine
import com.nexus.core.ExpeditionState
import com.nexus.core.XpEngine

/**
 * 홈 위젯 (#39, E6-1) — 캐릭터 + 레벨·컨디션·오늘 XP. 위젯은 [WidgetSnapshotStore]만 읽는다
 * (무거운 로드 금지 — 갱신 파이프라인은 E6-2, 4대 장치 콘텐츠는 E6-4).
 * 캐릭터는 앱과 같은 컴포저([CharacterComposer.composeFrameBitmap]) — 단일 렌더 모듈 계약(#26).
 */
class NexusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).read()
        val sprite = CharacterComposer.composeFrameBitmap(
            context = context,
            layers = listOf(snapshot.spriteState to 0),
            sizePx = SPRITE_SIZE_PX,
        )
        provideContent {
            GlanceTheme {
                WidgetContent(context, snapshot, sprite)
            }
        }
    }

    private companion object {
        const val SPRITE_SIZE_PX = 168
    }
}

/** 위젯 본문 — GlanceTheme 토큰이 라이트/다크·다이내믹 컬러를 자동 처리 (#64와 정합). */
@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot, sprite: android.graphics.Bitmap) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(sprite),
            contentDescription = context.getString(R.string.character_content_desc),
            modifier = GlanceModifier.size(56.dp),
        )
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(
                context.getString(R.string.widget_level_format, snapshot.level),
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
            )
            Text(
                context.getString(R.string.widget_condition_format, snapshot.condition),
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
            Text(
                context.getString(R.string.widget_today_format, snapshot.todayXp),
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
            // 장치 ③ 일일 진행바 — 오늘 성장(니 200pt 기준) (#72)
            LinearProgressIndicator(
                progress = (snapshot.todayXp / XpEngine.DAILY_KNEE).toFloat().coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
            // 장치 ②·④ — 원정 상태와 아침/저녁 이벤트 중 우선순위 높은 한 줄 (#72)
            statusLine(context, snapshot)?.let { line ->
                Text(line, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        }
    }
}

/**
 * 상태 한 줄 (#72) — 우선순위: 원정 개봉 대기 > 원정 진행 > 저녁 일지 > 아침 카드 > 없음.
 * 원정 잔여는 렌더 시점에 core 산술로 — 스냅샷 지연(≤15분)과 무관하게 정확.
 */
private fun statusLine(context: Context, snapshot: WidgetSnapshot): String? {
    when (
        val state = ExpeditionEngine.stateAt(
            snapshot.expeditionStartedAt.takeIf { it != 0L },
            System.currentTimeMillis(),
        )
    ) {
        ExpeditionState.ReadyToOpen -> return context.getString(R.string.widget_expedition_ready)

        is ExpeditionState.InProgress -> {
            val hours = state.remainingMillis / MILLIS_PER_HOUR
            return context.getString(R.string.widget_expedition_progress, hours)
        }

        ExpeditionState.Idle -> Unit
    }
    return when {
        snapshot.journalPending -> context.getString(R.string.widget_journal_pending)
        snapshot.morningPending -> context.getString(R.string.widget_morning_pending)
        else -> null
    }
}

private const val MILLIS_PER_HOUR = 3_600_000L

class NexusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexusWidget()
}
