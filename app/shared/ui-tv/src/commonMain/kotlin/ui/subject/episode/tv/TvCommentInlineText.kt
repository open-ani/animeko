/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import me.him188.ani.app.ui.richtext.StickerImage
import me.him188.ani.app.ui.richtext.StickerSizes
import me.him188.ani.app.ui.richtext.UIRichElement

/**
 * TV 评论区用的"紧凑文本": 富文本压平成一段文字, 但表情 (`(bgm38)` / `(=A=)`) 保留成行内小图.
 *
 * 面板卡片与回复弹窗都不上完整富文本 ([me.him188.ani.app.ui.richtext.RichText]): 那套带遮罩、
 * 可点链接, 会在"整块单焦点 + 上下键翻页"的导航模型里塞进一堆可点击目标. 行内表情不占焦点,
 * 所以这一层可以出图 —— 压成 `[表情]` 四个字等于把人家说的话吃掉了.
 */
@Immutable
class TvInlineText(
    val text: AnnotatedString,
    /**
     * 文本里用到的表情, 已按 [UIRichElement.Annotated.Sticker.id] 去重
     * (行内内容是按 id 索引的 map, 同 id 必然同图). 渲染时交给 [rememberTvInlineContent].
     */
    val stickers: List<UIRichElement.Annotated.Sticker>,
) {
    val isEmpty: Boolean get() = text.isEmpty()

    companion object {
        val Empty = TvInlineText(AnnotatedString(""), emptyList())
    }
}

/**
 * 行内表情的基准尺寸相对字号的倍率. 与手机端一致 (那边是 20dp 图配 15.5sp 正文).
 * 实际尺寸还要过 [StickerSizes] —— 各个表情包原图大小差得很远.
 */
private const val TV_INLINE_STICKER_SCALE = 1.3f

/** 没有配图的表情退化成这个占位文本 —— 不写点什么就成了一块看不见的空白. */
private const val TV_STICKER_FALLBACK_TEXT = "[表情]"

/** 图片在紧凑形态 (评论卡片) 与加载失败时的占位文本. */
internal const val TV_IMAGE_FALLBACK_TEXT = "[图片]"

/** 压平过程中的一段: 文字或表情, 按原顺序排. 首尾去空白要能认出"这段是文字", 所以不能直接拼字符串. */
private sealed interface TvTextPiece {
    class Text(val text: String) : TvTextPiece
    class Sticker(val sticker: UIRichElement.Annotated.Sticker) : TvTextPiece
}

/**
 * 逐段攒出一个 [TvInlineText]. 图片这类要单独成块的元素由调用方处理 (见 `toCommentBlocks`).
 */
internal class TvInlineTextBuilder {
    private val pieces = mutableListOf<TvTextPiece>()

    val isEmpty: Boolean get() = pieces.isEmpty()

    fun append(text: String) {
        if (text.isNotEmpty()) pieces += TvTextPiece.Text(text)
    }

    fun append(sticker: UIRichElement.Annotated.Sticker) {
        pieces += TvTextPiece.Sticker(sticker)
    }

    fun append(slice: List<UIRichElement.Annotated>) {
        slice.forEach { annotated ->
            when (annotated) {
                is UIRichElement.Annotated.Text -> append(annotated.content)
                is UIRichElement.Annotated.Sticker -> append(annotated)
            }
        }
    }

    fun clear() {
        pieces.clear()
    }

    /** 首尾文字去空白 (评论正文常带换行), 表情不动. */
    fun build(): TvInlineText {
        val trimmed = pieces.toMutableList()
        while (trimmed.isNotEmpty()) {
            val first = trimmed.first() as? TvTextPiece.Text ?: break
            val text = first.text.trimStart()
            if (text.isEmpty()) trimmed.removeAt(0) else {
                trimmed[0] = TvTextPiece.Text(text)
                break
            }
        }
        while (trimmed.isNotEmpty()) {
            val last = trimmed.last() as? TvTextPiece.Text ?: break
            val text = last.text.trimEnd()
            if (text.isEmpty()) trimmed.removeAt(trimmed.lastIndex) else {
                trimmed[trimmed.lastIndex] = TvTextPiece.Text(text)
                break
            }
        }
        if (trimmed.isEmpty()) return TvInlineText.Empty

        val stickers = mutableMapOf<String, UIRichElement.Annotated.Sticker>()
        val text = buildAnnotatedString {
            trimmed.forEach { piece ->
                when (piece) {
                    is TvTextPiece.Text -> append(piece.text)
                    is TvTextPiece.Sticker -> {
                        val sticker = piece.sticker
                        if (sticker.resource == null && sticker.imageUrl == null) {
                            append(TV_STICKER_FALLBACK_TEXT)
                        } else {
                            stickers[sticker.id] = sticker
                            appendInlineContent(sticker.id, sticker.id)
                        }
                    }
                }
            }
        }
        return TvInlineText(text, stickers.values.toList())
    }
}

/**
 * [TvInlineText.stickers] 对应的行内内容.
 *
 * 基准尺寸跟着 [fontSize] 走 (卡片是 bodyMedium, 弹窗正文一号大), 实际尺寸由 [StickerSizes]
 * 按拉到的原图给 —— 方图、扁的颜文字、宽的动图各按各自的比例摆.
 *
 * 不缓: 尺寸要跟着 [StickerSizes] 里新到手的原图尺寸变, 缓住就不会重排了.
 */
@Composable
internal fun tvStickerInlineContent(
    stickers: List<UIRichElement.Annotated.Sticker>,
    fontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize,
): Map<String, InlineTextContent> {
    if (stickers.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    val base = with(density) { fontSize.toDp() } * TV_INLINE_STICKER_SCALE
    return stickers.associate { sticker ->
        val size = StickerSizes.displaySize(sticker.id, base)
        sticker.id to InlineTextContent(
            placeholder = Placeholder(
                width = with(density) { size.width.toSp() },
                height = with(density) { size.height.toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
            children = {
                StickerImage(sticker, Modifier.fillMaxSize())
            },
        )
    }
}
