#!/usr/bin/env python3

#  Copyright (C) 2024-2026 OpenAni and contributors.
#
#  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
#  Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
#  https://github.com/open-ani/ani/blob/main/LICENSE

"""
重新生成 `danmaku/api/src/commonMain/kotlin/ZhPhraseConversionTable.kt`.

从 OpenCC 下载词组词典, 剔除掉"用现有单字表逐字转换就已经能得到正确结果"的冗余条目,
把剩下的纠正性条目写成 Kotlin 源码.

单字表 (`ZhConversionTable.kt`) 是手工生成并已提交的, 本脚本只读取它, 不会改写它 ——
过滤必须针对实际在用的单字表进行, 否则会漏掉或多留条目.

用法:
    python scripts/generate-zh-phrase-table.py
"""

import io
import os
import re
import sys
import urllib.request

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHAR_TABLE_KT = os.path.join(
	REPO_ROOT, 'danmaku', 'api', 'src', 'commonMain', 'kotlin', 'ZhConversionTable.kt')
OUTPUT_KT = os.path.join(
	REPO_ROOT, 'danmaku', 'api', 'src', 'commonMain', 'kotlin', 'ZhPhraseConversionTable.kt')

OPENCC_BASE = 'https://raw.githubusercontent.com/BYVoid/OpenCC/master/data/dictionary/'
ST_PHRASES_URL = OPENCC_BASE + 'STPhrases.txt'
TS_PHRASES_URL = OPENCC_BASE + 'TSPhrases.txt'

# 每个字符串字面量最多放多少个字符. JVM class 文件里单个 UTF-8 常量上限 65535 字节,
# 汉字按 3 字节算, 所以留足余量.
CHUNK_CHARS = 4000

ENTRY_SEPARATOR = '|'
KEY_VALUE_SEPARATOR = '='


def download(url: str) -> str:
	sys.stderr.write('downloading %s\n' % url)
	with urllib.request.urlopen(url) as response:
		return response.read().decode('utf-8')


def parse_opencc_dict(text: str) -> 'dict[str, str]':
	"""解析 OpenCC 词典格式 `key<TAB>value(s)`, 一对多时取第一个候选."""
	result = {}
	for line in text.splitlines():
		if not line or line.startswith('#'):
			continue
		parts = line.split('\t')
		if len(parts) < 2:
			continue
		result[parts[0]] = parts[1].split(' ')[0]
	return result


def parse_packed_pairs(source: str, const_name: str) -> 'dict[str, str]':
	"""从 ZhConversionTable.kt 里读出某个成对字符串常量, 还原成单字映射表."""
	start = source.index(const_name)
	cursor = source.index('=', start)
	literals = []
	while True:
		quote = source.find('"', cursor)
		if quote < 0:
			break
		# 两个字面量之间只允许出现空白和 `+`/`=`, 否则说明常量已经结束了
		if source[cursor:quote].strip(' \t\r\n=+') != '':
			break
		end = source.index('"', quote + 1)
		literals.append(source[quote + 1:end])
		cursor = end + 1
	packed = ''.join(literals)
	if len(packed) % 2 != 0:
		raise ValueError('%s has an odd number of chars' % const_name)
	return {packed[i]: packed[i + 1] for i in range(0, len(packed), 2)}


def convert_by_chars(text: str, char_table: 'dict[str, str]') -> str:
	return ''.join(char_table.get(c, c) for c in text)


def filter_corrective(phrases: 'dict[str, str]', char_table: 'dict[str, str]') -> 'dict[str, str]':
	"""只保留逐字转换得不到正确结果的条目, 其余的交给单字表就行."""
	return {k: v for k, v in phrases.items() if convert_by_chars(k, char_table) != v}


def pack(phrases: 'dict[str, str]', name: str) -> str:
	"""把词组表打包成一个扁平字符串: 每条记录是 `键=值`, 记录之间用 `|` 分隔.

	词典数据里不含任何 ASCII 字符, 所以这两个分隔符不会和内容冲突, 这里顺便校验一遍.
	"""
	records = []
	for key in sorted(phrases):
		value = phrases[key]
		for c in key + value:
			if ord(c) < 128:
				raise ValueError(
					'%s: entry %r -> %r contains ASCII char %r, which the separators assume '
					'never appears' % (name, key, value, c))
		records.append(key + KEY_VALUE_SEPARATOR + value)
	return ENTRY_SEPARATOR.join(records)


def render_literal(packed: str, indent: str) -> str:
	chunks = [packed[i:i + CHUNK_CHARS] for i in range(0, len(packed), CHUNK_CHARS)]
	return (' +\n' + indent).join('"%s"' % chunk for chunk in chunks)


