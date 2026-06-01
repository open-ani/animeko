/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import org.jetbrains.compose.resources.DrawableResource

/**
 * Bangumi 表情的图片地址表.
 *
 * 表情在 BBCode 里只是一段代码 (`(bgm38)` / `(musume_06)` / `(=A=)`), 图在 Bangumi 的图片站上,
 * 没有接口能查代码对应哪张图 —— 只能按 Bangumi 自己的命名规则拼地址. 这里的规则与官方客户端
 * 的表情表 (`bangumi/Bangumi-iOS` 的 `SmileyCatalog.swift`) 一致.
 *
 * 图**不随包**: 渲染层按地址现拉, 走 Coil 的内存/磁盘缓存, 命中缓存就不再下载
 * (见 `RichTextDefaults.AnnotatedText`). 所以 Bangumi 以后再加表情包, 在 [packs] 里加一项就行,
 * 既不用往包里塞图, 也不用动渲染层.
 *
 * 随包的那 125 张 ([BangumiCommentSticker]) 保留着当离线快路径: 有本地图就不必等网络.
 */
object BangumiStickers {
    /** Bangumi 的图片站. */
    private const val IMAGE_HOST = "https://lain.bgm.tv"

    private const val SMILES = "/img/smiles"

    /** 回应编号与表情代码之间的固定偏移, 见 [reactionStickerToken]. */
    private const val REACTION_CODE_OFFSET = 16

    /** Classic 包里是 gif 的那两枚, 其余是 png. */
    private val CLASSIC_GIF_IDS = setOf(11, 23)

    /** TV 500+ 包里是 gif 的那几枚, 其余是 png. */
    private val TV_500_GIF_IDS = setOf(500, 501, 505, 515, 516, 517, 518, 519, 521, 522, 523)

    /** Bangumi 娘: 97、98 是 Blake 娘独有的 (评分表情), musume 没有. */
    private val MUSUME_IDS = (1..118).filter { it != 97 && it != 98 }

    private val BLAKE_IDS = (1..118).toList()

    /**
     * 16 枚颜文字, **顺序必须与 `BBCode.g4` 里 `text_stiker` 的分支一致** ——
     * 它既是文法认得的全部颜文字, 也决定了图片文件名 (`smiles/1.gif` ~ `smiles/16.gif`).
     */
    private val KANMOJI_TOKENS = listOf(
        "(=A=)", "(=w=)", "(-w=)", "(S_S)", "(=v=)", "(@_@)", "(=W=)", "(TAT)",
        "(T_T)", "(='=)", "(=3=)", "(= =')", "(=///=)", "(=.,=)", "(:P)", "(LOL)",
    )

    /**
     * 一个表情包.
     *
     * @param name Bangumi 表情面板上的分组名. 目前只用于读代码 / 以后做表情选择器.
     * @param tokens 包里所有表情的完整 BBCode 形态 (含括号), 顺序即面板顺序.
     * @param relativePath 由 token 拼出图片在图片站上的路径.
     */
    class Pack internal constructor(
        val name: String,
        val tokens: List<String>,
        internal val relativePath: (token: String) -> String,
    )

