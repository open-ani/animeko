/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.richtext

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.tools.HtmlColor
import me.him188.ani.app.ui.comment.BangumiCommentSticker
import me.him188.ani.utils.bbcode.BBCode
import me.him188.ani.utils.bbcode.RichElement
import me.him188.ani.utils.bbcode.RichText
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

@Composable
fun rememberBBCodeRichTextState(
    initialText: String,
    defaultTextSize: TextUnit = LocalTextStyle.current.fontSize,
): BBCodeRichTextState {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        BBCodeRichTextState(initialText, defaultTextSize, scope)
    }
}

@Stable
class BBCodeRichTextState(
    initialText: String,
    defaultTextSize: TextUnit,
    scope: CoroutineScope,
    parseContext: CoroutineContext = Dispatchers.Default
) {
    private val logger = logger<BBCodeRichTextState>()

    private val textFlow = MutableStateFlow(initialText)
    var elements: List<UIRichElement> by mutableStateOf(listOf())
        private set

    init {
        scope.launch {
            textFlow.collectLatest { code ->
                val richText = withContext(parseContext) {
                    try {
                        BBCode.parse(code)
                    } catch (ex: Exception) {
                        logger.warn(ex) { "failed to parse bbcode \"$code\"" }
                        null
                    }
                }
                if (richText != null) {
                    elements = richText.toUIRichElements(defaultTextSize.value)
                }

            }
        }
    }

    fun setText(text: String) {
        textFlow.value = text
    }
}

// TODO: move to BBCodeRichTextState
fun RichText.toUIRichElements(overrideTextSize: Float? = null): List<UIRichElement> = buildList {
    val annotated = mutableListOf<UIRichElement.Annotated>()

    elements.forEach { e ->
        when (e) {
            is RichElement.Text -> {
                if (e.value.trim().isNotEmpty()) {
                    annotated.add(
                        UIRichElement.Annotated.Text(
                            content = e.value,
                            size = overrideTextSize ?: e.size.toFloat(),
                            color = HtmlColor.parse(e.color),
                            italic = e.italic,
                            underline = e.underline,
                            strikethrough = e.strikethrough,
                            bold = e.bold,
                            mask = e.mask,
                            code = e.code,
                            url = e.jumpUrl,
                        ),
                    )
                }
            }

            is RichElement.BangumiSticker -> annotated.add(
                UIRichElement.Annotated.Sticker(
                    id = "(bgm${e.id})",
                    resource = BangumiCommentSticker[e.id],
                    url = e.jumpUrl,
                ),
            )

            is RichElement.Kanmoji -> annotated.add(
                UIRichElement.Annotated.Sticker(
                    id = e.id,
                    resource = null, // TODO: path
                    url = e.jumpUrl,
                ),
            )

            is RichElement.Quote -> {
                if (annotated.isNotEmpty()) {
                    add(UIRichElement.AnnotatedText(annotated.toList()))
                    annotated.clear()
                }
                add(UIRichElement.Quote(e.contents.toUIRichElements()))
            }

            is RichElement.Image -> {
                if (annotated.isNotEmpty()) {
                    add(UIRichElement.AnnotatedText(annotated.toList()))
                    annotated.clear()
                }
                add(UIRichElement.Image(e.imageUrl, e.jumpUrl))
            }
        }
    }

    if (annotated.isNotEmpty()) {
        add(UIRichElement.AnnotatedText(annotated.toList()))
        annotated.clear()
    }
}

fun RichText.toUIBriefText(): UIRichElement.AnnotatedText {
    val plainText = StringBuilder()
    val annotated = mutableListOf<UIRichElement.Annotated>()

    elements.forEach { e ->
        when (e) {
            is RichElement.Text -> plainText.append(e.value.replace('\n', ' '))
            is RichElement.Image -> plainText.append("[图片]")
            is RichElement.Kanmoji -> plainText.append(e.id)
            is RichElement.Quote -> plainText.append("[引用]")
            is RichElement.BangumiSticker -> {
                if (plainText.isNotEmpty()) {
                    annotated.add(UIRichElement.Annotated.Text(plainText.toString(), RichTextDefaults.FontSize))
                    plainText.clear()
                }
                annotated.add(
                    UIRichElement.Annotated.Sticker(
                        id = "(bgm${e.id})",
                        resource = BangumiCommentSticker[e.id],
                        url = e.jumpUrl,
                    ),
                )
            }
        }
    }

    if (plainText.isNotEmpty()) {
        annotated.add(UIRichElement.Annotated.Text(plainText.toString(), RichTextDefaults.FontSize))
    }

    return UIRichElement.AnnotatedText(annotated)
}