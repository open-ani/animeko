/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import me.him188.ani.app.ui.foundation.stateOf
import org.openani.mediamp.InternalMediampApi
import androidx.compose.runtime.mutableStateOf
import org.openani.mediamp.metadata.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@OptIn(InternalMediampApi::class)
class PlayerSkipOpEdStateTest {
    class `OP chapter on start` {
        private val opChapterOnStart = listOf(
            Chapter("chapter1 op", 80_000L, 0),
            Chapter("chapter2", 10_000L, 100_000L),
            Chapter("chapter3", 10_000L, 110_000L),
        )

        private val videoLength = 24.minutes

        private fun createState_opChapterOnStart_24minutes(onSkip: (targetMillis: Long) -> Unit = {}): PlayerSkipOpEdState {
            return PlayerSkipOpEdState(
                stateOf(opChapterOnStart),
                onSkip = onSkip,
                stateOf(videoLength),
            )
        }

        @Test
        fun `on op`() {
            val state = createState_opChapterOnStart_24minutes()
            state.update(0)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `after op 3s`() {
            val state = createState_opChapterOnStart_24minutes()
            state.update(3000)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `after op 6s`() {
            val state = createState_opChapterOnStart_24minutes()
            state.update(6000)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `cancel on op`() {
            val state = createState_opChapterOnStart_24minutes()
            state.update(0)
            state.cancelSkipOpEd()
            state.update(1)
            assertEquals(false, state.showSkipTips)
            assertEquals(true, state.skipped)
        }

        @Test
        fun `cancel after op 3s`() {
            val state = createState_opChapterOnStart_24minutes()
            state.update(3000)
            state.cancelSkipOpEd()
            state.update(3001)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `cancel after op 6s`() {
            val state = createState_opChapterOnStart_24minutes()
            state.update(6000)
            state.cancelSkipOpEd()
            state.update(6001)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        /**
         * 成功自动跳过 OP 后, 用户又回到 OP 开头, 此时不能触发自动跳过
         */
        @Test
        fun `after skip op and return to op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnStart_24minutes() {
                skipTime = it
            }
            // 到达 OP 开头
            localState.update(0L)
            assertEquals(80_000L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 跳过 OP
            localState.update(skipTime)
            skipTime = 0L
            // 回到 OP 开头
            localState.update(0L)
            assertEquals(0L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
        }
    }

    class `OP chapter on chapter 2` {

        private val opChapterOnChapter2 = listOf(
            Chapter("chapter1", 10_000L, 0),
            Chapter("chapter2 op", 90_000L, 10_000L),
            Chapter("chapter3", 10_000L, 110_000L),
        )

        private val videoLength = 24.minutes

        private fun createState_opChapterOnChapter2_24minutes(onSkip: (targetMillis: Long) -> Unit = {}): PlayerSkipOpEdState {
            return PlayerSkipOpEdState(
                stateOf(opChapterOnChapter2),
                onSkip = onSkip,
                stateOf(videoLength),
            )
        }

        @Test
        fun `before op 6s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(4000L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `before op 3s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(7000L)
            assertEquals(true, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `on op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            localState.update(10_000L)
            assertEquals(100_000L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
        }

        @Test
        fun `after op 3s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(13_000L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `after op 6s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(16_000L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `cancel before op 6s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(4_000L)
            state.cancelSkipOpEd()
            state.update(4_001L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `cancel before op 3s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(7_000L)
            state.cancelSkipOpEd()
            state.update(7_001L)
            assertEquals(false, state.showSkipTips)
            assertEquals(true, state.skipped)
        }

        @Test
        fun `cancel on op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            localState.update(10_000L)
            localState.cancelSkipOpEd()
            localState.update(10_001L)
            assertEquals(100_000L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
        }

        @Test
        fun `cancel after op 3s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(13_000L)
            state.cancelSkipOpEd()
            state.update(13_001L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `cancel after op 6s`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(16_000L)
            state.cancelSkipOpEd()
            state.update(16_001L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        @Test
        fun `cancel before op 6s then play to op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            localState.update(4_000L)
            localState.cancelSkipOpEd()
            localState.update(4_001L)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            localState.update(10_000L)
            assertEquals(100_000L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
        }

        @Test
        fun `cancel before op 3s then play to op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            localState.update(7_000L)
            localState.cancelSkipOpEd()
            localState.update(7_001L)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
            localState.update(10_000L)
            assertEquals(0L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
        }

        @Test
        fun `after show tips user seek to other place`() {
            val state = createState_opChapterOnChapter2_24minutes()
            state.update(7_000L)
            assertEquals(true, state.showSkipTips)
            assertEquals(false, state.skipped)
            state.update(40_000L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.skipped)
        }

        /**
         * 成功自动跳过 OP 后, 用户又回到 OP 开头, 此时不能触发自动跳过
         */
        @Test
        fun `after skip op and return to op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            // 到达 OP 开头
            localState.update(10_000L)
            assertEquals(100_000L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 跳过 OP
            localState.update(skipTime)
            skipTime = 0L
            // 回到 OP 开头
            localState.update(10_000L)
            assertEquals(0L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
        }

        /**
         * 用户取消跳过后, 用户又回到 OP 开头, 此时不能触发自动跳过
         */
        @Test
        fun `after cancel skip op and return to op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            // 显示跳过提示
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 取消跳过
            localState.cancelSkipOpEd()
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
            // 到达 OP 开头
            localState.update(10_000L)
            assertEquals(0L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
            // 跳过 OP
            localState.update(skipTime)
            skipTime = 0L
            // 回到 OP 开头
            localState.update(10_000L)
            assertEquals(0L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
        }

        /**
         * 显示即将跳过 OP 的弹窗, 用户立即拖到别的地方, 应当取消跳过并且记忆操作. 当用户又回到 OP 开头, 此时不能触发自动跳过
         */
        @Test
        fun `show skip tips and seek to other place and return to op`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            // 显示跳过提示
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 滑到 OP 中
            localState.update(40_000L)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 回到 OP 开头
            localState.update(10_000L)
            assertEquals(0L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(true, localState.skipped)
        }

        @Test
        fun `from not show skip tips and seek to after op then return to show skip tips and reach op start`() {
            var skipTime = 0L
            val localState = createState_opChapterOnChapter2_24minutes {
                skipTime = it
            }
            // 到达 OP 前10秒
            localState.update(0)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 滑到 OP 后
            localState.update(110_000L)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 回到显示跳过提示
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 到达 OP 开头
            localState.update(10_000L)
            assertEquals(100_000L, skipTime)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
        }

        /** 同上, 只是中途滑到了整段 OP 之后 */
        @Test
        fun `seek past op then back before it re-arms the tip`() {
            val localState = createState_opChapterOnChapter2_24minutes()
            // 到达 OP 前3秒
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 滑到 OP 后
            localState.update(110_000L)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 拖回 OP 之前: 提示再来一遍
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
        }

        /**
         * 拖回 OP 之前 = 重新来一次: 提示再给一遍, 自动跳过也再武装一次.
         *
         * 早先这里断言的是"回来也不再提示" —— `skipped` 是一锤子买卖. 但用户拖回片头往往正是
         * 要重看这一段的前后, 那时该有的按钮一个都不出现
         */
        @Test
        fun `seek back before op re-arms the tip`() {
            val localState = createState_opChapterOnChapter2_24minutes()
            // 到达 OP 前3秒
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 滑到 OP 后
            localState.update(40_000L)
            assertEquals(false, localState.showSkipTips)
            assertEquals(false, localState.skipped)
            // 拖回 OP 之前: 提示再来一遍
            localState.update(7_000L)
            assertEquals(true, localState.showSkipTips)
            assertEquals(false, localState.skipped)
        }
    }

    /** 自动档只该有那颗短命的"取消跳过", 任何时候都不该冒出"跳过". OP = [10s, 100s). */
    class `auto mode never offers skip`() {

        private val opChapterOnChapter2 = listOf(
            Chapter("chapter1", 10_000L, 0),
            Chapter("chapter2 op", 90_000L, 10_000L),
            Chapter("chapter3", 10_000L, 110_000L),
        )

        private fun createState(onSkip: (targetMillis: Long) -> Unit = {}): PlayerSkipOpEdState {
            return PlayerSkipOpEdState(stateOf(opChapterOnChapter2), onSkip = onSkip, stateOf(24.minutes))
        }

        @Test
        fun `not offered while counting down`() {
            val state = createState()
            state.update(7_000L)
            assertEquals(true, state.showSkipTips)
            assertEquals(false, state.canSkipNow)
        }

        /** 按了取消之后整段 OP 都不该有按钮 (自动档里"取消"就是这一段的最终答复) */
        @Test
        fun `not offered after cancel`() {
            val state = createState()
            state.update(7_000L)
            state.cancelSkipOpEd()
            state.update(7_001L)
            assertEquals(null, state.currentTip)
            state.update(50_000L)
            assertEquals(false, state.canSkipNow)
            assertEquals(null, state.currentTip)
        }

        /** 自动跳过刚发生, 播放位置还没离开 OP 时也不该冒出来 */
        @Test
        fun `not offered right after auto skip`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(10_000L)
            assertEquals(100_000L, skipTime)
            assertEquals(false, state.canSkipNow)
            state.update(10_001L)
            assertEquals(false, state.canSkipNow)
        }

        /** 从别处 seek 进 OP 中间也不该冒出来 */
        @Test
        fun `not offered when seeking into the middle`() {
            val state = createState()
            state.update(50_000L)
            assertEquals(null, state.currentTip)
        }

        /** [PlayerSkipOpEdState.skipOpEd] 在自动档下是空操作 */
        @Test
        fun `skip now is no-op`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(50_000L)
            state.skipOpEd()
            assertEquals(0L, skipTime)
        }
    }

    /**
     * 每一段 OP/ED 都要给提示, 不是只有头一次 —— 换集之后 opEdChapters 整个换新,
     * 手上攥着的那个已经不在列表里了 (回归: 换成按实例比较之前, 后面每一段都换不进来)
     */
    class `every occurrence gets a tip`() {

        private val videoLength = 24.minutes

        @Test
        fun `op then ed in the same episode`() {
            val chapters = mutableStateOf(
                listOf(
                    Chapter("op", 90_000L, 10_000L),
                    Chapter("ed", 90_000L, 1_320_000L),
                ),
            )
            val state = PlayerSkipOpEdState(chapters, onSkip = {}, stateOf(videoLength))
            state.update(7_000L)
            assertEquals(SkipOpEdKind.OP, state.currentTip?.kind)
            state.update(10_000L) // 自动跳过 OP
            state.update(200_000L) // 正片
            assertEquals(null, state.currentTip)
            state.update(1_317_000L)
            assertEquals(SkipOpEdKind.ED, state.currentTip?.kind)
        }

        /**
         * 攥着上一段没放手的时候换集: 老代码只在"手上是 null"时才换新的一段, 于是新一集的 OP
         * 再也进不来 —— 提示就此绝迹. 换集恰好发生在片尾 (自动连播), 而那正是攥着 ED 的时候
         */
        @Test
        fun `next episode op while still holding the previous one`() {
            val chapters = mutableStateOf(listOf(Chapter("ed", 90_000L, 1_320_000L)))
            var skipTime = 0L
            val state = PlayerSkipOpEdState(chapters, onSkip = { skipTime = it }, stateOf(videoLength))
            // 片尾: 提示出来了, 用户按了取消 —— 这一段还攥在手里 (没走出提示窗口就换集了)
            state.update(1_317_000L)
            assertEquals(SkipOpEdKind.ED, state.currentTip?.kind)
            state.cancelSkipOpEd()
            assertEquals(null, state.currentTip)

            // 自动连播下一集: 章节列表整个换新, 播放位置回到片头
            chapters.value = listOf(Chapter("op", 90_000L, 10_000L))
            state.update(7_000L)
            assertEquals(SkipOpEdKind.OP, state.currentTip?.kind)
            state.update(10_000L)
            assertEquals(100_000L, skipTime)
        }
    }

    /**
     * 倍速播放时位置一次前进好几秒 (采样每秒一次, 长按倍速 2.5 倍, 手动倍速最高 4 倍),
     * 触发窗口只有 1 秒宽, 迈过去就再没有第二次机会 —— 回归: 表现是"倍速期间遇到 OP,
     * 提示自己没了也没跳", 与被误按了取消一模一样.
     */
    class `fast forward steps over the trigger window`() {

        private val videoLength = 24.minutes
        private val opChapter = listOf(Chapter("op", 90_000L, 10_000L))

        private fun createState(onSkip: (targetMillis: Long) -> Unit): PlayerSkipOpEdState =
            PlayerSkipOpEdState(stateOf(opChapter), onSkip = onSkip, stateOf(videoLength))

        /** 2.5 倍速: 采样落在 7.5s 与 10s 之间的 ... 这里取最坏的一种落法, 直接跨过 [9s, 10s] */
        @Test
        fun `skips even if no sample lands in the window`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(6_000L)
            assertEquals(SkipOpEdKind.OP, state.currentTip?.kind) // 提示照常出现
            state.update(8_500L)
            assertEquals(0L, skipTime) // 还没到触发点
            state.update(11_000L) // 一步迈过了 [9s, 10s]
            assertEquals(100_000L, skipTime)
        }

        /** 更快的一档: 跳过之前提示都没来得及出现 */
        @Test
        fun `skips when the first sample inside is already past the start`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(5_500L)
            assertEquals(SkipOpEdKind.OP, state.currentTip?.kind)
            state.update(13_500L)
            assertEquals(100_000L, skipTime)
        }

        /** 按过取消的那一段, 迈过窗口也不能跳 */
        @Test
        fun `does not skip a cancelled chapter`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(6_000L)
            state.cancelSkipOpEd()
            state.update(11_000L)
            assertEquals(0L, skipTime)
            assertEquals(null, state.currentTip)
        }

        /** 手上没攥着的那一段 (从别处 seek 进 OP 中间) 仍旧不跳 */
        @Test
        fun `does not skip when seeking into the middle`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(50_000L)
            assertEquals(0L, skipTime)
            assertEquals(null, state.currentTip)
        }
    }

    /** 手动档 (设置里选"显示跳过按钮"): 不倒计时也不自动跳, 只在 OP/ED 期间给"跳过". */
    class `manual mode` {

        private val opChapter = listOf(Chapter("op", 90_000L, 10_000L))

        private fun createState(onSkip: (targetMillis: Long) -> Unit = {}) = PlayerSkipOpEdState(
            stateOf(opChapter),
            onSkip = onSkip,
            stateOf(24.minutes),
            autoSkip = stateOf(false),
        )

        /** 倒计时那副面孔不出现 */
        @Test
        fun `no countdown tip`() {
            val state = createState()
            state.update(7_000L)
            assertEquals(false, state.showSkipTips)
            assertEquals(false, state.canSkipNow)
            assertEquals(null, state.currentTip)
        }

        /** 到点也不会自己跳 */
        @Test
        fun `never skips by itself`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(10_000L)
            assertEquals(0L, skipTime)
        }

        /** 整段 OP 期间给"跳过", 按下才跳 */
        @Test
        fun `offers skip through the whole op`() {
            var skipTime = 0L
            val state = createState { skipTime = it }
            state.update(10_000L)
            assertEquals(SkipOpEdTip(SkipOpEdKind.OP, canCancel = false), state.currentTip)
            state.update(99_000L)
            assertEquals(true, state.canSkipNow)
            state.skipOpEd()
            assertEquals(100_000L, skipTime)
            state.update(100_000L)
            assertEquals(null, state.currentTip)
        }
    }

    /** OP 还是 ED 按章节在时间轴上的位置判 (长度分不出来, 两者都是 90 秒左右). */
    class `op or ed by position` {

        private val videoLength = 24.minutes // 1_440_000ms

        private fun createState(chapters: List<Chapter>) =
            PlayerSkipOpEdState(stateOf(chapters), onSkip = {}, stateOf(videoLength))

        @Test
        fun `chapter at the beginning is op`() {
            val state = createState(listOf(Chapter("op", 90_000L, 10_000L)))
            state.update(7_000L)
            assertEquals(SkipOpEdTip(SkipOpEdKind.OP, canCancel = true), state.currentTip)
        }

        @Test
        fun `chapter near the end is ed`() {
            val state = createState(listOf(Chapter("ed", 90_000L, 1_320_000L)))
            state.update(1_317_000L)
            assertEquals(SkipOpEdTip(SkipOpEdKind.ED, canCancel = true), state.currentTip)
        }

        @Test
        fun `no tip outside any chapter`() {
            val state = createState(listOf(Chapter("op", 90_000L, 10_000L)))
            state.update(200_000L)
            assertEquals(null, state.currentTip)
        }
    }
}
