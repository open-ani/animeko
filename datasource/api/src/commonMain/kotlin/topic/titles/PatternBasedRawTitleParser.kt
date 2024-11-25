/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.topic.titles

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FrameRate
import me.him188.ani.datasources.api.topic.MediaOrigin
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage

/**
 * 人工适配的标题解析器
 */
class PatternBasedRawTitleParser : RawTitleParser() {
    private val brackets = Regex("""[\[【(](.*?)[]】)]""")
    private val newAnime = Regex("(?:★?|★(.*)?)([0-9]|[一二三四五六七八九十]{0,4}) ?[月年] ?(?:新番|日剧)★?")
    private val specialEpisode = Regex("★特别篇") // 风之圣殿
    private val excludeTags = arrayOf(newAnime, specialEpisode, Regex("(短篇动画)|(招募)"))

    override fun parse(text: String, allianceName: String?, builder: ParsedTopicTitle.Builder) {
        return parse(
            text = text,
            allianceName = allianceName,
            collectTag = { builder.tags.add(it) },
            collectChineseTitle = { builder.chineseTitle = it },
            collectOtherTitle = { builder.otherTitles.add(it) },
            collectEpisode = { builder.episodeRange = it },
            collectResolution = { builder.resolution = it },
            collectFrameRate = { builder.frameRate = it },
            collectMediaOrigin = { builder.mediaOrigin = it },
            collectSubtitleLanguage = { builder.subtitleLanguages.add(it) },
        )
    }

