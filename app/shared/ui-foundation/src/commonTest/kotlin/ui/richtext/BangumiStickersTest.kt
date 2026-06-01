/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.richtext

import me.him188.ani.app.ui.comment.BangumiStickers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 地址是按 Bangumi 的命名规则拼的 (见 [BangumiStickers]), 拼错了只会静默不出图, 所以每个表情包
 * 都钉一个已验证存在的地址; 各包的特例 (个别 gif、序号偏移) 也一并钉住.
 */
class BangumiStickersTest {
    @Test
    fun `classic pack - two digit name, gif for 11 and 23`() {
        assertEquals("https://lain.bgm.tv/img/smiles/bgm/01.png", BangumiStickers.imageUrlOf("(bgm1)"))
        assertEquals("https://lain.bgm.tv/img/smiles/bgm/10.png", BangumiStickers.imageUrlOf("(bgm10)"))
        assertEquals("https://lain.bgm.tv/img/smiles/bgm/11.gif", BangumiStickers.imageUrlOf("(bgm11)"))
        assertEquals("https://lain.bgm.tv/img/smiles/bgm/23.gif", BangumiStickers.imageUrlOf("(bgm23)"))
    }

    @Test
    fun `tv pack - file name is id minus the 23 classic ones`() {
        assertEquals("https://lain.bgm.tv/img/smiles/tv/01.gif", BangumiStickers.imageUrlOf("(bgm24)"))
        // (bgm38) 是站内最出名的那枚, Bangumi 自己的 HTML 里就是 tv/15.gif
        assertEquals("https://lain.bgm.tv/img/smiles/tv/15.gif", BangumiStickers.imageUrlOf("(bgm38)"))
        assertEquals("https://lain.bgm.tv/img/smiles/tv/102.gif", BangumiStickers.imageUrlOf("(bgm125)"))
    }

    @Test
    fun `vs and 500 packs`() {
        assertEquals("https://lain.bgm.tv/img/smiles/tv_vs/bgm_200.png", BangumiStickers.imageUrlOf("(bgm200)"))
        assertEquals("https://lain.bgm.tv/img/smiles/tv_vs/bgm_238.png", BangumiStickers.imageUrlOf("(bgm238)"))
        assertEquals("https://lain.bgm.tv/img/smiles/tv_500/bgm_500.gif", BangumiStickers.imageUrlOf("(bgm500)"))
        assertEquals("https://lain.bgm.tv/img/smiles/tv_500/bgm_502.png", BangumiStickers.imageUrlOf("(bgm502)"))
    }

    @Test
    fun `character packs - two digit name`() {
        assertEquals("https://lain.bgm.tv/img/smiles/musume/musume_06.gif", BangumiStickers.imageUrlOf("(musume_06)"))
        assertEquals("https://lain.bgm.tv/img/smiles/blake/blake_118.gif", BangumiStickers.imageUrlOf("(blake_118)"))
        // 97、98 只有 Blake 娘有
        assertNull(BangumiStickers.imageUrlOf("(musume_97)"))
        assertEquals("https://lain.bgm.tv/img/smiles/blake/blake_97.gif", BangumiStickers.imageUrlOf("(blake_97)"))
    }

    @Test
    fun `kanmoji - file name is the order in the grammar`() {
        assertEquals("https://lain.bgm.tv/img/smiles/1.gif", BangumiStickers.imageUrlOf("(=A=)"))
        assertEquals("https://lain.bgm.tv/img/smiles/6.gif", BangumiStickers.imageUrlOf("(@_@)"))
        assertEquals("https://lain.bgm.tv/img/smiles/16.gif", BangumiStickers.imageUrlOf("(LOL)"))
    }

    @Test
    fun `unknown codes resolve to null instead of a wrong url`() {
        // 各包之间的空档与包外的 id
        assertNull(BangumiStickers.imageUrlOf("(bgm0)"))
        assertNull(BangumiStickers.imageUrlOf("(bgm126)"))
        assertNull(BangumiStickers.imageUrlOf("(bgm199)"))
        assertNull(BangumiStickers.imageUrlOf("(bgm530)"))
        assertNull(BangumiStickers.imageUrlOf("(musume_00)"))
        assertNull(BangumiStickers.imageUrlOf("(musume_119)"))
        assertNull(BangumiStickers.imageUrlOf("(=X=)"))
    }

    @Test
    fun `findTokens picks up character codes but not ordinary parentheses`() {
        assertEquals(
            listOf("(musume_06)", "(blake_12)"),
            BangumiStickers.findTokens("前(musume_06)中(cast)后(blake_12)").map { it.value },
        )
        assertTrue(BangumiStickers.findTokens("这是(某个)括号 (bgm999) (musume_999)").isEmpty())
    }

    @Test
    fun `findTokens reports positions in order`() {
        val text = "a(musume_06)b"
        val tokens = BangumiStickers.findTokens(text)
        assertEquals(1, tokens.size)
        assertEquals(1, tokens[0].range.first)
        assertEquals("a", text.substring(0, tokens[0].range.first))
        assertEquals("b", text.substring(tokens[0].range.last + 1))
    }

    @Test
    fun `every declared sticker resolves to a url`() {
        val unresolved = BangumiStickers.packs
            .flatMap { it.tokens }
            .filter { BangumiStickers.imageUrlOf(it) == null }
        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    /**
     * 回应编号 -> 表情代码. 这 21 条是 `bangumi/server-private` 的 `lib/like.ts` 里
     * `ALLOWED_COMMON_REACTIONS` / `ALLOWED_SUBJECT_COLLECT_REACTIONS` / `HIDDEN_REACTIONS`
     * 三张表的注释逐条抄下来的 (`0, // bgm67` 这种形式), 即 Bangumi 自己声明的对应关系.
     */
    @Test
    fun `reaction value maps to sticker code`() {
        val expected = mapOf(
            0 to 67, // 历史遗留的"赞", 唯一不符合偏移规则的
            79 to 63, 54 to 38, 140 to 124, 62 to 46,
            122 to 106, 104 to 88, 80 to 64, 141 to 125,
            88 to 72, 85 to 69, 90 to 74,
            // HIDDEN_REACTIONS
            53 to 37, 92 to 76, 118 to 102, 60 to 44, 128 to 112,
            47 to 31, 68 to 52, 137 to 121, 76 to 60, 132 to 116,
        )
        for ((value, code) in expected) {
            assertEquals("(bgm$code)", BangumiStickers.reactionStickerToken(value), "reaction $value")
        }
    }

    @Test
    fun `reaction sticker always has an image`() {
        // 回应只取自 TV 那一包, 所以换算出来的代码必然在表里 —— 否则回应就只剩一个光秃秃的数字
        val values = listOf(0, 47, 53, 54, 60, 62, 68, 76, 79, 80, 85, 88, 90, 92, 104, 118, 122, 128, 132, 137, 140, 141)
        for (value in values) {
            val token = BangumiStickers.reactionStickerToken(value)
            assertTrue(BangumiStickers.imageUrlOf(token) != null, "no image for reaction $value ($token)")
        }
    }

    @Test
    fun `reaction token of transport value`() {
        assertEquals("(bgm38)", BangumiStickers.reactionStickerTokenOf("bgm54"))
        assertEquals("(bgm67)", BangumiStickers.reactionStickerTokenOf("bgm0"))
        assertNull(BangumiStickers.reactionStickerTokenOf("54"))
        assertNull(BangumiStickers.reactionStickerTokenOf("bgmx"))
    }
}
