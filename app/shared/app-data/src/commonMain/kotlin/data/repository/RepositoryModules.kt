/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import me.him188.ani.app.data.repository.danmaku.DanmakuRepository
import org.koin.core.KoinApplication
import org.koin.dsl.module


@Suppress("UnusedReceiverParameter")
fun KoinApplication.repositoryModules() = module {
    // Note, only new repositories are added here. old ones are still in [otherModules]. 
    single<DanmakuRepository> { DanmakuRepository(get()) }
}