    fun parse(
        text: String,
        allianceName: String?,
        collectTag: (title: String) -> Unit,
        collectChineseTitle: (String) -> Unit,
        collectOtherTitle: (String) -> Unit,
        collectEpisode: (EpisodeRange) -> Unit,
        collectResolution: (Resolution) -> Unit,
        collectFrameRate: (FrameRate) -> Unit,
        collectMediaOrigin: (MediaOrigin) -> Unit,
        collectSubtitleLanguage: (SubtitleLanguage) -> Unit,
    ) {
        val exceptTagsBuilder = StringBuilder()
        var index = 0
        var unknownTags = mutableListOf<String>()
        for (result in brackets.findAll(text)) {
            if (index < result.range.first) {
                exceptTagsBuilder.append(text.subSequence(index until result.range.first))
            }
            index = result.range.last + 1

            val tagOrTags = result.groups[1]!!.value // can be "WebRip 1080p HEVC-10bit AAC" or "简繁内封字幕"
            for (tag in splitTags(tagOrTags)) {
                val anyMatched = processTag(
                    tag = tag,
                    collectSubtitleLanguage = collectSubtitleLanguage,
                    collectResolution = collectResolution,
                    collectFrameRate = collectFrameRate,
                    collectMediaOrigin = collectMediaOrigin,
                    collectEpisode = {
                        if (allianceName == "天使动漫论坛") return@processTag
//                        if (text.indexOf(tag) < text.indexOf()) { // ignore tag that appeared before titles
                        collectEpisode(it)
//                        }
                    },
                )

                if (!anyMatched) {
                    unknownTags.add(tag.trim())
                }
            }
        }
        if (index < text.length) {
            exceptTagsBuilder.append(text.subSequence(index until text.length))
        }
        unknownTags = unknownTags.filterNotTo(mutableListOf()) { tag -> excludeTags.any { it.find(tag) != null } }
        unknownTags.removeFirstOrNull() // 字幕组名称

        // special cases
        // ★7月新番 【新网球王子 U-17世界杯】【The Prince of Tennis II - U-17 World Cup】【02v2】GB MP4_1080P
        // [Amor字幕组][组长女儿与照料专员(组长女儿与保姆)/Kumichou Musume to Sewagakari][02][1080P][CHS_JP][WEB-DL][MP4]
        // [Billion Meta Lab] Lycoris Recoil - 07_1080p_x264_CHS&CHT_简繁内封

        val exceptTags = exceptTagsBuilder.toString()
            .replace(newAnime) { "" }
            .replace(specialEpisode) { "" }
        if (exceptTags.isBlank() || allianceName == "极影字幕社" || text.contains("沸羊羊")) {
            // B 类
            val primaryTitles = unknownTags.removeFirstOrNull()
                ?: return // may contain multiple languages separated by '/' or other delimiters
            var collectedOtherTitle = false
            while (unknownTags.isNotEmpty()) {
                val name = unknownTags.removeAt(0)
                if (name.count { it == ' ' } > 2) {
                    collectOtherTitle(name)
                    collectedOtherTitle = true
                } else {
                    unknownTags.add(0, name)
                    break
                }
            }

            if (collectedOtherTitle) {
                // 诸神kamigami字幕组 [诸神字幕组][夏日重现][Summer Time Rendering][16][简繁日语字幕][1080P][MKV HEVC] 約2條評論
                collectChineseTitle(primaryTitles)
            } else {
                parseNames(primaryTitles, collectChineseTitle, collectOtherTitle)
            }

            for (unknownTag in unknownTags) {
                collectTag(unknownTag)
            }
        } else {
            // A 类
            for (unknownTag in unknownTags) {
                collectTag(unknownTag)
            }

            exceptTags.substringAfterLast('-', "").takeIf { it.isNotBlank() }?.trim()?.let { maybeEpisode ->
                if (maybeEpisode.contains("_")) {
                    maybeEpisode.splitToSequence("_").forEach {
                        processTag(
                            tag = it,
                            collectSubtitleLanguage = collectSubtitleLanguage,
                            collectResolution = collectResolution,
                            collectFrameRate = collectFrameRate,
                            collectMediaOrigin = collectMediaOrigin,
                            collectEpisode = collectEpisode,
                        )
                    }
                } else {
                    collectEpisode(EpisodeRange.single(EpisodeSort(maybeEpisode)))
                }
            }

            parseNames(exceptTags, collectChineseTitle, collectOtherTitle)
        }

        // A 类动画 标题在标签之外
        // ANi [ANi] 杜鵑婚約 [特別篇] - 14 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]
        // ANi [ANi] 即使如此依舊步步進逼（僅限港澳台地區） - 05 [1080P][Bilibili][WEB-DL][AAC AVC][CHT CHS][MP4] 約1條評論
        // ANi [ANi] My Stepmoms Daughter Is My Ex - 繼母的拖油瓶是我的前女友 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4] 約1條評論
        // 黑岩射手吧字幕组 [黑岩射手吧字幕组] Black Rock Shooter - Dawn Fall [12 END][1080p][简繁内挂] 約3條評論
        // MingYSub [MingY] 影宅 第二季 / Shadows House S2 [04][1080p][简体内嵌] 約1條評論
        // MingYSub [MingY] 銃動彼岸花 / Lycoris Recoil [05][1080p][繁日內嵌]
        // WBX-SUB [WBX-Raws] Go！プリンセスプリキュア/Go！Princess Precure/Go！Princess 光之美少女 TV EP01-50 全 [BDrip][HEVC 1080P FLAC]（附Musical Show） 約1條評論
        // 届恋字幕组 [届恋字幕组] 偶像大师 灰姑娘女孩剧场 Extra Stage / THE IDOLM@STER CINDERELLA GIRLS Theater Extra Stage - 48 [1080p HEVC-10bit AAC][简繁内封][完] 約4條評論
        // 極彩字幕组 [极彩字幕组] 异世界迷宫里的后宫生活 / Isekai Meikyuu de Harem wo [无修正][04][1080P][简繁内封] 約3條評論
        // [喵萌奶茶屋&LoliHouse] 继母的拖油瓶是我的前女友 / Mamahaha no Tsurego ga Motokano datta - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]
        // Lilith-Raws [Lilith-Raws] Overlord IV - 05 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4] 約1條評論
        // NC-Raws [NC-Raws] OVERLORD IV / Overlord S4 - 05 (B-Global 3840x2160 HEVC AAC MKV)
        // 桜都字幕组 [桜都字幕组] RWBY 冰雪帝国 / RWBY Hyousetsu Teikoku [05][1080p][简繁内封] 約1條評論
        // 天月動漫&發佈組 [Skymoon-Raws] 新網球王子: U-17 WORLD CUP / Shin Tennis no Ouji-sama: U-17 World Cup - 05 [ViuTV][WEB-RIP][720p][HEVC AAC][CHT][MP4]
        // 天月動漫&發佈組 [天月搬運組] 異世界迷宮裡的後宮生活 / Isekai Meikyuu de Harem wo - 05 [1080P][簡繁日外掛] 約1條評論
        // LoliHouse [LoliHouse] 邪神与厨二病少女X / Jashin-chan Dropkick X - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕] 約1條評論
        // LoliHouse [喵萌Production&LoliHouse] LoveLive! 超级明星!! 2期 / Love Live! Superstar!! S2 - 03 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕] 約1條評論

        // A 类动画 多语言标题
        // 离谱Sub [离谱Sub] 打工吧！！魔王大人 第二季 / はたらく魔王さま!! / Hataraku Maou-sama!! [03v2][HEVC AAC][1080p][PGS简繁内封] 約2條評論
        // 驯兽师联盟 【馴獸師聯盟】數碼寶貝/數碼暴龍/數碼獸幽靈遊戲[Digimon Ghost Game][36][1080p][繁日字幕]
        // LoliHouse [喵萌奶茶屋&LoliHouse] 风都侦探 / Fuuto Tantei / FUUTO PI - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕] 約1條評論
        // Little Subbers! [❀拨雪寻春❀] 夏日重現 / 夏日時光 / サマータイムレンダ / Summer Time Rendering [15][1080p][繁體內嵌] 約1條評論
        // Little Subbers! [❀拨雪寻春❀] 四月是你的谎言 / 四月は君の嘘 / Shigatsu wa Kimi no Uso [BDRip 1080p][HEVC-10bit FLAC][简繁日内封] 約5條評論

        // A 类动画 英文在前
        // VCB-Studio [VCB-Studio] BANANA FISH / 战栗杀机 10-bit 1080p HEVC BDRip [Fin] 約1條評論
        // 梦蓝字幕组 [梦蓝字幕组]New Doraemon 哆啦A梦新番[716][2022.07.23][AVC][10080P][GB_JP]

        // A 类动画 `-` 分割
        // 云光字幕组 [云光字幕组] 租借女友 - Kanojo, Okarishimasu [15][简体双语][1080p]招募时轴

        // 动画合集 / 季度全集
        // Alchemist RWBY /卷: 1-8 /系列: 1-106 of 106 ( Monty Oum / Monty Oum) [2013-2021, USA , techno-fantasy , action , comedy , school , WEBRip 1080p] 2x MVO + 2x MVO + 2x DVO + Original + Sub (Rus) 約4條評論
        // 离谱Sub [离谱Sub] 朋友遊戲 / 偷摸大雞遊戲 / トモダチゲーム / Tomodachi Game [01-12][修正合集][AVC AAC][1080p][繁體內嵌]
        // VCB-Studio [动漫国字幕组&VCB-Studio] Kujira no Kora wa Sajou ni Utau / 泥鲸之子们在沙地上歌唱 / クジラの子らは砂上に歌う 10-bit 1080p HEVC BDRip [Fin] 約4條評論
        // VCB-Studio [VCB-Studio] Kaifuku Jutsushi no Yarinaoshi / 回复术士的重来人生 / 回復術士のやり直し 10-bit 1080p HEVC BDRip [Fin] 約1條評論
        // 云光字幕组 [云光字幕组]间谍过家家 SPY×FAMILY [合集][简体双语][1080p]招募翻译
        // 云光字幕组 [云光字幕组]间谍过家家 SPY×FAMILY [合集][简体双语][1080p]招募翻译
        // 中肯字幕組 【中肯字幕組】【懷舊老番】【筋肉人II世 Kinnikuman Nisei】【01-51】【BIG5_MP4】【960X720】 約5條評論
        // 风之圣殿 【豌豆字幕组&风之圣殿字幕组】★10月新番[鬼灭之刃 / Kimetsu_no_Yaiba][01-44(S1+S2)][合集][简体][1080P][MP4] v2 約4條評論
        // 波洛咖啡厅 [波洛咖啡厅\PCSUB][国王排名\Ousama Ranking][合集\01-23END][简日\CHS_JP][1080P][MP4_AAC][网盘][急招翻译] 約1條評論

        // 日剧
        // TD-RAWS [TD-RAWS] 摇曳露营2 / 闲散露营2 / Yuru Camp△2 [BDRip 1080p HEVC-10bit FLAC][简体内封字幕] 約3條評論
        // Little字幕组 【Little字幕组】终点之所 (2017) [HDTVrip][1080P][中日双字][MP4]（招募翻译时间轴） 約1條評論
        // 幻月字幕组 【幻月字幕组】【22年日剧】【恶女的一切】【05】【1080P】【中日双语】
        // 魔星字幕团 [MagicStar] ANIMALS / アニマルズ EP07 [WEBDL] [1080p] [ABEMA]【生】
        // 魔星字幕团 【合集】[MagicStar] 各自的断崖 / それぞれの断崖 [WEBDL] [1080p] [HULU]【生】

        // RAW
        // AI-Raws [AI-Raws] 86 不存在的戰區 BDRip 1080p MKV 約3條評論
        // AI-Raws [AI-Raws] 無職転生〜異世界行ったら本気だす〜 Mushoku Tensei BDRip 1080p MKV 約2條評論

        // 音乐
        // TD-RAWS [TD-RAWS] Liyuu First Concert 2022「Fo(u)r YuU」 [BDRip 1080p HEVC-10bit FLAC] 約2條評論
        // DHR動研字幕組 [DHR-Raws]Inori Minase水瀬いのり LIVE TOUR HELLO HORIZON (BDrip FHD HEVC ALAC) 約1條評論
        // 天使动漫论坛 [Hi-Res][220803]TVアニメ『異世界薬局』OP主题歌「夢想的クロニクル」／石原夏織[96kHz/24bit][FLAC]
        // 天使动漫论坛 [Hi-Res][220803]TVアニメ『Lycoris Recoil リコリス・リコイル』OP主题歌「ALIVE」／ClariS[96kHz/24bit][FLAC] 約1條評論

        // 其他
        // 萝莉社活动室 【No.145】もふもふなセーラー服~500枚+500枚~ฅ^•ω•^ฅ
        // 萝莉社活动室 [萝莉哒胖次真的不见了！][第167期][500P]
        // DBD制作组 [DBD-Raws][梅林传奇 第三季/Merlin Season 3][01-13TV全集+花絮][1080P][BDRip][HEVC-10bit][简体外挂][FLAC][MKV]
        // DBD制作组 [DBD-Raws][剧场版 魔法少女伊莉雅：无名少女/劇場版 Fate/kaleid liner プリズマ☆イリヤ Licht 名前の無い少女/Gekijouban Fate/Kaleid Liner Prisma Illya Licht: Namae no Nai Shoujo][正片+特典映像][1080P][BDRip][HEVC-10bit][FLAC][MKV]

        // 特摄
        // Amor字幕组 [Amor字幕组][德凯奥特曼][01][CHS_JP][1080P][HDrip][MP4]
        // 未央阁联盟 [晨曦&九時&滴彩制作组][奥特银河格斗 命运的冲突][JP+EN][Webrip][HEVC Main10P AAC MKV]
        // 肥猫压制 [肥猫压制][FatCatRAW][影星侠][21-28][1080P][HDTVRIP]
        // KRL字幕组 [KRL字幕組][Revice外傳 - 假面騎士Vail][全集][810P] 約4條評論

        // 漫画
        // LoveEcho! [AngelEcho]サイダーのように言葉が湧き上がる 言语如苏打般涌现 第14话

        // C 类动画 标签与标题混合
        // 80v08 瓢蟲少女 Miraculous Tales Of Ladybug And Cat Noir 第一季 全26話 英語發音 中英雙字 720P 約4條評論
        // 华盟字幕社 [澄空学园&华盟字幕社] 欢迎来到实力至上主义的教室2 第04话 MP4 720p 約1條評論
        // 华盟字幕社 [澄空学园&雪飘工作室&华盟字幕社]擅长捉弄的高木同学3 第12话 MP4 720p 完 約2條評論


        // B 类动画 标题在标签之内
        // EMe [BLEACH ANIMATION BEST][死神 千年血战篇 放送纪念！][#03][BDrip][1080p][HEVC 10bit FLAC MKV][日语+简日双语字幕] 約3條評論
        // Liella!の烧烤摊 [Liella!の烧烤摊][Love Live! Superstar!! 第二季][03][简日内嵌][特效歌词][TVRip][1080p][HEVC AAC MP4] 約1條評論
        // 夢幻戀櫻 【夢幻戀櫻字幕組】[Pocket Monsters (2019)][寵物小精靈/精靈寶可夢(2019)][第97-111話][BIG5][1280x720][MP4] 約1條評論
        // 夜莺家族 [夜莺家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1247]小丸子陷入大恐慌[2022.07.24][粤][日][GB][JP][1080P][MP4]
        // AQUA工作室 [AQUA工作室][水星領航員 劇場版 ARIA The CREPUSCOLO][繁簡外掛字幕][BDRIP][1080P][HEVC+DTS+FLACx3][MKV] 約8條評論
        // 爱咕字幕组 【爱咕字幕组】[看得见的女孩] Mieruko-chan - 10-12 END [1080p_AVC] [简体内嵌] 約1條評論
        // 冷番补完字幕组 【冷番补完字幕组】[学园特搜][Campus Special Investigator Hikaruon][OVA][480P][1987][简体外挂] 約1條評論
        // 冷番补完字幕组 [冷番补完字幕组][魔幻美少女 / 奇迹女孩][Miracle Girls][1993][HDTV][01-51 Fin][1080p][x265][内封简繁中字] 約2條評論
        // 百冬練習組 【百冬練習組】【身為女主角 ～被討厭的女主角和秘密的工作～_Heroine Tarumono!】[12END][1080p AVC AAC][繁體] 約2條評論
        // 柯南事务所 [APTX4869][CONAN][名侦探柯南 976 追踪！侦探出租车][HDTV][简体MP4] 約2條評論
        // ARIA吧汉化组 [Aria吧漢化組][水星領航員]AriaTheCrepuscolo[V2][WebRip_1080p_AVC_AAC][繁日內嵌字幕] 約2條評論
        // 虹咲学园烤肉同好会 [虹咲学园烤肉同好会][Love Live! 虹咲学园学园偶像同好会 第二季][13END][简日内嵌][特效歌词][WebRip][1080p][AVC AAC MP4] 約3條評論
        // DHR動研字幕組 【DHR動研字幕組&茉語星夢】[在地下城尋求邂逅是否搞錯了什麼 第四季_DanMachi S4][01][繁體][1080P][MP4] 約1條評論
        // 动音漫影 【Dymy字幕組】【鬼滅之刃 遊郭篇 Kimetsu no Yaiba - Yuukaku Hen】【01-11】【BIG5】【1920X1080】【MP4】【修正合集】 約3條評論
        // 波洛咖啡厅 [波洛咖啡厅\PCSUB][国王排名\Ousama Ranking][合集\01-23END][简日\CHS_JP][1080P][MP4_AAC][网盘][急招翻译] 約1條評論
        // 波洛咖啡厅 [波洛咖啡厅\PCSUB][相合之物\Deaimon][08][简日\CHS_JP][1080P][MP4_AAC][网盘][急招后期]
        // YWCN字幕组 [剧场版][妖怪手表Jam][妖怪学园Y 猫也能成为英雄吗Youkai Gakuen Y Neko wa Hero ni Nareru ka][GB][1080P][BDrip][MP4] 約1條評論	
        // YWCN字幕组 【YWCN字幕组】[妖怪手表!Youkai Watch!][15][GB][1280X720][MP4] 約1條評論
        // 风之圣殿 【豌豆字幕组&风之圣殿字幕组】★10月新番[鬼灭之刃 游郭篇 / Kimetsu_no_Yaiba-Yuukaku_Hen][11(44)][完][简体][1080P][MP4] 約4條評論
        // 风之圣殿 【豌豆字幕组&风之圣殿字幕组】★特别篇[Dr.STONE 新石纪 龙水][SP][简体][1080P][MP4] 約2條評論
        // SW字幕组 【SW字幕组】[宠物小精灵/宝可梦 旅途][115][简日双语字幕][2022.06.17][1080P][AVC][MP4][GB_JP][V2]
        // 中肯字幕組 【中肯字幕組】【1月新番】【川尻小玉的懒散生活】【18】【BIG5_MP4】【1920X1080】
        // 豌豆字幕组 【豌豆字幕组】[王者天下 第四季 / Kingdom_S4][17][简体][1080P][MP4] 約1條評論
        // 轻之国度 [轻之国度字幕组][新来的女佣有点怪/最近雇的女仆有点怪][02][1080P][MP4] 約1條評論
        // 枫叶字幕组 【枫叶字幕组】[宠物小精灵 / 宝可梦 旅途][119][简体][1080P][MP4] 約2條評論
        // 枫叶字幕组 【枫叶字幕组】[短篇动画][宠物小精灵 / 宝可梦 进化][05][简体][1080P][MP4] 約4條評論
        // SweetSub [SweetSub][剧场版回转企鹅罐 RE:cycle of the PENGUINDRUM [前篇] 你的列车是生存战略][WebRip][1080P][HEVC 10bit][简繁日内封] 約3條評論
        // 星空字幕组 [星空字幕組][異世界藥局 / Isekai Yakkyoku][04][繁日雙語][1080P][WEBrip][MP4]（急招校對、後期）
        // 丸子家族 [丸子家族][樱桃小丸子第二期(Chibi Maruko-chan II)][1343-1347][2022.06][简日_繁日内封][1080P][hevc-10bit_aac][MKV]
        // MCE汉化组 【MCE汉化组】[BanG Dream! Morfonication][特别篇][02][简体][1080P][x264 AAC] 約2條評論
        // MCE汉化组 【MCE汉化组】[武藏野！/むさしの！/ 浦和小調 第二季][Musashino!][05][繁體][1080P][x264 AAC]
        // MCE汉化组 【MCE汉化组】[OVERLORD IV][不死者之王 第四季][SP][05][繁體][1080P][x264 AAC]
        // 雪飄工作室(FLsnow) [雪飘工作室][Delicious Party Precure/デリシャスパーティ プリキュア][WEBDL][1080p][21][简繁外挂](检索:光之美少女/Q娃) 約1條評論
        // 风车字幕组 [風車字幕組][名偵探柯南][1049][目暮、刑警事業的危機][1080P][繁體][MP4]
        // GMTeam [GM-Team][国漫][诛仙][Jade Dynasty][2022][01-03][HEVC][GB][4K] 約5條評論
        // 霜庭云花Sub [霜庭云花Sub][夏日重现 / サマータイムレンダ / Summer Time Rendering][15][1080P][AVC][简日内嵌][WebRip][招募]
        // 诸神kamigami字幕组 [诸神字幕组][夏日重现][Summer Time Rendering][16][简繁日语字幕][1080P][MKV HEVC] 約2條評論
        // IET字幕組 [酷漫404][來自深淵 烈日的黃金鄉][04][1080P][WebRip][繁日雙語][AVC AAC][MP4][字幕組招人內詳]
        // 千夏字幕组 【千夏字幕组】【传颂之物 二人的白皇_Utawarerumono Futari no Hakuoro​】[第06话][1080p_AVC][简体] 約2條評論
        // c.c动漫 [c.c動漫][7月新番][異世界迷宮裡的後宮生活][04][BIG5][1080P][MP4][AT-X] 約12條評論
        // 动漫国字幕组 【澄空学园&动漫国字幕组】★07月新番[传颂之物 二人的白皇][06][1080P][简体][MP4] 約1條評論
        // 动漫国字幕组 【动漫国字幕组】★04月新番[夏日时光 / 夏日重现][14-15][1080P][简体][MP4] 約1條評論
        // 动漫国字幕组 【澄空学园&动漫国字幕组】★07月新番[契约之吻 / Engage Kiss][04][1080P][简体][MP4] 約1條評論
        // 幻樱字幕组 【幻樱字幕组】【7月新番】【异世界舅舅 Isekai Ojisan】【04】【GB_MP4】【1920X1080】
        // 幻樱字幕组 【幻櫻字幕組】【加油吧同期醬 Ganbare Douki-chan】【01~13】【BDrip】【BIG5_MP4】【1920X1080】【合集】
        // 爱恋字幕社 [爱恋&漫猫字幕组][7月新番][OVERLORD 第四季][Overlord IV][05+小剧场][1080p][MP4][GB][简中] 約1條評論
        // 悠哈C9字幕社 【悠哈璃羽字幕社】[最近僱的女僕有點奇怪_Saikin Yatotta Maid ga Ayashii][02][x264 1080p][CHT]
        // 极影字幕社 【极影字幕社】 ★4月新番 【间谍过家家】【SPY×FAMILY】【10】GB MP4_720P
        // 喵萌奶茶屋 【喵萌奶茶屋】★07月新番★[契约之吻/Engage Kiss][05][1080p][简体][招募翻译校对] 約2條評論

        // B 类, 特别分隔符
        // 注意 SW字幕组 有两种
        // SW字幕组 [SWSUB][7月新番][继母的拖油瓶是我的前女友\継母の連れ子が元カノだった][004][GB_JP][AVC][1080P][网盘][无修正] 約1條評論
        // 银色子弹字幕组 [银色子弹字幕组][名侦探柯南][第1052集 少年侦探团的试胆冒险][简繁日多语MKV][1080P] 約4條評論
    }

