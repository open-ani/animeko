/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.di

import me.him188.ani.app.data.repository.user.SettingsRepository
import org.koin.core.Koin

/** TV 应用主题与共享 ViewModel 的依赖容器。 */
class TvAppDependencies(val koin: Koin) {
    val settingsRepository: SettingsRepository by lazy { koin.get() }

    companion object {
        fun fromKoin(koin: Koin): TvAppDependencies = TvAppDependencies(koin)
    }
}
