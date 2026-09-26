/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_about_app_description
import me.him188.ani.app.ui.lang.settings_appearance_description
import me.him188.ani.app.ui.lang.settings_media_preference_description
import me.him188.ani.app.ui.lang.settings_media_torrent_sharing_description
import me.him188.ani.app.ui.lang.settings_player_description
import me.him188.ani.app.ui.lang.settings_tab_about
import me.him188.ani.app.ui.lang.settings_tab_appearance
import me.him188.ani.app.ui.lang.settings_tab_media_selector
import me.him188.ani.app.ui.lang.settings_tab_media_source
import me.him188.ani.app.ui.lang.settings_tab_player
import me.him188.ani.app.ui.lang.settings_tab_theme
import me.him188.ani.app.ui.lang.settings_theme_palette
import me.him188.ani.app.ui.lang.tv_settings_bittorrent
import me.him188.ani.app.ui.lang.tv_settings_sources_description
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class TvSettingsSection(val title: StringResource) {
    Appearance(Lang.settings_tab_appearance),
    Theme(Lang.settings_tab_theme),
    Player(Lang.settings_tab_player),
    Sources(Lang.settings_tab_media_source),
    Watching(Lang.settings_tab_media_selector),
    BitTorrent(Lang.tv_settings_bittorrent),
    About(Lang.settings_tab_about),
}

internal enum class TvSettingsAboutPage { Overview, Developers, Acknowledgements, Licenses }

internal enum class TvSettingsPlayerPage { Overview, Playback, Picture, Danmaku, Advanced }

internal enum class TvSettingsExtra(val origin: String, val playerPage: TvSettingsPlayerPage? = null) {
    Playback("player-Playback", TvSettingsPlayerPage.Playback),
    Picture("player-Picture", TvSettingsPlayerPage.Picture),
    Danmaku("player-Danmaku", TvSettingsPlayerPage.Danmaku),
    PlayerAdvanced("player-Advanced", TvSettingsPlayerPage.Advanced),
    WatchingAdvanced("watching-advanced"),
    Subscriptions("subscriptions"),
}

internal data class TvSettingItem(
    val id: String,
    val title: String,
    val value: String = "",
    val description: String? = null,
    val checked: Boolean? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val color: Color? = null,
    val icon: ImageVector? = null,
    val artwork: DrawableResource? = null,
    val artworkShape: Shape = RectangleShape,
    val opensDetail: Boolean = true,
    val opensPane: Boolean = false,
    val onClick: () -> Unit,
)

internal data class TvSettingsChoice(val id: String, val title: String)

internal sealed interface TvSettingsDialog {
    val origin: String
    val title: String

    data class Choice(
        override val origin: String,
        override val title: String,
        val values: List<TvSettingsChoice>,
        val selected: String,
        val onSelect: (String) -> Unit,
    ) : TvSettingsDialog

    data class Input(
        override val origin: String,
        override val title: String,
        val initial: String,
        val description: String?,
        val validate: (String) -> Boolean = { true },
        val multiline: Boolean = false,
        val onSave: (String) -> Unit,
    ) : TvSettingsDialog

    data class Order(
        override val origin: String,
        override val title: String,
        val values: List<TvSettingsChoice>,
        val selected: List<String>?,
        val onSave: (List<String>) -> Unit,
    ) : TvSettingsDialog

    data class Regex(
        override val origin: String,
        override val title: String,
        val filter: DanmakuRegexFilter,
        val isNew: Boolean,
    ) : TvSettingsDialog

    data class SpeedRange(
        override val origin: String,
        override val title: String,
        val config: VideoScaffoldConfig,
    ) : TvSettingsDialog

    data class Info(
        override val origin: String,
        override val title: String,
        val text: String,
        val website: String? = null,
    ) : TvSettingsDialog

    data class Link(
        override val origin: String,
        override val title: String,
        val url: String,
    ) : TvSettingsDialog

    data class Import(
        override val origin: String,
        override val title: String,
    ) : TvSettingsDialog
}

internal class TvSettingsItems(
    val state: TvSettingsUiState,
    val onIntent: (TvSettingsIntent) -> Unit,
    val open: (TvSettingsDialog) -> Unit,
) {
    val items = mutableListOf<TvSettingItem>()

    fun action(
        id: String,
        title: String,
        value: String = "",
        description: String? = null,
        enabled: Boolean = true,
        artwork: DrawableResource? = null,
        artworkShape: Shape = RectangleShape,
        opensDetail: Boolean = true,
        opensPane: Boolean = false,
        onClick: () -> Unit,
    ) {
        items += TvSettingItem(id, title, value, description, enabled = enabled, artwork = artwork,
            artworkShape = artworkShape, opensDetail = opensDetail, opensPane = opensPane, onClick = onClick)
    }

    fun toggle(id: String, title: String, checked: Boolean, description: String? = null, change: (Boolean) -> Unit) {
        items += TvSettingItem(id, title, description = description, checked = checked, onClick = { change(!checked) })
    }

    fun <T> choice(
        id: String,
        title: String,
        value: T,
        values: List<T>,
        labels: List<String>,
        description: String? = null,
        enabled: Boolean = true,
        onSelect: (T) -> Unit,
    ) {
        action(id, title, labels.getOrElse(values.indexOf(value)) { value.toString() }, description, enabled) {
            open(TvSettingsDialog.Choice(
                id, title, values.indices.map { TvSettingsChoice(it.toString(), labels[it]) },
                values.indexOf(value).toString(),
            ) { selected -> onSelect(values[selected.toInt()]) })
        }
    }
}

@Composable
internal fun TvSettingsSection.description(): String = when (this) {
    TvSettingsSection.Appearance -> stringResource(Lang.settings_appearance_description)
    TvSettingsSection.Theme -> stringResource(Lang.settings_theme_palette)
    TvSettingsSection.Player -> stringResource(Lang.settings_player_description)
    TvSettingsSection.Sources -> stringResource(Lang.tv_settings_sources_description)
    TvSettingsSection.Watching -> stringResource(Lang.settings_media_preference_description)
    TvSettingsSection.BitTorrent -> stringResource(Lang.settings_media_torrent_sharing_description)
    TvSettingsSection.About -> stringResource(Lang.settings_about_app_description)
}
