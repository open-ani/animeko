/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport",
)

package me.him188.ani.datasources.bangumi.next.models

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 
 *
 * @param airdate
 * @param comment
 * @param disc
 * @param duration
 * @param id
 * @param name
 * @param nameCN 
 * @param sort 
 * @param subjectID 
 * @param type 
 * @param desc 
 * @param status 
 * @param subject 
 */
@Serializable

data class BangumiNextEpisode(

    @SerialName(value = "airdate") @Required val airdate: kotlin.String,

    @SerialName(value = "comment") @Required val comment: kotlin.Int,

    @SerialName(value = "disc") @Required val disc: kotlin.Int,

    @SerialName(value = "duration") @Required val duration: kotlin.String,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCN") @Required val nameCN: kotlin.String,

    @SerialName(value = "sort") @Required val sort: @Serializable(me.him188.ani.utils.serialization.BigNumAsDoubleStringSerializer::class) me.him188.ani.utils.serialization.BigNum,

    @SerialName(value = "subjectID") @Required val subjectID: kotlin.Int,

    @SerialName(value = "type") @Required val type: BangumiNextEpisodeType,

    @SerialName(value = "desc") val desc: kotlin.String? = null,

    @SerialName(value = "status") val status: BangumiNextEpisodeCollectionStatus? = null,

    @SerialName(value = "subject") val subject: BangumiNextSlimSubject? = null

) {


}