    private fun splitTags(tagOrTags: String): Sequence<String> {
        return sequenceOf(tagOrTags)
//        if (tagOrTags.count { it == ' ' } > 2) {
//            // more possibly be a name
//            return sequenceOf(tagOrTags)
//        }
//        return tagOrTags.splitToSequence(' ')
    }

    private fun parseNames(
        string: String,
        collectChineseTitle: (String) -> Unit,
        collectOtherTitle: (String) -> Unit,
    ) {
        val names = string.substringBeforeLast('-').split('/', '\\', '-', '_')
        names.firstOrNull()?.let(collectChineseTitle)
        names.asSequence().drop(1).mapNotNull { it.trim().takeIf(String::isNotEmpty) }.forEach(collectOtherTitle)
    }

    private fun processTag(
        tag: String,
        collectSubtitleLanguage: (SubtitleLanguage) -> Unit,
        collectResolution: (Resolution) -> Unit,
        collectFrameRate: (FrameRate) -> Unit,
        collectMediaOrigin: (MediaOrigin) -> Unit,
        collectEpisode: (EpisodeRange) -> Unit,
    ): Boolean {
        var anyMatched = false
        anyMatched = anyMatched or tag.parseSubtitleLanguages(collectSubtitleLanguage)
        anyMatched = anyMatched or tag.parseResolution(collectResolution)
        anyMatched = anyMatched or tag.parseFrameRate(collectFrameRate)
        anyMatched = anyMatched or tag.parseMediaOrigin(collectMediaOrigin)
        anyMatched = anyMatched or tag.parseEpisode(collectEpisode)

        return anyMatched
    }


