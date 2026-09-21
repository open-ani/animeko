/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.settings.tabs.about

import me.him188.ani.app.ui.foundation.Res as FoundationRes
import me.him188.ani.app.ui.foundation.a
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_developers_bangumi_upstream
import me.him188.ani.app.ui.lang.settings_developers_contributor
import me.him188.ani.app.ui.lang.settings_developers_daily_maintenance
import me.him188.ani.app.ui.lang.settings_developers_icon_drawing
import me.him188.ani.app.ui.lang.settings_developers_ml_research
import me.him188.ani.app.ui.lang.settings_developers_organization
import me.him188.ani.app.ui.lang.settings_developers_project_initiator
import me.him188.ani.app.ui.lang.settings_developers_server_development
import me.him188.ani.app.ui.lang.settings_developers_website_development
import me.him188.ani.app.ui.settings.Res
import me.him188.ani.app.ui.settings.btmuli
import me.him188.ani.app.ui.settings.generalk1ng
import me.him188.ani.app.ui.settings.him188
import me.him188.ani.app.ui.settings.jerryz233
import me.him188.ani.app.ui.settings.misakatat
import me.him188.ani.app.ui.settings.nekoouo
import me.him188.ani.app.ui.settings.nick
import me.him188.ani.app.ui.settings.nier4ever
import me.him188.ani.app.ui.settings.nihildigit
import me.him188.ani.app.ui.settings.rdlwicked
import me.him188.ani.app.ui.settings.sanlorng
import me.him188.ani.app.ui.settings.stageguard
import me.him188.ani.app.ui.settings.woleoz
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class DeveloperCredit(
    val name: String,
    val url: String,
    val role: StringResource,
    val avatar: DrawableResource,
)

/** Contributors and their responsibilities shared by all settings presentations. */
val developerCredits = listOf(
    DeveloperCredit("Him188", "https://github.com/him188", Lang.settings_developers_project_initiator, Res.drawable.him188),
    DeveloperCredit("StageGuard", "https://github.com/StageGuard", Lang.settings_developers_daily_maintenance, Res.drawable.stageguard),
    DeveloperCredit("General_K1ng", "https://github.com/GeneralK1ng", Lang.settings_developers_contributor, Res.drawable.generalk1ng),
    DeveloperCredit("JerryZ233", "https://github.com/JerryZ233", Lang.settings_developers_server_development, Res.drawable.jerryz233),
    DeveloperCredit("MisakaTAT", "https://github.com/MisakaTAT", Lang.settings_developers_bangumi_upstream, Res.drawable.misakatat),
    DeveloperCredit("NeKoOuO", "https://github.com/NeKoOuO", Lang.settings_developers_icon_drawing, Res.drawable.nekoouo),
    DeveloperCredit("NickChenヰ", "https://github.com/nick-cjyx9", Lang.settings_developers_website_development, Res.drawable.nick),
    DeveloperCredit("NieR4ever", "https://github.com/NieR4ever", Lang.settings_developers_contributor, Res.drawable.nier4ever),
    DeveloperCredit("NihilDigit", "https://github.com/NihilDigit", Lang.settings_developers_contributor, Res.drawable.nihildigit),
    DeveloperCredit("rdlwicked", "https://github.com/rdlwicked", Lang.settings_developers_ml_research, Res.drawable.rdlwicked),
    DeveloperCredit("Sanlorng", "https://github.com/Sanlorng", Lang.settings_developers_contributor, Res.drawable.sanlorng),
    DeveloperCredit("WoLeo-Z", "https://github.com/WoLeo-Z", Lang.settings_developers_contributor, Res.drawable.woleoz),
    DeveloperCredit("目棃", "https://github.com/BTMuli", Lang.settings_developers_website_development, Res.drawable.btmuli),
    DeveloperCredit("OpenAni", "https://github.com/open-ani", Lang.settings_developers_organization, FoundationRes.drawable.a),
)
