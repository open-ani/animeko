#!/usr/bin/env bash
# 生成 HLS 代理测试用的真实 HLS 夹具. 需要 ffmpeg.
# 桌面 (RealHlsProxyTest, RealPlayerHlsProxyValidationTest) 与 Android 设备测试 (ExoPlayerHlsProxyDeviceTest) 共用.
# 视频为 64x36 4fps 的 testsrc2 加 440Hz 正弦音, 体积极小但是合法的 H.264/AAC 流.
# vod: 96 秒, 3 秒一片 (32 片), 供广告过滤测试拼出足够长的正片组; 其余变体 60 秒, 6 秒一片.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf vod high aes fmp4 single live ads withads.m3u8 && mkdir -p vod high aes fmp4 single

common_in=(-f lavfi -i "testsrc2=size=64x36:rate=4" -f lavfi -i "sine=frequency=440:sample_rate=8000" -t 60)
common_enc=(-c:v libx264 -preset ultrafast -tune zerolatency -b:v 12k -maxrate 16k -bufsize 32k -pix_fmt yuv420p
  -force_key_frames "expr:gte(t,n_forced*6)" -sc_threshold 0 -c:a aac -b:a 8k -ac 1 -ar 8000)
hls_common=(-f hls -hls_time 6 -hls_list_size 0 -hls_playlist_type vod)

# 1. 普通点播 (TS 分片): 96 秒, 3 秒一片
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=64x36:rate=4" -f lavfi -i "sine=frequency=440:sample_rate=8000" -t 96 \
  -c:v libx264 -preset ultrafast -tune zerolatency -b:v 12k -maxrate 16k -bufsize 32k -pix_fmt yuv420p \
  -force_key_frames "expr:gte(t,n_forced*3)" -sc_threshold 0 -c:a aac -b:a 8k -ac 1 -ar 8000 \
  -f hls -hls_time 3 -hls_list_size 0 -hls_playlist_type vod \
  -hls_segment_filename vod/seg%03d.ts vod/index.m3u8

# 2. 第二码率, 供主播放列表引用
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=128x72:rate=4" -f lavfi -i "sine=frequency=880:sample_rate=8000" -t 60 \
  "${common_enc[@]}" "${hls_common[@]}" -hls_segment_filename high/seg%03d.ts high/index.m3u8
cat > master.m3u8 <<'M3U'
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=30000,RESOLUTION=64x36,CODECS="avc1.42c00a,mp4a.40.2"
vod/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=50000,RESOLUTION=128x72,CODECS="avc1.42c00a,mp4a.40.2"
high/index.m3u8
M3U

# 3. AES-128 加密
printf '0123456789abcdef' > aes/key.bin
printf 'key.bin\naes/key.bin\n000102030405060708090a0b0c0d0e0f\n' > aes/key.info
ffmpeg -loglevel error -y "${common_in[@]}" "${common_enc[@]}" "${hls_common[@]}" \
  -hls_key_info_file aes/key.info -hls_segment_filename aes/seg%03d.ts aes/index.m3u8
rm aes/key.info

# 4. fMP4 分片 (EXT-X-MAP 初始化片段)
ffmpeg -loglevel error -y "${common_in[@]}" "${common_enc[@]}" "${hls_common[@]}" \
  -hls_segment_type fmp4 -hls_fmp4_init_filename init.mp4 -hls_segment_filename fmp4/seg%03d.m4s fmp4/index.m3u8

# 5. 单文件 + EXT-X-BYTERANGE (代理应当不接管分片)
ffmpeg -loglevel error -y "${common_in[@]}" "${common_enc[@]}" "${hls_common[@]}" \
  -hls_flags single_file -hls_segment_filename single/all.ts single/index.m3u8

# 6. 直播样式 (无 ENDLIST), 分片沿用 vod 的
mkdir -p live
grep -v -e '#EXT-X-ENDLIST' -e '#EXT-X-PLAYLIST-TYPE' vod/index.m3u8 | sed 's#^seg#../vod/seg#' > live/index.m3u8

# 7. 带广告分片组的播放列表: 16 片正片 (48 秒), 2 片 "/ads/" 路径广告, 16 片正片. 广告分片是真实 TS (复用 vod 的).
#    正片组需要超过 12 片且超过 45 秒, 否则会被过滤器当成可疑短组.
#    播放列表本身不能放在 ads/ 目录下: 过滤器按解析后的完整路径匹配 "/ads/", 否则正片分片也会被当成广告.
mkdir -p ads
cp vod/seg000.ts ads/ad000.ts; cp vod/seg001.ts ads/ad001.ts
{
  echo '#EXTM3U'; echo '#EXT-X-VERSION:3'; echo '#EXT-X-TARGETDURATION:3'; echo '#EXT-X-MEDIA-SEQUENCE:0'
  for i in $(seq 0 15); do echo '#EXTINF:3.000000,'; printf 'vod/seg%03d.ts\n' "$i"; done
  echo '#EXT-X-DISCONTINUITY'
  for i in 0 1; do echo '#EXTINF:3.000000,'; echo "ads/ad00$i.ts"; done
  echo '#EXT-X-DISCONTINUITY'
  for i in $(seq 16 31); do echo '#EXTINF:3.000000,'; printf 'vod/seg%03d.ts\n' "$i"; done
  echo '#EXT-X-ENDLIST'
} > withads.m3u8

du -sh . ; find . -type f | sort | xargs ls -l | awk '{print $5, $9}'
