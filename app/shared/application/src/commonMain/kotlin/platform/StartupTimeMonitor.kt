/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.TimeSource

class StartupTimeMonitor(
    private val start: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
) {
    private class Mark(
        val step: String,
        val time: Duration,
        val duration: Duration,
    )

    private val marks = ArrayList<Mark>(10)

    fun mark(stepDone: StepName) {
        mark(stepDone.name)
    }

    private fun mark(stepDone: String) {
        val time = start.elapsedNow()
        val duration = if (marks.isEmpty()) {
            time
        } else {
            time - marks.last().time
        }
        marks.add(Mark(stepDone, time, duration))
    }

    fun getMarks(): Map<String, Long> {
        return buildMap {
            for (mark in marks) {
                put("time_" + mark.step, mark.duration.inWholeMilliseconds)
            }
        }
    }

    fun getTotalDuration(): Duration {
        return if (marks.isEmpty()) {
            Duration.ZERO
        } else {
            marks.sumOf { it.duration }
        }
    }
}


@JvmInline
value class StepName(
    val name: String,
) {
    override fun toString(): String = name

    companion object {
        val UncaughtExceptionHandler = StepName("UncaughtExceptionHandler")
        val ProjectDirectories = StepName("ProjectDirectories")
        val Logging = StepName("Logging")
        val WindowAndContext = StepName("WindowAndContext")
        val SingletonChecker = StepName("SingletonChecker")
        val Modules = StepName("Modules")
        val LaunchAsyncInitializers = StepName("LaunchAsyncInitializers")
        val PagingHack = StepName("PagingHack")
        val Analytics = StepName("Analytics")
        val ThemeDetector = StepName("ThemeDetector")
    }
}


private inline fun <T> Iterable<T>.sumOf(selector: (T) -> Duration): Duration {
    var sum: Duration = Duration.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
