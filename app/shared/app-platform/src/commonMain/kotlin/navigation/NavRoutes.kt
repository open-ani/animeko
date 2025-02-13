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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
sealed class NavRoutes {
    @Serializable
    data object Welcome : NavRoutes()

    @Serializable
    data object Onboarding : NavRoutes()
    
    @Serializable
    data object OnboardingComplete : NavRoutes()

    @Serializable
    data class Main(
        val initialPage: MainScreenPage,
        val requestSearchFocus: Boolean = false,
    ) : NavRoutes()

    @Serializable
    data object BangumiOAuth : NavRoutes()

    @Serializable
    data object BangumiTokenAuth : NavRoutes()

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
    data class SubjectDetail(
        val subjectId: Int,
        val placeholder: SubjectDetailPlaceholder? = null,
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
}

@Serializable
data class SubjectDetailPlaceholder(
    val id: Int,
    val name: String = "",
    val nameCN: String = "",
    val coverUrl: String = "",
) {
    companion object {
        val NavType = SerializableNavType(serializer())
    }
}

@Serializable
enum class MainScreenPage {
    Exploration,
    Collection,
    CacheManagement,
    Search, ;

    companion object {
        @Stable
        val visibleEntries by lazy(LazyThreadSafetyMode.PUBLICATION) { entries - Search }

        @Stable
        val NavType by lazy(LazyThreadSafetyMode.PUBLICATION) {
            EnumNavType(kotlin.enums.enumEntries<MainScreenPage>())
        }
    }
}

@Immutable
enum class SettingsTab {
    APPEARANCE,
    THEME,
    UPDATE,

    PLAYER,
    MEDIA_SOURCE,
    MEDIA_SELECTOR,
    DANMAKU,

    PROXY,
    BT,
    CACHE,
    STORAGE,

    ABOUT,
    DEBUG,
    ;

    companion object {
        @Stable
        val NavType by lazy(LazyThreadSafetyMode.PUBLICATION) {
            EnumNavType(kotlin.enums.enumEntries<SettingsTab>())
        }
    }
}

@Stable
fun MainScreenPage.getIcon() = when (this) {
    MainScreenPage.Exploration -> Icons.Rounded.TravelExplore
    MainScreenPage.Collection -> Icons.Rounded.Star
    MainScreenPage.CacheManagement -> Icons.Rounded.DownloadDone
    MainScreenPage.Search -> Icons.Rounded.Search
}

@Stable
fun MainScreenPage.getText(): String = when (this) {
    MainScreenPage.Exploration -> "探索"
    MainScreenPage.Collection -> "追番"
    MainScreenPage.CacheManagement -> "缓存"
    MainScreenPage.Search -> "搜索"
}
