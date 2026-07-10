/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.chrisbanes.haze.rememberHazeState
import me.him188.ani.app.data.models.person.CharacterDetailsInfo
import me.him188.ani.app.data.models.person.CharacterSubjectInfo
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonCastInfo
import me.him188.ani.app.data.models.person.PersonDetailsInfo
import me.him188.ani.app.data.models.person.PersonWorkInfo
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.LocalAppChromeHazeState
import me.him188.ani.app.ui.foundation.theme.appChromeFrostedGlass
import me.him188.ani.app.ui.foundation.theme.appChromeHazeSource
import me.him188.ani.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.person_details_basic_info
import me.him188.ani.app.ui.lang.person_details_casts
import me.him188.ani.app.ui.lang.person_details_character_subjects
import me.him188.ani.app.ui.lang.person_details_voice_actors
import me.him188.ani.app.ui.lang.person_details_works
import me.him188.ani.app.ui.lang.subject_details_summary
import me.him188.ani.app.ui.subject.details.components.PersonCard
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.SubjectSummarySection
import me.him188.ani.app.ui.subject.details.sections.ViewAllSheet
import org.jetbrains.compose.resources.stringResource

/**
 * 人物 (声优/制作人员) 详情页. 布局断点与条目详情一致 (Compact 单栏 / Medium 双栏 / Expanded 三栏).
 */
@Composable
fun PersonDetailsScreen(
    vm: PersonDetailsViewModel,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val details by vm.details.collectAsState()
    val casts = vm.castsPager.collectAsLazyPagingItems()
    val works = vm.worksPager.collectAsLazyPagingItems()

    PeopleDetailsScaffold(
        topBarTitle = details?.person?.displayName ?: "",
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        isPlaceholder = details == null,
        sidebarImageUrl = details?.person?.imageLarge,
        sidebarInfo = details?.infobox.orEmpty(),
        titleBlock = { isPlaceholder ->
            PeopleTitleBlock(
                displayName = details?.person?.displayName ?: "",
                originalName = details?.person?.name,
                metaLine = peopleMetaLine(personKindLabel(details?.career.orEmpty()), details?.collects ?: 0),
                isPlaceholder = isPlaceholder,
            )
        },
        summary = details?.person?.summary.orEmpty(),
        centerStrips = { PersonStrips(casts, works) },
        commentState = vm.commentState,
        compactContent = {
            PersonDetailsContentColumn(details, casts, works, vm.commentState)
        },
        modifier = modifier,
    )
}

/**
 * 角色详情页. 布局同 [PersonDetailsScreen].
 */
@Composable
fun CharacterDetailsScreen(
    vm: CharacterDetailsViewModel,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val details by vm.details.collectAsState()
    val subjects = vm.subjectsPager.collectAsLazyPagingItems()

    PeopleDetailsScaffold(
        topBarTitle = details?.character?.displayName ?: "",
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        isPlaceholder = details == null,
        sidebarImageUrl = details?.character?.imageLarge,
        sidebarInfo = details?.infobox.orEmpty(),
        titleBlock = { isPlaceholder ->
            PeopleTitleBlock(
                displayName = details?.character?.displayName ?: "",
                originalName = details?.character?.name,
                metaLine = peopleMetaLine(characterRoleLabel(details?.role ?: 1), details?.collects ?: 0),
                isPlaceholder = isPlaceholder,
            )
        },
        summary = details?.summary.orEmpty(),
        centerStrips = { CharacterStrips(details, subjects) },
        commentState = vm.commentState,
        compactContent = {
            CharacterDetailsContentColumn(details, subjects, vm.commentState)
        },
        modifier = modifier,
    )
}

/**
 * 人物/角色详情页共用的自适应骨架:
 * - Compact: 单栏 (由 [compactContent] 渲染, 含头部行);
 * - Medium: 左栏 (图片 + 基本信息) + 中栏 (标题/简介/横滑条/评论预览);
 * - Expanded: 评论卡移到右栏.
 *
 * 整页一起滚动, 与条目详情多栏布局一致.
 */
