/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import me.him188.ani.android.BuildConfig
import me.him188.ani.android.InstallFormFactorUi
import me.him188.ani.android.formFactorUiBehavior
import me.him188.ani.android.onFormFactorActivityCreated
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.rememberPlatformWindow
import me.him188.ani.app.ui.exprovider.ExternalContentProviderFactory
import me.him188.ani.app.ui.exprovider.LocalExternalContentProvider
import me.him188.ani.app.ui.foundation.UiScaleApplier
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : AniComponentActivity() {
    private val logger = logger<MainActivity>()
    private val aniNavigator = AniNavigator()

    private val externalContentProviderFactory: ExternalContentProviderFactory by inject()
    private val settingsRepository: SettingsRepository by inject()

    /**
     * 本次 Activity 创建时落到窗口层的界面缩放, 由 [attachBaseContext] 定下, 之后不再变 ——
     * 主窗口与所有弹窗都按它渲染. 见 [UiScaleApplier].
     */
    private var appliedUiScale: Float = 1f

    /**
     * 已经请求过重建. `recreate()` 自己会销毁 Compose 树, 从而**再次**触发那个「离开设置页就对齐」的
     * onDispose —— 那时读到的仍是本 Activity 的旧 [appliedUiScale], 会对着一个正在销毁的 Activity
     * 再调一次 `recreate()`. 这个标志让重建请求只发一次.
     */
    private var uiScaleRestartRequested = false

    private val uiScaleApplier = object : UiScaleApplier {
        override val appliedScale: Float get() = appliedUiScale

        override fun apply(scale: Float) {
            if (scale == appliedUiScale || uiScaleRestartRequested) return
            if (isFinishing || isDestroyed) return
            uiScaleRestartRequested = true
            // 重建后的 attachBaseContext 会重新读镜像, 所以必须先落盘再 recreate
            UiScaleMirror.write(this@MainActivity, scale)
            recreate()
        }
    }

    /**
     * 界面缩放要改的是 Activity 的 `densityDpi` —— 只有这样弹窗 (各自独立 window) 才会跟着变.
     * 这是唯一能在 Activity 创建前介入的时机.
     */
    override fun attachBaseContext(newBase: Context) {
        val scale = UiScaleMirror.read(newBase)
        appliedUiScale = scale
        super.attachBaseContext(newBase.withUiScale(scale))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleStartIntent(intent)
    }

    private fun handleStartIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "ani") return
        if (data.host == "subjects") {
            val id = data.pathSegments.getOrNull(0)?.toIntOrNull() ?: return
            lifecycleScope.launch {
                try {
                    if (!aniNavigator.isNavControllerReady()) {
                        aniNavigator.awaitNavController()
                        delay(1000) // 等待初始化好, 否则跳转可能无效
                    }
                    aniNavigator.navigateSubjectDetails(id, placeholder = null)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to navigate to subject details" }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStartIntent(intent)

        // 本形态 (phone / tv) 的附加初始化, 见各 flavor 下的 FormFactorSetup.kt
        onFormFactorActivityCreated(this)

        enableEdgeToEdge(
            // 透明状态栏
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            // 透明导航栏
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        // 允许画到 system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val toaster = object : Toaster {
            override fun toast(text: String) {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }

        val externalContentProvider = externalContentProviderFactory.create(this, lifecycleScope)

        // 把界面缩放抄进 SharedPreferences: attachBaseContext 拿不到 DataStore (还没有协程可用).
        // 挪到 IO: write 是同步落盘 (commit), 拖滑块每过一格都会触发一次, 放主线程会加剧拖动时的卡顿
        lifecycleScope.launch(Dispatchers.IO) {
            settingsRepository.themeSettings.flow
                .map { it.effectiveUiScale }
                .distinctUntilChanged()
                .collect { UiScaleMirror.write(this@MainActivity, it) }
        }

        setContent {
            // 界面行为由本形态决定, 共享界面代码不判断设备 (见 AniUiBehavior)
            AniApp(uiBehavior = formFactorUiBehavior, uiScaleApplier = uiScaleApplier) {
                val externalComponentProviderUpdated by rememberUpdatedState(externalContentProvider)

                SystemBarColorEffect()

                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides rememberPlatformWindow(this),
                    LocalExternalContentProvider provides externalComponentProviderUpdated,
                ) {
                    // Expose Modifier.testTag as resource-id in accessibility/uiautomator dumps,
                    // so UI-automation agents can locate elements by stable ids (debug only).
                    @OptIn(ExperimentalComposeUiApi::class)
                    val rootModifier = if (BuildConfig.DEBUG) {
                        Modifier.semantics { testTagsAsResourceId = true }
                    } else {
                        Modifier
                    }
                    Box(rootModifier) {
                        // 本形态特有的页面变体装配 (见各 Local*Variant 插槽)
                        InstallFormFactorUi {
                            AniAppContent(aniNavigator)
                        }
                    }
                }
            }
        }
    }
}
