/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers.getSearchKeyword
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests
import kotlin.test.assertEquals

class MediaSourceEngineHelpersTest {
    @TestFactory
    fun `getSearchKeyword cases`() = runDynamicTests {
        fun case(
            expected: String,
            originalTitle: String,
            removeSpecial: Boolean = true, // default configuration of SelectorMediaSource
            useOnlyFirstWord: Boolean = true,
        ) {
            add("$originalTitle -> $expected") {
                assertEquals(expected, getSearchKeyword(originalTitle, removeSpecial, useOnlyFirstWord))
            }
        }

        case("香格里拉", "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～ 第二季")
        case("异世界魔王与召唤少女的奴隶魔术Ω", "异世界魔王与召唤少女的奴隶魔术Ω")
        case("天降之物f", "天降之物f")
        case("五等分的新娘＊", "五等分的新娘＊")
        case("邪神与厨二病少女’", "邪神与厨二病少女’")
        case("打工吧魔王大人", "打工吧！！魔王大人")
        case("理科生坠入情网故尝试证明", "理科生坠入情网故尝试证明[r=1-sinθ]♡")
        case("new", "new game!")
        case("new", "new game!!")
    }
}