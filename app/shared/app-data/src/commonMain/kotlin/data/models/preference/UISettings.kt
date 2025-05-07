/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.him188.ani.app.navigation.MainScreenPage


@Serializable
@Immutable
data class UISettings(
    /**
     * APP 语言 (如果系统支持设置).
     *
     * 如果为 `null`, 则使用系统默认语言.
     *
     * @since 4.9
     * @see me.him188.ani.app.ui.lang.SupportedLocales
     */
    val appLanguage: @Serializable(LocaleSerializer::class) Locale? = null,

    /**
     * 启动 App 时的初始页面
     */
    val mainSceneInitialPage: MainScreenPage = MainScreenPage.Exploration,

    @Suppress("DEPRECATION") @Deprecated(
        "For migration. Use themeSettings instead",
        level = DeprecationLevel.WARNING,
    ) val theme: LegacyThemeSettings? = null,
    val myCollections: MyCollectionsSettings = MyCollectionsSettings.Default,
    val searchSettings: SearchSettings = SearchSettings.Default,
    val episodeProgress: EpisodeProgressSettings = EpisodeProgressSettings.Default,
    /**
     * 欢迎向导是否已经完成, 若为 false 则在启动 APP 时进入 OnboardingScreen, 而非 MainScreen
     */
    val onboardingCompleted: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = UISettings()
    }
}

@Deprecated("For migration. Use themeSettings instead", level = DeprecationLevel.WARNING)
@Suppress("DEPRECATION")
@Serializable
@Immutable
data class LegacyThemeSettings(
    val darkMode: DarkMode = DarkMode.AUTO,
    /**
     * Only on Android
     */
    val dynamicTheme: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = LegacyThemeSettings()
    }
}

@Serializable
@Immutable
data class MyCollectionsSettings(
    val enableListAnimation1: Boolean = true,
) {
    companion object {
        @Stable
        val Default = MyCollectionsSettings()
    }
}

@Serializable
enum class NsfwMode {
    /**
     * 从列表中完全隐藏 NSFW 内容
     */
    HIDE,

    /**
     * 显示一个遮罩
     */
    BLUR,

    /**
     * 不做任何隐藏操作, 像一个正常条目一样显示
     */
    DISPLAY,
}

@Serializable
@Immutable
data class SearchSettings(
    val enableNewSearchSubjectApi: Boolean = false,
    val nsfwMode: NsfwMode = NsfwMode.BLUR,
) {
    companion object {
        @Stable
        val Default = SearchSettings()
    }
}

@Serializable
@Immutable
data class EpisodeProgressSettings(
    val theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
) {
    companion object {
        @Stable
        val Default = EpisodeProgressSettings()
    }
}

@Immutable
@Serializable
enum class EpisodeListProgressTheme {
    /**
     * 点亮模式, 看过的是亮色
     */
    LIGHT_UP,

    /**
     * 动作模式, 可以看的是亮色
     */
    ACTION;

    companion object {
        @Stable
        val Default = ACTION
    }
}

internal object LocaleSerializer : KSerializer<Locale> {
    @Serializable
    data class Delegate(
        val languageTag: String,
    )

    override val descriptor: SerialDescriptor = Delegate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: Locale
    ) {
        Delegate.serializer().serialize(encoder, Delegate(value.toLanguageTag()))
    }

    override fun deserialize(decoder: Decoder): Locale {
        val delegate = Delegate.serializer().deserialize(decoder)
        return Locale(delegate.languageTag)
    }
}
