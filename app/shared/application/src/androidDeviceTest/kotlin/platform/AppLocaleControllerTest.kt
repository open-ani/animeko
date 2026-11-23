/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.core.app.LocaleManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_app_language
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Exercises the production activity integration and Compose resources on both sides of API 33. */
class AppLocaleControllerTest {
    @AfterTest
    fun resetLanguage() {
        withActivity { select(it, null) }
    }

    @Test
    fun languageSelectionSurvivesRecreationAndFreshLaunch() {
        withActivity { scenario ->
            select(scenario, "zh-CN")
            assertLanguage(scenario, "zh-CN", "语言")
            scenario.onActivity { activity ->
                if (Build.VERSION.SDK_INT < 33) {
                    // Android refreshes the process default when dispatching a system configuration change.
                    LocaleList.setDefault(
                        LocaleList.forLanguageTags(LocaleManagerCompat.getSystemLocales(activity).toLanguageTags()),
                    )
                    activity.onConfigurationChanged(Configuration(activity.resources.configuration))
                    assertEquals("zh-CN", Locale.getDefault().toLanguageTag())
                }
            }
            select(scenario, "en")
            assertLanguage(scenario, "en", "Language")
            scenario.recreate()
            assertLanguage(scenario, "en", "Language")
        }
        withActivity { scenario ->
            try {
                assertLanguage(scenario, "en", "Language")
                select(scenario, "zh-TW")
                assertLanguage(scenario, "zh-TW", "語言")
                select(scenario, null)
                await(scenario) { activity ->
                    val systemLocales = LocaleManagerCompat.getSystemLocales(activity)
                    createAppLocaleController(activity).languageTag == null &&
                        activity.resources.configuration.locales[0] == systemLocales[0]
                }
                scenario.recreate()
                scenario.onActivity { assertNull(createAppLocaleController(it).languageTag) }
            } finally {
                select(scenario, null)
            }
        }
    }

    @Test
    fun selectingCurrentLanguageKeepsActivity() {
        withActivity { scenario ->
            try {
                select(scenario, "en")
                assertLanguage(scenario, "en", "Language")
                lateinit var original: LocaleTestActivity
                scenario.onActivity { original = it }
                select(scenario, "en")
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { assertSame(original, it) }
            } finally {
                select(scenario, null)
            }
        }
    }

    private fun select(scenario: ActivityScenario<LocaleTestActivity>, tag: String?) {
        scenario.onActivity { createAppLocaleController(it).setLanguageTag(it, tag) }
    }

    private fun assertLanguage(scenario: ActivityScenario<LocaleTestActivity>, tag: String, text: String) {
        await(scenario) { activity ->
            createAppLocaleController(activity).languageTag == tag &&
                activity.resources.configuration.locales[0].toLanguageTag() == tag &&
                activity.renderedLanguage == text && Locale.getDefault().toLanguageTag() == tag
        }
        scenario.onActivity { assertEquals(tag, createAppLocaleController(it).languageTag) }
    }

    private fun await(scenario: ActivityScenario<LocaleTestActivity>, predicate: (LocaleTestActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var matched = false
        var actual = ""
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity {
                matched = predicate(it)
                actual = "selection=${createAppLocaleController(it).languageTag}, " +
                    "locales=${it.resources.configuration.locales}, text=${it.renderedLanguage}"
            }
            if (!matched) SystemClock.sleep(50)
        }
        assertTrue(matched, "The activity configuration, selection and rendered translation must agree: $actual")
    }

    private fun withActivity(block: (ActivityScenario<LocaleTestActivity>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().context
        ActivityScenario.launch<LocaleTestActivity>(Intent(context, LocaleTestActivity::class.java)).use(block)
    }
}

class LocaleTestActivity : AniComponentActivity() {
    var renderedLanguage: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val text = stringResource(Lang.settings_app_language)
            SideEffect { renderedLanguage = text }
            Text(text)
        }
    }
}
