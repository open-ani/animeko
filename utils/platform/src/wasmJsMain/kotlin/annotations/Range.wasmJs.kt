package me.him188.ani.utils.platform.annotations

@Suppress("unused")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.TYPE)
actual annotation class Range actual constructor(actual val from: Long, actual val to: Long)
