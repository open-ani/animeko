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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 客户端认识的功能开关. 服务端在后台按开关名配置取值列表 (每个取值有比例、名单和一个属性表 properties) 与分发策略
 * (按用户固定分桶、每次请求随机, 以及名单、平台与版本条件), 客户端按开关名请求 `GET /v2/feature-flags/{key}`,
 * 拿到的是本次请求命中的取值的属性表 (见 [FeatureFlagManager]).
 *
 * 没有命中任何取值 (总开关关闭, 关闭名单, 平台 / 版本不符, 没分到) 时服务端返回 `{}`; 服务端没有这个开关 (404),
 * 或还没有拉取到时客户端也用 `{}`. 所以每个开关读属性时都要给默认值, 默认值就是 "关闭" 时的行为.
 *
 * [key] 必须与后台配置的开关名一致. 新增开关时在这里加一项, 在 KDoc 里写清读哪些属性和默认值, 并在后台创建同名开关.
 */
enum class FeatureFlag(val key: String) {
    /**
     * 播放加载流程埋点 `play_flow` (见 `docs/contributing/code/analytics-play-flow.md`).
     *
     * 读 `threshold: Double`, 默认 `0`: 进入播放页时抽一个 `[0, 1)` 的随机数, 小于 `threshold` 的播放会话才向 Analytics 发送
     * `play_flow` 事件, 整个播放会话内一致 (见 `PlayFlowRecorder.shouldReport`); 例如 `{"threshold": 0.3}` 即 30% 的播放会话上报.
     * 其他情况 (没有配置、总开关关闭、没抽中) 都不发送, 但播放页仍然记录到调试悬浮窗. 每次播放会话创建记录器时
     * [FeatureFlagManager.consume] 一次; 抽样由客户端按 `threshold` 完成, 服务端的取值比例与灰度配 100% 即可.
     */
    PLAY_FLOW_REPORT("play_flow_report"),
}

/**
 * 一个开关的配置: 服务端为本次请求命中的取值配置的属性表, 由业务代码按自己认识的键读取.
 *
 * 读取都是宽容的: 键不存在或类型不对都返回调用方给的默认值, 后台配错不会让客户端崩溃. 嵌套的对象和数组不当作标量,
 * 需要时用 [get] 自己解析.
 */
class FeatureFlagConfig(val properties: JsonObject) {
    /** 原始的 JSON 值; 没有这个键时 `null` */
    operator fun get(key: String): JsonElement? = properties[key]

    fun has(key: String): Boolean = key in properties

    fun boolean(key: String, default: Boolean): Boolean = literal(key)?.booleanOrNull ?: default

    fun string(key: String, default: String): String = primitive(key)?.takeIf { it.isString }?.content ?: default

    fun int(key: String, default: Int): Int = literal(key)?.intOrNull ?: default

    fun long(key: String, default: Long): Long = literal(key)?.longOrNull ?: default

    fun double(key: String, default: Double): Double = literal(key)?.doubleOrNull ?: default

    private fun primitive(key: String): JsonPrimitive? = properties[key] as? JsonPrimitive

    /** 不带引号的标量 (数字、布尔、null): `"7"` 这样的字符串不当作数字 */
    private fun literal(key: String): JsonPrimitive? = primitive(key)?.takeIf { !it.isString }

    override fun equals(other: Any?): Boolean = other is FeatureFlagConfig && other.properties == properties
    override fun hashCode(): Int = properties.hashCode()
    override fun toString(): String = "FeatureFlagConfig($properties)"

    companion object {
        /** 没有配置: 所有属性都取默认值 */
        val Empty = FeatureFlagConfig(JsonObject(emptyMap()))
    }
}

/**
 * 上次从服务端拉取到的各开关的配置, 持久化后供下次启动在拉取完成前使用.
 *
 * 不放进设置: 设置会随备份恢复到其他设备, 而固定分桶的结果属于这台设备或这个用户.
 */
@Serializable
data class FeatureFlagSnapshot(
    /**
     * 按开关名. 只有本版本认识的开关 (请求是按 [FeatureFlag] 逐个发的).
     */
    val flags: Map<String, JsonObject> = emptyMap(),
) {
    companion object {
        val Empty = FeatureFlagSnapshot()
    }
}
