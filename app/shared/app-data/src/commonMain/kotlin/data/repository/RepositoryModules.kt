/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.data.persistent.PlatformDataStoreManager
import me.him188.ani.app.data.repository.danmaku.DanmakuRepository
import me.him188.ani.app.data.repository.subject.BangumiSyncCommandRepository
import me.him188.ani.app.data.repository.user.UserRepository
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module

val Scope.aniApiProvider get() = get<AniApiProvider>()

@Suppress("UnusedReceiverParameter")
fun KoinApplication.repositoryModules(dataStores: PlatformDataStoreManager) = module {
    // Note, only new repositories are added here. old ones are still in [otherModules]. 
    single<DanmakuRepository> { DanmakuRepository(get()) }
    single<UserRepository> {
        UserRepository(
            dataStores.selfInfoStore,
            get(),
            aniApiProvider.userApi,
            aniApiProvider.userAuthApi,
            aniApiProvider.userProfileApi,
            aniApiProvider.bangumiApi,
            get(),
        )
    }
    single<BangumiSyncCommandRepository> {
        BangumiSyncCommandRepository(
            aniApiProvider.bangumiApi,
        )
    }
}
