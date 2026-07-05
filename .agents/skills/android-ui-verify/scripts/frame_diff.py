#!/usr/bin/env python3
"""Analyze a UI screen recording for abrupt frame-to-frame changes (transient glitches).

Scans every frame's scene-change score (ffmpeg MAFD-based), groups consecutive
high-score frames into "events", and for each event exports:
  - a contact sheet (frames tiled left-to-right, top-to-bottom) for quick visual scanning
  - the individual full-resolution frames, named with their video timestamps

Both recordings produced by droid.sh/desk.sh are variable-frame-rate: a frame is
only written when the screen actually changed, so *any* frame in the output means
something changed at that moment.

Usage:
  frame_diff.py VIDEO                          # scan + auto-export around every event
  frame_diff.py VIDEO --crop W:H:X:Y           # analyze only a region (video pixels), e.g. a progress bar
  frame_diff.py VIDEO --threshold 0.003        # more sensitive event detection
  frame_diff.py VIDEO --around 3.2 --window 1  # export frames around a specific timestamp only
  frame_diff.py VIDEO --list                   # additionally print every frame's score

Requires ffmpeg/ffprobe on PATH.
"""

import argparse
import math
import os
import re
import subprocess
import sys


def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def scan_scores(video, crop):
    """Return [(pts_time, scene_score)] for every frame (first frame has no score -> skipped)."""
    vf = []
    if crop:
        vf.append(f"crop={crop}")
    vf.append("select='gte(scene,0)'")
    vf.append("metadata=print")
    p = run(["ffmpeg", "-hide_banner", "-i", video, "-vf", ",".join(vf), "-an", "-f", "null", "-"])
    frames = []
    t = None
    for line in p.stderr.splitlines():
        m = re.search(r"pts_time:([0-9.]+)", line)
        if m:
            t = float(m.group(1))
            continue
        m = re.search(r"lavfi\.scene_score=([0-9.eE+-]+)", line)
        if m and t is not None:
            frames.append((t, float(m.group(1))))
            t = None
    if not frames:
        sys.exit(f"no frames scanned — ffmpeg said:\n{p.stderr[-2000:]}")
    return frames


def group_events(frames, threshold, merge_gap=0.5):
    """Group frames with score >= threshold into events; merge events closer than merge_gap seconds."""
    events = []  # each: {"start", "end", "peak_t", "peak_score", "count"}
    for t, s in frames:
        if s < threshold:
            continue
        if events and t - events[-1]["end"] <= merge_gap:
            ev = events[-1]
            ev["end"] = t
            ev["count"] += 1
            if s > ev["peak_score"]:
                ev["peak_score"], ev["peak_t"] = s, t
        else:
            events.append({"start": t, "end": t, "peak_t": t, "peak_score": s, "count": 1})
    return events


