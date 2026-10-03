/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.lang

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import org.jetbrains.compose.resources.stringResource

/** Language choices use their native names so they remain recognizable in every app language. */
@Composable
fun renderLocale(locale: Locale?): String {
    if (locale == null) return stringResource(Lang.settings_app_language_system)
    return when (locale.language) {
        "en", "eng" -> "English"
        "zh", "chi", "zho" -> when (locale.region) {
            "CN" -> "简体中文"
            "HK" -> "繁體中文(香港)"
            "TW" -> "正體中文"
            else -> "繁體中文"
        }
        else -> "${locale.language}-${locale.region}"
    }
}
