/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

/**
 * 一次「粗略定位 → IANA 时区」的结果.
 */
sealed interface LocationTimeZoneResult {
    /**
     * 定位与匹配都成功. [zoneId] 是 IANA 时区 ID, 可直接写入时区偏好.
     */
    data class Success(val zoneId: String) : LocationTimeZoneResult

    /**
     * 应用没有定位权限. 调用方需先获得定位授权再重试.
     */
    data object PermissionDenied : LocationTimeZoneResult

    /**
     * 没有取到坐标, [reason] 说明失败原因.
     */
    data class Failed(val reason: LocationFailureReason) : LocationTimeZoneResult

    /**
     * 当前平台不提供定位能力.
     */
    data object UnsupportedPlatform : LocationTimeZoneResult

    /**
     * 取到了坐标, 但无法匹配到 IANA 时区.
     */
    data object ZoneNotFound : LocationTimeZoneResult
}

/**
 * [LocationTimeZoneResult.Failed] 的失败原因.
 */
enum class LocationFailureReason {
    /**
     * 设备没有可用的定位能力: 没有定位服务, 或所有定位方式都被关闭.
     */
    NoProvider,

    /**
     * 在超时上限内没有取到坐标.
     */
    Timeout,

    /**
     * 系统定位服务在执行中出错.
     */
    SystemError,
}

/**
 * 由设备粗略定位得到 IANA 时区 ID.
 *
 * 只发起一次定位请求, 返回后不再监听位置. 实现不申请权限: Android 上调用方必须保证
 * `ACCESS_COARSE_LOCATION` 已授权, 否则调用返回 [LocationTimeZoneResult.PermissionDenied].
 *
 * 实例由 Koin 提供: `KoinPlatform.getKoin().get<LocationTimeZoneProvider>()`.
 */
interface LocationTimeZoneProvider {
    /**
     * 定位并匹配时区. 所有失败都体现在返回值中, 不会抛出异常.
     */
    suspend fun locateTimeZone(): LocationTimeZoneResult
}

/**
 * 平台没有定位能力时的实现, 恒返回 [LocationTimeZoneResult.UnsupportedPlatform].
 */
object UnsupportedLocationTimeZoneProvider : LocationTimeZoneProvider {
    override suspend fun locateTimeZone(): LocationTimeZoneResult = LocationTimeZoneResult.UnsupportedPlatform
}

expect fun createLocationTimeZoneProvider(context: ContextMP): LocationTimeZoneProvider
