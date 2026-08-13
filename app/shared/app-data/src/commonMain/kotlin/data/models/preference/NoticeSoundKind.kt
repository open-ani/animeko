/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import kotlinx.serialization.Serializable

/**
 * 后台会话提示 (见 `RetainedPlaybackNotice`) 响哪一声.
 *
 * 全部取自**系统自带的界面音效**, 不随包带音频资源: 电视上压根没有通知音可用
 * (真机 ROM 里只有按键音与闹钟音), 而合成音音色难听. 具体到各平台的映射见
 * `rememberNoticeSoundPlayer` 的 actual 实现; 命名故意按"听起来像什么"而不是按平台常量,
 * 因为同一个语义在不同平台落到的音效不一样.
 *
 * **系统里有几个音就给几个选项**, 哪怕原本的按键语义与"提示"无关 (删除键、空格键):
 * Animeko 自己一处音效都不用, 用户不会把这声提示当成操作反馈或噪音, 所以能选的越多越好 ——
 * 每台电视的这几颗音色差别很大, 只有用户自己试听才知道哪个在他家客厅里听得见.
 * (Android 上 `FX_FOCUS_NAVIGATION_*` 不单列: AOSP 里它们与 `FX_KEY_CLICK` 指向同一个文件.)
 */
@Serializable
enum class NoticeSoundKind {
    /** 不响. 只留 toast. */
    None,

    /** 确认音 (Android: `FX_KEYPRESS_RETURN`). 语义最贴"好了", 默认值. */
    Confirm,

    /** 普通按键音 (Android: `FX_KEYPRESS_STANDARD`). */
    Standard,

    /**
     * 提醒音 (Android: `FX_KEYPRESS_INVALID`).
     *
     * ROM 里这颗通常是最长最显眼的一个, 所以拿来当"叫人回来"的提示; 代价是它原本的语义是
     * "这个操作无效".
     */
    Alert,

    /**
     * 轻点音 (Android: `FX_KEY_CLICK`).
     *
     * **最容易被忽略的一个**: 电视系统 UI 自己的遥控器导航音往往就是它, 用户在桌面上一直在听,
     * 拿它当提示很可能被当成背景噪音. 留着是因为它最不打扰.
     */
    Tick,

    /** 删除音 (Android: `FX_KEYPRESS_DELETE`). */
    Delete,

    /** 空格音 (Android: `FX_KEYPRESS_SPACEBAR`). */
    Space,
    ;

    companion object {
        val Default = Confirm
    }
}
