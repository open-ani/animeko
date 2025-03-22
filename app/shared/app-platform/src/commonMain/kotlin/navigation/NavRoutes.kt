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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.Serializable

@Serializable
sealed class NavRoutes {
    @Serializable
    data object Welcome : NavRoutes()

    @Serializable
    data class Onboarding(
        /**
         * 在这个界面, 用户只能导航到 [OnboardingComplete] 随后导航到 [Main],
         * 并且不能再回到 [Onboarding] 和 [OnboardingComplete].
         *
         * 但是由于用户可能从不同的界面进入 [Onboarding] (如首次进入 APP 从 [Welcome] 进入, 重新运行向导从 [Settings] 进入),
         * 所以最后 [OnboardingComplete] 导航到 [Main] 的 [NavOptionsBuilder.popUpTo] 的参数也不一样.
         *
         * 如果为 `null`, 则不使用 `[NavOptionsBuilder.popUpTo]` 选项.
         */
        val popUpTargetInclusive: NavRoutes? = null,
    ) : NavRoutes()

    @Serializable
    data class OnboardingComplete(
        /**
         * 同 [Onboarding.popUpTargetInclusive]
         */
        val popUpTargetInclusive: NavRoutes? = null,
    ) : NavRoutes()

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
    data object SubjectSearch : NavRoutes()

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

    companion object {
        val NavType by lazy { SerializableNavType(serializer()) }
    }
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
    ;

    companion object {
        @Stable
        val visibleEntries get() = entries

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
}

@Stable
fun MainScreenPage.getText(): String = when (this) {
    MainScreenPage.Exploration -> "探索"
    MainScreenPage.Collection -> "追番"
    MainScreenPage.CacheManagement -> "缓存"
}
