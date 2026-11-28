/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.SubtitleTextConversion
import me.him188.ani.app.domain.player.SubtitleTextConverter

@Composable
fun ExternalSubtitleOverlay(controller: ExternalSubtitleController, modifier: Modifier = Modifier) {
    val text by controller.text.collectAsState()
    SubtitleTextOverlay(text, modifier)
}

/** 所选文本字幕共用简繁转换、字号与控制栏避让；原始对白用于每次转换。 */
@Composable
internal fun SubtitleTextOverlay(rawText: String, modifier: Modifier = Modifier) {
    val text = rememberConvertedSubtitleText(rawText)
    if (text.isEmpty()) return
    val safeArea = LocalSubtitleSafeArea.current
    val appearance = LocalSubtitleAppearance.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        // 与 mpv 的默认文本字幕一致，以播放器高度 720 时字号 38 为基准。
        // 字幕相对画面的大小由字幕设置决定，界面字体缩放不参与视频内排版。
        val fontSize = with(density) { (maxHeight * (SubtitleAppearance.DefaultFontSize / 720f) * appearance.effectiveFontScale).toSp() }
        val region = subtitleRegion(maxWidth.value.toDouble(), maxHeight.value.toDouble(), safeArea, appearance.effectiveStyle.verticalPosition)
        Box(
            Modifier.offset(region.left.dp, region.top.dp).size(region.width.dp, region.height.dp).clipToBounds(),
            contentAlignment = BiasAlignment(0f, appearance.effectiveStyle.verticalPosition * 2 - 1),
        ) {
            SubtitleStyledText(text, fontSize, appearance, Modifier.testTag("ExternalSubtitleText"))
        }
    }
}

/** 正文、描边与背景共用相同文字布局，描边随字号缩放。 */
@Composable
internal fun SubtitleStyledText(text: String, fontSize: TextUnit, appearance: SubtitleAppearance, modifier: Modifier = Modifier) {
    val style = appearance.effectiveStyle
    val density = LocalDensity.current
    val strokeWidth = with(density) { fontSize.toPx() } * style.outlineWidth / SubtitleAppearance.DefaultFontSize
    val padding = if (style.backgroundEnabled) with(density) { (fontSize.toPx() * 0.15f).toDp() } else 0.dp
    Box(
        modifier.semantics(mergeDescendants = true) {}.background(
            if (style.backgroundEnabled) Color(style.backgroundColor).copy(alpha = style.backgroundOpacity) else Color.Transparent,
            RoundedCornerShape(3.dp),
        ).padding(horizontal = padding, vertical = padding / 2),
        contentAlignment = Alignment.Center,
    ) {
        val typography = TextStyle(fontSize = fontSize, textAlign = TextAlign.Center)
        if (strokeWidth > 0) {
            Text(text, Modifier.clearAndSetSemantics {}, color = Color(style.outlineColor), maxLines = 6, overflow = TextOverflow.Ellipsis,
                style = typography.copy(drawStyle = Stroke(strokeWidth * 2, join = StrokeJoin.Round)))
        }
        Text(text, color = Color(style.textColor), maxLines = 6, overflow = TextOverflow.Ellipsis, style = typography)
    }
}

@Composable
internal fun rememberConvertedSubtitleText(text: String): String {
    val target = LocalSubtitleTextConversion.current
    val converter = remember { SubtitleTextConverter() }
    val converted by produceState<ConvertedSubtitle?>(null, text, target) {
        value = ConvertedSubtitle(text, target, converter.convert(text, target))
    }
    if (text.isEmpty() || target == SubtitleTextConversion.ORIGINAL) return text
    return converted?.takeIf { it.source == text && it.target == target }?.text.orEmpty()
}

private data class ConvertedSubtitle(val source: String, val target: SubtitleTextConversion, val text: String)
