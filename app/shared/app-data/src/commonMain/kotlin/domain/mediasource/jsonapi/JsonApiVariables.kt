/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

/**
 * 各阶段请求模板里可用的变量. 正式搜索与编辑器的测试面板用同一套, 保证两边发出的请求一致.
 */
object JsonApiVariables {
    /** 搜索阶段请求模板可用的变量名. */
    val SEARCH: Set<String> = setOf("keyword", "page", "pageSize")

    /** 剧集列表阶段请求模板可用的变量名. */
    val SUBJECT: Set<String> = setOf("id", "url", "name")

    /** 播放阶段请求模板与视频请求头可用的变量名. */
    val PLAY: Set<String> = setOf("id", "url", "name", "subjectId", "subjectUrl", "subjectName", "channel", "channelMatch")

    /** 条目页面地址模板可用的变量名. */
    val SUBJECT_URL: Set<String> = setOf("id")

    /** 剧集页面地址模板可用的变量名. */
    val EPISODE_URL: Set<String> = setOf("id", "subjectId")

    /** 剧集显示名称模板可用的变量名. */
    val EPISODE_NAME: Set<String> = setOf("sort", "name", "id")

    /**
     * 搜索阶段: `keyword`, `page`, `pageSize`.
     */
    fun search(keyword: String, stage: JsonApiSearchStage): Map<String, String> = mapOf(
        "keyword" to keyword,
        "page" to "1",
        "pageSize" to stage.pageSize.toString(),
    )

    /**
     * 剧集列表阶段: 条目的 `id`, `url`, `name`.
     */
    fun subject(subject: JsonApiSubjectInfo): Map<String, String> = mapOf(
        "id" to subject.id,
        "url" to subject.url,
        "name" to subject.name,
    )

    /**
     * 播放阶段: 剧集的 `id`, `url`, `name`, 条目的 `subjectId`, `subjectUrl`, `subjectName`,
     * 以及有线路时的 `channel` 与 `channelMatch`. 也是资源上保存的播放参数.
     */
    fun play(subject: JsonApiSubjectInfo, episode: JsonApiEpisodeInfo): Map<String, String> = buildMap {
        put("id", episode.id)
        put("url", episode.url)
        put("name", episode.name)
        put("subjectId", subject.id)
        put("subjectUrl", subject.url)
        put("subjectName", subject.name)
        episode.channel?.let { put("channel", it) }
        episode.channelMatch?.let { put("channelMatch", it) }
    }
}
