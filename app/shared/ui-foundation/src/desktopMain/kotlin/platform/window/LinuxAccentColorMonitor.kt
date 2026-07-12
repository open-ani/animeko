/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.him188.ani.utils.logging.logger
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

/**
 * Monitors the session-wide accent color exposed by XDG Desktop Portal.
 *
 * Unlike Windows accent color notifications, portal settings are not tied to a window, so all Animeko windows share
 * this single D-Bus listener and [accentColor] state.
 */
internal object LinuxAccentColorMonitor {
    private const val PortalBusName = "org.freedesktop.portal.Desktop"
    private const val PortalObjectPath = "/org/freedesktop/portal/desktop"
    private const val AppearanceNamespace = "org.freedesktop.appearance"
    private const val AccentColorKey = "accent-color"

    private val logger = logger<LinuxAccentColorMonitor>()
    private val mutableAccentColor = MutableStateFlow(Color.Unspecified)

    val accentColor: StateFlow<Color> = mutableAccentColor.asStateFlow()

    init {
        Thread(::listenForAccentColor, "Linux-accent-color").apply {
            isDaemon = true
            start()
        }
    }

    private fun listenForAccentColor() {
        runCatching {
            withDbusClassLoader {
                val connection = DBusConnectionBuilder.forSessionBus().build()
                val settings = connection.getRemoteObject(
                    PortalBusName,
                    PortalObjectPath,
                    PortalSettings::class.java,
                )
                connection.addSigHandler(PortalSettings.SettingChanged::class.java, settings) { signal ->
                    if (signal.namespace == AppearanceNamespace && signal.key == AccentColorKey) {
                        updateAccentColor(signal.value)
                    }
                }
                updateAccentColor(settings.readAccentColor())
            }
        }.onFailure {
            logger.debug("Failed to read Linux accent color from XDG Desktop Portal", it)
        }
    }

    private fun PortalSettings.readAccentColor(): Variant<*> {
        return runCatching { ReadOne(AppearanceNamespace, AccentColorKey) }
            .getOrElse { Read(AppearanceNamespace, AccentColorKey) }
    }

    private fun updateAccentColor(value: Variant<*>) {
        mutableAccentColor.value = value.toComposeColor() ?: Color.Unspecified
    }

    private inline fun <T> withDbusClassLoader(block: () -> T): T {
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = DBusConnectionBuilder::class.java.classLoader
        return try {
            block()
        } finally {
            currentThread.contextClassLoader = originalClassLoader
        }
    }
}

internal fun Variant<*>.toComposeColor(): Color? {
    val channels = when (val value = unwrapVariant()) {
        is PortalAccentColor -> listOf(value.red, value.green, value.blue)
        is Array<*> -> value.mapNotNull { (it as? Number)?.toDouble() }
        is List<*> -> value.mapNotNull { (it as? Number)?.toDouble() }
        else -> return null
    }
    if (channels.size != 3 || channels.any { it !in 0.0..1.0 }) {
        return null
    }
    return Color(channels[0].toFloat(), channels[1].toFloat(), channels[2].toFloat())
}

private tailrec fun Variant<*>.unwrapVariant(): Any = when (val unwrapped = value) {
    is Variant<*> -> unwrapped.unwrapVariant()
    else -> unwrapped
}

internal class PortalAccentColor(
    @field:Position(0) val red: Double,
    @field:Position(1) val green: Double,
    @field:Position(2) val blue: Double,
) : Struct()

@DBusInterfaceName("org.freedesktop.portal.Settings")
private interface PortalSettings : DBusInterface {
    @Suppress("FunctionName")
    fun Read(namespace: String, key: String): Variant<Variant<PortalAccentColor>>

    @Suppress("FunctionName")
    fun ReadOne(namespace: String, key: String): Variant<PortalAccentColor>

    class SettingChanged(
        path: String,
        val namespace: String,
        val key: String,
        val value: Variant<*>,
    ) : DBusSignal(path, namespace, key, value)
}
