/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * 无后端的确定性截图: 渲染统一评论组件的各个变体, 导出 PNG 供与 Figma 设计稿
 * "💬 评论系统" (node 1911:269) 对照.
 *
 * 卡片宽度 372 + 水平 padding 20 与 Figma 定稿示例画板一致 (内容宽 332).
 * 输出目录默认为模块 build/screenshots.
 */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class CommentItemScreenshotTest {
    private val outDir: File =
        File(System.getProperty("ani.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private val figmaPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

    private fun capture(
        name: String,
        width: Int = 372,
        height: Int = 300,
        content: @Composable () -> Unit,
    ) {
        val previousLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE)
        try {
            runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview(darkMode = DarkMode.LIGHT) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f),
                        LocalTimeFormatter provides TimeFormatter(
                            getTimeNow = { CommentItemTestFixtures.fixedNow },
                        ),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            content()
                        }
                    }
                }
            }
            waitForIdle()
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Failed to encode screenshot $name")
            File(outDir, "$name.png").writeBytes(png)
            }
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

    /** Figma "单条评论 · 完整插槽(剧集 · Ani 源)" */
    @Test
    fun aniFullSlots() = capture("comment-ani-full", height = 260) {
        CommentItem(
            comment = CommentItemTestFixtures.aniComment(
                reactions = CommentItemTestFixtures.defaultReactions,
                withReply = true,
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    /** Figma "回应行 · 我贴了贴纸(点击 toggle)" + 点赞态 */
    @Test
    fun aniUpvotedToggledSticker() = capture("comment-ani-upvoted", height = 200) {
        CommentItem(
            comment = CommentItemTestFixtures.aniComment(
                reactions = CommentItemTestFixtures.toggledReactions,
                selfVote = UICommentVote.LIKE,
                content = "一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。",
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    /** Figma "列表模式 · 贴纸最多一行,溢出渐隐" */
    @Test
    fun clippedReactions() = capture("comment-clipped", height = 200) {
        CommentItem(
            comment = CommentItemTestFixtures.aniComment(
                reactions = CommentItemTestFixtures.overflowingReactions,
                likeCount = 45,
                content = "片尾曲歌词翻译很用心，staff 表滚动那里还有彩蛋。",
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    /** Figma "无贴纸 · 有回复(showReactions=false + showReply=true)" */
    @Test
    fun noReactionsWithReply() = capture("comment-no-reactions-reply", height = 220) {
        CommentItem(
            comment = CommentItemTestFixtures.aniComment(
                reactions = emptyList(),
                withReply = true,
                likeCount = 3,
                content = "下周武道馆场次的曲目单有人扒出来了吗？",
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    /** Figma "三预设 · 条目评价:评分右上(示例 bgm 只读源)" */
    @Test
    fun presetSubjectReviews() = capture("comment-preset-subject", height = 280) {
        Column {
            CommentItem(
                comment = CommentItemTestFixtures.bangumiComment(
                    nickname = "绿夜",
                    content = "趣味和感动兼存的优秀作品",
                    rating = 9,
                    hoursAgo = 2,
                ),
                onClickUrl = {},
                onClickImage = {},
                showRating = true,
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            CommentItem(
                comment = CommentItemTestFixtures.bangumiComment(
                    nickname = "澄谷染",
                    content = "不多说了，剧情不拖沓，角色人设塑造到位。不过感觉社恐塑造得太过火了，扣一分。",
                    rating = 8,
                    hoursAgo = 22,
                ),
                onClickUrl = {},
                onClickImage = {},
                showRating = true,
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
        }
    }

    /** Figma "三预设 · 剧集评论:讨论形态(第二条为 bgm 只读)" */
    @Test
    fun presetEpisodeComments() = capture("comment-preset-episode", height = 340) {
        Column {
            CommentItem(
                comment = CommentItemTestFixtures.aniComment(
                    reactions = listOf(UICommentReaction("bgm11", count = 3, selected = false)),
                    content = "一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。",
                ),
                onClickUrl = {},
                onClickImage = {},
                onClickReply = {},
                onToggleVote = { _, _ -> },
                onToggleReaction = { _, _ -> },
                menu = CommentMenuHandlers(onReport = {}),
                contentPadding = figmaPadding,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            CommentItem(
                comment = CommentItemTestFixtures.bangumiComment(),
                onClickUrl = {},
                onClickImage = {},
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
        }
    }

    /** Figma "三预设 · 人物评论:只读留言(长按可举报)" */
    @Test
    fun presetPersonComments() = capture("comment-preset-person", height = 280) {
        Column {
            CommentItem(
                comment = CommentItemTestFixtures.personComment(
                    nickname = "卡拉彼丘",
                    content = "一里的呜咽和碎碎念都配得太有灵魂了，吉他手辛苦了。",
                    daysAgo = 3,
                ),
                onClickUrl = {},
                onClickImage = {},
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            CommentItem(
                comment = CommentItemTestFixtures.personComment(
                    nickname = "薄荷糖",
                    content = "从 WUG 一路走过来，终于等到属于她的代表作，live 也超强。",
                    daysAgo = 5,
                ),
                onClickUrl = {},
                onClickImage = {},
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
        }
    }

    /** Figma "举报 · bottom sheet(桌面为对话框)" 的内容部分 */
    @Test
    fun reportSheetContent() = capture("comment-report-sheet", width = 360, height = 520) {
        CommentReportSheetContent(
            snapshotText = "凉山下：一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。运镜和作画都在燃烧经费。",
            onSubmit = { _, _ -> },
            onCancel = {},
            modifier = Modifier.padding(24.dp),
        )
    }
}
