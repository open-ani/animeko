/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * 一段待本地化的文本. 文案规则 (行文本如何组合) 用它表达成纯函数, 在 UI 中再解析为字符串.
 */
@Immutable
sealed interface NotificationText {
    /**
     * 字符串资源, [args] 依次作为格式化参数.
     */
    @Immutable
    data class Res(
        val resource: StringResource,
        val args: List<NotificationText> = emptyList(),
    ) : NotificationText

    /**
     * 不需要本地化的文本, 例如条目名、打码的邮箱、品牌名.
     */
    @Immutable
    data class Plain(val text: String) : NotificationText

    /**
     * 用一个空格连接的几个部分, 例如 "macOS 扫码登录", "葬送的芙莉莲 第 12 话".
     */
    @Immutable
    data class Words(val parts: List<NotificationText>) : NotificationText

    /**
     * 首字母大写的 [text]. 英文中有的短语在句中小写, 单独出现时要大写开头, 例如登录方式 "email code sign-in". 对中文没有影响.
     */
    @Immutable
    data class Capitalized(val text: NotificationText) : NotificationText
}

@Composable
fun NotificationText.resolve(): String = when (this) {
    is NotificationText.Plain -> text
    is NotificationText.Res -> {
        if (args.isEmpty()) {
            stringResource(resource)
        } else {
            val resolvedArgs = args.map { it.resolve() }
            stringResource(resource, *resolvedArgs.toTypedArray())
        }
    }

    is NotificationText.Words -> {
        val resolvedParts = parts.map { it.resolve() }
        resolvedParts.joinToString(" ")
    }

    is NotificationText.Capitalized -> text.resolve().capitalizeFirst()
}

/**
 * 在 Composition 之外解析, 例如测试中计算期望的文本.
 */
suspend fun NotificationText.resolveString(): String = when (this) {
    is NotificationText.Plain -> text
    is NotificationText.Res -> {
        if (args.isEmpty()) {
            getString(resource)
        } else {
            val resolvedArgs = args.map { it.resolveString() }
            getString(resource, *resolvedArgs.toTypedArray())
        }
    }

    is NotificationText.Words -> parts.map { it.resolveString() }.joinToString(" ")
    is NotificationText.Capitalized -> text.resolveString().capitalizeFirst()
}

private fun String.capitalizeFirst(): String = replaceFirstChar { it.titlecase() }