    private fun String.parseSubtitleLanguages(collect: (SubtitleLanguage) -> Unit): Boolean {
        var any = false
        for (entry in SubtitleLanguage.matchableEntries) {
            if (entry.matches(this)) {
                collect(entry)
                any = true
            }
        }
        return any
    }

    private fun String.parseResolution(collect: (Resolution) -> Unit): Boolean {
        return Resolution.tryParse(this)?.let(collect) != null
    }

    private fun String.parseFrameRate(collect: (FrameRate) -> Unit): Boolean {
        return FrameRate.tryParse(this)?.let(collect) != null
    }

    private fun String.parseMediaOrigin(collect: (MediaOrigin) -> Unit): Boolean {
        return MediaOrigin.tryParse(this)?.let(collect) != null
    }

    private val collectionKeywords = listOf("全集", "合集", "Fin", "END")
    private val collectionPattern = Regex("""(\d{2,4})\s?-\s?(\d{2,4})""")

    private fun String.parseEpisode(collectEpisode: (EpisodeRange) -> Unit): Boolean {
        this.toFloatOrNull()?.let {
            collectEpisode(EpisodeRange.single(this))
            return true
        }
        if (this.startsWith("第")) {
            collectEpisode(EpisodeRange.single(this.removePrefix("第").removeSuffix("话").removeSuffix("話"))) // 千夏字幕組
            return true
        }
        if (this.contains("SP", ignoreCase = true) || this.contains("小剧场")) {
            collectEpisode(EpisodeRange.single(this))
            return true
        }
        if (collectionKeywords.any { this.contains(it, ignoreCase = true) }) {
            // "01-12 合集"
            collectionPattern.find(this)?.let {
                if (it.groupValues.size != 3) {
                    return@let
                }
                val (start, end) = it.destructured
                collectEpisode(EpisodeRange.range(start, end))
                return true
            }
        }
        return false
    }
}
