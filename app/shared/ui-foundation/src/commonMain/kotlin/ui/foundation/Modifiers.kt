/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE")

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.Modifier

inline fun Modifier.thenNotNull(
    modifier: Modifier?
): Modifier {
    return if (modifier == null) this else this.then(modifier)
}

@OverloadResolutionByLambdaReturnType
inline fun Modifier.ifThen(
    condition: Boolean,
    modifier: Modifier.Companion.() -> Modifier?
): Modifier {
    return if (condition) this.then(modifier(Modifier.Companion) ?: Modifier) else this
}

@OverloadResolutionByLambdaReturnType
inline fun <T> Modifier.ifNotNullThen(
    value: T,
    modifier: Modifier.Companion.(T & Any) -> Modifier?
): Modifier {
    return if (value != null) this.then(modifier(Modifier.Companion, value) ?: Modifier) else this
}
