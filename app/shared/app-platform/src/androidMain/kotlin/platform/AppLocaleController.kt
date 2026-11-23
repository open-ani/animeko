/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.app.Activity
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.MainThread
import androidx.core.app.LocaleManagerCompat

/**
 * Android app language selection. A null language tag follows the system language.
 *
 * Hosts must call [wrapBaseContext] from Activity.attachBaseContext and [applyDefaultLocale]
 * before creating their UI and after configuration changes.
 * [setLanguageTag] persists the selection and recreates the host when required by the platform.
 * No AppCompat activity or delegate is required.
 */
interface AppLocaleController {
    val languageTag: String?

    @MainThread
    fun setLanguageTag(activity: Activity, languageTag: String?)

    fun wrapBaseContext(context: Context): Context

    /** Keeps Compose resources and locale-sensitive formatting aligned with the host configuration. */
    fun applyDefaultLocale(context: Context)
}

fun createAppLocaleController(context: Context): AppLocaleController = AndroidAppLocaleController(context)

private class AndroidAppLocaleController(context: Context) : AppLocaleController {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("app_locales", Context.MODE_PRIVATE)

    init {
        if (Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("migrated_to_system", false)) {
            val manager = appContext.getSystemService(LocaleManager::class.java)
            // Preserve the pre-Android-13 selection across an OS upgrade, unless the system has one.
            val savedTags = preferences.getString("language_tags", null)
            if (manager.applicationLocales.isEmpty && !savedTags.isNullOrEmpty()) {
                manager.applicationLocales = LocaleList.forLanguageTags(savedTags)
            }
            preferences.edit().putBoolean("migrated_to_system", true).apply()
        }
    }

    private val languageTags: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            appContext.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            // LocaleManagerCompat can read selections persisted by released AppCompat-based versions.
            preferences.getString("language_tags", null)
                ?: LocaleManagerCompat.getApplicationLocales(appContext).toLanguageTags()
        }

    override val languageTag: String?
        get() = languageTags.substringBefore(',').takeIf { it.isNotEmpty() }

    override fun setLanguageTag(activity: Activity, languageTag: String?) {
        val tags = languageTag.orEmpty()
        if (languageTags == tags) return
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        } else {
            preferences.edit().putString("language_tags", tags).apply()
            activity.recreate()
        }
    }

    override fun applyDefaultLocale(context: Context) {
        if (Build.VERSION.SDK_INT < 33) {
            LocaleList.setDefault(context.resources.configuration.locales)
        }
    }

    override fun wrapBaseContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tags = languageTags
        if (tags.isEmpty()) return context
        val systemTags = LocaleManagerCompat.getSystemLocales(context).toLanguageTags()
        val locales = LocaleList.forLanguageTags(listOf(tags, systemTags).filter { it.isNotEmpty() }.joinToString(","))
        // Only override locale fields so density, font scale and other configuration changes propagate.
        val override = Configuration().apply { setLocales(locales) }
        return context.createConfigurationContext(override)
    }
}
