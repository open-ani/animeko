/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.platform.currentSystemTimeZone

/**
 * 全局时区偏好及其解析结果.
 *
 * 用户选择的时区是全 App 绝对日期时间显示的唯一来源; 偏好以 IANA 时区 ID 保存.
 */
interface TimeZoneProvider {
    /**
     * 用户偏好, IANA 时区 ID. `null` 表示跟随系统时区.
     */
    val preference: StateFlow<String?>

    /**
     * 解析后的时区, 恒非空: 偏好为 `null` 或无法解析时取设备系统时区.
     */
    val timeZone: StateFlow<TimeZone>

    /**
     * 保存偏好. [zoneId] 为 IANA 时区 ID; `null` 表示跟随系统时区.
     */
    suspend fun setTimeZoneId(zoneId: String?)

    /**
     * 重新读取设备系统时区, 用于设备时区变化之后 (例如 App 回到前台时).
     */
    fun refresh()
}

/**
 * 偏好存于 [SettingsRepository.uiSettings], 系统时区由 [systemZone] 提供.
 */
class DefaultTimeZoneProvider(
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    private val systemZone: () -> TimeZone = ::currentSystemTimeZone,
) : TimeZoneProvider {
    /**
     * 最近一次读取到的系统时区.
     */
    private val system: MutableStateFlow<TimeZone> = MutableStateFlow(systemZone())

    override val preference: StateFlow<String?> = settingsRepository.uiSettings.flow
        .map { it.timeZoneId }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val timeZone: StateFlow<TimeZone> = combine(preference, system) { zoneId, systemZone ->
        zoneId.toTimeZoneOrNull() ?: systemZone
    }.stateIn(scope, SharingStarted.Eagerly, system.value)

    override suspend fun setTimeZoneId(zoneId: String?) {
        settingsRepository.uiSettings.update { copy(timeZoneId = zoneId) }
    }

    override fun refresh() {
        system.value = systemZone()
    }
}

/**
 * 把 IANA 时区 ID 解析为 [TimeZone], 无法解析时返回 `null`.
 */
fun String?.toTimeZoneOrNull(): TimeZone? =
    this?.takeIf { it.isNotBlank() }?.let { zoneId -> runCatching { TimeZone.of(zoneId) }.getOrNull() }
