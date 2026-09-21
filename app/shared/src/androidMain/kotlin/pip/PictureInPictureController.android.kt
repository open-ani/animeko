/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_pip_action_pause
import me.him188.ani.app.ui.lang.video_player_pip_action_play
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.isMediaLoaded

private const val ACTION_PIP_PLAY = "me.him188.ani.app.pip.PLAY"
private const val ACTION_PIP_PAUSE = "me.him188.ani.app.pip.PAUSE"

// RemoteAction 的 requestCode 必须唯一, 否则系统按 Intent filter 去重后只剩一个
private const val REQUEST_CODE_PLAY = 1
private const val REQUEST_CODE_PAUSE = 2

/**
 * 由宿主 Activity 实现的画中画宿主回调.
 *
 * - [android.app.Activity.onUserLeaveHint] 没有 androidx 监听者 API,
 *   Android 11 以下的自动进入小窗需要通过它转发.
 * - [android.app.Activity.onPictureInPictureModeChanged] 本身是回调方法,
 *   但框架没有公开的监听者 API (隐藏 API), 小窗模式变化同样通过这里转发.
 *
 * 由 `MainActivity` 实现; 其他 Activity (或无此接口时) 退化为仅支持 Android 12+ 的系统自动进入,
 * 且小窗状态变化依赖 [PictureInPictureController.refreshParams] 时同步.
 */
interface PictureInPictureHost {
    /**
     * 注册用户离开 (home/最近任务) 监听. 返回注销句柄.
     */
    fun registerUserLeaveHintListener(listener: () -> Unit): AutoCloseable

    /**
     * 注册小窗模式变化监听. 返回注销句柄.
     */
    fun registerPictureInPictureModeChangedListener(
        listener: (isInPictureInPicture: Boolean) -> Unit,
    ): AutoCloseable
}

@Composable
actual fun rememberPictureInPictureController(player: MediampPlayer): PictureInPictureController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playLabel = stringResource(Lang.video_player_pip_action_play)
    val pauseLabel = stringResource(Lang.video_player_pip_action_pause)
    val controller = remember(activity, player, playLabel, pauseLabel) {
        AndroidPictureInPictureController(activity, player, playLabel, pauseLabel)
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    return controller
}

/**
 * Android 画中画控制器.
 *
 * - Android 12+ (API 31+): 通过 [PictureInPictureParams.Builder.setAutoEnterEnabled] 启用
 *   "上滑回桌面自动进入小窗" 的系统动画, 播放状态变化时必须刷新 params (系统要求)
 * - Android 10 及以下 (API 26–30): 无系统自动进入, 通过 [PictureInPictureHost] 监听
 *   [android.app.Activity.onUserLeaveHint] 手动调用 [Activity.enterPictureInPictureMode]
 * - 小窗播控: 单个播放/暂停切换 [RemoteAction], broadcast 到本应用动态注册的 receiver
 */
