/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.domain.bangumi.BangumiConflictChecker
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification_action
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

/**
 * 主界面的 Bangumi 收藏冲突提示: 会话有效且已绑定 Bangumi 时触发 [BangumiConflictChecker] 的后台检查 (由它节流),
 * 发现冲突则弹出带「处理」动作的 snackbar, 点击跳转合并收藏界面.
 *
 * 点击「处理」只关闭本次提示 (关闭状态以冲突数为 key, 同一计数不再重复提示), 不清空 checker 的计数:
 * 用户不处理直接返回时设置入口仍显示数量; 冲突是否仍存在由合并完成后的强制检查重新判定.
 *
 * 使用自己的 [SnackbarHostState] 与 [SnackbarHost], 不与 UpdateNotifier 共享.
 *
 * @param checker 全局单例, 生命周期与 APP 相同; 测试可注入.
 */
@Composable
fun BoxScope.BangumiConflictNotifier(
    selfInfo: SelfInfoUiState,
    onNavigateToMerge: () -> Unit,
    modifier: Modifier = Modifier,
    checker: BangumiConflictChecker = remember { KoinPlatform.getKoin().get<BangumiConflictChecker>() },
) {
    val sessionReady = selfInfo.isSessionValid == true && selfInfo.bangumiConnected == true
    // 明确的会话失效 / 未绑定 (而不是加载中) 才清空: 否则上一个账号的冲突数会继续驱动 UI, 且节流会压制新账号的首次检查.
    val sessionGone = selfInfo.isSessionValid == false || selfInfo.bangumiConnected == false
    LaunchedEffect(sessionGone) {
        if (sessionGone) {
            checker.reset()
        }
    }
    if (sessionReady) {
        // 与 UpdateNotifier 一致: 每次重组都触发, 由 checker 内部节流 (间隔内不实际检查).
        SideEffect {
            checker.startCheck()
        }
    }

    val conflictCount by checker.conflictCount.collectAsStateWithLifecycle()
    BangumiConflictNotifierContent(
        // 展示也以会话状态门控: 登出瞬间正在展示的 snackbar 会随之关闭.
        conflictCount = if (sessionReady) conflictCount else 0,
        onResolveClick = onNavigateToMerge,
        modifier = modifier,
    )
}

/**
 * 无状态部分: [conflictCount] > 0 时展示 snackbar.
 *
 * 关闭状态以 [conflictCount] 为 key: 用户关闭后本次不再提示, 冲突数变化时重新提示.
 */
@Composable
fun BoxScope.BangumiConflictNotifierContent(
    conflictCount: Int,
    onResolveClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dismissed by rememberSaveable(conflictCount) { mutableStateOf(false) }

    if (conflictCount > 0 && !dismissed) {
        val message = stringResource(Lang.bangumi_merge_conflict_notification, conflictCount)
        val actionLabel = stringResource(Lang.bangumi_merge_conflict_notification_action)
        LaunchedEffect(message, actionLabel) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
            dismissed = true
            if (result == SnackbarResult.ActionPerformed) {
                onResolveClick()
            }
        }
    }

    SnackbarHost(snackbarHostState, modifier.align(Alignment.BottomCenter))
}
