/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.media.cache.MediaAutoCacheService
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.isSessionVerified
import me.him188.ani.app.domain.session.unverifiedAccessTokenOrNull
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.isInDebugMode
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatform

class AboutTabViewModel : AbstractViewModel(), KoinComponent {
    val browserNavigator: BrowserNavigator by inject()
    private val sessionManager: SessionManager by inject()
    private val cacheManager: MediaCacheManager by inject()

    private val validMediaCacheTasks = kotlin.run {
        val mediaCacheListFromStorages = cacheManager.storages.map { storageFlow ->
            storageFlow.flatMapLatest { storage ->
                if (storage == null) {
                    return@flatMapLatest emptyFlow()
                }
                storage.listFlow
            }.onStart { emit(emptyList()) }
        }
        if (mediaCacheListFromStorages.isEmpty()) {
            return@run flowOf(0)
        }
        combine(mediaCacheListFromStorages) { lists ->
            lists.asSequence().flatten().count { it.isValid() }
        }
    }

    val debugInfo = debugInfoFlow().shareInBackground(started = SharingStarted.Eagerly)

    @OptIn(OpaqueSession::class)
    private fun debugInfoFlow() = combine(
        sessionManager.state,
        sessionManager.processingRequest.flatMapLatest { it?.state ?: flowOf(null) },
        sessionManager.isSessionVerified,
        validMediaCacheTasks,
    ) { session, processingRequest, isSessionValid, activeTasks ->
        DebugInfo(
            properties = buildMap {
                val buildConfig = currentAniBuildConfig
                put("isDebug", buildConfig.isDebug.toString())
                if (buildConfig.isDebug) {
                    put("accessToken", session.unverifiedAccessTokenOrNull)
                    put("domain/session", session.toString())
                }
                put("processingRequest.state", processingRequest.toString())
                put("sessionManager.isSessionValid", isSessionValid.toString())
                put("mediaCacheManager.validTasks", activeTasks.toString())
            },
        )
    }
}

class DebugInfo(
    val properties: Map<String, String?>,
)

@Stable
private const val GITHUB_REPO = "https://github.com/him188/ani"

@Stable
private const val BANGUMI = "https://bangumi.tv"

@Stable
private const val DANDANPLAY = "https://www.dandanplay.com/"

@Stable
private const val DMHY = "https://dmhy.org/"

@Stable
private const val ACG_RIP = "https://acg.rip/"

@Stable
private const val MIKAN = "https://mikanime.tv/"

