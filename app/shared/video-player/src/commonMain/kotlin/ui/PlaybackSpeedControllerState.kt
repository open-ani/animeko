/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.math.roundToInt

/**
 * Side-effect: creation of this state will immediately set the playback speed to the initial speed.
 *
 * @param scope coroutine scope for playback speed collector, usually [rememberCoroutineScope].
 */
@Stable
class PlaybackSpeedControllerState(
    private val playbackSpeed: PlaybackSpeed,
    speedProvider: () -> List<Float> = { listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) },
    scope: CoroutineScope
) {
    companion object {
        const val MIN_CUSTOM_SPEED = 0.1f
        const val MAX_CUSTOM_SPEED = 5.0f
    }

    val speedList: List<Float> by derivedStateOf(speedProvider)
    var currentSpeed by mutableStateOf(playbackSpeed.value)
        private set
    var isCustomSpeedDialogVisible by mutableStateOf(false)
        private set
    var customSpeedInput by mutableStateOf(playbackSpeed.value.formatPlaybackSpeed())
        private set

    /**
     * `-1` represents a invalid index, which means the current speed is not in the list.
     */
    val currentIndex: Int by derivedStateOf { speedList.indexOf(currentSpeed) }

    init {
        require(speedList.isNotEmpty()) { "Playback speed list must not be empty" }

        var hasOriginalSpeed = false
        val isMonotonicIncreasing = speedList
            .asSequence()
            .zipWithNext()
            .all { (a, b) ->
                if (a == 1f || b == 1f) hasOriginalSpeed = true
                a <= b
            }

        require(isMonotonicIncreasing) { "Playback speed list should be monotonic increasing" }
        require(hasOriginalSpeed) { "Playback speed list should contain 1.0f" }

        scope.launch {
            playbackSpeed.valueFlow
                .distinctUntilChanged()
                .collect { value ->
                    currentSpeed = value
                    if (!isCustomSpeedDialogVisible) {
                        customSpeedInput = value.formatPlaybackSpeed()
                    }
                }
        }
    }

    /**
     * Set playback speed to the next index presented in list based on [currentIndex].
     *
     * If current speed is not at the provider list, set to the nearest up speed in the list.
     */
    fun speedUp() {
        if (currentIndex == -1) {
            val nearestUp = speedList.firstOrNull { it > currentSpeed } ?: speedList.last()
            playbackSpeed.set(nearestUp)
        } else if (currentIndex < speedList.size - 1) {
            playbackSpeed.set(speedList[currentIndex + 1])
        }
    }

    fun speedDown() {
        if (currentIndex == -1) {
            val nearestDown = speedList.lastOrNull { it < currentSpeed } ?: speedList.first()
            playbackSpeed.set(nearestDown)
        } else if (currentIndex > 0) {
            playbackSpeed.set(speedList[currentIndex - 1])
        }
    }

    fun setSpeed(index: Int) {
        require(index in speedList.indices) {
            "Speed index is out of range, index: $index, size: ${speedList.size}"
        }
        setSpeed(speedList[index])
    }

    fun setSpeed(value: Float) {
        playbackSpeed.set(value)
    }

    fun setCustomSpeed(value: Float) {
        val normalized = ((value * 10).roundToInt() / 10f)
            .coerceIn(MIN_CUSTOM_SPEED, MAX_CUSTOM_SPEED)
        setSpeed(normalized)
    }

    fun openCustomSpeedDialog() {
        customSpeedInput = currentSpeed.formatPlaybackSpeed()
        isCustomSpeedDialogVisible = true
    }

    fun closeCustomSpeedDialog() {
        isCustomSpeedDialogVisible = false
        customSpeedInput = currentSpeed.formatPlaybackSpeed()
    }

    fun updateCustomSpeedInput(value: String) {
        customSpeedInput = value
    }

    fun reset() {
        setSpeed(1f)
    }
}

private fun Float.formatPlaybackSpeed(): String {
    return if (this % 1f == 0f) {
        roundToInt().toString()
    } else {
        toString().trimEnd('0').trimEnd('.')
    }
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpPlaybackSpeedController : PlaybackSpeed {
    override val value: Float = 1f
    override val valueFlow: Flow<Float> = flowOf(1f)

    override fun set(speed: Float) {

    }
}
