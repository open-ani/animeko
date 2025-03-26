/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.paging.PagingLogger
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.FixedValue.value
import net.bytebuddy.matcher.ElementMatchers

object PagingLoggingHack {
    fun install() {
        ByteBuddyAgent.install()
        ByteBuddy()
            .redefine(PagingLogger::class.java)
            .method(ElementMatchers.named("isLoggable"))
            .intercept(value(false))
            .make()
            .load(
                PagingLogger::class.java.getClassLoader(),
                ClassReloadingStrategy.fromInstalledAgent(),
            )
    }
}
