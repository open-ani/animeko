/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.main

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import java.io.File
import kotlin.test.Test

/**
 * TV 约定边界守护 (atv-architecture.md §4.2/§11.1).
 *
 * D1 放弃编译期隔离后, 手机 UI 树对 tv variant 完整可见 —— 本测试是「TV 不调用手机 UI」
 * 约定的主要机械守护: 禁止 TV 代码 import 手机 UI 树 (白名单基建除外).
 *
 * v4 起不再禁 material3: 上游 PR#3217 的 TV 方案就是 material3 + 自研焦点系统
 * (完全不用 tv-material), 我们的新基建 (TvImmersiveCards/TvNavigationSideRail) 与之对齐.
 */
class TvArchitectureTest {

    /** me.him188.ani.app.ui.* 中 TV 允许 import 的基建白名单 (§4.2). */
    private val uiFoundationInfraAllowList = listOf(
        "me.him188.ani.app.ui.foundation.AsyncImage",
        "me.him188.ani.app.ui.foundation.LocalImageLoader",
        "me.him188.ani.app.ui.foundation.createDefaultImageLoader",
        "me.him188.ani.app.ui.foundation.AbstractViewModel",
        "me.him188.ani.app.ui.foundation.animation.",
        "me.him188.ani.app.ui.foundation.widgets.Toaster",
        "me.him188.ani.app.ui.foundation.widgets.LocalToaster",
        "me.him188.ani.app.ui.foundation.navigation.BackHandler",
        "me.him188.ani.app.ui.search.renderLoadErrorToastMessage",
        // v4 (对齐上游 PR#3217) 新增: 侧边栏头像
        "me.him188.ani.app.ui.foundation.avatar.",
        // 时间表状态层复用 (D3: ScheduleViewModel/presentation 数据类; UI composable 仍禁用)
        "me.him188.ani.app.ui.exploration.schedule.",
        // 追番状态层复用 (D3: UserCollectionsViewModel/UserCollectionsState; UI composable 仍禁用)
        "me.him188.ani.app.ui.subject.collection.",
        // 详情页状态层复用 (D3: SubjectDetailsViewModel/SubjectDetailsState; UI composable 仍禁用)
        "me.him188.ani.app.ui.subject.details.",
        // 选集列表数据类 (EpisodeListUiState/EpisodeListItem, 详情页选集卡数据)
        "me.him188.ani.app.ui.subject.episode.list.",
        // 评论状态类 (UIComment/UIRichText) 与富文本元素 (纯数据, 渲染 TV 自绘)
        "me.him188.ani.app.ui.comment.",
        "me.him188.ani.app.ui.richtext.",
        // 登录状态层复用 (D3: EmailLoginViewModel/EmailLoginUiState)
        "me.him188.ani.app.ui.login.",
        // 探索/搜索状态层复用 (D3: 精确到类, ui.main 包下还有手机壳 UI)
        "me.him188.ani.app.ui.main.ExplorationPageViewModel",
        "me.him188.ani.app.ui.main.SearchViewModel",
        "me.him188.ani.app.ui.exploration.ExplorationPageState",
        "me.him188.ani.app.ui.exploration.search.",
        // 登录态 UI 状态 (SelfInfoUiState/SelfInfoStateProducer)
        "me.him188.ani.app.ui.user.",
    )

    /** TV 代码 = app/android 下全部 ui-*-tv 模块 + src/tv 出包胶水 (§4.1). */
    private fun tvScope() = run {
        // Konsist 的根目录探测会被 app/gradlew 误导, 自行向上定位仓库根 (settings.gradle.kts 所在),
        // 并用 scopeFromExternalFiles (接受任意绝对路径) 构建作用域
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val tvDirs = File(repoRoot, "app/android").listFiles { f: File ->
            f.isDirectory && f.name.startsWith("ui-") && f.name.endsWith("-tv")
        }.orEmpty().map { File(it, "src/main") } + File(repoRoot, "app/android/src/tv")
        val dirs = tvDirs.filter { it.exists() }.map { it.absolutePath }
        check(dirs.size >= 11) { "TV 源集目录数量异常: $dirs" }
        Konsist.scopeFromExternalDirectories(dirs.toSet())
    }

    @Test
    fun `tv code must not import phone ui tree except whitelisted infra`() {
        tvScope().files.assertFalse { file ->
            file.imports.any { import ->
                import.name.startsWith("me.him188.ani.app.ui.") &&
                    uiFoundationInfraAllowList.none { allowed -> import.name.startsWith(allowed) }
            }
        }
    }

    @Test
    fun `tv code must not reference torrent or media cache implementations`() {
        // 运行期由 DI 门控为空实现 (D4), 直接引用会绕过门控 (§4.2)
        tvScope().files.assertFalse { file ->
            file.imports.any {
                it.name.startsWith("me.him188.ani.app.domain.torrent.") ||
                    it.name.startsWith("me.him188.ani.app.domain.media.cache.engine.") ||
                    it.name.startsWith("me.him188.ani.app.domain.media.cache.storage.")
            }
        }
    }
}
