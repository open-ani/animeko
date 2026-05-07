/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.him188.ani.app.data.models.preference.PikPakConfig.Companion.SLOT_QUEUE_MAX_NUMERIC
import me.him188.ani.app.data.models.preference.PikPakConfig.Companion.SLOT_QUEUE_UNLIMITED
import me.him188.ani.utils.io.obscure
import me.him188.ani.utils.io.tryReveal

/**
 * User-configurable settings for the PikPak offline-download backend.
 *
 * The engine runs a server-side slot cache: completed offline tasks stay in a
 * well-known working folder on the user's PikPak drive, keyed by source
 * bucket, so replays of the same magnet are served straight from the cache.
 * Old buckets are evicted to honor [slotQueueLength] — they are *not* deleted
 * immediately after each resolve. See `PikPakOfflineDownloadEngine` for the
 * full eviction policy.
 *
 * [refreshToken] is written by the engine after a successful signin/refresh
 * (not user-editable). It lets the next app launch skip the rate-limited
 * `/v1/auth/signin` endpoint and go straight to a cheap refresh.
 *
 * [password] is accepted from the settings UI to bootstrap the first signin
 * and stays on disk so the engine can silently re-signin if the stored
 * refresh token gets revoked server-side (Test / playback would otherwise
 * fail until the user re-typed the password). To keep credentials out of
 * casual reads of the on-disk JSON, both [password] and [refreshToken] are
 * persisted via [ObscuredStringSerializer] — AES-CTR with a hardcoded key,
 * the same approach `rclone obscure` takes. This blocks eyedropping; it is
 * not real encryption (the key is in the binary). The settings UI also
 * never echoes [password] back.
 */
@Serializable
data class PikPakConfig(
    val enabled: Boolean = false,
    val username: String = "",
    @Serializable(with = ObscuredStringSerializer::class)
    val password: String = "",
    @Serializable(with = ObscuredStringSerializer::class)
    val refreshToken: String = "",
    /**
     * How many distinct source buckets the engine keeps cached in its
     * working folder ("Animeko-Playing"). Real numeric values 1..13 are
     * bucket caps; the UI also offers a final "unlimited" stop (stored as
     * [SLOT_QUEUE_UNLIMITED]) that disables eviction entirely.
     */
    val slotQueueLength: Int = 1,
) {
    override fun toString(): String {
        return "PikPakConfig(enabled=$enabled, username=$username, password.hash=${password.hashCode()}, " +
                "refreshToken.hash=${refreshToken.let { if (it.isNotEmpty()) it.hashCode() else "" }}, " +
                "slotQueueLength=$slotQueueLength)"
    }

    companion object {
        val Default = PikPakConfig()

        /** Last numeric step on the slider. */
        const val SLOT_QUEUE_MAX_NUMERIC: Int = 13

        /**
         * One step past [SLOT_QUEUE_MAX_NUMERIC]: the dedicated "no eviction"
         * stop. Any value ≥ this is treated as unlimited by the engine.
         */
        const val SLOT_QUEUE_UNLIMITED: Int = SLOT_QUEUE_MAX_NUMERIC + 1
    }
}

/**
 * Encodes [String] values via [obscure] / [tryReveal] so the JSON written to
 * DataStore contains an `ob1:`-tagged AES-CTR payload instead of plaintext.
 *
 * Decoding falls back to treating the raw value as plaintext when the magic
 * prefix is absent — that path covers the migration from PR #2978's V2, which
 * persisted [PikPakConfig.password] and [PikPakConfig.refreshToken] in the
 * clear. The next [SerializablePreference.update] then writes the obscured
 * form back automatically.
 */
internal object ObscuredStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ObscuredString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(obscure(value))
    }

    override fun deserialize(decoder: Decoder): String {
        val raw = decoder.decodeString()
        return tryReveal(raw) ?: raw
    }
}
