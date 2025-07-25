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


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 
 *
 * @param producer 
 * @param mangaka 
 * @param artist 
 * @param seiyu 
 * @param writer 
 * @param illustrator 
 * @param actor 
 */
@Serializable

data class BangumiPersonRevisionProfession(

    @SerialName(value = "producer") val producer: kotlin.String? = null,

    @SerialName(value = "mangaka") val mangaka: kotlin.String? = null,

    @SerialName(value = "artist") val artist: kotlin.String? = null,

    @SerialName(value = "seiyu") val seiyu: kotlin.String? = null,

    @SerialName(value = "writer") val writer: kotlin.String? = null,

    @SerialName(value = "illustrator") val illustrator: kotlin.String? = null,

    @SerialName(value = "actor") val actor: kotlin.String? = null

) {


}

