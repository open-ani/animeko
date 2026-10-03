package me.him188.ani.datasources.api.source

import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.contains

/**
 * A media matched from the source.
 */
data class MediaMatch(
    val media: Media,
    val kind: MatchKind,
)

/**
 * 判断该 [MediaMatch] 的剧集范围是否包含 [request] 中的当前剧集.
 *
 * 数据源查询以条目为单位, [MediaSource.fetch] 不得据此剔除资源; 该函数供数据源编辑器的测试功能等展示用途使用.
 *
 * 返回 `null` 表示条件不足以判断: 当 [Media.episodeRange] 为 `null` 时, 无法知道该资源的剧集范围.
 */
fun MediaMatch.matches(request: MediaFetchRequest): Boolean? {
    val actualEpRange = this.media.episodeRange ?: return null
    val expectedEp = request.episodeEp
    return !(request.episodeSort !in actualEpRange && (expectedEp == null || expectedEp !in actualEpRange))
}

/**
 * 当且仅当该资源一定包含请求中的当前剧集时返回 `true`. 若条件不足, 返回 `false`.
 */
fun MediaMatch.definitelyMatches(request: MediaFetchRequest): Boolean = matches(request) == true

/**
 * 数据源对结果属于请求条目的把握. 匹配以条目为单位.
 */
enum class MatchKind {
    /**
     * 通过条目 ID (例如 Bangumi 条目 ID 或缓存记录的条目 ID) 精确定位到了条目.
     */
    EXACT,

    /**
     * 通过关键字搜索得到, 尽力而为, 可能属于其他条目.
     */
    FUZZY,
}
