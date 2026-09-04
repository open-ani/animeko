/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.main_screen_page_cache_management
import me.him188.ani.app.ui.lang.main_screen_page_collection
import me.him188.ani.app.ui.lang.main_screen_page_exploration
import org.jetbrains.compose.resources.stringResource

@Serializable
sealed class NavRoutes : NavKey {
    @Serializable
    data object EmailLoginStart : NavRoutes()

    @Serializable
    data object EmailLoginVerify : NavRoutes()

    @Serializable
    data class Main(
        val initialPage: MainScreenPage,
        val requestSearchFocus: Boolean = false,
    ) : NavRoutes()

    @Serializable
    data object BangumiAuthorize : NavRoutes()

    @Serializable
    data class Settings(
        /**
         * 如果指定了 [tab]，则直接跳转到指定的设置页. 在按返回时将回到上一页, 而不是设置页的导航 (list).
         *
         * 如果为 `null`, 则正常打开设置页的导航.
         */
        val tab: SettingsTab? = null,
    ) : NavRoutes()

    @Serializable
    data class SubjectSearch(
        val keyword: String? = null,
        val tags: List<String>? = null,
    ) : NavRoutes()

    @Serializable
    data class SubjectDetail(
        val subjectId: Int,
        val placeholder: SubjectDetailPlaceholder? = null,
    ) : NavRoutes()

    @Serializable
    data class PersonDetail(
        val personId: Int,
    ) : NavRoutes()

    @Serializable
    data class CharacterDetail(
        val characterId: Int,
    ) : NavRoutes()

    @Serializable
    data class SubjectCaches(
        val subjectId: Int,
    ) : NavRoutes()

    @Serializable
    data class EpisodeDetail(
        val subjectId: Int,
        val episodeId: Int,
    ) : NavRoutes()

    @Serializable
    data class EditMediaSource(
        val factoryId: String,
        val mediaSourceInstanceId: String,
    ) : NavRoutes()

    @Serializable
    data object TorrentPeerSettings : NavRoutes()

    @Serializable
    data object Caches : NavRoutes()

    @Serializable
    data class CacheDetail(
        val cacheId: String,
    ) : NavRoutes()

    @Serializable
    data object Schedule : NavRoutes()

    @Serializable
    data object PlaybackHistory : NavRoutes()

    @Serializable
    data object PlaybackHistorySyncStatus : NavRoutes()

    /**
     * 合并收藏: 处理 Animeko 与 Bangumi 两侧的收藏冲突.
     */
    @Serializable
    data object BangumiMerge : NavRoutes()
}

@Serializable
data class SubjectDetailPlaceholder(
    val id: Int,
    val name: String = "",
    val nameCN: String = "",
    val coverUrl: String = "",
)

@Serializable
enum class MainScreenPage {
    Exploration,
    Collection,
    CacheManagement,
    ;

    companion object {
        @Stable
        val visibleEntries get() = entries
    }
}

@Immutable
@Serializable
enum class SettingsTab {
    PROFILE,

    APPEARANCE,
    THEME,
    UPDATE,

    PLAYER,
    MEDIA_SOURCE,
    MEDIA_SELECTOR,
    SERVER,

    PROXY,
    BT,

    //    CACHE, // 自动缓存, 4.10 删除
    STORAGE,

    SETTINGS_BACKUP,

    ABOUT,
    LOG,
    DEBUG,
    ;

    companion object {
        /**
         * 在 PC 上右侧默认显示的 tab.
         */
        val Default = APPEARANCE
    }
}

@Stable
fun MainScreenPage.getIcon() = when (this) {
    MainScreenPage.Exploration -> Icons.Rounded.TravelExplore
    MainScreenPage.Collection -> Icons.Rounded.Star
    MainScreenPage.CacheManagement -> Icons.Rounded.DownloadDone
}

@Stable
@Composable
fun MainScreenPage.getText(): String = when (this) {
    MainScreenPage.Exploration -> stringResource(Lang.main_screen_page_exploration)
    MainScreenPage.Collection -> stringResource(Lang.main_screen_page_collection)
    MainScreenPage.CacheManagement -> stringResource(Lang.main_screen_page_cache_management)
}