    /**
     * 全部表情包. **加新表情包只需在这里加一项.**
     *
     * 前六项与官方客户端的六个分组一一对应; 最后一项是最早的颜文字
     * (官方表情表里没收, 但 BBCode 文法 (`BBCode.g4` 的 `text_stiker`) 一直支持, 评论里也在用).
     */
    val packs: List<Pack> = listOf(
        // (bgm1)~(bgm23): 文件名两位补零
        Pack("Classic", bgmTokens(1..23)) { token ->
            val id = token.bgmId()
            "$SMILES/bgm/${id.pad2()}.${if (id in CLASSIC_GIF_IDS) "gif" else "png"}"
        },
        // (bgm24)~(bgm125): 文件名是"减掉前 23 枚"之后的序号, 两位补零
        Pack("BangumiTV", bgmTokens(24..125)) { token ->
            "$SMILES/tv/${(token.bgmId() - 23).pad2()}.gif"
        },
        // (bgm200)~(bgm238)
        Pack("TV VS", bgmTokens(200..238)) { token ->
            "$SMILES/tv_vs/bgm_${token.bgmId()}.png"
        },
        // (bgm500)~(bgm529)
        Pack("TV 500+", bgmTokens(500..529)) { token ->
            val id = token.bgmId()
            "$SMILES/tv_500/bgm_$id.${if (id in TV_500_GIF_IDS) "gif" else "png"}"
        },
        // (musume_01)~(musume_118), 缺 97、98
        Pack("Bangumi 娘", characterTokens("musume", MUSUME_IDS)) { token ->
            "$SMILES/musume/${token.unwrap()}.gif"
        },
        // (blake_01)~(blake_118)
        Pack("Blake 娘", characterTokens("blake", BLAKE_IDS)) { token ->
            "$SMILES/blake/${token.unwrap()}.gif"
        },
        // 最早的 16 枚颜文字: 图在 smiles 根目录下, 文件名即在 KANMOJI_TOKENS 里的序号
        Pack("颜文字", KANMOJI_TOKENS) { token ->
            "$SMILES/${KANMOJI_TOKENS.indexOf(token) + 1}.gif"
        },
    )

    private val PATH_BY_TOKEN: Map<String, String> = buildMap {
        packs.forEach { pack ->
            pack.tokens.forEach { token ->
                // 万一两个包声明了同一个代码, 以靠前的包为准 (与 Bangumi 面板顺序一致)
                if (token !in this) put(token, pack.relativePath(token))
            }
        }
    }

    /**
     * 括号里是"字母开头 + 字母数字下划线"的一段, 才有可能是表情代码.
     * 是不是真的还要过 [PATH_BY_TOKEN], 这里只是先把绝大多数普通括号排除掉.
     */
    private val POSSIBLE_TOKEN_REGEX = Regex("""\([A-Za-z][A-Za-z0-9_]*\)""")

    /**
     * 表情代码 (完整形态, 如 `"(bgm38)"` / `"(musume_06)"` / `"(=A=)"`) 对应的图片地址.
     * 不认识的代码返回 `null` —— 调用方应当退化成显示原文本, 而不是显示一块空白.
     */
    fun imageUrlOf(token: String): String? = PATH_BY_TOKEN[token]?.let { IMAGE_HOST + it }

    /** 这段代码是不是表情. */
    operator fun contains(token: String): Boolean = token in PATH_BY_TOKEN

    /**
     * 一枚表情的取图方式: 随包图 ([resource]) 是离线快路径, 网络地址 ([imageUrl]) 是通路,
     * 两者都没有就说明这个代码**不认识** ([hasImage] 为 false), 调用方必须退化成显示 [token] 原文本
     * —— 画成空白等于整枚表情凭空消失, 换个不相干的占位图标 (比如 Face) 更糟: 用户连它本来是什么都看不出来.
     *
     * 只说"图从哪来", 不说"画成什么": 正文里是行内富文本元素, 回应条上是个 24dp 小图标,
     * 两处渲染差得远, 强行统一反而把渲染绑死在这里.
     */
    class Sticker internal constructor(
        /** 完整的表情代码, 如 `"(bgm38)"`. [hasImage] 为 false 时调用方原样显示它. */
        val token: String,
        val imageUrl: String?,
        val resource: DrawableResource?,
    ) {
        val hasImage: Boolean get() = imageUrl != null || resource != null
    }

    /**
     * 表情代码 (完整形态) -> 取图方式. **token -> 表情的唯一入口**: 随包图查找与"认不出"的判定
     * 都在这里, 各处自己拼一遍就会出现同一枚表情在正文里退化成 `(bgm600)`、在回应条上却变成
     * 一个看不出是什么的图标这种不一致.
     */
    fun stickerOf(token: String): Sticker = Sticker(
        token = token,
        imageUrl = imageUrlOf(token),
        resource = token.bundledResource(),
    )

