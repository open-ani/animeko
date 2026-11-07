/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.models.episode.renderEpisodeEp
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.network.AniCommentReportService
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.person.PersonCommentRepository
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.GetCommentImageStatusUseCase
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.domain.comment.UploadCommentImageUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.CommentThreadTarget
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.comment.BangumiCommentSticker
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentReportHost
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.EditCommentSheet
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.reportSnapshotText
import me.him188.ani.app.ui.comment.toDataReason
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.ImageViewerBackHandler
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_thread_title
import me.him188.ani.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import me.him188.ani.app.ui.lang.foundation_richtext_open_failed_prefix
import me.him188.ani.app.ui.lang.notification_go_to_character
import me.him188.ani.app.ui.lang.notification_go_to_episode
import me.him188.ani.app.ui.lang.notification_go_to_person
import me.him188.ani.app.ui.richtext.RichTextDefaults
import org.jetbrains.compose.resources.stringResource
import org.koin.core.Koin

/**
 * 整页评论详情 ([NavRoutes.CommentThread]) 的状态: thread 本身, 以及它自己的举报与编辑器状态
 * (播放页与人物页的 thread 复用宿主页面的这两样).
 */
class CommentThreadViewModel(
    val route: NavRoutes.CommentThread,
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val episodeCommentRepository: EpisodeCommentRepository by koin.inject()
    private val personCommentRepository: PersonCommentRepository by koin.inject()
    private val commentReportService: AniCommentReportService by koin.inject()
    private val postCommentUseCase: PostCommentUseCase by koin.inject()
    private val uploadCommentImageUseCase: UploadCommentImageUseCase by koin.inject()
    private val getCommentImageStatusUseCase: GetCommentImageStatusUseCase by koin.inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by koin.inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by koin.inject()
    private val personDetailsRepository: PersonDetailsRepository by koin.inject()

    private val key = CommentThreadKey(route.target, route.commentId, route.focusReplyId)

    val thread: CommentThreadState = when (val target = route.target) {
        is CommentThreadTarget.Episode ->
            episodeCommentThreadState(key, target, snapshot = null, episodeCommentRepository, backgroundScope)

        is CommentThreadTarget.Person, is CommentThreadTarget.Character ->
            personCommentThreadState(
                key,
                target.toPersonCommentTarget() ?: error("unreachable"),
                snapshot = null,
                personCommentRepository,
                backgroundScope,
            )
    }

    /**
     * 顶栏副标题: 评论所在的位置 (条目与剧集, 或人物/角色的名字). 取不到就不显示, 不影响 thread 加载.
     */
    val subtitle: StateFlow<String?> = subtitleFlow()
        .catch { emit(null) }
        .stateInBackground(null)

    private fun subtitleFlow(): Flow<String?> = when (val target = route.target) {
        is CommentThreadTarget.Episode -> combine(
            subjectCollectionRepository.subjectCollectionFlow(target.subjectId),
            episodeCollectionRepository.episodeCollectionInfoFlow(target.subjectId, target.episodeId),
        ) { subject, episode -> "${subject.subjectInfo.displayName} ${episode.episodeInfo.renderEpisodeEp()}" }

        is CommentThreadTarget.Person ->
            personDetailsRepository.personDetailsFlow(target.personId).map { it.person.displayName }

        is CommentThreadTarget.Character ->
            personDetailsRepository.characterDetailsFlow(target.characterId).map { it.character.displayName }
    }

    val reportState: CommentReportState = CommentReportState(
        onSubmitReport = { comment, reason, detail ->
            val target = route.target
            commentReportService.createReport(
                targetType = when (target) {
                    is CommentThreadTarget.Episode -> CommentReportTargetType.EPISODE_COMMENT
                    is CommentThreadTarget.Person -> CommentReportTargetType.PERSON_COMMENT
                    is CommentThreadTarget.Character -> CommentReportTargetType.CHARACTER_COMMENT
                },
                targetId = comment.sourceCommentId,
                reason = reason.toDataReason(),
                commentAuthorId = comment.author?.id,
                detail = detail.takeIf { it.isNotEmpty() },
                contentSnapshot = comment.reportSnapshotText(),
                subjectId = (target as? CommentThreadTarget.Episode)?.subjectId?.toLong(),
                episodeId = (target as? CommentThreadTarget.Episode)?.let { comment.episodeId ?: it.episodeId.toLong() },
            )
        },
        backgroundScope = backgroundScope,
    )

    val editorState: CommentEditorState = CommentEditorState(
        showExpandEditCommentButton = true,
        initialEditExpanded = false,
        panelTitle = subtitle.produceState(null),
        stickers = flowOf(BangumiCommentSticker.map { EditCommentSticker(it.first, it.second) })
            .produceState(emptyList()),
        richTextRenderer = { text ->
            withContext(Dispatchers.Default) {
                with(CommentMapperContext) { parseBBCode(text) }
            }
        },
        onSend = { context, content -> postCommentUseCase(context, content) },
        onUploadImage = { uploadCommentImageUseCase(it) },
        onCheckImageStatus = { getCommentImageStatusUseCase(it) },
        backgroundScope = backgroundScope,
    )

    /**
     * 打开编辑器回复 [target] (根评论或某条回复).
     */
    fun startReply(target: UIComment) {
        val context = when (val host = route.target) {
            is CommentThreadTarget.Episode -> CommentContext.EpisodeReply(
                subjectId = host.subjectId,
                episodeId = target.episodeId ?: host.episodeId.toLong(),
                commentId = target.sourceCommentId,
            )

            is CommentThreadTarget.Person ->
                CommentContext.PersonCommentReply(PersonCommentTarget.Person(host.personId), target.sourceCommentId)

            is CommentThreadTarget.Character ->
                CommentContext.PersonCommentReply(PersonCommentTarget.Character(host.characterId), target.sourceCommentId)
        }
        editorState.startEdit(context, replyToName = target.author?.nickname)
    }

    /**
     * Bangumi 上对应的页面, 供菜单的 "在 Bangumi 打开".
     */
    val originalCommentsUrl: String = when (val target = route.target) {
        is CommentThreadTarget.Episode -> "https://bgm.tv/ep/${target.episodeId}"
        is CommentThreadTarget.Person -> "https://bgm.tv/person/${target.personId}"
        is CommentThreadTarget.Character -> "https://bgm.tv/character/${target.characterId}"
    }
}