@OptIn(DelicateCoroutinesApi::class, TestOnly::class)
@Composable
fun AboutTab(
    onTriggerDebugMode: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AboutTabViewModel = viewModel { AboutTabViewModel() },
) {
    val context by rememberUpdatedState(LocalContext.current)
    val toaster = LocalToaster.current
    val navigator = LocalNavigator.current

    SettingsTab(modifier) {
        Group(
            title = { Text("关于 Animeko") },
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val style by rememberUpdatedState(
                    MaterialTheme.typography.bodyMedium.toSpanStyle()
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                )
                val primaryColor by rememberUpdatedState(MaterialTheme.colorScheme.primary)
                val text by remember {
                    derivedStateOf {
                        buildAnnotatedString {
                            pushStyle(style)
                            append("Animeko 完全免费无广告且开源, 源代码可在 ")
                            pushLink(
                                LinkAnnotation.Url(
                                    GITHUB_REPO,
                                    TextLinkStyles(
                                        SpanStyle(
                                            color = primaryColor,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                ) {
                                    vm.browserNavigator.openBrowser(context, GITHUB_REPO)
                                },
                            )
                            append("GitHub")
                            pop()
                            append(" 找到")
                            pop()
                        }

                    }
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Group(title = { Text("感谢你的支持") }) {
                AniHelpSection(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }

        Group(
            title = { Text("鸣谢") },
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Animeko 使用了许多爱好者用爱发电维护的免费服务.", style = MaterialTheme.typography.bodyMedium)

                Text("特别感谢:", style = MaterialTheme.typography.bodyMedium)

                val listStyle = MaterialTheme.typography.bodyMedium.copy(MaterialTheme.colorScheme.primary)
                val uriHandler = LocalUriHandler.current
                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(BANGUMI, TextLinkStyles()) {
                                uriHandler.openUri(GITHUB_REPO)
                            },
                        )
                        append("· Bangumi 番组计划")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(MIKAN, TextLinkStyles()) {
                                uriHandler.openUri(MIKAN)
                            },
                        )
                        append("· Mikan 蜜柑计划")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(DMHY, TextLinkStyles()) {
                                uriHandler.openUri(DMHY)
                            },
                        )
                        append("· 動漫花園資源網")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(ACG_RIP, TextLinkStyles()) {
                                uriHandler.openUri(ACG_RIP)
                            },
                        )
                        append("· acg.rip")
                    },
                    style = listStyle,
                )

                Text(
                    buildAnnotatedString {
                        pushLink(
                            LinkAnnotation.Url(DANDANPLAY, TextLinkStyles()) {
                                uriHandler.openUri(DANDANPLAY)
                            },
                        )
                        append("· 弹弹play")
                    },
                    style = listStyle,
                )
            }
        }

        Group(
            title = {
                Text(
                    text = "调试信息",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTriggerDebugMode,
                    ),
                )
            },
            description = { Text("在反馈问题时附上日志可能有用") },
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isInDebugMode()) {
                    val debugInfo by vm.debugInfo.collectAsStateWithLifecycle(null)
                    val clipboard = LocalClipboardManager.current
                    for ((name, value) in debugInfo?.properties.orEmpty()) {
                        Text(
                            "$name: $value",
                            Modifier.fillMaxWidth().clickable {
                                value?.let { clipboard.setText(AnnotatedString(it)) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    FilledTonalButton(
                        {
                            GlobalScope.launch {
                                KoinPlatform.getKoin().get<MediaAutoCacheService>().checkCache()
                            }
                        },
                    ) {
                        Text("执行自动缓存")
                    }

                    FilledTonalButton(
                        {
                            GlobalScope.launch {
                                KoinPlatform.getKoin().get<MediaCacheManager>().closeAllCaches()
                                withContext(Dispatchers.Main) {
                                    toaster.toast("已关闭所有缓存任务")
                                }
                            }
                        },
                    ) {
                        Text("关闭所有缓存任务")
                    }

                    FilledTonalButton(
                        {
                            GlobalScope.launch {
                                KoinPlatform.getKoin().get<SessionManager>().clearSession()
                            }
                        },
                    ) {
                        Text("清除游客模式记录")
                    }

                    FilledTonalButton(
                        {
                            GlobalScope.launch { KoinPlatform.getKoin().get<SessionManager>().invalidateSession() }
                        },
                    ) {
                        Text("Invalidate Session")
                    }
                    
                    FilledTonalButton(
                        {
                            val navController = navigator.currentNavigator
                            val mainRouteFQN = NavRoutes.Main::class.qualifiedName!!
                            // 找到上一个 NavRoutes.Main 的路由
                            val lastMainRoute = navController.currentBackStack.value
                                .asReversed()
                                .firstOrNull { it.destination.route?.contains(mainRouteFQN) == true }
                                ?.toRoute<NavRoutes.Main>()
                            // 从 SettingsScreen 进入 onboarding, 最后 navigateMain 要 popUpTo Main
                            // 如果 back stack 没有 Main, 那就 popUpTo Settings, 这个一定有
                            navigator.navigateOnboarding(
                                lastMainRoute
                                    ?: navController.currentBackStackEntry?.toRoute<NavRoutes.Settings>(),
                            )
                        },
                    ) {
                        Text("重新进入新手向导")
                    }
                }

                FilledTonalButton(
                    {
                        GlobalScope.launch {
                            KoinPlatform.getKoin().get<SessionManager>().clearSession()
                        }
                    },
                ) {
                    Text("退出登录")
                }

                PlatformDebugInfoItems()
            }
        }
    }
}


@Composable
internal expect fun ColumnScope.PlatformDebugInfoItems()

const val GITHUB_HOME = "https://github.com/open-ani/ani"
const val ANI_WEBSITE = "https://myani.org"
const val ISSUE_TRACKER = "https://github.com/open-ani/ani/issues"

@Composable
fun AniHelpSection(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "欢迎加入 QQ 群反馈建议或者闲聊: 927170241. Telegram 群 openani. 如遇到问题, 除加群外也可以在 GitHub 反馈.",
        )

        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val uriHandler = LocalUriHandler.current
            Box {
                var showOpenDropdown by remember { mutableStateOf(false) }
                DropdownMenu(showOpenDropdown, { showOpenDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text("GitHub") },
                        onClick = {
                            uriHandler.openUri(GITHUB_HOME)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("反馈问题") },
                        onClick = { uriHandler.openUri(ISSUE_TRACKER) },
                    )
                    DropdownMenuItem(
                        text = { Text("Ani 官网") },
                        onClick = { uriHandler.openUri(ANI_WEBSITE) },
                    )
                }

                OutlinedButton({ showOpenDropdown = true }) {
                    Text("打开...")
                }
            }

            Box {
                var showHelp by remember { mutableStateOf(false) }
                Button({ showHelp = true }) {
                    Text("加群")
                }
                HelpDropdown(showHelp, { showHelp = false })
            }
        }

        Text(
            "要让每个番剧都拥有不错的弹幕量需要不小用户基数, 如果你喜欢本应用, 请向朋友推荐以增加弹幕量!",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
