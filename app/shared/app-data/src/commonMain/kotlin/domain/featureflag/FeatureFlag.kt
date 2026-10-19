/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.featureflag

import kotlinx.serialization.Serializable

/**
 * 客户端认识的功能开关. 服务端在后台按开关名配置分发策略 (全量、按用户固定分桶、每次请求随机、客户端随机、仅名单, 以及平台与版本条件),
 * 客户端启动时拉取一次全部开关的值 (见 [FeatureFlagManager]).
 *
 * [key] 必须与后台配置的开关名一致. 服务端没有配置这个开关, 或者还没有拉取到值时, 用 [default].
 * 新增开关时在这里加一项, 并在后台创建同名开关; 后台没有创建时客户端按 [default] 行为.
 */
enum class FeatureFlag(
    val key: String,
    val default: Boolean,
) {
    /**
     * 播放加载流程埋点 `play_flow` (见 `docs/contributing/code/analytics-play-flow.md`).
     * 关闭时播放页仍然记录到调试悬浮窗, 但不向 Analytics 发送 `play_flow` 事件. 每次播放会话创建记录器时读一次, 所以服务端用
     * "客户端随机" 策略即可按播放会话抽样以控制事件量.
     */
    PLAY_FLOW_ANALYTICS("play_flow_analytics", default = true),
    ;

    companion object {
        fun byKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 服务端为一次请求求出的一个开关的值.
 *
 * [enabled] 为 `false` 是确定的关闭 (总开关停用, 关闭名单, 平台 / 版本条件不满足, 未命中); `true` 且 [threshold] 为 `null`
 * 是确定的开启; `true` 且有 [threshold] 是 "客户端随机" 策略: 服务端不抽签, 由 [FeatureFlagManager] 在每次读取时按 [threshold]
 * 独立抽一次. 业务代码不直接看这个类型, 只通过 [FeatureFlagManager.isEnabled] 或 [FeatureFlagManager.flow] 拿布尔值.
 */
@Serializable
data class FeatureFlagValue(
    val enabled: Boolean,
    /** 客户端随机策略下启用的概率, 0..1; 其他策略为 `null` */
    val threshold: Float? = null,
) {
    companion object {
        val On = FeatureFlagValue(enabled = true)
        val Off = FeatureFlagValue(enabled = false)
    }
}

/**
 * 上次从服务端拉取到的开关值, 持久化后供下次启动在拉取完成前使用.
 *
 * 不放进设置: 设置会随备份恢复到其他设备, 而固定分桶的结果属于这台设备或这个用户.
 */
@Serializable
data class FeatureFlagSnapshot(
    /**
     * 服务端返回的全部开关, 按开关名. 可能包含本版本客户端不认识的开关.
     */
    val flags: Map<String, FeatureFlagValue> = emptyMap(),
    /**
     * 拉取时登录的用户 id, 未登录时为 `null`. 只用于排查问题.
     */
    val userId: String? = null,
    val fetchedAtMillis: Long? = null,
) {
    companion object {
        val Empty = FeatureFlagSnapshot()
    }
}
