`resize-test.mp4` is a synthetic 320 × 180 H.264 color-bar video used by
`VideoPlayerResizeTest`. It requires no network or external media.

Generate it with:

```shell
ffmpeg -f lavfi -i 'smptebars=size=320x180:rate=10' -t 4 \
  -c:v libx264 -pix_fmt yuv420p -movflags +faststart resize-test.mp4
```
