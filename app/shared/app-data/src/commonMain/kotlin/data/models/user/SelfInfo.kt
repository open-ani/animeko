package me.him188.ani.app.data.models.user

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * @since 5.0
 */
@Serializable
data class SelfInfo(
    val id: Uuid,
    val nickname: String,
    val email: String?,
    val hasPassword: Boolean,
    val avatarUrl: String?,
)
