#!/usr/bin/env python3
"""把抓取的真实站点页面脱敏成可入库的测试夹具.

用法: python3 clean.py <原始 html> <输出 html>

规则:
1. 删除所有 <script> 与 <noscript> 块 (统计脚本、播放器脚本与选择器无关).
2. 所有绝对 URL (含协议相对) 整体替换为 https://static.fixture.invalid/ ,
   相对链接原样保留: 条目页、播放页和静态资源路径是选择器要匹配的东西.
3. 站点名称与品牌串替换为占位词.
4. 输出前检查残留的品牌串, 有则报错.
"""
import re
import sys

BRAND_WORDS = {
    "稀饭动漫": "示例动漫",
    "稀饭": "示例",
}
BRAND_PATTERNS = [
    re.compile(r"xf(?:manga|ani|dm|vod|anchat|pla)", re.I),
]
RESIDUAL = re.compile(r"稀饭|xf(?:manga|ani|dm|vod|anchat|pla)|51\.la|qq\.com|备案|icp", re.I)


def clean(html: str) -> str:
    html = re.sub(r"<script\b[^>]*>.*?</script>", "", html, flags=re.S | re.I)
    html = re.sub(r"<noscript\b[^>]*>.*?</noscript>", "", html, flags=re.S | re.I)
    html = re.sub(r"(?:https?:)?//[A-Za-z0-9.\-]+\.[A-Za-z]{2,}(?::\d+)?[^\s\"'<>)]*", "https://static.fixture.invalid/", html)
    for word, placeholder in BRAND_WORDS.items():
        html = html.replace(word, placeholder)
    for pattern in BRAND_PATTERNS:
        html = pattern.sub("example", html)
    return html


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8", errors="replace") as f:
        html = clean(f.read())
    residual = sorted(set(RESIDUAL.findall(html)))
    if residual:
        raise SystemExit(f"residual identifying strings: {residual}")
    with open(dst, "w", encoding="utf-8") as f:
        f.write(html)


if __name__ == "__main__":
    main()
