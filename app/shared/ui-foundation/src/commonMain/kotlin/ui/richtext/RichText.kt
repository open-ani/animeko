/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.richtext

import androidx.annotation.UiThread
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ClickableText
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.widgets.Toaster
import org.jetbrains.compose.resources.painterResource

@Composable
fun RichText(
    elements: List<UIRichElement>,
    modifier: Modifier = Modifier,
    onClickUrl: (String) -> Unit = { },
    onClickImage: (String) -> Unit = { }
) {
    if (elements.isEmpty()) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        elements.toLayout(onClickUrl, onClickImage)
    }
}

@Composable
fun List<UIRichElement>.toLayout(
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit
) = forEach { e ->
    when (e) {
        is UIRichElement.AnnotatedText -> {
            val maskState = rememberAnnotatedMaskState(e.slice)
            RichTextDefaults.AnnotatedText(
                slice = e.slice,
                maskState = maskState,
                modifier = Modifier,
                maxLine = e.maxLine,
                onClick = { it.url?.let(onClickUrl) },
            )
        }

        is UIRichElement.Image -> RichTextDefaults.Image(
            element = e,
            modifier = Modifier,
            onClick = { onClickImage(e.imageUrl) },
        )

        is UIRichElement.Quote -> RichTextDefaults.Quote(
            elements = e.content,
            modifier = Modifier,
            onClickUrl = onClickUrl,
        )
    }
}

@Composable
private fun rememberAnnotatedMaskState(
    slice: List<UIRichElement.Annotated>
): RichTextDefaults.AnnotatedMaskState {
    return remember(slice) {
        RichTextDefaults.AnnotatedMaskState(slice)
    }
}

@Stable
object RichTextDefaults {
    val StickerSize: Int = 20
    val FontSize: Float = 16f

    @UiThread
    fun checkSanityAndOpen(url: String, navigator: UriHandler, toaster: Toaster) {
        try {
            if (url.startsWith("https://") || url.startsWith("http://")) {
                navigator.openUri(url)
            } else {
                toaster.toast("此链接可能会打开其他应用，ani 将不会打开此链接：\n$url")
            }
        } catch (ex: Exception) {
            toaster.toast("无法打开此链接：\n$url")
        }
    }

    class AnnotatedMaskState(slice: List<UIRichElement.Annotated>) {
        private var maskConnection: Map<Int, Int> by mutableStateOf(mapOf())
        private var maskState: Map<Int, Boolean> by mutableStateOf(mapOf())

        init {
            var currentMaskIndex = 0

            val state = mutableMapOf<Int, Boolean>()
            val connection = mutableMapOf<Int, Int>()

            slice.forEachIndexed { index, e ->
                val maskIndex = currentMaskIndex
                if (e is UIRichElement.Annotated.Text && e.mask) {
                    if (slice.getOrNull(index - 1).let {
                            !(it is UIRichElement.Annotated.Text && it.mask)
                        }) {
                        // 上一个 text 没有遮罩，增加遮罩块索引
                        // 如果上一个 text 也有遮罩，则与上一个遮罩状态合并
                        currentMaskIndex += 1
                    }
                    state[maskIndex] = true
                    connection[index] = maskIndex
                }
            }

            maskConnection = connection
            maskState = state
        }

        /**
         * set the underlying mask state
         */
        fun setMask(sliceIndex: Int, masked: Boolean) {
            val maskIndex = maskConnection[sliceIndex] ?: return
            maskState = buildMap {
                putAll(maskState)
                set(maskIndex, masked)
            }
        }

        infix operator fun get(sliceIndex: Int): Boolean? {
            val maskIndex = maskConnection[sliceIndex] ?: return null
            return maskState[maskIndex]
        }
    }

