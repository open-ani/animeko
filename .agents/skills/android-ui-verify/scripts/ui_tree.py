#!/usr/bin/env python3
"""Render an uiautomator XML dump (the Compose semantics projection) for UI verification.

Default: pretty-print an indented tree of semantic nodes with dp sizes.
--find: print matching nodes (substring of text/content-desc/resource-id) with tap-ready centers.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET


def parse_bounds(s):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", s or "")
    return tuple(map(int, m.groups())) if m else None


def flags(a):
    out = [k for k in ("clickable", "scrollable", "checked", "selected", "focused", "password") if a.get(k) == "true"]
    if a.get("enabled") == "false":
        out.append("disabled")
    return out


def describe(a, density):
    b = parse_bounds(a.get("bounds"))
    parts = []
    cls = (a.get("class") or "?").rsplit(".", 1)[-1]
    parts.append(cls)
    if a.get("text"):
        parts.append(f"text={a['text']!r}")
    if a.get("content-desc"):
        parts.append(f"desc={a['content-desc']!r}")
    if a.get("resource-id"):
        parts.append(f"id={a['resource-id']}")
    if b:
        x1, y1, x2, y2 = b
        w, h = x2 - x1, y2 - y1
        if density:
            dp = lambda px: round(px * 160 / density)
            parts.append(f"{dp(w)}x{dp(h)}dp@({dp(x1)},{dp(y1)})dp")
        parts.append(f"center=({(x1 + x2) // 2},{(y1 + y2) // 2})px")
    f = flags(a)
    if f:
        parts.append("[" + ",".join(f) + "]")
    return " ".join(parts)


def is_semantic(a):
    return bool(
        a.get("text")
        or a.get("content-desc")
        or a.get("resource-id")
        or a.get("clickable") == "true"
        or a.get("scrollable") == "true"
        or a.get("checked") == "true"
        or a.get("selected") == "true"
        or "EditText" in (a.get("class") or "")
    )


def print_tree(node, density, show_all, depth=0):
    a = node.attrib
    printed = show_all or is_semantic(a)
    if printed and a.get("class"):
        print("  " * depth + "- " + describe(a, density))
    for child in node:
        print_tree(child, density, show_all, depth + 1 if printed else depth)


def find_nodes(root, query, density):
    q = query.lower()
    hits = 0
    for n in root.iter():
        a = n.attrib
        hay = " ".join(a.get(k, "") for k in ("text", "content-desc", "resource-id")).lower()
        if q in hay and parse_bounds(a.get("bounds")):
            print(describe(a, density))
            hits += 1
    if hits == 0:
        print("no match")
        sys.exit(1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("--density", type=int, default=0, help="screen density for px->dp (px*160/density)")
    ap.add_argument("--find", help="substring of text/content-desc/resource-id")
    ap.add_argument("--all", action="store_true", help="print every node, not only semantic ones")
    args = ap.parse_args()

    root = ET.parse(args.xml).getroot()
    if args.find:
        find_nodes(root, args.find, args.density)
    else:
        for child in root:
            print_tree(child, args.density, args.all)


if __name__ == "__main__":
    main()
