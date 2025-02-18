package me.him188.ani.danmaku.ui

import androidx.compose.runtime.IntState
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class FloatingDanmakuTrackTest {
    @Test
    fun `test placement`() = runComposeStateTest {
        val frameTimeNanosState = mutableLongStateOf(0)
        var frameTimeNanos by frameTimeNanosState
        val baseTrackSpeed = 100f

        val presentDanmaku = mutableListOf<FloatingDanmaku<TestDanmaku>>()
        // 轨道长度 1000px, 弹幕速度 100px/second
        val track = createFloatingDanmakuTrack(
            frameTimeNanosState = frameTimeNanosState,
            trackWidth = mutableIntStateOf(1000),
            speedPxPerSecond = baseTrackSpeed,
            onRemoveDanmaku = { d -> presentDanmaku.removeAll { it.danmaku == d.danmaku } },
        )

        suspend fun elapseTime(milliseconds: Long) {
            frameTimeNanos += milliseconds.ms2ns
            presentDanmaku.forEach { it.distanceX += milliseconds / 1000.0f * (baseTrackSpeed * it.speedMultiplier) }
            track.tick()
            takeSnapshot()
        }

        val danmaku1 = TestDanmaku(100)
        val positioned1 = track.tryPlace(danmaku1)
        assertNotNull(positioned1) // 一定可以放
        presentDanmaku.add(positioned1)
        assertEquals(danmaku1, positioned1.danmaku)

        // 有未确定放置时间的弹幕，没办法再放一个未确定时间的弹幕
        assertNull(track.tryPlace(TestDanmaku(50)))
        // 无法放到轨道右侧边缘之外
        assertNull(track.tryPlace(TestDanmaku(50), 1000L.ms2ns))

        // 运动 10000 ms, 现在 positioned1 应该在轨道左侧
        elapseTime(10000L)
        assertEquals(1000f, positioned1.distanceX)

        // 放到帧时间 2000L 的位置
        // 模拟 repopulate 指定放置时间的操作
        val danmaku2 = TestDanmaku(50)
        val positioned2 = track.tryPlace(danmaku2, 2000L.ms2ns)
        assertNotNull(positioned2)
        presentDanmaku.add(positioned2)
        assertEquals(danmaku2, positioned2.danmaku)
        assertEquals(800f, positioned2.distanceX)

        // 放一个未指定放置时间的弹幕
        val danmaku3 = TestDanmaku(200)
        val positioned3 = track.tryPlace(danmaku3)
        assertNotNull(positioned3)
        presentDanmaku.add(positioned3)
        assertEquals(danmaku3, positioned3.danmaku)

        // UI帧运动 50ms
        elapseTime(1500L)
        // positioned1 在 UI 帧运动 150ms 后应该消失
        assertEquals(2, presentDanmaku.size)
        assertEquals(150f, positioned3.distanceX)
        // positioned3 还没完全显示出来，没办法再放未确定放置时间的弹幕
        assertNull(track.tryPlace(TestDanmaku(100)))

        // 再运动 15000ms，所有弹幕都应该消失了
        elapseTime(15000L)
        assertEquals(0, presentDanmaku.size)
    }
    
    @Test
    fun `test collision`() = runComposeStateTest {
        val frameTimeNanosState = mutableLongStateOf(0)
        var frameTimeNanos by frameTimeNanosState
        val baseTrackSpeed = 100f
        val baseSpeedTextWidth = 50

        val presentDanmaku = mutableListOf<FloatingDanmaku<TestDanmaku>>()
        // 轨道长度 1000px, 弹幕速度 100px/second
        val track = createFloatingDanmakuTrack(
            frameTimeNanosState = frameTimeNanosState,
            trackWidth = mutableIntStateOf(1000),
            speedPxPerSecond = baseTrackSpeed,
            baseSpeedTextWidth = baseSpeedTextWidth,
            speedMultiplier = 2f,
            safeSeparation = 0f,
            onRemoveDanmaku = { d -> presentDanmaku.removeAll { it.danmaku == d.danmaku } },
        )

        suspend fun elapseTime(milliseconds: Long) {
            frameTimeNanos += milliseconds.ms2ns
            presentDanmaku.forEach { it.distanceX += milliseconds / 1000.0f * (baseTrackSpeed * it.speedMultiplier) }
            track.tick()
            takeSnapshot()
        }
        
        elapseTime(10000L)
        
        // 此弹幕的左侧应该在轨道左侧, speed = 1x
        val danmakuInLeft = track.tryPlace(TestDanmaku(baseSpeedTextWidth), 0L)
        assertNotNull(danmakuInLeft)
        presentDanmaku.add(danmakuInLeft)
        assertEquals(1000f, danmakuInLeft.distanceX)
        
        // 此弹幕应该紧跟在 danmakuInLeft 后面, 并且运动速度和它一样
        // 因此不会撞车, 所以可以放下
        val danmakuFollowing = track.tryPlace(TestDanmaku(baseSpeedTextWidth), 500L.ms2ns)
        assertNotNull(danmakuFollowing)
        presentDanmaku.add(danmakuFollowing)
        assertEquals(950f, danmakuFollowing.distanceX)
        
        elapseTime(500L)
        assertEquals(1, presentDanmaku.size)
        // 运动了 5000ms 后, 尝试放 danmakuWillClash 紧跟着 danmakuFollowing
        // danmakuWillClash 的速度比 danmakuFollowing 快, 所以一定会撞车, 因此放不下
        val danmaku2xSpeed = track.tryPlace(TestDanmaku(baseSpeedTextWidth * 2), 475L.ms2ns)
        assertNull(danmaku2xSpeed)
        
        // 延迟 100ms 放, danmakuFollowing 刚好消失, 所以这个弹幕不会撞车
        val danmaku2xSpeed2 = track.place(TestDanmaku(baseSpeedTextWidth * 2), 575L.ms2ns)
        assertNotNull(danmaku2xSpeed2)
        presentDanmaku.add(danmaku2xSpeed2)
        
        // 重置一下轨道, 先让所有弹幕都滚动消失再设置帧时间为 0
        elapseTime(100000L)
        frameTimeNanos = 0
        assertEquals(0, presentDanmaku.size)

        elapseTime(5000L)
        // 此弹幕的左侧应该在轨道左侧, speed = 2x
        val danmakuInLeft2 = track.tryPlace(TestDanmaku(baseSpeedTextWidth * 2), 0L)
        assertNotNull(danmakuInLeft2)
        presentDanmaku.add(danmakuInLeft2)
        assertEquals(1000f, danmakuInLeft2.distanceX)
        
        // 运动了 5000ms 后, 尝试放 danmakuWillClash2 紧跟着 danmakuInLeft2
        // danmakuWillClash2 的速度比 danmakuInLeft2 快, 所以一定会撞车, 因此放不下
        val danmaku2xSpeed3 = track.tryPlace(TestDanmaku(baseSpeedTextWidth * 4), 2750L.ms2ns)
        assertNull(danmaku2xSpeed3)
        
        // 在 danmakuInLeft2 刚好 fully visible 时直接放置 danmakuDot5xSpeed
        // danmakuDot5xSpeed 的速度比 danmakuInLeft2 慢, 所以一定不会撞车, 因此放得下
        val danmakuDot5xSpeed = track.tryPlace(TestDanmaku(baseSpeedTextWidth), 500L.ms2ns)
        assertNotNull(danmakuDot5xSpeed)
        presentDanmaku.add(danmakuDot5xSpeed)
        assertEquals(450f, danmakuDot5xSpeed.distanceX)
    }
}

