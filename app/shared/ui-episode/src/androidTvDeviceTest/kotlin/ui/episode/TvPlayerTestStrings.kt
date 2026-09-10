/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import android.os.LocaleList
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale

internal fun playerTestString(resource: StringResource, vararg args: Any): String = runBlocking {
    getString(resource, *args)
}

/** Changes only this test process, restoring the locale even when an assertion fails. */
internal fun <T> withPlayerTestLocale(tag: String, block: () -> T): T {
    val previous = LocaleList.getDefault()
    return try {
        LocaleList.setDefault(LocaleList(Locale.forLanguageTag(tag)))
        block()
    } finally {
        LocaleList.setDefault(previous)
    }
}
