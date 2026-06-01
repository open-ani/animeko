/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.person_details_open_full_page
import org.jetbrains.compose.resources.stringResource

/** 点击人物/角色时要打开的目标. */
@Immutable
sealed class PeoplePreviewTarget {
    data class Person(val personId: Int) : PeoplePreviewTarget()
    data class Character(val characterId: Int) : PeoplePreviewTarget()
}

/**
 * 非 null 时, 点击人物/角色先打开侧边预览 (中大屏, 方案C); 为 null 时直接导航到全页.
 *
 * 由 [PeoplePreviewHost] 在多栏布局下提供.
 */
val LocalPeoplePreviewHandler = staticCompositionLocalOf<((PeoplePreviewTarget) -> Unit)?> { null }

/** 统一的人物/角色点击行为: 有预览环境则开侧边预览, 否则导航到全页. */
@Composable
fun rememberPeopleClickHandler(): (PeoplePreviewTarget) -> Unit {
    val preview = LocalPeoplePreviewHandler.current
    val navigator = LocalNavigator.current
    return remember(preview, navigator) {
        { target ->
            when {
                preview != null -> preview(target)
                target is PeoplePreviewTarget.Person -> navigator.navigatePersonDetails(target.personId)
                target is PeoplePreviewTarget.Character -> navigator.navigateCharacterDetails(target.characterId)
            }
        }
    }
}

/**
 * 在 [content] 范围内启用人物/角色侧边预览: 提供 [LocalPeoplePreviewHandler],
 * 并在有目标时渲染右侧 modal side sheet. 仅应在中大屏布局使用.
 */
@Composable
fun PeoplePreviewHost(
    /**
     * 预览开合上报 (TV 播放器用于抑制控制层自动隐藏: 预览是独立窗口,
     * 按键不经过播放器根路由, 不上报会在预览开着时被 5 秒计时收掉).
     */
    onPreviewOpenChanged: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var target by remember { mutableStateOf<PeoplePreviewTarget?>(null) }
    if (target != null && onPreviewOpenChanged != null) {
        DisposableEffect(Unit) {
            onPreviewOpenChanged(true)
            onDispose { onPreviewOpenChanged(false) }
        }
    }
    CompositionLocalProvider(LocalPeoplePreviewHandler provides { target = it }) {
        content()
    }
    target?.let { current ->
        PeoplePreviewSideSheet(current, onDismissRequest = { target = null })
    }
}

/**
 * 右侧 modal side sheet, 内容复用单栏详情列; 头部提供「打开完整页面」与关闭.
 *
 * TV 上改为居中大弹窗 (侧贴形态在遥控器上像是页面的一部分, 且关闭按钮多余):
 * 保留「打开完整页面」按钮 (兼作初始焦点), 不显示关闭按钮, 返回键关闭.
 */
