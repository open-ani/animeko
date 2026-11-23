/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

/**
 * 评论中的一张图片, 即文本中的一个 `[img]地址[/img]` 或 `[img=宽,高]地址[/img]` 标签.
 *
 * 评论文本 (`TextFieldState`) 中保存的始终是 BBCode. 编辑框把每个图片标签显示为一个不可分割的 "图片 N" 标签
 * (见 [CommentImageChipOutputTransformation]), 编辑时作为一个整体 (见 [CommentImageChipInputTransformation]).
 *
 * @property number 序号, 从 1 开始, 按在文本中出现的顺序编号. 同一个地址出现多次时每次都是一张图片.
 * @property range 标签在文本中的范围.
 * @property source 标签的原文.
 * @property url 标签中的图片地址. 待上传的图片是 [pendingImageTag] 中的占位地址.
 */
@Immutable
internal data class CommentImageChip(
    val number: Int,
    val range: TextRange,
    val source: String,
    val url: String,
) {
    /**
     * 待上传图片的本地标识, 见 [pendingImageTag]. 不是待上传的图片时为 `null`.
     */
    val uploadId: String?
        get() = if (url.startsWith(PENDING_IMAGE_URL_PREFIX)) url.substring(PENDING_IMAGE_URL_PREFIX.length) else null

    /**
     * [offset] 是否在标签内部 (不含两端). 光标和选区的端点不能在标签内部.
     */
    fun contains(offset: Int): Boolean = offset > range.start && offset < range.end
}

/**
 * 待上传图片的占位标签 `[img]ani-upload:<uploadId>[/img]`. 选择图片后立即在光标处插入占位标签, 上传成功后替换为图片地址.
 * 占位标签只存在于编辑框中, 不会发送给服务器 (见 [CommentEditorState.send]).
 *
 * @param uploadId 本地标识, 只能包含字母、数字、`-` 和 `_`.
 */
internal fun pendingImageTag(uploadId: String): String = "[img]$PENDING_IMAGE_URL_PREFIX$uploadId[/img]"

internal const val PENDING_IMAGE_URL_PREFIX = "ani-upload:"

/**
 * 找出 [text] 中的所有图片标签, 按出现顺序编号.
 *
 * 与 BBCode 解析器 (BBCode.g4, `[img=宽,高]` 由 BBCode.kt 处理) 一致, 标签名全小写或全大写, 开标签与闭标签的大小写可以不同.
 * 只把地址非空、不含方括号和空白的标签当作图片. 解析器还接受其他写法 (如 `[img] a b [/img]`), 但它们不是能显示的图片,
 * 按普通文字显示; 这也避免了两个不相关的标签之间的一段文字被当作一张图片隐藏起来.
 */
internal fun parseImageChips(text: CharSequence): List<CommentImageChip> {
    if (!text.contains("[img", ignoreCase = true)) return emptyList()
    return ImageTagPattern.findAll(text).mapIndexed { index, match ->
        CommentImageChip(
            number = index + 1,
            range = TextRange(match.range.first, match.range.last + 1),
            source = match.value,
            url = match.groupValues[1],
        )
    }.toList()
}

private val ImageTagPattern = Regex("""\[(?:img|IMG)(?:=\d+,\d+)?]([^\[\]\s]+)\[/(?:img|IMG)]""")

/**
 * 删除第 [number] 张图片. 选区随文字移动, 被删除的标签内的端点移到删除处.
 */
internal fun CommentText.removeImageChip(number: Int): CommentText {
    val chip = parseImageChips(text).firstOrNull { it.number == number } ?: return this
    return replaceRanges(listOf(chip.range), "")
}

/**
 * 选中第 [number] 张图片的整个标签.
 */
internal fun CommentText.selectImageChip(number: Int): CommentText {
    val chip = parseImageChips(text).firstOrNull { it.number == number } ?: return this
    return copy(selection = chip.range)
}

/**
 * 把待上传图片 [uploadId] 的所有占位标签替换为 [url] 的图片. 文本中已经没有这个占位标签时 (例如用户删除了它) 返回 `null`.
 *
 * 选区恰好是一个占位标签时, 替换后仍选中替换后的标签.
 */
internal fun CommentText.replacePendingImage(uploadId: String, url: String): CommentText? {
    val ranges = parseImageChips(text).filter { it.uploadId == uploadId }.map { it.range }
    if (ranges.isEmpty()) return null
    return replaceRanges(ranges, imageTag(url))
}

/**
 * 删除待上传图片 [uploadId] 的所有占位标签. 文本中没有这个占位标签时返回 `null`.
 */
internal fun CommentText.removePendingImage(uploadId: String): CommentText? {
    val ranges = parseImageChips(text).filter { it.uploadId == uploadId }.map { it.range }
    if (ranges.isEmpty()) return null
    return replaceRanges(ranges, "")
}

/**
 * 把 [text] 中已上传成功的待上传图片的占位标签替换为图片. [urlOf] 返回上传后的地址, 还没有上传成功时返回 `null`, 这样的占位标签保持不变.
 *
 * 上传成功时占位标签已被替换 (见 [replacePendingImage]), 但撤销、粘贴等操作可能让它重新出现. 发送、预览和复制前用它处理文本.
 */
internal fun resolvePendingImages(text: String, urlOf: (uploadId: String) -> String?): String =
    replacePendingImages(text) { chip -> urlOf(checkNotNull(chip.uploadId))?.let { imageTag(it) } }

/**
 * 去掉 [text] 中所有待上传图片的占位标签.
 */
internal fun removePendingImages(text: String): String = replacePendingImages(text) { "" }

/**
 * 把 [text] 中每个待上传图片的占位标签替换为 [replacement] 的结果, 结果为 `null` 时保留原文.
 */
private inline fun replacePendingImages(text: String, replacement: (CommentImageChip) -> String?): String {
    if (!text.contains(PENDING_IMAGE_URL_PREFIX)) return text
    val result = StringBuilder()
    var cursor = 0
    for (chip in parseImageChips(text)) {
        if (chip.uploadId == null) continue
        val replaced = replacement(chip) ?: continue
        result.append(text, cursor, chip.range.start).append(replaced)
        cursor = chip.range.end
    }
    if (cursor == 0) return text
    return result.append(text, cursor, text.length).toString()
}

private fun imageTag(url: String): String = IMAGE_OPEN + encodeUrlForBBCodeContent(url) + IMAGE_CLOSE

private const val IMAGE_OPEN = "[img]"
private const val IMAGE_CLOSE = "[/img]"

/**
 * 把 [ranges] (互不重叠, 按位置排序) 中的文字都替换为 [replacement].
 *
 * 选区端点在某个范围之前时不变, 在之后时随文字移动, 等于范围起点时不变, 在范围内或等于范围终点时移到替换的文字之后.
 */
private fun CommentText.replaceRanges(ranges: List<TextRange>, replacement: String): CommentText {
    fun map(offset: Int): Int {
        var delta = 0
        for (range in ranges) {
            if (offset <= range.start) return offset + delta
            if (offset <= range.end) return range.start + delta + replacement.length
            delta += replacement.length - range.length
        }
        return offset + delta
    }

    val result = StringBuilder()
    var cursor = 0
    for (range in ranges) {
        result.append(text, cursor, range.start).append(replacement)
        cursor = range.end
    }
    result.append(text, cursor, text.length)
    return CommentText(result.toString(), TextRange(map(selection.start), map(selection.end)))
}
