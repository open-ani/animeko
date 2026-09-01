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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification_action
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 后台检查 Bangumi 收藏冲突.
 *
 * 每次进入主界面时触发, [checkIntervalMillis] 内最多实际检查一次
 * (检查需要拉取全部远端收藏, 不宜频繁). 检查失败静默, 只记日志.
 */
@Stable
class BangumiConflictCheckViewModel(
    private val getCurrentTimeMillis: () -> Long = { currentTimeMillis() },
    private val checkIntervalMillis: Long = 60 * 60 * 1000L,
) : AbstractViewModel(), KoinComponent {
    private val mergeRepository: BangumiMergeRepository by inject()

    private val autoCheckTasker = MonoTasker(backgroundScope)

    /** 上次成功检查的时刻; `null` 表示从未检查过, 不参与节流比较. */
    private val lastCheckTime = MutableStateFlow<Long?>(null)

    private val _conflictCount = MutableStateFlow(0)

    /**
     * 最近一次检查发现的冲突数. `0` 表示无冲突或尚未检查过.
     */
    val conflictCount: StateFlow<Int> get() = _conflictCount

    fun startAutomaticCheck() {
        if (autoCheckTasker.isRunning.value) return
        val last = lastCheckTime.value
        if (last != null && getCurrentTimeMillis() - last < checkIntervalMillis) return
        autoCheckTasker.launch {
            val count = try {
                mergeRepository.computeMergePlan().totalConflictCount
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 后台检查, 失败不打扰用户; 也不记录检查时间, 失败不消耗节流窗口.
                logger.warn(e) { "Failed to check Bangumi merge conflicts" }
                return@launch
            }
            lastCheckTime.value = getCurrentTimeMillis()
            _conflictCount.value = count
        }
    }

    /**
     * 会话失效或解绑 Bangumi 时调用: 取消进行中的检查并清空结果,
     * 否则上一个账号的冲突数会继续驱动 UI, 且节流会压制新账号的首次检查.
     */
    fun reset() {
        autoCheckTasker.cancel()
        lastCheckTime.value = null
        _conflictCount.value = 0
    }

    /**
     * 用户已跳转到合并界面处理: 清空计数, 冲突是否仍存在由下次检查重新判定,
     * 避免用户处理完返回后仍被过期计数重复提示.
     */
    fun clearConflicts() {
        _conflictCount.value = 0
    }

    /**
     * 等待当前检查任务完成. 仅测试用.
     */
    @TestOnly
    suspend fun joinCheck() {
        autoCheckTasker.join()
    }
}

/**
 * 主界面的 Bangumi 收藏冲突提示: 会话有效且已绑定 Bangumi 时后台检查,
 * 发现冲突则弹出带「处理」动作的 snackbar, 点击跳转合并收藏界面.
 */
@Composable
fun BoxScope.BangumiConflictNotifier(
    selfInfo: SelfInfoUiState,
    onNavigateToMerge: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    vm: BangumiConflictCheckViewModel = viewModel { BangumiConflictCheckViewModel() },
) {
    val sessionReady = selfInfo.isSessionValid == true && selfInfo.bangumiConnected == true
    LaunchedEffect(sessionReady) {
        if (!sessionReady) {
            vm.reset()
        }
    }
    if (sessionReady) {
        // 与 UpdateNotifier 一致: 每次重组都触发, 由 VM 内部节流 (间隔内不实际检查).
        SideEffect {
            vm.startAutomaticCheck()
        }
    }

    val conflictCount by vm.conflictCount.collectAsStateWithLifecycle()
    BangumiConflictNotifierContent(
        // 展示也以会话状态门控: 登出瞬间正在展示的 snackbar 会随之关闭.
        conflictCount = if (sessionReady) conflictCount else 0,
        onResolveClick = {
            vm.clearConflicts()
            onNavigateToMerge()
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
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
