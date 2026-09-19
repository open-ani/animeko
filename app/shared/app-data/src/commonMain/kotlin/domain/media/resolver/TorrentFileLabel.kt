/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType

/**
 * 整季包里一个非正片文件的类别与编号, 从文件名读出.
 *
 * 类别按 Bangumi 的剧集类型分, 因为匹配的另一端是 Bangumi 的剧集: SP 集只在 [Kind.SP] 的文件里找,
 * OP 集只在 [Kind.OP] 里找. 编号是发布方在该类别内的序号, 与 Bangumi 的序号是两套体系,
 * 只在同类文件不止一个时用作次级依据.
 *
 * 写法来自 2026-09 对 nyaa 2008 至 2026 年整季包的核对: 3269 个多文件种子、129209 个视频文件名、
 * 615 个发布组. 判别顺序有讲究:
 * jsum 的 `[Vol.01][SP01][NCOP]` 与 Moozzi2 的 `[SP01] NCOP - 02` 里 SP 序号只是花絮的流水号,
 * 真正的类别是后面的 NCOP, 所以花絮关键字先于 SP 标签判断.
 *
 * 分类器只需答两题: 文件是不是正片, 以及对某个 SP 集哪些文件算同类. OP 与 ED 之间、PV 与 OTHER
 * 之间分错不影响这两题, 所以取词以"别把正片判成花絮"为界, 单独出现没有意义的词一律不收.
 */
internal data class TorrentFileLabel(val kind: Kind, val number: Int?) {
    enum class Kind {
        /** SP、OVA、OAD、S00Exx、特典、番外. */
        SP,
        OP,
        ED,

        /** PV、CM、预告 (Preview、Yokoku). */
        PV,

        /** Menu、Interview、Logo、Announcement、Commentary 一类, 不对应任何 Bangumi 剧集. */
        OTHER,
    }

