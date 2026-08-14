/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable

/**
 * 一条已解析的 mpv 选项, 可直接传给 `MPVHandle.option`.
 *
 * @see parseMpvOptions
 */
@Immutable
data class MpvOption(
    val key: String,
    val value: String,
)

/**
 * 解析用户配置的 mpv 选项 ([PlayerKernelConfig.mpvOptions]), 每个元素是一行.
 *
 * 格式与 `mpv.conf` 基本一致:
 * - `key=value`, `=` 两侧的空格会被忽略. 只按第一个 `=` 分割, 因此 `script-opts=osc-scalewindowed=2` 的值是
 *   `osc-scalewindowed=2`.
 * - 值两侧的成对引号 (`"` 或 `'`) 会被去掉, 便于填写含空格的值.
 * - 允许 `--` 前缀, 便于直接粘贴命令行参数.
 * - 不带 `=` 的行视为开关选项: `key` 等价于 `key=yes`, `no-key` 等价于 `key=no`.
 * - 空行, 以及以 `#` 开头的注释行会被忽略.
 *
 * 无法识别的行 (例如键为空) 会被忽略. 选项名和值本身不做校验, 是否有效由 mpv 决定.
 */
fun parseMpvOptions(lines: List<String>): List<MpvOption> {
    return lines.mapNotNull { parseMpvOptionLine(it) }
}

/**
 * 把编辑框中的多行文本转换为 [PlayerKernelConfig.mpvOptions] 的存储形式.
 *
 * 注释和空行会原样保留, 以免用户的编辑内容在往返后丢失; 但首尾的空行会被去掉, 全空的文本得到空列表.
 */
fun splitMpvOptionLines(text: String): List<String> {
    return text.lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
}

private fun parseMpvOptionLine(line: String): MpvOption? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

    val separatorIndex = trimmed.indexOf('=')
    val rawKey = if (separatorIndex == -1) trimmed else trimmed.substring(0, separatorIndex)
    var key = rawKey.trim().removePrefix("--")
    if (key.isEmpty()) return null

    val value = if (separatorIndex == -1) {
        // 开关选项. mpv 的选项名不含 `no-` 前缀, 这里还原成 `key=no`
        if (key.length > 3 && key.startsWith("no-")) {
            key = key.removePrefix("no-")
            "no"
        } else {
            "yes"
        }
    } else {
        trimmed.substring(separatorIndex + 1).trim().removeSurroundingQuotes()
    }

    return MpvOption(key, value)
}

private fun String.removeSurroundingQuotes(): String {
    if (length < 2) return this
    val first = first()
    if ((first == '"' || first == '\'') && last() == first) {
        return substring(1, length - 1)
    }
    return this
}