@Composable
private fun PeoplePreviewSideSheet(
    target: PeoplePreviewTarget,
    onDismissRequest: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val centeredPanel = LocalAniUiBehavior.current.panelsAsCenteredDialogs
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Surface(
                if (centeredPanel) {
                    Modifier.align(Alignment.Center)
                        .fillMaxHeight(TV_PEOPLE_PREVIEW_HEIGHT_FRACTION)
                        .width(TV_PEOPLE_PREVIEW_WIDTH)
                } else {
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(412.dp)
                },
                shape = if (centeredPanel) {
                    RoundedCornerShape(16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                when (target) {
                    is PeoplePreviewTarget.Person -> PersonPreviewContent(
                        target.personId,
                        onOpenFullPage = {
                            onDismissRequest()
                            navigator.navigatePersonDetails(target.personId)
                        },
                        onDismissRequest = onDismissRequest,
                    )

                    is PeoplePreviewTarget.Character -> CharacterPreviewContent(
                        target.characterId,
                        onOpenFullPage = {
                            onDismissRequest()
                            navigator.navigateCharacterDetails(target.characterId)
                        },
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonPreviewContent(
    personId: Int,
    onOpenFullPage: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val vm = viewModel<PersonDetailsViewModel>(key = "person-preview-$personId") { PersonDetailsViewModel(personId) }
    val details by vm.details.collectAsState()
    // 焦点驱动的滚动到不了不可聚焦的头图/名字: 顶部内容块 (头图+简介) 或头部
    // 按钮获得焦点时整体滚回顶部, 露出完整头图.
    // 指针设备不能这么做: 鼠标点简介展开也会给它焦点, 不应跟着跳回顶部
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = { scope.launch { scrollState.animateScrollTo(0) } }
    Column {
        PreviewSheetHeader(
            details?.person?.displayName ?: "", onOpenFullPage, onDismissRequest,
            onHeaderFocused = if (focusDriven) scrollToTop else ({}),
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(scrollState),
        ) {
            PersonDetailsContentColumn(
                details = details,
                casts = vm.castsPager.collectAsLazyPagingItems(),
                works = vm.worksPager.collectAsLazyPagingItems(),
                commentState = vm.commentState,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                // 预览内点击跳转前先关闭预览
                navigation = rememberPeopleDetailsNavigation(onBeforeNavigate = onDismissRequest),
                onTopContentFocused = if (focusDriven) scrollToTop else null,
            )
        }
    }
}

@Composable
private fun CharacterPreviewContent(
    characterId: Int,
    onOpenFullPage: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val vm = viewModel<CharacterDetailsViewModel>(key = "character-preview-$characterId") {
        CharacterDetailsViewModel(characterId)
    }
    val details by vm.details.collectAsState()
    // 焦点驱动的滚动归零, 同 PersonPreviewContent
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = { scope.launch { scrollState.animateScrollTo(0) } }
    Column {
        PreviewSheetHeader(
            details?.character?.displayName ?: "", onOpenFullPage, onDismissRequest,
            onHeaderFocused = if (focusDriven) scrollToTop else ({}),
        )
        Column(
            Modifier.fillMaxWidth().verticalScroll(scrollState),
        ) {
            CharacterDetailsContentColumn(
                details = details,
                subjects = vm.subjectsPager.collectAsLazyPagingItems(),
                commentState = vm.commentState,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                // 预览内点击跳转前先关闭预览
                navigation = rememberPeopleDetailsNavigation(onBeforeNavigate = onDismissRequest),
                onTopContentFocused = if (focusDriven) scrollToTop else null,
            )
        }
    }
}

@Composable
private fun PreviewSheetHeader(
    title: String,
    onOpenFullPage: () -> Unit,
    onDismissRequest: () -> Unit,
    /** 头部 (按钮) 获得焦点时回调: TV 用它把内容滚回顶部 (头图完整露出). */
    onHeaderFocused: () -> Unit = {},
) {
    // 焦点驱动的界面: Dialog 打开时焦点不会自动进入, 会卡在弹窗外; 初始聚焦到
    // 「打开完整页面」 (此时不显示关闭按钮 —— 返回键即关闭, 右上角 ✕ 是指针设备的产物)
    val initialFocus = remember { FocusRequester() }
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation
    if (focusDriven) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { initialFocus.requestFocus() }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .onFocusChanged { if (it.hasFocus) onHeaderFocused() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onOpenFullPage, Modifier.ifThen(focusDriven) { focusRequester(initialFocus) }) {
            Icon(
                Icons.Rounded.OpenInFull,
                contentDescription = stringResource(Lang.person_details_open_full_page),
            )
        }
        if (!focusDriven) {
            IconButton(onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = null)
            }
        }
    }
}

/** TV 人物预览弹窗的宽度与高度占屏比例. */
private val TV_PEOPLE_PREVIEW_WIDTH = 560.dp
private const val TV_PEOPLE_PREVIEW_HEIGHT_FRACTION = 0.85f
