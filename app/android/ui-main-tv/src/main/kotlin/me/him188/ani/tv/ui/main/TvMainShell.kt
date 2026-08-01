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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import me.him188.ani.tv.ui.exploration.TvExplorationScreen
import me.him188.ani.tv.ui.exploration.TvExplorationViewModel

/**
 * TV 主壳 (atv-architecture.md §6.4): 左侧 NavigationDrawer + 内容区.
 *
 * 条目: 搜索 -> 探索 -> 追番 -> 设置 (无缓存条目, §1.2 裁剪).
 * M0 为空壳占位; 返回语义 (非探索 tab -> 回探索, 探索 -> 退出) 已按 §6.3 接线.
 */
@Composable
fun TvMainShell(modifier: Modifier = Modifier) {
    // TV 端仅探索/追番两个 tab (CacheManagement 已裁剪, §1.2)
    val pages = listOf(MainScreenPage.Exploration, MainScreenPage.Collection)
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    // 返回语义: 非探索 tab 先回探索; 探索 tab 交给系统 (退出应用)
    BackHandler(enabled = selectedIndex != 0) {
        selectedIndex = 0
    }

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
                    onClick = { /* M2: TvSearchScreen */ },
                    leadingContent = { Icon(Icons.Rounded.Search, contentDescription = "搜索") },
                ) {
                    Text("搜索")
                }
                for ((index, page) in pages.withIndex()) {
                    NavigationDrawerItem(
                        selected = false, // 当前 tab 不做常驻高亮 (PR 结论: 聚焦高亮与选中高亮并存会误导)
                        onClick = { selectedIndex = index },
                        leadingContent = { Icon(page.getIcon(), contentDescription = page.getText()) },
                    ) {
                        Text(page.getText())
                    }
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = { /* M3: TvSettingsScreen */ },
                    leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = "设置") },
                ) {
                    Text("设置")
                }
            }
        },
    ) {
        // 内容区左缘 = 侧栏收起宽 48dp (附录 A 度量)
        Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
            when (pages[selectedIndex]) {
                MainScreenPage.Exploration -> {
                    val navigator = LocalNavigator.current
                    TvExplorationScreen(
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
                }

                MainScreenPage.Collection -> TvPagePlaceholder("追番页 · M2 实装")
                MainScreenPage.CacheManagement -> error("unreachable: TV 无缓存 (§1.2)")
            }
        }
    }
}

@Composable
private fun TvPagePlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}
