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
 * 设置页定位框可直接绑定的状态, 由 [toUiState] 从 [LocationTimeZoneResult] 映射得到.
 *
 * 只描述终态; 请求进行中的状态由调用方维护.
 */
sealed interface LocationTimeZoneUiState {
    /**
     * 定位成功. [zoneId] 是 IANA 时区 ID.
     */
    data class Success(val zoneId: String) : LocationTimeZoneUiState

    /**
     * 没有取到时区. [kind] 决定提示内容, [canRetry] 决定是否提供重试入口.
     */
    data class Failure(
        val kind: LocationFailureKind,
        val canRetry: Boolean,
    ) : LocationTimeZoneUiState
}

/**
 * [LocationTimeZoneUiState.Failure] 的种类.
 */
enum class LocationFailureKind {
    /**
     * 没有定位权限.
     */
    PermissionDenied,

    /**
     * 定位失败: 设备没有定位能力, 超时未取到坐标, 或系统定位服务出错.
     */
    LocateFailed,

    /**
     * 当前平台不提供定位.
     */
    UnsupportedPlatform,

    /**
     * 取到了坐标但匹配不到时区.
     */
    ZoneNotFound,
}

/**
 * 把定位结果映射为界面状态.
 *
 * [LocationFailureKind.LocateFailed] 能否重试取决于 [LocationTimeZoneResult.Failed.reason]:
 * 设备没有定位能力时重试不会改变结果.
 */
fun LocationTimeZoneResult.toUiState(): LocationTimeZoneUiState = when (this) {
    is LocationTimeZoneResult.Success -> LocationTimeZoneUiState.Success(zoneId)
    LocationTimeZoneResult.PermissionDenied ->
        LocationTimeZoneUiState.Failure(LocationFailureKind.PermissionDenied, canRetry = true)

    LocationTimeZoneResult.UnsupportedPlatform ->
        LocationTimeZoneUiState.Failure(LocationFailureKind.UnsupportedPlatform, canRetry = false)

    LocationTimeZoneResult.ZoneNotFound ->
        LocationTimeZoneUiState.Failure(LocationFailureKind.ZoneNotFound, canRetry = false)

    is LocationTimeZoneResult.Failed -> LocationTimeZoneUiState.Failure(
        kind = LocationFailureKind.LocateFailed,
        canRetry = reason != LocationFailureReason.NoProvider,
    )
}
