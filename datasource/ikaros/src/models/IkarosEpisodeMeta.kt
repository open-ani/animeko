package me.him188.ani.datasources.ikaros.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IkarosEpisodeMeta(
    val id: Long,
    @SerialName("subject_id") val subjectId: Long,
    val name: String,
    @SerialName("name_cn") val nameCn: String?,
    val description: String,
    @SerialName("air_time") val airTime: String?,
    val sequence: Int,
    val group: IkarosEpisodeGroup,
)