/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.metadata.Chapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_OP_ED_SKIP_DURATION = VideoScaffoldConfig.Default.opEdSkipDuration

/**
 * 提前多少毫秒就可以动手跳过. 位置采样有误差 (每秒一次, 且 seek 之后的第一帧未必对齐),
 * 所以不等严格到达章节开头.
 */
private const val SKIP_TRIGGER_LEAD_MILLIS = 1000L

/**
 * 越过章节开头多远之内仍然认这一段"还来得及跳" (见 [PlayerSkipOpEdState.update] 里的 `holding`).
 *
 * 位置每秒采样一次, 而**播放速度不一定是 1 倍**: 最高 4 倍速时一次前进 4 秒, 触发窗口整段跨过去
 * 是常事. 取 6 秒 = 最高倍速一步 + 余量.
 *
 * 不放宽到整段 OP/ED: 那样"从别处 seek 进 OP 中间"也会被当成到点自动跳走, 而那一直是"什么都不做".
 */
private const val SKIP_TRIGGER_OVERSHOOT_MILLIS = 6000L

/**
 * OP/ED 的三个去向 (跳了 / 用户取消 / 没跳成就作罢) 各打一条:
 * 界面上"提示自己没了"和"被按了取消"长得一模一样, 事后只有日志分得清是哪一种.
 */
private val logger = logger<PlayerSkipOpEdState>()

@Stable
class PlayerSkipOpEdState(
    chapters: State<List<Chapter>>,
    private val onSkip: (targetMillis: Long) -> Unit,
    videoLength: State<Duration>,
    /**
     * 到点是否自动跳过 (设置里的"自动跳过"档). false = 手动档: 不倒计时也不自动跳,
     * 只在 OP/ED 期间把"跳过"那副面孔亮出来 (见 [canSkipNow]).
     */
    private val autoSkip: State<Boolean> = mutableStateOf(true),
) {
    private var currentChapter: CurrentChapter? by mutableStateOf(null)

    /**
     * 播放位置正落在其中的那一段 OP/ED; null = 不在任何 OP/ED 里, 或者根本不是手动档.
     *
     * 与 [currentChapter] 是两回事: 后者盯的是"章节开头快到了, 准备自动跳", 本字段盯的是
     * "人已经在这段 OP/ED 里面了". **只有手动档才算**: 自动档下该给的只有那颗短命的"取消跳过",
     * 跳没跳成都不该再冒出一颗"跳过"来.
     */
    private var insideChapter: CurrentChapter? by mutableStateOf(null)

    private val opEdChapters by derivedStateOf {
        val length = videoLength.value
        chapters.value.filter {
            OpEdLength.fromVideoLengthOrNull(length)?.isOpEdChapter(it.durationMillis.milliseconds) == true
        }.map { CurrentChapter(chapter = it, skipped = false, kind = opEdKindOf(it, length)) }
    }

    val skipped: Boolean by derivedStateOf {
        currentChapter?.skipped ?: false
    }

    /** 自动跳过正在倒计时 (章节开头就快到了): 可以按"取消跳过". */
    val showSkipTips: Boolean by derivedStateOf {
        currentChapter != null && !skipped
    }

    /**
     * 人已经在 OP/ED 里面了, 可以按"跳过"直接跳到本段结尾 (见 [skipOpEd]).
     *
     * **只属于手动档** (见 [insideChapter]); 与 [showSkipTips] 天然互斥.
     */
    val canSkipNow: Boolean by derivedStateOf {
        insideChapter != null && !showSkipTips
    }

    /**
     * 眼下该给用户看的那条提示; null = 不该显示.
     *
     * 把"是 OP 还是 ED"与"该给取消还是该给跳过"打成一个值一起发出去: UI 那边是一颗会变脸的
     * 按钮, 两个字段分开读的话渐隐途中会一个先变一个后变.
     */
    val currentTip: SkipOpEdTip? by derivedStateOf {
        when {
            showSkipTips -> currentChapter?.let { SkipOpEdTip(it.kind, canCancel = true) }
            canSkipNow -> insideChapter?.let { SkipOpEdTip(it.kind, canCancel = false) }
            else -> null
        }
    }

    fun cancelSkipOpEd() {
        val chapter = currentChapter ?: return
        logger.info { "OP/ED skip cancelled by user: ${chapter.kind} at ${chapter.chapter.offsetMillis}" }
        chapter.skipped = true
    }

    /** 跳到当前所在这一段 OP/ED 的结尾; 不在 OP/ED 里时无操作. */
    fun skipOpEd() {
        val chapter = insideChapter?.chapter ?: return
        onSkip(chapter.offsetMillis + chapter.durationMillis)
    }


    /**
     * 每秒调用一次update
     * 根据[currentPos]感知[currentPos]到5秒后这个区间是否会有章节开头，
     * 根据当前秒的位置显示/隐藏tips，
     * 并且如果[currentPos]在章节开头的位置，根据[skipped]跳过该章节
     */
    fun update(currentPos: Long) {
        if (opEdChapters.isEmpty()) {
            currentChapter = null
            insideChapter = null
            return
        }
        if (!autoSkip.value) {
            // 手动档: 不倒计时也不自动跳, 只认下面那句"人在不在这一段里面" ——
            // 于是提示只会有"跳过"那副面孔
            currentChapter = null
        } else {
            // 播放位置回到了某一段之前 = 用户自己拖回去了, 那一段重新武装: 再给一次提示, 也再跳一次.
            // 不重置的话 skipped 是一锤子买卖 —— 拖回片头重看, OP 那儿什么都不会再发生.
            //
            // 手上攥着的那一段除外: 按了取消之后播放位置仍在它开头之前, 一并重置就把这次取消撤销了
            // (下一个 tick 它会被移出提示窗口并放手, 那之后才轮到重置)
            opEdChapters.forEach {
                if (it !== currentChapter && currentPos < it.chapter.offsetMillis) {
                    it.skipped = false
                }
            }
            // 已经越过触发点、人还在这一段里面的时候**攥住不放**, 不再走下面那道认领/放手.
            //
            // 位置是每秒采样一次的, 而**播放速度不一定是 1 倍**: 长按倍速 2.5 倍 (最高 4 倍) 时
            // 每次采样前进 2.5~4 秒, 下面那个 1 秒宽的触发窗口整段跨过去是常事 —— 于是这一段
            // 既没跳成, 又在下一次采样时因为"不在提示窗口里"被放手 (skipped 置真), 表现就是
            // "倍速期间遇到 OP, 提示自己没了也没跳", 看着和被误按了取消一模一样.
            val holding = currentChapter?.takeIf {
                currentPos >= it.chapter.offsetMillis - SKIP_TRIGGER_LEAD_MILLIS &&
                        currentPos < it.chapter.offsetMillis + minOf(
                    SKIP_TRIGGER_OVERSHOOT_MILLIS,
                    it.chapter.durationMillis,
                )
            }
            if (holding == null) {
                // 在显示跳过提示范围.
                //
                // 用 `!==` 而不是"当前为 null 才赋值": 后者只要手上还攥着上一段 (换集之后 opEdChapters
                // 整个换新, 攥着的那个已经不在列表里了), 后面每一段 OP/ED 都再也换不进来, 表现就是
                // "只有头一次遇到 OP/ED 才有提示". 同一段重复命中仍是同一个实例, 不会把取消过的重新武装
                opEdChapters.find { it.chapter.offsetMillis in currentPos - 1000..currentPos + 5000 }?.let {
                    if (currentChapter !== it) {
                        currentChapter = it
                    }
                } ?: run {
                    // 手上攥着的那一段就此作罢 (位置离开了提示窗口). 打条日志: "提示自己没了也没跳"
                    // 与"被按了取消"在界面上完全一样, 出问题时只有这里分得清
                    currentChapter?.let {
                        if (!it.skipped) {
                            logger.info {
                                "OP/ED tip dropped without skipping: ${it.kind} at " +
                                        "${it.chapter.offsetMillis}, pos=$currentPos"
                            }
                        }
                        it.skipped = true
                    }
                    currentChapter = null
                }
            }
            // 到点就跳. 判据是"位置已经到了触发点", 不是"落在触发窗口里" —— 见上面 holding 那段:
            // 窗口是按 1 倍速的采样间隔定的, 倍速期间迈过去就再也不会有第二次机会了
            currentChapter?.takeIf { currentPos >= it.chapter.offsetMillis - SKIP_TRIGGER_LEAD_MILLIS }?.run {
                if (skipped) return@run
                logger.info {
                    "Auto skipping $kind at ${chapter.offsetMillis}, pos=$currentPos, " +
                            "target=${chapter.offsetMillis + chapter.durationMillis}"
                }
                onSkip(chapter.offsetMillis + chapter.durationMillis)
                skipped = true
                currentChapter = null
            }
        }
        // 人在不在 OP/ED 里面 —— 只有手动档要这个 (自动档下"跳过"那副面孔根本不该出现)
        insideChapter = if (autoSkip.value) {
            null
        } else {
            opEdChapters.find {
                currentPos >= it.chapter.offsetMillis &&
                        currentPos < it.chapter.offsetMillis + it.chapter.durationMillis
            }
        }
    }
}

