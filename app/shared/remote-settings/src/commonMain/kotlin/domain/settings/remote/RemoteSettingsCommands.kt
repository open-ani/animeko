/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.settings.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.datasources.api.source.MediaSourceConfig

@Serializable
sealed interface MediaSourceCommand {
    @Serializable
    @SerialName("add")
    data class Add(val source: MediaSourceSave) : MediaSourceCommand

    @Serializable
    @SerialName("edit")
    data class Edit(val instanceId: String, val config: MediaSourceConfig) : MediaSourceCommand

    @Serializable
    @SerialName("delete")
    data class Delete(val ids: List<String>) : MediaSourceCommand

    @Serializable
    @SerialName("reorder")
    data class Reorder(val ids: List<String>) : MediaSourceCommand

    @Serializable
    @SerialName("enable")
    data class Enable(val ids: List<String>, val enabled: Boolean) : MediaSourceCommand

    @Serializable @SerialName("import") data class Import(val text: String) : MediaSourceCommand

    @Serializable
    @SerialName("export")
    data class Export(val ids: List<String>) : MediaSourceCommand

    @Serializable
    @SerialName("subscriptionAdd")
    data class SubscriptionAdd(val subscription: MediaSourceSubscription) : MediaSourceCommand

    @Serializable
    @SerialName("subscriptionEdit")
    data class SubscriptionEdit(val subscription: MediaSourceSubscription) : MediaSourceCommand

    @Serializable
    @SerialName("subscriptionDelete")
    data class SubscriptionDelete(val id: String) : MediaSourceCommand

    @Serializable
    @SerialName("subscriptionRefresh")
    data class SubscriptionRefresh(val id: String? = null) : MediaSourceCommand
}

@Serializable data class ReplaceDanmakuFilters(val filters: List<DanmakuRegexFilter>)

@Serializable
sealed interface RemoteBackupCommand {
    @Serializable @SerialName("export") data object Export : RemoteBackupCommand

    @Serializable
    @SerialName("preview")
    data class Preview(val backup: RemoteSettingsBackup) : RemoteBackupCommand

    @Serializable @SerialName("apply") data class Apply(val planId: String) : RemoteBackupCommand
}

@Serializable
data class RemoteSettingsBackup(
    val schemaVersion: Int = 1,
    val preferences: List<RemotePreference>,
    val mediaSources: List<MediaSourceSave>,
    val subscriptions: List<MediaSourceSubscription>,
    val danmakuFilters: List<DanmakuRegexFilter>,
)

@Serializable
data class RemoteBackupPreview(
    val planId: String,
    val preferenceCount: Int,
    val sourceCount: Int,
    val subscriptionCount: Int,
    val filterCount: Int,
)

@Serializable
data class RemoteBackupResult(val applied: List<String>, val failed: Map<String, String>)

class RemoteSettingsException(val code: String, message: String) : Exception(message)

fun checkRemote(condition: Boolean, code: String = "INVALID_ARGUMENT", message: String) {
    if (!condition) throw RemoteSettingsException(code, message)
}