/**
 * 整页评论详情: 顶栏 (标题 "评论详情" + 位置副标题 + 前往所在剧集/人物的按钮) + [CommentThreadPane] + 举报弹层 + 编辑器.
 * 宽屏居中单栏.
 *
 * @param onOpenTarget 前往评论所在的剧集 / 人物 / 角色页.
 */
@Composable
fun CommentThreadScreen(
    vm: CommentThreadViewModel,
    onOpenTarget: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val subtitle by vm.subtitle.collectAsStateWithLifecycle()
    // 编辑目标存在 view model 里, 进程重建后编辑器不自动恢复
    var showEditor by remember { mutableStateOf(false) }
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current
    val externalAppLinkWarningPrefix = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val openLinkFailedPrefix = stringResource(Lang.foundation_richtext_open_failed_prefix)
    val imageViewer = rememberImageViewerHandler()
    ImageViewerBackHandler(imageViewer)

    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = {
                    Column {
                        AniTopAppBarDefaults.Title(stringResource(Lang.comment_thread_title))
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = navigationIcon,
                actions = {
                    IconButton(onClick = onOpenTarget) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(
                                when (vm.route.target) {
                                    is CommentThreadTarget.Episode -> Lang.notification_go_to_episode
                                    is CommentThreadTarget.Person -> Lang.notification_go_to_person
                                    is CommentThreadTarget.Character -> Lang.notification_go_to_character
                                },
                            ),
                        )
                    }
                },
                colors = AniThemeDefaults.topAppBarColors(),
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        CommentThreadPane(
            state = vm.thread,
            onClickUrl = { url ->
                RichTextDefaults.checkSanityAndOpen(
                    url,
                    browserNavigator,
                    toaster,
                    externalAppLinkWarningPrefix,
                    openLinkFailedPrefix,
                )
            },
            onClickImage = { imageViewer.viewImage(it) },
            onReply = {
                vm.startReply(it)
                showEditor = true
            },
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .wrapContentWidth(align = Alignment.CenterHorizontally)
                .widthIn(max = CommentThreadDefaults.MaximumContentWidth),
            reportState = vm.reportState,
            onOpenOriginal = { browserNavigator.openUri(vm.originalCommentsUrl) },
        )
    }

    CommentReportHost(vm.reportState)
    if (showEditor) {
        EditCommentSheet(
            state = vm.editorState,
            onDismiss = {
                showEditor = false
                vm.editorState.cancelSend()
            },
            onSendComplete = { vm.thread.onReplySent() },
        )
    }
    ImageViewer(imageViewer) { imageViewer.clear() }
}
