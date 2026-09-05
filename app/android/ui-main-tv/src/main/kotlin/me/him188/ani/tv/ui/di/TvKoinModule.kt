/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * TV 页面层 Koin 注册表: M1 起注册各页薄 ViewModel (atv-architecture.md §7).
 */
fun getTvKoinModule(): Module = module {
    // M1: TvExplorationViewModel / TvSubjectDetailsViewModel / TvEpisodeViewModel ...
}
