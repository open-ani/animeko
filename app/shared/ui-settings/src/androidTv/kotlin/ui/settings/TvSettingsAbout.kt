/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.tmdb
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.acknowledgements
import me.him188.ani.app.ui.lang.developer_list
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.settings_about_app_description
import me.him188.ani.app.ui.lang.settings_about_app_name
import me.him188.ani.app.ui.lang.settings_about_feedback
import me.him188.ani.app.ui.lang.settings_about_release_notes
import me.him188.ani.app.ui.lang.settings_about_source_code
import me.him188.ani.app.ui.lang.settings_about_version
import me.him188.ani.app.ui.lang.settings_about_website
import me.him188.ani.app.ui.lang.settings_acknowledgements_bangumi
import me.him188.ani.app.ui.lang.settings_acknowledgements_bangumi_description
import me.him188.ani.app.ui.lang.settings_acknowledgements_dandanplay
import me.him188.ani.app.ui.lang.settings_acknowledgements_dandanplay_description
import me.him188.ani.app.ui.lang.settings_acknowledgements_dmhy
import me.him188.ani.app.ui.lang.settings_acknowledgements_dmhy_description
import me.him188.ani.app.ui.lang.settings_acknowledgements_mikan
import me.him188.ani.app.ui.lang.settings_acknowledgements_mikan_description
import me.him188.ani.app.ui.lang.settings_acknowledgements_oss_licenses
import me.him188.ani.app.ui.lang.settings_acknowledgements_tmdb
import me.him188.ani.app.ui.lang.settings_acknowledgements_tmdb_description
import me.him188.ani.app.ui.lang.settings_developers_view_more_on_github
import me.him188.ani.app.ui.lang.settings_help_telegram
import me.him188.ani.app.ui.lang.settings_load_failed
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.settings_tab_about
import me.him188.ani.app.ui.settings.tabs.AniHelperDestination
import me.him188.ani.app.ui.settings.tabs.about.developerCredits
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSettingsAboutPage.title(): String = stringResource(when (this) {
    TvSettingsAboutPage.Overview -> Lang.settings_tab_about
    TvSettingsAboutPage.Developers -> Lang.developer_list
    TvSettingsAboutPage.Acknowledgements -> Lang.acknowledgements
    TvSettingsAboutPage.Licenses -> Lang.settings_acknowledgements_oss_licenses
})

@Composable
internal fun TvSettingsItems.about(
    page: TvSettingsAboutPage,
    navigate: (TvSettingsAboutPage) -> Unit,
) {
    fun link(id: String, title: String, url: String, description: String? = null, artwork: DrawableResource? = null,
             artworkShape: Shape = RectangleShape) {
        action(id, title, description = description, artwork = artwork, artworkShape = artworkShape) {
            open(TvSettingsDialog.Link(id, title, url))
        }
    }
    when (page) {
        TvSettingsAboutPage.Overview -> {
            val version = currentAniBuildConfig.versionName
            val title = stringResource(Lang.settings_about_app_name)
            val description = stringResource(Lang.settings_about_app_description)
            action("version", stringResource(Lang.settings_about_version), "$title $version") {
                open(TvSettingsDialog.Info("version", title, "$description\n\n$version"))
            }
            link("releases", stringResource(Lang.settings_about_release_notes), AniHelperDestination.RELEASE_PREFIX + version)
            link("website", stringResource(Lang.settings_about_website), AniHelperDestination.ANI_WEBSITE)
            link("feedback", stringResource(Lang.settings_about_feedback), AniHelperDestination.ISSUE_TRACKER)
            link("source-code", stringResource(Lang.settings_about_source_code), AniHelperDestination.GITHUB_HOME)
            action("developers", stringResource(Lang.developer_list)) { navigate(TvSettingsAboutPage.Developers) }
            action("acknowledgements", stringResource(Lang.acknowledgements)) { navigate(TvSettingsAboutPage.Acknowledgements) }
            link("telegram", stringResource(Lang.settings_help_telegram), "https://t.me/openani")
        }
        TvSettingsAboutPage.Developers -> {
            developerCredits.forEach { credit ->
                link("developer-${credit.name}", credit.name, credit.url, stringResource(credit.role), credit.avatar, CircleShape)
            }
            link("contributors", stringResource(Lang.settings_developers_view_more_on_github), AniHelperDestination.GITHUB_CONTRIBUTORS)
        }
        TvSettingsAboutPage.Acknowledgements -> {
            link("bangumi", stringResource(Lang.settings_acknowledgements_bangumi), AniHelperDestination.BANGUMI,
                stringResource(Lang.settings_acknowledgements_bangumi_description))
            link("dandanplay", stringResource(Lang.settings_acknowledgements_dandanplay), AniHelperDestination.DANDANPLAY,
                stringResource(Lang.settings_acknowledgements_dandanplay_description))
            link("mikan", stringResource(Lang.settings_acknowledgements_mikan), AniHelperDestination.MIKAN,
                stringResource(Lang.settings_acknowledgements_mikan_description))
            link("dmhy", stringResource(Lang.settings_acknowledgements_dmhy), AniHelperDestination.DMHY,
                stringResource(Lang.settings_acknowledgements_dmhy_description))
            link("tmdb", stringResource(Lang.settings_acknowledgements_tmdb), "https://www.themoviedb.org",
                stringResource(Lang.settings_acknowledgements_tmdb_description), Res.drawable.tmdb)
            action("licenses", stringResource(Lang.settings_acknowledgements_oss_licenses)) { navigate(TvSettingsAboutPage.Licenses) }
        }
        TvSettingsAboutPage.Licenses -> {
            if (state.librariesFailed) action("licenses-retry", stringResource(Lang.settings_mediasource_retry),
                description = stringResource(Lang.settings_load_failed), opensDetail = false) { onIntent(TvSettingsIntent.LoadLibraries) }
            else if (state.librariesLoading) action("licenses-loading", stringResource(Lang.foundation_loading), opensDetail = false) {}
            state.libraries.forEach { library ->
                action("license-${library.id}", library.name, "${library.version} · ${library.license}") {
                    open(TvSettingsDialog.Info(
                        "license-${library.id}", library.name, library.licenseText.ifBlank { library.license }, library.website,
                    ))
                }
            }
        }
    }
}
