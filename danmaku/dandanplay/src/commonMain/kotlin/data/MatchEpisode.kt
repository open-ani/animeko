package me.him188.ani.danmaku.dandanplay.data

import kotlinx.serialization.Serializable

/*
SearchEpisodesResponse {
hasMore (boolean): 是否有更多未显示的搜索结果。当返回的搜索结果过多时此值为true ,
animes (Array[SearchEpisodesAnime], optional): 搜索结果（作品信息）列表 ,
errorCode (integer): 错误代码，0表示没有发生错误，非0表示有错误，详细信息会包含在errorMessage属性中 ,
success (boolean, read only): 接口是否调用成功 ,
errorMessage (string, optional, read only): 当发生错误时，说明错误具体原因
}
SearchEpisodesAnime {
animeId (integer): 作品编号 ,
animeTitle (string, optional): 作品标题 ,
type (string): 作品类型 = ['tvseries', 'tvspecial', 'ova', 'movie', 'musicvideo', 'web', 'other', 'jpmovie', 'jpdrama', 'unknown'],
typeDescription (string, optional): 类型描述 ,
episodes (Array[SearchEpisodeDetails], optional): 此作品的剧集列表
}
SearchEpisodeDetails {
episodeId (integer): 剧集ID（弹幕库编号） ,
episodeTitle (string, optional): 剧集标题
}
 */

@Serializable
data class DandanplaySearchEpisodeResponse(
    val hasMore: Boolean = false,
    val animes: List<SearchEpisodesAnime> = listOf(),
    val errorCode: Int = 0,
    val success: Boolean = true,
    val errorMessage: String? = null,
)

@Serializable
data class DandanplayGetBangumiResponse(
    val hasMore: Boolean = false,
    val bangumi: DandanplayBangumiDetails? = null,
    val errorCode: Int = 0,
    val success: Boolean = true,
    val errorMessage: String? = null,
)

@Serializable
data class DandanplaySeasonSearchResponse(
    val hasMore: Boolean = false,
    val bangumiList: List<SearchEpisodesAnime> = listOf(),
    val errorCode: Int = 0,
    val success: Boolean = true,
    val errorMessage: String? = null,
)

@Serializable
data class SearchEpisodesAnime(
    val animeId: Int,
    val bangumiId: Int? = null,
    val animeTitle: String? = null,
//    val type: String? = null,
    val typeDescription: String? = null,
    val episodes: List<SearchEpisodeDetails> = listOf(),
)

@Serializable
data class SearchEpisodeDetails(
    val episodeId: Int,
    val episodeTitle: String? = null,
    val episodeNumber: String? = null, // 可能没有, 我随便加的
)