HEADER = '''/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 本文件由 scripts/generate-zh-phrase-table.py 生成, 不要手改.

package me.him188.ani.danmaku.api

/**
 * 简繁转换的词组映射表, 用来修正单字表 ([ZhConversionTable]) 逐字转换会弄错的地方,
 * 例如 "头发" 逐字转换成 "頭發", 查词组表才能得到 "頭髮".
 *
 * 数据来源: [OpenCC](https://github.com/BYVoid/OpenCC) 的 `STPhrases.txt` 与 `TSPhrases.txt` (Apache-2.0),
 * 一对多时取第一个候选. OpenCC 原始词典里绝大多数条目是冗余的 —— 逐字转换本来就能得到正确结果 ——
 * 这里只保留"逐字转换结果与词组结果不一致"的纠正性条目, 简→繁 %(st_raw)d 条筛剩 %(st_kept)d 条,
 * 繁→简 %(ts_raw)d 条筛剩 %(ts_kept)d 条.
 *
 * 编码: 每条记录是 `键=值`, 记录之间用 `|` 分隔. 词典数据里不含任何 ASCII 字符, 生成脚本会校验这一点,
 * 所以分隔符不会和内容冲突. 字面量按 %(chunk)d 字符一段切开, 避免单个字符串常量超过 class 文件 64 KB 的上限.
 */
internal object ZhPhraseConversionTable {
    /**
     * 一个首字对应的所有词组, 按键长从长到短排好序, 于是顺序匹配到的第一条就是最长匹配.
     * [keys] 与 [values] 一一对应.
     */
    internal class Bucket(
        val keys: Array<String>,
        val values: Array<String>,
    )

    /**
     * 繁体 -> 简体, 共 %(ts_kept)d 条, 按词组首字分桶.
     */
    val traditionalToSimplified: Map<Char, Bucket> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        parse(TRADITIONAL_TO_SIMPLIFIED_PHRASES)
    }

    /**
     * 简体 -> 繁体, 共 %(st_kept)d 条, 按词组首字分桶.
     */
    val simplifiedToTraditional: Map<Char, Bucket> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        parse(SIMPLIFIED_TO_TRADITIONAL_PHRASES)
    }

    private fun parse(packed: String): Map<Char, Bucket> {
        val keysByFirstChar = HashMap<Char, MutableList<String>>()
        val valuesByKey = HashMap<String, String>()
        var start = 0
        while (start < packed.length) {
            var end = packed.indexOf(ENTRY_SEPARATOR, start)
            if (end < 0) end = packed.length
            val separator = packed.indexOf(KEY_VALUE_SEPARATOR, start)
            val key = packed.substring(start, separator)
            valuesByKey[key] = packed.substring(separator + 1, end)
            keysByFirstChar.getOrPut(key[0]) { ArrayList() }.add(key)
            start = end + 1
        }
        return keysByFirstChar.mapValues { (_, keys) ->
            keys.sortByDescending { it.length } // 长的排前面, 顺序匹配即最长匹配
            Bucket(
                keys = keys.toTypedArray(),
                values = Array(keys.size) { valuesByKey.getValue(keys[it]) },
            )
        }
    }

    private const val ENTRY_SEPARATOR = '|'
    private const val KEY_VALUE_SEPARATOR = '='
'''

FOOTER = '''}
'''


def main():
	char_source = io.open(CHAR_TABLE_KT, encoding='utf-8').read()
	t2s_chars = parse_packed_pairs(char_source, 'TRADITIONAL_TO_SIMPLIFIED_PAIRS')
	s2t_chars = parse_packed_pairs(char_source, 'SIMPLIFIED_TO_TRADITIONAL_PAIRS')
	sys.stderr.write('char table: %d 繁->简, %d 简->繁\n' % (len(t2s_chars), len(s2t_chars)))

	st_raw = parse_opencc_dict(download(ST_PHRASES_URL))
	ts_raw = parse_opencc_dict(download(TS_PHRASES_URL))
	st_kept = filter_corrective(st_raw, s2t_chars)
	ts_kept = filter_corrective(ts_raw, t2s_chars)
	sys.stderr.write('简->繁 phrases: %d -> %d\n' % (len(st_raw), len(st_kept)))
	sys.stderr.write('繁->简 phrases: %d -> %d\n' % (len(ts_raw), len(ts_kept)))

	st_packed = pack(st_kept, 'STPhrases')
	ts_packed = pack(ts_kept, 'TSPhrases')

	indent = ' ' * 12
	body = HEADER % {
		'st_raw': len(st_raw), 'st_kept': len(st_kept),
		'ts_raw': len(ts_raw), 'ts_kept': len(ts_kept),
		'chunk': CHUNK_CHARS,
	}
	body += '\n%sprivate val TRADITIONAL_TO_SIMPLIFIED_PHRASES: String =\n%s%s\n' % (
		' ' * 4, indent, render_literal(ts_packed, indent))
	body += '\n%sprivate val SIMPLIFIED_TO_TRADITIONAL_PHRASES: String =\n%s%s\n' % (
		' ' * 4, indent, render_literal(st_packed, indent))
	body += FOOTER

	with io.open(OUTPUT_KT, 'w', encoding='utf-8', newline='\n') as f:
		f.write(body)
	sys.stderr.write('wrote %s (%d KB)\n' % (OUTPUT_KT, len(body.encode('utf-8')) // 1024))


if __name__ == '__main__':
	main()