    @Composable
    fun AnnotatedText(
        slice: List<UIRichElement.Annotated>,
        maskState: AnnotatedMaskState,
        modifier: Modifier = Modifier,
        maxLine: Int? = null,
        onClick: (UIRichElement.Annotated) -> Unit
    ) {
        val inlineStickerMap: MutableMap<String, InlineTextContent> = remember { mutableStateMapOf() }
        val stickerSizeSp = with(LocalDensity.current) { StickerSize.dp.toSp() }
        val bodyLarge = MaterialTheme.typography.bodyLarge.fontSize.value
        val colorScheme = MaterialTheme.colorScheme

        val currentOnClick by rememberUpdatedState(onClick)
        val contentColor = LocalContentColor.current

        val content = buildAnnotatedString {
            var currentLength = 0

            slice.forEachIndexed { index, e ->
                val elementLength: Int

                when (e) {
                    is UIRichElement.Annotated.Text -> {
                        elementLength = e.content.length
                        append(e.content)

                        if (e.mask) {
                            // 为这个 text 片段添加遮罩 annotation，用于处理点击遮罩的事件
                            addStringAnnotation(
                                tag = "mask",
                                annotation = index.toString(),
                                start = currentLength,
                                end = currentLength + elementLength,
                            )
                        }

                        val background by animateColorAsState(
                            when {
                                maskState[index] == true -> {
                                    colorScheme.onPrimaryContainer.copy(0.12f)
                                        .compositeOver(colorScheme.surfaceContainerHigh)
                                }

                                e.code -> colorScheme.surfaceContainer
                                else -> Color.Unspecified
                            },
                        )
                        val textColor by animateColorAsState(
                            if (maskState[index] == true) {
                                colorScheme.onPrimaryContainer.copy(0.12f)
                                    .compositeOver(colorScheme.surfaceContainerHigh)
                            } else {
                                e.color.takeOrElse { contentColor }
                            },
                        )

                        addStyle(
                            style = SpanStyle(
                                color = textColor,
                                fontSize = if (e.size != bodyLarge) e.size.sp else 15.5.sp,
                                fontWeight = if (e.bold) FontWeight.Bold else null,
                                fontStyle = if (e.italic) FontStyle.Italic else null,
                                textDecoration = if (!e.underline && !e.strikethrough) null
                                else TextDecoration.combine(
                                    buildList {
                                        if (e.underline) add(TextDecoration.Underline)
                                        if (e.strikethrough) add(TextDecoration.LineThrough)
                                    },
                                ),
                                background = background,
                                fontFamily = if (e.code) FontFamily.Monospace else null,
                            ),
                            start = currentLength,
                            end = currentLength + elementLength,
                        )
                    }

                    is UIRichElement.Annotated.Sticker -> {
                        val inlineContentId = e.id
                        val correspondingText = inlineContentId
                        elementLength = correspondingText.length

                        appendInlineContent(inlineContentId, correspondingText)
                        inlineStickerMap[inlineContentId] = InlineTextContent(
                            placeholder = Placeholder(
                                stickerSizeSp,
                                stickerSizeSp,
                                PlaceholderVerticalAlign.AboveBaseline,
                            ),
                            children = { id ->
                                val sticker = slice
                                    .asSequence()
                                    .filterIsInstance<UIRichElement.Annotated.Sticker>()
                                    .find { it.id == id }

                                if (sticker?.resource != null) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(sticker.resource),
                                        contentDescription = null,
                                        modifier = Modifier.size(StickerSize.dp).offset(y = 2.dp),
                                    )
                                }
                            },
                        )
                    }
                }

                if (e.url != null) {
                    addStringAnnotation(
                        tag = "url",
                        annotation = index.toString(),
                        start = currentLength,
                        end = currentLength + elementLength,
                    )
                    if (e is UIRichElement.Annotated.Text) {
                        addStyle(
                            style = SpanStyle(
                                color = colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                            ),
                            start = currentLength,
                            end = currentLength + elementLength,
                        )
                    }
                }
                currentLength += elementLength
            }
        }

        ClickableText(
            text = content,
            modifier = modifier,
            inlineContent = inlineStickerMap,
            style = TextStyle.Default,
            maxLines = maxLine ?: Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            onClick = { textPos ->
                val annotations = content.getStringAnnotations(textPos, textPos)

                // 先检查是不是 mask
                val maskAnno = annotations.firstOrNull { it.tag == "mask" }
                if (maskAnno != null) {
                    // 若 annotation item 不是 slice index，视作无效的 annotation，没必要继续梳理
                    val sliceIndex = maskAnno.item.toIntOrNull() ?: return@ClickableText
                    // 去掉 mask，不继续处理
                    // 例如 mask 了一个 url，第一次点击去掉 mask，第二次跳转
                    if (maskState[sliceIndex] == true) {
                        maskState.setMask(sliceIndex, false)
                        return@ClickableText
                    }
                }

                // 检查有没有 url 跳转
                val urlAnno = annotations.firstOrNull { it.tag == "url" }
                if (urlAnno != null) {
                    val sliceIndex = urlAnno.item.toIntOrNull() ?: return@ClickableText
                    slice.getOrNull(sliceIndex)?.let(currentOnClick)
                }
            },
        )
    }

    @Composable
    fun Image(
        element: UIRichElement.Image,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val context = LocalPlatformContext.current
        var state by rememberSaveable { mutableIntStateOf(0) } // 0: loading, 1: success, 2: failed

        AsyncImage(
            model = remember(element.imageUrl, context) {
                ImageRequest.Builder(context)
                    .data(element.imageUrl)
                    .crossfade(false)
                    .build()
            },
            contentDescription = null,
            modifier = modifier
                .padding(4.dp)
                .ifThen(state != 1) {
                    sizeIn(minWidth = 80.dp, minHeight = 80.dp)
                }
                .animateContentSize()
                .placeholder(state == 0)
                .clip(RoundedCornerShape(8.dp))
                .then(Modifier.clickable { onClick() }),
            contentScale = ContentScale.Fit,
            onSuccess = {
                if (state != 1) state = 1
            },
        )
    }

    @Composable
    fun Quote(
        elements: List<UIRichElement>,
        modifier: Modifier = Modifier,
        onClickUrl: (String) -> Unit,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().then(modifier),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    elements.toLayout(onClickUrl, { })
                }
            }
        }
    }
}