def export_window(video, crop, frames, a, b, out_dir, tag):
    """Export a contact sheet + individual full-res frames for pts in [a, b]. Returns report lines."""
    sel = [t for t, _ in frames if a <= t <= b]
    # the scan skips the very first video frame (it has no score); include it if in range
    if not sel:
        return [f"  {tag}: no frames recorded in {a:.2f}s..{b:.2f}s (screen was static)"]

    os.makedirs(out_dir, exist_ok=True)
    base_vf = ([f"crop={crop}"] if crop else []) + [f"select='between(t,{a:.3f},{b:.3f})'"]

    # individual full-res frames
    frame_dir = os.path.join(out_dir, tag)
    os.makedirs(frame_dir, exist_ok=True)
    pattern = os.path.join(frame_dir, "raw_%04d.png")
    p = run(["ffmpeg", "-hide_banner", "-y", "-i", video, "-vf", ",".join(base_vf),
             "-fps_mode", "passthrough", pattern])
    produced = sorted(f for f in os.listdir(frame_dir) if f.startswith("raw_"))
    # rename by timestamp when extraction count matches the scan (off-by-one possible at range edges)
    if len(produced) == len(sel):
        for name, t in zip(produced, sel):
            os.rename(os.path.join(frame_dir, name), os.path.join(frame_dir, f"t{t:07.3f}s.png"))
    lines = [f"      frames: {frame_dir}/  ({len(produced)} files, {a:.2f}s..{b:.2f}s)"]

    # contact sheet: subsample to <= 40 tiles, 5 per row
    n = len(sel)
    step = max(1, math.ceil(n / 40))
    shown = math.ceil(n / step)
    cols = min(5, shown)
    rows = math.ceil(shown / cols)
    sheet = os.path.join(out_dir, f"{tag}_sheet.png")
    vf = base_vf + [f"select='not(mod(n,{step}))'", "scale=360:-1", f"tile={cols}x{rows}"]
    p = run(["ffmpeg", "-hide_banner", "-y", "-i", video, "-vf", ",".join(vf),
             "-frames:v", "1", "-fps_mode", "passthrough", sheet])
    if os.path.exists(sheet):
        lines.insert(0, f"      contact sheet: {sheet}  ({shown} tiles, read L->R T->B, every {step} frame(s))")
    return lines


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("video")
    ap.add_argument("--crop", help="W:H:X:Y in video pixels — restrict analysis to a region")
    ap.add_argument("--threshold", type=float, default=0.01,
                    help="scene-score threshold for events (default 0.01; scores are frame-wide means, "
                         "so crop to the region of interest when hunting small-element glitches)")
    ap.add_argument("--around", type=float, help="just export frames around this timestamp (seconds)")
    ap.add_argument("--window", type=float, default=1.0, help="half-window for --around / event export pad")
    ap.add_argument("--out", help="output dir (default <video>-frames/)")
    ap.add_argument("--list", action="store_true", help="print every frame's score")
    args = ap.parse_args()

    if not os.path.exists(args.video):
        sys.exit(f"no such file: {args.video}")
    out_dir = args.out or os.path.splitext(args.video)[0] + "-frames"

    frames = scan_scores(args.video, args.crop)
    scores = [s for _, s in frames]
    dur = frames[-1][0]
    print(f"{len(frames)} frames over {dur:.2f}s  "
          f"(mean score {sum(scores)/len(scores):.4f}, max {max(scores):.4f})"
          + (f"  [crop {args.crop}]" if args.crop else ""))
    print("VFR recording: frames exist only where the screen changed; gaps = static screen.")

    if args.list:
        for t, s in frames:
            bar = "#" * min(60, int(s * 400))
            print(f"  {t:8.3f}s  {s:.4f} {bar}")

    if args.around is not None:
        a, b = max(0.0, args.around - args.window), args.around + args.window
        print(f"\nExporting around t={args.around:.2f}s:")
        for line in export_window(args.video, args.crop, frames, a, b, out_dir, f"t{args.around:.1f}"):
            print(line)
        return

    events = group_events(frames, args.threshold)
    if not events:
        print(f"\nNo events with score >= {args.threshold}. Top frames:")
        for t, s in sorted(frames, key=lambda f: -f[1])[:10]:
            print(f"  {t:8.3f}s  {s:.4f}")
        print("Lower --threshold or --crop to the region of interest to look closer.")
        return

    print(f"\n{len(events)} event(s) with score >= {args.threshold}:")
    for i, ev in enumerate(events, 1):
        print(f"  #{i}  {ev['start']:.2f}s..{ev['end']:.2f}s  "
              f"peak {ev['peak_score']:.4f} @ {ev['peak_t']:.2f}s  ({ev['count']} frames)")
        pad = min(args.window, 0.5)
        for line in export_window(args.video, args.crop, frames,
                                  max(0.0, ev["start"] - pad), ev["end"] + pad, out_dir, f"ev{i}"):
            print(line)
    print("\nJudge events against *expected* changes (your own taps/scrolls/animations); "
          "an event where nothing should have changed — or a change-and-revert pair — is the glitch.")


if __name__ == "__main__":
    main()