internal class AndroidPictureInPictureController(
    private val activity: Activity?,
    private val player: MediampPlayer,
    private val playActionLabel: String,
    private val pauseActionLabel: String,
) : PictureInPictureController {
    override val isSupported: Boolean get() = activity != null

    private val _isInPictureInPicture =
        MutableStateFlow(activity?.isInPictureInPictureMode == true)
    override val isInPictureInPicture: StateFlow<Boolean> = _isInPictureInPicture.asStateFlow()

    private val _isPictureInPicturePossible = MutableStateFlow(isSupported)
    override val isPictureInPicturePossible: StateFlow<Boolean> = _isPictureInPicturePossible.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 策略状态, 由 updatePolicy 写入
    @Volatile
    private var policyAutoEnter: Boolean = false
    @Volatile
    private var aspectWidth: Int? = null
    @Volatile
    private var aspectHeight: Int? = null

    /**
     * 上次实际应用的 params 签名, 用于去抖, 避免高频调用 [Activity.setPictureInPictureParams].
     */
    private var appliedSignature: ParamsSignature? = null

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_PLAY -> player.play()
                ACTION_PIP_PAUSE -> player.pause()
            }
        }
    }

    private val pipModeChangedRegistration: AutoCloseable? =
        (activity as? PictureInPictureHost)?.registerPictureInPictureModeChangedListener { isIn ->
            _isInPictureInPicture.value = isIn
            // 进入/退出小窗时刷新播控按钮
            refreshParams()
        }

    private val userLeaveHintRegistration: AutoCloseable? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            (activity as? PictureInPictureHost)?.registerUserLeaveHintListener {
                // Android 11 及以下没有系统自动进入: 策略允许且正在播放时手动进入
                if (policyAutoEnter && player.state.value.playWhenReady) {
                    enterPictureInPicture()
                }
            }
        } else {
            null
        }

    init {
        activity?.let {
            ContextCompat.registerReceiver(
                it,
                pipActionReceiver,
                IntentFilter().apply {
                    addAction(ACTION_PIP_PLAY)
                    addAction(ACTION_PIP_PAUSE)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        // 播放/暂停切换时刷新小窗播控按钮
        scope.launch {
            player.state.collect { refreshParams() }
        }
    }

    override fun updatePolicy(autoEnterEnabled: Boolean, aspectWidth: Int?, aspectHeight: Int?) {
        policyAutoEnter = autoEnterEnabled
        this.aspectWidth = aspectWidth
        this.aspectHeight = aspectHeight
        refreshParams()
    }

    override fun enterPictureInPicture() {
        val activity = activity ?: return
        if (activity.isDestroyed || activity.isFinishing) return
        if (_isInPictureInPicture.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.enterPictureInPictureMode(buildParams())
        }
    }

    fun dispose() {
        scope.cancel()
        userLeaveHintRegistration?.close()
        pipModeChangedRegistration?.close()
        runCatching { activity?.unregisterReceiver(pipActionReceiver) }
    }

    private fun refreshParams() {
        val activity = activity ?: return
        if (activity.isDestroyed || activity.isFinishing) return
        // 宿主未实现 PictureInPictureHost 时 (无模式变化回调), 在这里同步一次小窗状态
        _isInPictureInPicture.value = activity.isInPictureInPictureMode
        val params = buildParams()
        val signature = ParamsSignature(
            autoEnter = policyAutoEnter,
            aspect = computePipAspectRatio(aspectWidth, aspectHeight),
            playing = player.state.value.let { it.isMediaLoaded && it.playWhenReady },
        )
        if (signature == appliedSignature) return
        appliedSignature = signature
        activity.setPictureInPictureParams(params)
    }

    private fun buildParams(): PictureInPictureParams {
        return PictureInPictureParams.Builder()
            .apply {
                computePipAspectRatio(aspectWidth, aspectHeight)?.let { (width, height) ->
                    setAspectRatio(Rational(width, height))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(policyAutoEnter)
                }
                setActions(buildActions())
            }
            .build()
    }

    private fun buildActions(): List<RemoteAction> {
        val activity = activity ?: return emptyList()
        val state = player.state.value
        if (!state.isMediaLoaded) return emptyList()

        return listOf(
            if (state.playWhenReady) {
                RemoteAction(
                    Icon.createWithResource(activity, android.R.drawable.ic_media_pause),
                    pauseActionLabel,
                    pauseActionLabel,
                    PendingIntent.getBroadcast(
                        activity,
                        REQUEST_CODE_PAUSE,
                        Intent(ACTION_PIP_PAUSE).setPackage(activity.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                RemoteAction(
                    Icon.createWithResource(activity, android.R.drawable.ic_media_play),
                    playActionLabel,
                    playActionLabel,
                    PendingIntent.getBroadcast(
                        activity,
                        REQUEST_CODE_PLAY,
                        Intent(ACTION_PIP_PLAY).setPackage(activity.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            },
        )
    }

    private data class ParamsSignature(
        val autoEnter: Boolean,
        val aspect: Pair<Int, Int>?,
        val playing: Boolean,
    )
}
