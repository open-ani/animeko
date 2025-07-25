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

package me.him188.ani.datasources.bangumi.models

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 
 *
 * @param episode 
 * @param type 
 */
@Serializable

data class BangumiUserEpisodeCollection(

    @SerialName(value = "episode") @Required val episode: BangumiEpisode,

    @SerialName(value = "type") @Required val type: BangumiEpisodeCollectionType

) {


}