@Composable
private fun PeopleDetailsScaffold(
    topBarTitle: String,
    navigationIcon: @Composable () -> Unit,
    windowInsets: WindowInsets,
    isPlaceholder: Boolean,
    sidebarImageUrl: String?,
    sidebarInfo: List<InfoboxRowInfo>,
    titleBlock: @Composable (isPlaceholder: Boolean) -> Unit,
    summary: String,
    centerStrips: @Composable () -> Unit,
    commentState: CommentState,
    compactContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val layoutParams = SubjectDetailsLayoutParams.calculate(maxWidth)
        val scrollState = rememberScrollState()
        val density = LocalDensity.current
        // 页内标题滚出可视区后切换到粘性标题栏 (M3), 与条目详情多栏一致.
        // 单栏的名字在头部行 (110x147 图片右侧垂直居中), 多栏在中栏标题块首行.
        val titleScrollOutHeight =
            if (layoutParams.isMultiColumn) MULTI_COLUMN_TITLE_HEIGHT else COMPACT_HEADER_TITLE_HEIGHT
        // 单栏: 透明 top bar 已占据顶部空间, 内容直接贴其下方 (与条目详情单栏一致); 不再额外加顶距.
        val contentTopPadding = if (layoutParams.isMultiColumn) layoutParams.contentTopPadding else 0.dp
        val stickyTopBarVisible by remember(scrollState, density, layoutParams) {
            derivedStateOf {
                scrollState.value >
                        with(density) { (contentTopPadding + titleScrollOutHeight).toPx() }
            }
        }
        val topAppBarWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        val backgroundColor = AniThemeDefaults.pageContentBackgroundColor
        val stickyTopBarColor = AniThemeDefaults.navigationContainerColor

        // 本页自带一个独立的毛玻璃作用域: 粘性顶栏模糊其下方滚过的内容.
        CompositionLocalProvider(LocalAppChromeHazeState provides rememberHazeState()) {
        val frostedGlassActive = isAppChromeFrostedGlassActive()
        Scaffold(
            topBar = {
                Box {
                    // 透明背景的, 总是显示
                    TopAppBar(
                        title = {},
                        navigationIcon = navigationIcon,
                        colors = AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                        windowInsets = topAppBarWindowInsets,
                    )
                    // 有背景和标题的, 仅在页内标题滚出后显示
                    AniAnimatedVisibility(stickyTopBarVisible && topBarTitle.isNotBlank()) {
                        TopAppBar(
                            title = {
                                Text(topBarTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.appChromeFrostedGlass(
                                enabled = frostedGlassActive,
                                containerColor = stickyTopBarColor,
                            ),
                            navigationIcon = navigationIcon,
                            colors = if (frostedGlassActive) {
                                AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent)
                            } else {
                                AniThemeDefaults.topAppBarColors(containerColor = stickyTopBarColor)
                            },
                            windowInsets = topAppBarWindowInsets,
                        )
                    }
                }
            },
            containerColor = backgroundColor,
            contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) { padding ->
            if (!layoutParams.isMultiColumn) {
                val layoutDirection = LocalLayoutDirection.current
                Column(
                    Modifier
                        .fillMaxSize()
                        // 毛玻璃粘性顶栏的模糊来源.
                        .appChromeHazeSource(backgroundColor = backgroundColor)
                        // top padding 放在滚动内容内, 让内容可以滚动到顶栏下方 (与条目详情单栏一致).
                        .padding(
                            start = padding.calculateStartPadding(layoutDirection),
                            end = padding.calculateEndPadding(layoutDirection),
                            bottom = padding.calculateBottomPadding(),
                        )
                        .verticalScroll(scrollState)
                        .padding(top = padding.calculateTopPadding())
                        .padding(horizontal = layoutParams.contentHorizontalPadding)
                        .padding(
                            top = contentTopPadding,
                            bottom = layoutParams.contentBottomPadding,
                        ),
                ) {
                    compactContent()
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        // 毛玻璃粘性顶栏的模糊来源 (多栏内容不延伸到顶栏下方, 采样到页面背景).
                        .appChromeHazeSource(backgroundColor = backgroundColor)
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .padding(
                            start = layoutParams.contentHorizontalPadding,
                            end = layoutParams.contentHorizontalPadding,
                            top = contentTopPadding,
                            bottom = layoutParams.contentBottomPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(layoutParams.columnSpacing),
                ) {
                    // 左栏: 图片 + 基本信息
                    Column(
                        Modifier.width(layoutParams.sidebarWidth),
                        verticalArrangement = Arrangement.spacedBy(layoutParams.sidebarItemSpacing),
                    ) {
                        // 定稿: 固定宽度, 高按原图比例自适应 (加载前用 340:482 占位)
                        var coverAspect by remember(sidebarImageUrl) { mutableStateOf(340f / 482f) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(coverAspect.coerceIn(0.4f, 1.6f))
                                .clip(MaterialTheme.shapes.medium)
                                .placeholder(isPlaceholder),
                        ) {
                            AsyncImage(
                                model = sidebarImageUrl,
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    val size = state.painter.intrinsicSize
                                    if (size.width > 0f && size.height > 0f) {
                                        coverAspect = size.width / size.height
                                    }
                                },
                            )
                        }
                        if (sidebarInfo.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(Lang.person_details_basic_info),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                PeopleInfoTable(sidebarInfo)
                            }
                        }
                    }

                    // 中栏
                    Column(
                        Modifier.weight(1f).widthIn(max = 840.dp),
                        verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
                    ) {
                        titleBlock(isPlaceholder)
                        if (summary.isNotBlank()) {
                            SubjectSummarySection(summary)
                        }
                        centerStrips()
                        if (!layoutParams.showRail) {
                            PersonCommentsSection(commentState, onShowAll = { showAllComments = true })
                        }
                    }

                    // 右栏 (仅三栏): 评论卡
                    if (layoutParams.showRail) {
                        Surface(
                            Modifier.width(layoutParams.railWidth),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            PersonCommentsSection(
                                commentState,
                                onShowAll = { showAllComments = true },
                                // 对齐修正后的设计稿 rail 卡 (视觉: 标题字形距顶 ~21, 距侧 20):
                                // 标题行自带 ~17dp 顶空 (TextButton min 40dp 居中 + 行框留白, 截图实测),
                                // 故 top=4; 左右 20 / 下 18 与设计稿一致.
                                Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp),
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showAllComments) {
        PersonCommentsSheet(commentState, onDismissRequest = { showAllComments = false })
    }
}

/** 单栏头部行里名字的大致底部位置 (110x147 图片右侧垂直居中), 用于估算标题滚出的时机. */
private val COMPACT_HEADER_TITLE_HEIGHT = 96.dp

/** 多栏中栏标题块首行 (headlineMedium) 约一行的高度. */
private val MULTI_COLUMN_TITLE_HEIGHT = 40.dp

/**
 * 人物详情单栏内容列 (Compact 页与侧边预览共用). 不含滚动.
 */
@Composable
internal fun PersonDetailsContentColumn(
    details: PersonDetailsInfo?,
    casts: LazyPagingItems<PersonCastInfo>,
    works: LazyPagingItems<PersonWorkInfo>,
    commentState: CommentState,
    modifier: Modifier = Modifier,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PeopleHeaderRow(
            imageUrl = details?.person?.imageLarge,
            displayName = details?.person?.displayName ?: "",
            originalName = details?.person?.name,
            metaLine = peopleMetaLine(personKindLabel(details?.career.orEmpty()), details?.collects ?: 0),
            isPlaceholder = details == null,
        )
        details?.person?.summary?.takeIf { it.isNotBlank() }?.let { summaryText ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.subject_details_summary))
                SubjectSummarySection(summaryText)
            }
        }
        details?.infobox?.takeIf { it.isNotEmpty() }?.let { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.person_details_basic_info))
                PeopleInfoTable(rows)
            }
        }
        PersonStrips(casts, works, navigation)
        PersonCommentsSection(commentState, onShowAll = { showAllComments = true })
    }
    if (showAllComments) {
        PersonCommentsSheet(commentState, onDismissRequest = { showAllComments = false })
    }
}

