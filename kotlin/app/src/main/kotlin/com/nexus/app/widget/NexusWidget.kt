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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer

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
        }
    }
}

class NexusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexusWidget()
}
