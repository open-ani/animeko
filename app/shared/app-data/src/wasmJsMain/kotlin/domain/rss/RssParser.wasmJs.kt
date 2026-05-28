package me.him188.ani.app.domain.rss

import kotlinx.datetime.LocalDateTime

@Suppress("FunctionName")
internal actual fun RssParser_parseTime(text: String): LocalDateTime? = RssParser_parseTimeUsingKtx(text)