/** 人物详情的两个横滑条: 出演角色 / 参与作品 (+ 各自的查看全部 sheet). */
@Composable
private fun PersonStrips(
    casts: LazyPagingItems<PersonCastInfo>,
    works: LazyPagingItems<PersonWorkInfo>,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
) {
    var showAllCasts by rememberSaveable { mutableStateOf(false) }
    var showAllWorks by rememberSaveable { mutableStateOf(false) }

    PeopleStripSection(
        stringResource(Lang.person_details_casts),
        casts,
        onViewAll = { showAllCasts = true },
    ) { cast ->
        PeoplePortraitCard(
            imageUrl = cast.character.imageMedium,
            name = cast.character.displayName,
            caption = cast.subject.displayName,
            onClick = { navigation.onClickCharacter(cast.character.id) },
        )
    }
    PeopleStripSection(
        stringResource(Lang.person_details_works),
        works,
        onViewAll = { showAllWorks = true },
    ) { work ->
        PeopleSubjectCard(
            subject = work.subject,
            caption = work.positions.firstNotNullOfOrNull { it.nameCn },
            onClick = { navigation.onClickSubject(work.subject) },
        )
    }

    if (showAllCasts) {
        ViewAllSheet(
            title = stringResource(Lang.person_details_casts),
            items = casts,
            onDismissRequest = { showAllCasts = false },
        ) { cast ->
            PersonCard(
                avatarUrl = cast.character.imageMedium,
                name = cast.character.displayName,
                relation = cast.subject.displayName,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { navigation.onClickCharacter(cast.character.id) },
            )
        }
    }
    if (showAllWorks) {
        ViewAllSheet(
            title = stringResource(Lang.person_details_works),
            items = works,
            onDismissRequest = { showAllWorks = false },
        ) { work ->
            PersonCard(
                avatarUrl = work.subject.imageLarge,
                name = work.subject.displayName,
                relation = work.positions.mapNotNull { it.nameCn }.distinct().joinToString("、"),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { navigation.onClickSubject(work.subject) },
            )
        }
    }
}

