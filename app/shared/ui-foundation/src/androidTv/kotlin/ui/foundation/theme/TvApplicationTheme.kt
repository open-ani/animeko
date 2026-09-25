/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Applies TV appearance to Compose resources and native input widgets without replacing the page. */
@Composable
fun TvApplicationTheme(seedColor: Color, languageTag: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val systemLocales = Resources.getSystem().configuration.locales
    val locales by produceState<LocaleList?>(null, languageTag, systemLocales) {
        val target = languageTag?.let { LocaleList(Locale.forLanguageTag(it)) } ?: systemLocales
        LocaleList.setDefault(target)
        value = target
    }
    val appliedLocales = locales ?: return
    val configuration = remember(systemConfiguration, appliedLocales) {
        Configuration(systemConfiguration).apply { setLocales(appliedLocales) }
    }
    val localizedContext = remember(context, configuration) {
        ContextThemeWrapper(context, context.theme).apply { applyOverrideConfiguration(configuration) }
    }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalContext provides localizedContext,
    ) {
        AniTvTheme(seedColor, content)
    }
}
