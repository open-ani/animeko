package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.WebViewDriver
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.platform.isDesktop

@Composable
internal fun SettingsScope.VideoResolverGroup(
    videoResolverSettingsState: SettingsState<VideoResolverSettings>,
    modifier: Modifier = Modifier,
) {
    // There are not many options for the player.
    if (!LocalPlatform.current.isDesktop()) {
        return
    }

    val config by videoResolverSettingsState

    Group(
        title = {
            Text("视频解析")
        },
        modifier = modifier,
    ) {
        val itemText: @Composable (WebViewDriver) -> Unit = {
            when (it) {
                WebViewDriver.CHROME -> Text("Chrome")
                WebViewDriver.EDGE -> Text("Edge浏览器")
                WebViewDriver.AUTO -> Text("自动选择")
            }
        }
        DropdownItem(
            selected = { config.driver },
            values = { WebViewDriver.enabledEntries },
            itemText = itemText,
            exposedItemText = itemText,
            onSelect = {
                videoResolverSettingsState.update(
                    config.copy(
                        driver = it,
                    ),
                )
            },
            title = { Text("浏览器引擎") },
            description = { Text("播放部分视频源时需要使用无头浏览器引擎，请在电脑上安装 Chrome 或 Edge 浏览器，Safari 不支持") },
        )
        DropdownItem(
            selected = { config.effectiveDataSourceBrowserConcurrency },
            values = { VideoResolverSettings.DataSourceBrowserConcurrencyOptions },
            itemText = { Text("$it 个") },
            onSelect = {
                videoResolverSettingsState.update(
                    config.copy(
                        dataSourceBrowserConcurrency = it,
                    ),
                )
            },
            title = { Text("数据源查询浏览器数") },
            description = { Text("限制数据源查询阶段可同时使用的浏览器数量，例如验证码处理") },
        )
    }
}

// TODO: More accurate icons
//@Composable
//private fun WebViewDriverIcon(driver: WebViewDriver) {
////    when (driver) {
////        else -> Icon(Icons.Rounded.TravelExplore, driver.toString())
////    }
//    Icon(Icons.Rounded.TravelExplore, driver.toString())
//}