    /** 随包的那 125 张只覆盖 `(bgm1)`~`(bgm125)`; 别的包 (颜文字 / 娘) 一律现拉. */
    private fun String.bundledResource(): DrawableResource? {
        val unwrapped = unwrap()
        if (!unwrapped.startsWith("bgm")) return null
        return BangumiCommentSticker[unwrapped.removePrefix("bgm").toIntOrNull() ?: return null]
    }

    /**
     * Bangumi「回应」(给别人的评论贴表情) 的编号 -> 表情代码.
     *
     * 回应用的是**另一套编号** (数据库 `chii_likes.value`), 与表情代码整体差 [REACTION_CODE_OFFSET]:
     * `54` = `(bgm38)`, `104` = `(bgm88)`, `141` = `(bgm125)`. `0` 是历史遗留的"赞", 单独对应 `(bgm67)`.
     *
     * 权威出处: `bangumi/server-private` 的 `lib/like.ts` —— `ALLOWED_COMMON_REACTIONS` /
     * `ALLOWED_SUBJECT_COLLECT_REACTIONS` / `HIDDEN_REACTIONS` 三张表的注释逐条写明了对应关系,
     * 21 条全部符合上面这条规则. 网页端则是把图名 (`/img/smiles/tv/65.gif`) 直接下发, 不走代码.
     *
     * 回应只取自 Bangumi TV 那一包 (`(bgm24)`..`(bgm125)`), 所以拼出来的代码必然在 [PATH_BY_TOKEN] 里;
     * 万一 Bangumi 以后放开到别的包, [imageUrlOf] 会返回 null, 调用方按"认不出"处理即可.
     */
    fun reactionStickerToken(reactionValue: Int): String =
        if (reactionValue == 0) "(bgm67)" else "(bgm${reactionValue - REACTION_CODE_OFFSET})"

    /**
     * 传输用的回应值 (`"bgm54"`, 见 `EpisodeCommentReaction.value`) -> 表情代码.
     *
     * 注意那个 `bgm` 前缀里的数字**不是**表情代码, 而是回应编号 —— 直接当代码用会显示错的表情
     * (差 16 枚). 见 [reactionStickerToken].
     */
    fun reactionStickerTokenOf(reactionValue: String): String? {
        if (!reactionValue.startsWith("bgm")) return null
        val value = reactionValue.removePrefix("bgm").toIntOrNull() ?: return null
        return reactionStickerToken(value)
    }

    /**
     * 从纯文本里找出表情代码.
     *
     * 用途: `(musume_06)` 这类**带下划线**的代码 BBCode 文法没有对应产生式 (`bgm_sticker` 只认
     * `(bgm` + 数字), 会被当普通文本原样吐出来, 所以在这一层补一道扫描. 只认 [contains] 认得的,
     * 普通的括号内容 (`(cast)`) 不会被误判.
     *
     * @return 匹配到的代码及其在 [text] 里的位置, 按出现顺序.
     */
    fun findTokens(text: String): List<MatchResult> =
        POSSIBLE_TOKEN_REGEX.findAll(text).filter { it.value in PATH_BY_TOKEN }.toList()

    private fun bgmTokens(ids: IntRange): List<String> = ids.map { "(bgm$it)" }

    private fun characterTokens(prefix: String, ids: List<Int>): List<String> =
        ids.map { "(${prefix}_${it.pad2()})" }

    /** 去掉外层括号: `"(bgm38)"` -> `"bgm38"`. */
    private fun String.unwrap(): String = removeSurrounding("(", ")")

    private fun String.bgmId(): Int = unwrap().removePrefix("bgm").toInt()

    private fun Int.pad2(): String = toString().padStart(2, '0')
}