/**
 * 角色详情单栏内容列 (Compact 页与侧边预览共用). 不含滚动.
 */
@Composable
internal fun CharacterDetailsContentColumn(
    details: CharacterDetailsInfo?,
    subjects: LazyPagingItems<CharacterSubjectInfo>,
    commentState: CommentState,
    modifier: Modifier = Modifier,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PeopleHeaderRow(
            imageUrl = details?.character?.imageLarge,
            displayName = details?.character?.displayName ?: "",
            originalName = details?.character?.name,
            metaLine = peopleMetaLine(characterRoleLabel(details?.role ?: 1), details?.collects ?: 0),
            isPlaceholder = details == null,
        )
        details?.summary?.takeIf { it.isNotBlank() }?.let { summaryText ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.subject_details_summary))
                SubjectSummarySection(summaryText)
            }
        }
        details?.infobox?.takeIf { it.isNotEmpty() }?.let { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.person_details_basic_info))
                PeopleInfoTable(rows)
            }
        }
        CharacterStrips(details, subjects, navigation)
        PersonCommentsSection(commentState, onShowAll = { showAllComments = true })
    }
    if (showAllComments) {
        PersonCommentsSheet(commentState, onDismissRequest = { showAllComments = false })
    }
}

/** 角色详情的两个横滑条: 声优 / 出演作品 (+ 查看全部 sheet). */
@Composable
private fun CharacterStrips(
    details: CharacterDetailsInfo?,
    subjects: LazyPagingItems<CharacterSubjectInfo>,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
) {
    var showAllSubjects by rememberSaveable { mutableStateOf(false) }

    val actors = details?.character?.actors.orEmpty()
    if (actors.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(Lang.person_details_voice_actors))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (actor in actors) {
                    PeoplePortraitCard(
                        imageUrl = actor.imageMedium,
                        name = actor.displayName,
                        caption = null,
                        onClick = { navigation.onClickPerson(actor.id) },
                        width = 76.dp,
                        circleCrop = true,
                    )
                }
            }
        }
    }
    PeopleStripSection(
        stringResource(Lang.person_details_character_subjects),
        subjects,
        onViewAll = { showAllSubjects = true },
    ) { item ->
        PeopleSubjectCard(
            subject = item.subject,
            caption = item.role.nameCn,
            onClick = { navigation.onClickSubject(item.subject) },
        )
    }

    if (showAllSubjects) {
        ViewAllSheet(
            title = stringResource(Lang.person_details_character_subjects),
            items = subjects,
            onDismissRequest = { showAllSubjects = false },
        ) { item ->
            PersonCard(
                avatarUrl = item.subject.imageLarge,
                name = item.subject.displayName,
                relation = item.role.nameCn,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { navigation.onClickSubject(item.subject) },
            )
        }
    }
}
