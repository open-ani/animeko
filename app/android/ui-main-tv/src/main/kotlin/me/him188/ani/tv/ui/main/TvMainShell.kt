/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.tv.ui.collection.TvCollectionScreen
import me.him188.ani.tv.ui.collection.TvCollectionViewModel
import me.him188.ani.tv.ui.exploration.TvExplorationScreen
import me.him188.ani.tv.ui.exploration.TvExplorationViewModel
import me.him188.ani.tv.ui.login.TvLoginScreen
import me.him188.ani.tv.ui.login.TvLoginViewModel
import me.him188.ani.tv.ui.search.TvSearchScreen
import me.him188.ani.tv.ui.search.TvSearchViewModel
import me.him188.ani.tv.ui.settings.TvSettingsScreen
import me.him188.ani.tv.ui.settings.TvSettingsViewModel

/**
 * TV 主壳 (atv-architecture.md §6.4): 左侧 NavigationDrawer + 内容区.
 *
 * 条目: 搜索 -> 探索 -> 追番 -> 设置 (无缓存条目, §1.2 裁剪).
 * M0 为空壳占位; 返回语义 (非探索 tab -> 回探索, 探索 -> 退出) 已按 §6.3 接线.
 */
/** 主壳内容区: 探索/追番 (MainScreenPage 子集) + 搜索/登录/设置 (TV 额外 tab). */
private enum class TvShellContent { Search, Exploration, Collection, Login, Settings }

@Composable
fun TvMainShell(modifier: Modifier = Modifier) {
    var content by rememberSaveable { mutableStateOf(TvShellContent.Exploration) }

    // 返回语义: 非探索内容先回探索; 探索交给系统 (退出应用)
    BackHandler(enabled = content != TvShellContent.Exploration) {
        content = TvShellContent.Exploration
    }

    val navigator = LocalNavigator.current

    val loginViewModel = viewModel { TvLoginViewModel() }
    val selfInfo by loginViewModel.selfInfo.collectAsState()

    NavigationDrawer(
        modifier = modifier,
        drawerContent = {
            // tv-material 的 drawerContent 不自带布局容器, 需自行排列
            Column(
                Modifier.fillMaxHeight().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                NavigationDrawerItem(
                    selected = false,
                    onClick = { content = TvShellContent.Login },
                    leadingContent = { Icon(Icons.Rounded.AccountCircle, contentDescription = "账号") },
                ) {
                    Text(selfInfo?.nickname?.takeIf { it.isNotBlank() } ?: "登录")
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = { content = TvShellContent.Search },
                    leadingContent = { Icon(Icons.Rounded.Search, contentDescription = "搜索") },
                ) {
                    Text("搜索")
                }
                NavigationDrawerItem(
                    selected = false, // 当前 tab 不做常驻高亮 (PR 结论: 聚焦高亮与选中高亮并存会误导)
                    onClick = { content = TvShellContent.Exploration },
                    leadingContent = {
                        Icon(
                            MainScreenPage.Exploration.getIcon(),
                            contentDescription = MainScreenPage.Exploration.getText(),
                        )
                    },
                ) {
                    Text(MainScreenPage.Exploration.getText())
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = { navigator.navigateSchedule() },
                    leadingContent = { Icon(Icons.Rounded.CalendarMonth, contentDescription = "时间表") },
                ) {
                    Text("时间表")
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = { content = TvShellContent.Collection },
                    leadingContent = {
                        Icon(
                            MainScreenPage.Collection.getIcon(),
                            contentDescription = MainScreenPage.Collection.getText(),
                        )
                    },
                ) {
                    Text(MainScreenPage.Collection.getText())
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = { content = TvShellContent.Settings },
                    leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = "设置") },
                ) {
                    Text("设置")
                }
            }
        },
    ) {
        // 内容区左缘 = 侧栏收起宽 48dp (附录 A 度量)
        Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
            when (content) {
                TvShellContent.Exploration -> TvExplorationScreen(
                    viewModel { TvExplorationViewModel() },
                    onClickSubject = { hero ->
                        navigator.navigateSubjectDetails(
                            hero.subjectId,
                            SubjectDetailPlaceholder(
                                id = hero.subjectId,
                                nameCN = hero.title,
                                coverUrl = hero.imageUrl,
                            ),
                        )
                    },
                )

                TvShellContent.Collection -> TvCollectionScreen(
                    viewModel { TvCollectionViewModel() },
                    onClickSubject = { info ->
                        navigator.navigateSubjectDetails(
                            info.subjectId,
                            SubjectDetailPlaceholder(
                                id = info.subjectId,
                                name = info.subjectInfo.name,
                                nameCN = info.subjectInfo.nameCn,
                                coverUrl = info.subjectInfo.imageLarge,
                            ),
                        )
                    },
                )

                TvShellContent.Search -> TvSearchScreen(
                    viewModel { TvSearchViewModel() },
                    onClickSubject = { details ->
                        navigator.navigateSubjectDetails(
                            details.subjectInfo.subjectId,
                            SubjectDetailPlaceholder(
                                id = details.subjectInfo.subjectId,
                                name = details.subjectInfo.name,
                                nameCN = details.subjectInfo.nameCn,
                                coverUrl = details.subjectInfo.imageLarge,
                            ),
                        )
                    },
                )

                TvShellContent.Login -> TvLoginScreen(
                    loginViewModel,
                    onLoggedIn = { content = TvShellContent.Exploration },
                )

                TvShellContent.Settings -> TvSettingsScreen(
                    viewModel { TvSettingsViewModel() },
                )
            }
        }
    }
}