    companion object {
        /**
         * CRC32 尾缀里的十六进制会凑出标签: `[ED12A80D]` 的开头就是 ED, `(ED6EBF92)` 同理.
         * 判别前先剥掉, 否则整整一季正片会因为尾缀被判成 ED.
         */
        private val crc32Suffix = Regex("""\[[0-9A-Fa-f]{8}]|\([0-9A-Fa-f]{8}\)""")

        /**
         * 标签两侧的分隔符写成显式字符类, 不用 `\b`. 下划线是 ASCII 词字符, `Character_PV1`、
         * `_OP1_`、`OREDAM_OP` 里标签前面没有词边界; 标签后面接数字时 (`PV01`、`CM01`) 同样没有.
         * 用 `\b` 的旧写法在这两种上全部落空.
         */
        private const val HEAD = """(?<![A-Za-z0-9])"""

        /** 完整单词的左边界. 与 [HEAD] 的区别是允许前面是数字: `01special` 仍算 special. */
        private const val WORD_HEAD = """(?<![A-Za-z])"""

        /** 右边界只挡字母不挡数字, 这样 `PV01` 能中而 `OPUS`、`Especial` 不会. */
        private const val TAIL = """(?![A-Za-z])"""

        private const val CREDITLESS =
            """(?:Creditless|Textless|Clean|Non[ _-]?Telop|ノンテロップ|ノンクレジット)[ _\-\[\]]*"""

        private val patterns: List<Pair<Regex, Kind>> = listOf(
            Regex("""\bmenu""", RegexOption.IGNORE_CASE) to Kind.OTHER,
            // IV 必须带序号: 不带的那个 IV 是罗马数字第四季, `Overlord IV - 01` 是正片.
            Regex(
                """${HEAD}IV\d+$TAIL|interview|\blogo\b|announcement|commentary|scans?\b""" +
                        """|eyecatch|staff ?credit|recording|${WORD_HEAD}event[ _\-]?\d""",
                RegexOption.IGNORE_CASE,
            ) to Kind.OTHER,
            // 裸 OP 与 OPv1 都要中. `OPv1` 的 v1 是版本号, 所以匹配只吃 OP 两个字母, 让序号照旧取空.
            Regex(
                """NCOP|$CREDITLESS(?:OP|Opening)|$HEAD(?:OP|Opening)(?:$TAIL|(?=v\d))|${HEAD}NC$TAIL""",
                RegexOption.IGNORE_CASE,
            ) to Kind.OP,
            Regex(
                """NCED|$CREDITLESS(?:ED|Ending)|$HEAD(?:ED|Ending)(?:$TAIL|(?=v\d))""",
                RegexOption.IGNORE_CASE,
            ) to Kind.ED,
            Regex(
                """${HEAD}PV$TAIL|${HEAD}CM$TAIL|TV-?CM|preview|yokoku|trailer|予告""" +
                        """|teaser|${WORD_HEAD}spot$TAIL|promotion[ _\-]?video|特報""",
                RegexOption.IGNORE_CASE,
            ) to Kind.PV,
            Regex("""S00E\d+""", RegexOption.IGNORE_CASE) to Kind.SP,
            // special 要左边界: 语料里有 `Murumuru-sensei Especial - 03` 与 `The Unspecialized`.
            // extra 只收复数形式, 单数会吃掉 `Fate Extra Last Encore - 01` 这个作品名;
            // live 干脆不收, `Love Live! Sunshine!! - 01` 是正片.
            Regex(
                """${HEAD}SP$TAIL|${HEAD}OAD$TAIL|${HEAD}OVA$TAIL""" +
                        """|${WORD_HEAD}special|${WORD_HEAD}tokuten|${WORD_HEAD}extras$TAIL""" +
                        """|${WORD_HEAD}bonus$TAIL|${WORD_HEAD}omake$TAIL|${WORD_HEAD}recaps?$TAIL""" +
                        """|${WORD_HEAD}digest$TAIL|${WORD_HEAD}drama$TAIL""" +
                        """|特典|特别篇|特別篇|番外|小剧场|総集編|おまけ""",
                RegexOption.IGNORE_CASE,
            ) to Kind.SP,
        )

        /**
         * 目录名说明类别的写法: `Extras/Clean Opening.mkv` 这样文件名里一个类别词都没有的,
         * 只有目录名能判. 关键字挂在成分末尾即可, 不必独占整个成分 — `BD Menu`、`Disc 2 - Bonus`
         * 都是真实写法.
         */
        private val dirPatterns: List<Pair<Regex, Kind>> = listOf(
            Regex("""$HEAD(?:NCOP|OP|Opening)s?\d{0,2}${'$'}""", RegexOption.IGNORE_CASE) to Kind.OP,
            Regex("""$HEAD(?:NCED|ED|Ending)s?\d{0,2}${'$'}""", RegexOption.IGNORE_CASE) to Kind.ED,
            Regex("""$HEAD(?:Menu|メニュー|Interview|访谈)s?\d{0,2}${'$'}""", RegexOption.IGNORE_CASE) to Kind.OTHER,
            Regex("""$HEAD(?:PV|CM|Preview|Trailer|Teaser|予告)s?\d{0,2}${'$'}""", RegexOption.IGNORE_CASE) to Kind.PV,
            Regex(
                """$HEAD(?:SP|Special|Extra|Bonus|Tokuten|OVA|OAD|NC|Creditless|Omake""" +
                        """|特典|映像特典|特典映像|おまけ|オマケ|花絮|その他)s?\d{0,2}${'$'}""",
                RegexOption.IGNORE_CASE,
            ) to Kind.SP,
        )

        /**
         * 紧跟标签的序号: `NCOP01`, `NCED_EP12`, `[PV][01]`, `Creditless ED_1`, `Special Recap][03]`,
         * `[SP01] NCOP - 02`. 三位以内且后面不能再接字母或数字: `[SP][1080P]` 里的 1080 是分辨率,
         * `OPv1` 的 v1 是版本 (单个字母不算隔开的词).
         */
        private val trailingNumber =
            Regex("""^[\]\[_ -]*(?:[A-Za-z]{2,}[\]\[_ -]*)?(\d{1,3})(?![\dA-Za-z])""", RegexOption.IGNORE_CASE)
        private val seasonPrefix = Regex("""^S\d+E""", RegexOption.IGNORE_CASE)
        private val firstNumber = Regex("""\d+""")

        fun of(fileName: String): TorrentFileLabel? {
            val parts = fileName.replace('\\', '/').split('/')
            val name = parts.last().substringBeforeLast('.').replace(crc32Suffix, " ")
            for ((pattern, kind) in patterns) {
                val match = pattern.find(name) ?: continue
                val number = trailingNumber.find(name.substring(match.range.last + 1))
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: firstNumber.find(match.value.replace(seasonPrefix, ""))?.value?.toIntOrNull()
                return TorrentFileLabel(kind, number)
            }
            // 文件名说不出类别时看目录, 从最靠近文件的一层往外找.
            // 种子根目录不能参与: 根目录名列的是整包的内容清单, `Title - TV + OVA + SP` 末尾正好是 SP,
            // 按它判会把整包连正片一起当成花絮. 但单看一条路径分不出第一段是不是根目录, 那取决于引擎
            // 给的路径带不带根目录, 所以剥根目录是 TorrentMediaResolver 拿到整份清单之后的事,
            // 这里只跳过带加号的目录, 那同样是内容清单的写法.
            for (i in parts.size - 2 downTo 0) {
                val dir = parts[i].trim()
                if (dir.contains('+')) continue
                for ((pattern, kind) in dirPatterns) {
                    // 序号只有正片匹配会读, 花絮的序号无人使用, 目录判定不再去文件名里找一个.
                    if (pattern.containsMatchIn(dir)) return TorrentFileLabel(kind, null)
                }
            }
            return null
        }

        /** Bangumi 剧集要在哪一类文件里找. 正片与 MAD 返回 null. */
        fun kindOf(sort: EpisodeSort): Kind? {
            if (sort !is EpisodeSort.Special) return null
            return when (sort.type) {
                EpisodeType.SP, EpisodeType.OVA, EpisodeType.OAD -> Kind.SP
                EpisodeType.OP -> Kind.OP
                EpisodeType.ED -> Kind.ED
                EpisodeType.PV -> Kind.PV
                EpisodeType.MainStory, EpisodeType.MAD -> null
            }
        }
    }
}
