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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import me.him188.ani.app.data.models.person.CharacterDetailsInfo
import me.him188.ani.app.data.models.person.CharacterSubjectInfo
import me.him188.ani.app.data.models.person.InfoboxRowInfo
import me.him188.ani.app.data.models.person.PersonCastInfo
import me.him188.ani.app.data.models.person.PersonCommentInfo
import me.him188.ani.app.data.models.person.PersonDetailsInfo
import me.him188.ani.app.data.models.person.PersonWorkInfo
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
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
    val comments = vm.commentsPager.collectAsLazyPagingItems()

    PeopleDetailsScaffold(
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
        comments = comments,
        commentCount = details?.commentCount,
        compactContent = {
            PersonDetailsContentColumn(details, casts, works, comments)
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
    val comments = vm.commentsPager.collectAsLazyPagingItems()

    PeopleDetailsScaffold(
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
        comments = comments,
        commentCount = details?.commentCount,
        compactContent = {
            CharacterDetailsContentColumn(details, subjects, comments)
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
    navigationIcon: @Composable () -> Unit,
    windowInsets: WindowInsets,
    isPlaceholder: Boolean,
    sidebarImageUrl: String?,
    sidebarInfo: List<InfoboxRowInfo>,
    titleBlock: @Composable (isPlaceholder: Boolean) -> Unit,
    summary: String,
    centerStrips: @Composable () -> Unit,
    comments: LazyPagingItems<PersonCommentInfo>,
    commentCount: Int?,
    compactContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = navigationIcon,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layoutParams = SubjectDetailsLayoutParams.calculate(maxWidth)
            if (!layoutParams.isMultiColumn) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(horizontal = layoutParams.contentHorizontalPadding)
                        .padding(
                            top = layoutParams.contentTopPadding,
                            bottom = layoutParams.contentBottomPadding,
                        ),
                ) {
                    compactContent()
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(
                            start = layoutParams.contentHorizontalPadding,
                            end = layoutParams.contentHorizontalPadding,
                            top = layoutParams.contentTopPadding,
                            bottom = layoutParams.contentBottomPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(layoutParams.columnSpacing),
                ) {
                    // 左栏: 图片 + 基本信息
                    Column(
                        Modifier.width(layoutParams.sidebarWidth),
                        verticalArrangement = Arrangement.spacedBy(layoutParams.sidebarItemSpacing),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                // Figma Cover 340x482; Fit 完整显示立绘/照片
                                .aspectRatio(340f / 482f)
                                .clip(MaterialTheme.shapes.medium)
                                .placeholder(isPlaceholder),
                        ) {
                            AvatarImage(
                                sidebarImageUrl,
                                Modifier.matchParentSize(),
                                contentScale = ContentScale.Fit,
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
                            PersonCommentsSection(comments, commentCount, onShowAll = { showAllComments = true })
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
                                comments,
                                commentCount,
                                onShowAll = { showAllComments = true },
                                Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAllComments) {
        PersonCommentsSheet(comments, commentCount, onDismissRequest = { showAllComments = false })
    }
}

/**
 * 人物详情单栏内容列 (Compact 页与侧边预览共用). 不含滚动.
 */
@Composable
internal fun PersonDetailsContentColumn(
    details: PersonDetailsInfo?,
    casts: LazyPagingItems<PersonCastInfo>,
    works: LazyPagingItems<PersonWorkInfo>,
    comments: LazyPagingItems<PersonCommentInfo>,
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
        PersonCommentsSection(comments, details?.commentCount, onShowAll = { showAllComments = true })
    }
    if (showAllComments) {
        PersonCommentsSheet(comments, details?.commentCount, onDismissRequest = { showAllComments = false })
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
    comments: LazyPagingItems<PersonCommentInfo>,
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
        PersonCommentsSection(comments, details?.commentCount, onShowAll = { showAllComments = true })
    }
    if (showAllComments) {
        PersonCommentsSheet(comments, details?.commentCount, onDismissRequest = { showAllComments = false })
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
