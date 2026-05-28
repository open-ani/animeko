package me.him188.ani.utils.platform

import kotlin.random.Random

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT") // JvmInline
actual value class Uuid internal constructor(
    @Suppress("MemberVisibilityCanBePrivate") internal val delegate: UuidDelegate,
) {
    actual override fun toString(): String = delegate

    actual companion object {
        actual fun random(random: Random): Uuid = Uuid(generateRandomUuid(random))
        actual fun randomString(random: Random): String = generateRandomUuid(random)
    }
}

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias UuidDelegate = String
