package me.him188.ani.datasources.api.source

abstract class WebMediaSource : HttpMediaSource() {
    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
}