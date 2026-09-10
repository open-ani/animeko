/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.main

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
        "me.him188.ani.app.ui.foundation.LocalSketch",
        "me.him188.ani.app.ui.foundation.rememberAniSketchInstance",
        "me.him188.ani.app.ui.foundation.AbstractViewModel",
        "me.him188.ani.app.ui.foundation.animation.",
        // Skeleton drawing/animation is shared infrastructure, independent of phone layouts.
        "me.him188.ani.app.ui.external.placeholder.",
        "me.him188.ani.app.ui.foundation.widgets.Toaster",
        "me.him188.ani.app.ui.foundation.widgets.LocalToaster",
        "me.him188.ani.app.ui.foundation.widgets.showLoadError",
        "me.him188.ani.app.ui.foundation.navigation.BackHandler",
        "me.him188.ani.app.ui.search.renderLoadErrorToastMessage",
        // Playback shares semantic mappings and the platform-neutral loading indicator, with TV typography.
        "me.him188.ani.app.ui.search.renderLoadErrorMessage",
        "me.him188.ani.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator",
        "me.him188.ani.app.ui.subject.episode.video.loading.shouldShowVideoLoadingIndicator",
        "me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresentation",
        "me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresence",
        "me.him188.ani.app.ui.watchtogether.WatchTogetherConnectionPresentation",
        "me.him188.ani.app.ui.watchtogether.WatchTogetherPlaybackPresentation",
        "me.him188.ani.app.ui.watchtogether.toWatchTogetherMemberPresentation",
        "me.him188.ani.app.ui.watchtogether.toWatchTogetherPlaybackPresentation",
        "me.him188.ani.app.ui.watchtogether.stateIconAndText",
        "me.him188.ani.app.ui.watchtogether.watchTogetherStatusText",
        // Shared resources and locale-dependent text mappings contain no phone layouts.
        "me.him188.ani.app.ui.lang.",
        "me.him188.ani.app.ui.rating.EditableRatingState",
        "me.him188.ani.app.ui.rating.RateRequest",
        // Platform-neutral star glyphs and score descriptions, without the phone rating editor.
        "me.him188.ani.app.ui.rating.FiveRatingStars",
        "me.him188.ani.app.ui.rating.rememberRatingScoreLabels",
        "me.him188.ani.app.ui.rating.renderScoreClass",
        "me.him188.ani.app.ui.subject.AiringLabelState",
        "me.him188.ani.app.ui.subject.SubjectProgressState",
        "me.him188.ani.app.ui.subject.rememberSubjectStatusStrings",
        "me.him188.ani.app.ui.media.renderSubtitleLanguage",
        "me.him188.ani.app.ui.media.rememberMediaDetailsStrings",
        "me.him188.ani.app.ui.media.webCaptchaRequiredMessage",
        "me.him188.ani.app.ui.episode.danmaku.renderDanmakuServiceId",
        "me.him188.ani.app.ui.watchtogether.watchTogetherJoinFailureMessage",
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
        "me.him188.ani.app.ui.exploration.ExplorationPageViewModel",
        "me.him188.ani.app.ui.main.SearchViewModel",
        "me.him188.ani.app.ui.exploration.ExplorationPageState",
        "me.him188.ani.app.ui.exploration.search.",
        // 登录态 UI 状态 (SelfInfoUiState/SelfInfoStateProducer)
        "me.him188.ani.app.ui.user.",
    )

    /** TV 代码 = shared 中各模块的 src/androidTv 目录 + Android 应用的 src/tv 出包胶水. */
    private fun tvScope() = run {
        // Konsist 的根目录探测会被 app/gradlew 误导, 自行向上定位仓库根 (settings.gradle.kts 所在),
        // 并用 scopeFromExternalFiles (接受任意绝对路径) 构建作用域
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val sharedRoot = File(repoRoot, "app/shared")
        val featureModules = sharedRoot.listFiles { file ->
            file.isDirectory && file.name.startsWith("ui-")
        }.orEmpty()
        val tvDirs = (listOf(sharedRoot) + featureModules)
            .map { File(it, "src/androidTv") } + File(repoRoot, "app/android/src/tv")
        val dirs = tvDirs.filter { it.exists() }.map { it.absolutePath }
        check(dirs.size >= 9) { "TV 源集目录数量异常: $dirs" }
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

    @Test
    fun `tv watchtogether must not depend on player ui`() {
        tvScope().files.assertFalse { file ->
            file.path.replace('\\', '/').contains("/ui-watchtogether/src/androidTv/") &&
                file.imports.any { it.name.startsWith("me.him188.ani.leanback.ui.episode.") }
        }
    }

    @Test
    fun `tv foundation must not depend on feature ui`() {
        tvScope().files.assertFalse { file ->
            file.path.replace('\\', '/').contains("/ui-foundation/src/androidTv/") &&
                file.imports.any {
                    it.name.startsWith("me.him188.ani.leanback.ui.") &&
                        !it.name.startsWith("me.him188.ani.leanback.ui.foundation.")
                }
        }
    }


    @Test
    fun `tv composables must not resolve dependencies or access repositories and services`() {
        tvScope().files.assertFalse { file ->
            file.text.contains("@Composable") && (
                file.imports.any {
                    it.name.startsWith("org.koin.") ||
                        it.name.startsWith("me.him188.ani.app.data.repository.") ||
                        it.name.endsWith("GlobalKoin") ||
                        it.name.endsWith("Repository") ||
                        it.name.endsWith("Service") ||
                        it.name.endsWith("UseCase")
                } ||
                    file.text.contains("me.him188.ani.app.data.repository.") ||
                    file.text.contains("me.him188.ani.app.domain.usecase.GlobalKoin")
                )
        }
    }

    @Test
    fun `tv feature code must not use global koin or component injection`() {
        tvScope().files.assertFalse { file ->
            file.imports.any {
                it.name.endsWith("GlobalKoin") ||
                    it.name.startsWith("org.koin.core.component.")
            }
        }
    }

    @Test
    fun `tv screens must render state and dispatch intents instead of calling viewmodels`() {
        tvScope().files.assertFalse { file ->
            file.path.endsWith("Screen.kt") && (
                file.imports.any { it.name.endsWith("ViewModel") } ||
                    file.text.contains("viewModel.") ||
                    file.text.contains("viewModel<") ||
                    file.text.contains("tvViewModel<")
                )
        }
    }

    @Test
    fun `tv viewmodels must only be constructed in app content`() {
        val declaration = Regex("""\bclass\s+Tv\w*ViewModel\s*\(""")
        val constructor = Regex("""\bTv\w*ViewModel\s*\(|::Tv\w*ViewModel\b""")
        tvScope().files.assertFalse { file ->
            !file.path.replace('\\', '/').endsWith("/TvAniAppContent.kt") &&
                constructor.containsMatchIn(file.text.replace(declaration, ""))
        }
    }

    @Test
    fun `tv viewmodel provisioning must use the app content helper without koin registration`() {
        val providerCall = Regex("""\b(?:tvViewModel|viewModel|koinViewModel)\s*(?:<[^>]+>)?\s*[({]""")
        tvScope().files.assertFalse { file ->
            val path = file.path.replace('\\', '/')
            val isAppContent = path.endsWith("/TvAniAppContent.kt")
            val isHelper = path.endsWith("/foundation/TvViewModel.kt")
            val hasKoinModule = file.imports.any { it.name == "org.koin.dsl.module" }
            (!isAppContent && !isHelper && providerCall.containsMatchIn(file.text)) ||
                (!isAppContent && file.imports.any { it.name.endsWith(".tvViewModel") }) ||
                (!isHelper && file.imports.any { it.name == "androidx.lifecycle.viewmodel.compose.viewModel" }) ||
                (hasKoinModule && file.text.contains("ViewModel"))
        }
    }

    // ============ 焦点框架规约 (atv-architecture.md §14.4; 违反 = 运行期焦点 bug) ============

    @Test
    fun `tv viewmodels must not own focus or player presentation navigation`() {
        val presentationTypes = Regex(
            """\b(?:TvFocusScope|FocusRequester|TvPlayerPresentationState|TvPlayerOverlayState|TvPlayerFocusRequest|TvPlayerAction|TvRemoteKey)\b""",
        )
        tvScope().files.assertFalse { file ->
            file.path.endsWith("ViewModel.kt") && presentationTypes.containsMatchIn(file.text)
        }
    }

    /** 页面持有 TvFocusScope 就必须装解析循环 + 用户交互放弃信号, 否则送焦请求无人消化 / 轮询抢焦点. */
    @Test
    fun `files owning a focus scope must install resolver and nav signal`() {
        tvScope().files.assertFalse { file ->
            // 框架自身 (定义处/kdoc 示例) 不受此约束
            !file.path.replace('\\', '/').contains("/ui-foundation/src/androidTv/") &&
                file.text.contains("rememberTvFocusScope()") &&
                !(
                    file.text.contains(".Resolver()") &&
                        (file.text.contains("tvFocusNavSignal") || file.text.contains("tvFocusHotkey"))
                    )
        }
    }

    /** 焦点处理禁止轮询/延时 (§14.4-8): 框架目录内不得出现 delay/帧等待, 一律事件驱动. */
    @Test
    fun `focus framework must be event driven with no delays or frame polling`() {
        tvScope().files.assertFalse { file ->
            file.path.replace('\\', '/').contains("/ui-foundation/src/androidTv/") &&
                file.path.replace('\\', '/').contains("/focus/") &&
                (file.text.contains("delay(") || file.text.contains("withFrameNanos"))
        }
    }

    /**
     * requesterOf 互操作只允许框架内部使用: 裸 requester 送焦没有锚点附着/焦点上报,
     * 事件驱动解析无法感知目标 (曾以轮询形态酿成抢焦事故, `7635c6ea1`).
     * 页面/组件需要程序化送焦一律走 tvFocusAnchor + request(key).
     */
    @Test
    fun `pages must not use raw requesterOf interop`() {
        tvScope().files.assertFalse { file ->
            !file.path.replace('\\', '/').contains("/ui-foundation/src/androidTv/") &&
                file.text.contains("requesterOf(")
        }
    }
}