/** OP/ED 提示指向的是哪一段. */
enum class SkipOpEdKind { OP, ED }

/**
 * 眼下该给用户看的那条 OP/ED 提示 (见 [PlayerSkipOpEdState.currentTip]).
 *
 * @param kind 这一段是 OP 还是 ED.
 * @param canCancel true = 自动跳过正在倒计时, 该给"取消跳过"; false = 人已经在这一段里面了
 * (按过取消, 或从别处 seek 进来), 该给"跳过".
 */
data class SkipOpEdTip(
    val kind: SkipOpEdKind,
    val canCancel: Boolean,
)

/**
 * 按章节在时间轴上的位置判 OP 还是 ED: 中点落在前半段算 OP, 后半段算 ED.
 *
 * 只能看位置, 不能看长度 —— OP 与 ED 长度几乎一样 (都是一首歌 90 秒左右), [OpEdLength] 那道
 * 筛子对两者是同一个区间, 分不出来. 位置则很稳: ED 一定贴着片尾, OP 一定在前半段
 * (哪怕前面有段冷开场也远不到中点).
 */
private fun opEdKindOf(chapter: Chapter, videoLength: Duration): SkipOpEdKind {
    val middleMillis = chapter.offsetMillis + chapter.durationMillis / 2
    return if (middleMillis * 2 < videoLength.inWholeMilliseconds) SkipOpEdKind.OP else SkipOpEdKind.ED
}

@Stable
class CurrentChapter(val chapter: Chapter, skipped: Boolean, val kind: SkipOpEdKind) {
    var skipped by mutableStateOf(skipped)
}

fun interface OpEdLength {
    fun isOpEdChapter(chapterLength: Duration): Boolean

    companion object {
        private val Normal = OpEdLength { it in 80.seconds..95.seconds }
        private val Short = OpEdLength { it in 55.seconds..65.seconds }

        fun fromVideoLengthOrNull(length: Duration): OpEdLength? {
            return when {
                length > 20.minutes -> Normal
                length > 10.minutes -> Short
                else -> null
            }
        }
    }
}