private val Long.ms2ns get() = milliseconds.inWholeNanoseconds

private fun createFloatingDanmakuTrack(
    frameTimeNanosState: LongState,
    trackWidth: IntState,
    speedPxPerSecond: Float = 100f,
    safeSeparation: Float = 10f,
    baseSpeedTextWidth: Int = 100,
    speedMultiplier: Float = 1f,
    onRemoveDanmaku: (FloatingDanmaku<TestDanmaku>) -> Unit = { },
) = FloatingDanmakuTrack(
    trackIndex = 0, // 测试时没用
    frameTimeNanosState = frameTimeNanosState,
    trackWidth = trackWidth,
    trackHeight = mutableIntStateOf(50), // 测试时没用
    baseSpeedPxPerSecond = speedPxPerSecond,
    safeSeparation = safeSeparation,
    baseSpeedTextWidth = baseSpeedTextWidth, // 如果不进行撞车测试, 那请设置 speedMultiplier 为 1f
    speedMultiplier = mutableFloatStateOf(speedMultiplier),
    randomizeSpeedFluctuation = 0f, // 测试时不能有速度波动
    onRemoveDanmaku = onRemoveDanmaku,
)

// 只要有长度就可以在 DanmakuTrack 里测量
private class TestDanmaku(
    override val danmakuWidth: Int
) : SizeSpecifiedDanmaku {
    // floating danmaku doesn't concern about danmaku height.
    override val danmakuHeight: Int = 0
}