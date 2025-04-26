/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.analytics

import me.him188.ani.utils.platform.currentPlatform
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmInline

/**
 * Global instance of [IAnalytics].
 *
 * 如果用户愿意分享数据，此实例将为 [AnalyticsImpl]. 否则为 no-op.
 */
val Analytics: IAnalytics get() = AnalyticsHolder.getInstance()

/**
 * Defines common interface for analytics
 */
interface IAnalytics {
    fun recordEvent(
        event: AnalyticsEvent,
        properties: Map<String, Any?> = emptyMap(),
    )

    fun onAppStart() {}
}

inline fun IAnalytics.recordEvent(
    event: AnalyticsEvent,
    buildProperties: MutableMap<String, Any?>.() -> Unit = {},
) {
    recordEvent(event, buildMap(buildProperties))
}

@JvmInline
value class AnalyticsEvent(val event: String) {
    companion object {
        val Screen = AnalyticsEvent($$"$screen")
        val AppStart = Screen // compatibility

        val OnboardingStart = AnalyticsEvent("onboarding_start")
        val OnboardingNetworkEnter = AnalyticsEvent("onboarding_network_enter")
        val OnboardingLoginEnter = AnalyticsEvent("onboarding_login_enter")
        val OnboardingDone = AnalyticsEvent("onboarding_done")

        val NetworkCheckFailed = AnalyticsEvent("network_check_failed")
        val LoginClick = AnalyticsEvent("login_click")
        val LoginBangumiSuccess = AnalyticsEvent("login_bangumi_success")
        val LoginSkipClick = AnalyticsEvent("login_skip_click")

        val EpisodeEnter = AnalyticsEvent("episode_enter")
        val EpisodeSwitch = AnalyticsEvent("episode_switch")

        val CacheCreate = AnalyticsEvent("cache_create")
    }
}


///////////////////////////////////////////////////////////////////////////
// implementation
///////////////////////////////////////////////////////////////////////////

data class AnalyticsConfig(
    val appVersion: String,
    val debugLogging: Boolean,
) {
    companion object
}

/**
 * Common implementation of [IAnalytics]
 */
abstract class CommonAnalyticsImpl(
    protected val config: AnalyticsConfig,
) : IAnalytics {
    protected val intrinsicProperties: Map<String, Any> = buildMap {
        val platform = currentPlatform()
        put("os", platform.name)
        put("arch", platform.arch.name)
        put("\$app_version", config.appVersion)
    }

    final override fun recordEvent(event: AnalyticsEvent, properties: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        recordEventImpl(
            event,
            properties.filterValues { it != null } as Map<String, Any>,
        )
    }

    protected abstract fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any> = emptyMap())
}

object AnalyticsHolder {
    @Volatile
    private var instance: IAnalytics = NoopAnalytics

    internal fun getInstance(): IAnalytics = instance

    /**
     * 启动 APP 时调用.
     */
    fun init(instance: IAnalytics) {
        check(this.instance === NoopAnalytics) { "Analytics instance is already initialized" }
        this.instance = instance
    }
}

private object NoopAnalytics : IAnalytics {
    override fun recordEvent(event: AnalyticsEvent, properties: Map<String, Any?>) {
    }

    override fun onAppStart() {
    }
}
