package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.Text

@Stable
class CacheDirectoryGroupState(
    val mediaCacheSettingsState: SettingsState<MediaCacheSettings>,
    val permissionManager: PermissionManager,
)

@Composable
expect fun SettingsScope.CacheDirectoryGroup(
    state: CacheDirectoryGroupState,
) {
    Group(
        title = {
            Text("缓存设置")
        },
        description = {
            Text("设置视频缓存的行为")
        },
    ) {
        val settings by state.mediaCacheSettingsState
        
        SwitchItem(
            checked = settings.burnAfterRead,
            onCheckedChange = { checked ->
                state.mediaCacheSettingsState.update { it.copy(burnAfterRead = checked) }
            },
            title = {
                Text("阅后即焚")
            },
            description = {
                Text("播放完成后自动删除缓存")
            },
        )
        
        SwitchItem(
            checked = settings.autoCleanup,
            onCheckedChange = { checked ->
                state.mediaCacheSettingsState.update { it.copy(autoCleanup = checked) }
            },
            title = {
                Text("自动清理")
            },
            description = {
                Text("定期清理已观看的剧集缓存")
            },
        )
    }
}
