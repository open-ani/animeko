"""Generate synthetic Matroska subtitle fixtures; requires ffmpeg. No copyrighted media."""
import argparse
import pathlib
import struct
import subprocess
import tempfile
import zlib


def element(tag, data):
    tag = tag.to_bytes((tag.bit_length() + 7) // 8, 'big')
    n = next(n for n in range(1, 9) if len(data) < (1 << (7 * n)) - 1)
    return tag + (len(data) | (1 << (7 * n))).to_bytes(n, 'big') + data


def uint(tag, value):
    return element(tag, value.to_bytes(max(1, (value.bit_length() + 7) // 8), 'big'))


parser = argparse.ArgumentParser()
parser.add_argument('--codec', choices=['ass', 'srt'], default='ass')
codec = parser.parse_args().codec

header = b'''[Script Info]
ScriptType: v4.00+
PlayResX: 640
PlayResY: 360
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,sans-serif,30,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,25,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
'''
ebml = element(0x1A45DFA3, uint(0x4286, 1) + uint(0x42F7, 1) + uint(0x42F2, 4)
               + uint(0x42F3, 8) + element(0x4282, b'matroska') + uint(0x4287, 4) + uint(0x4285, 2))
info = element(0x1549A966, uint(0x2AD7B1, 1000000) + element(0x4489, struct.pack('>d', 10000)))
video = element(0xAE, uint(0xD7, 1) + uint(0x73C5, 1) + uint(0x83, 1)
                + element(0x86, b'V_VP8') + element(0xE0, uint(0xB0, 640) + uint(0xBA, 360)))
tracks = video
for number in (2, 3, 4):
    compression = b''
    if number != 4:
        # Track 2 exercises the Matroska default; track 3 explicitly declares zlib.
        compression = element(0x6D80, element(0x6240, element(0x5034,
                              b'' if number == 2 else uint(0x4254, 0))))
    tracks += element(0xAE, uint(0xD7, number) + uint(0x73C5, number) + uint(0x83, 17)
                      + uint(0x88, int(number == 2)) + element(0x86, b'S_TEXT/ASS' if codec == 'ass' else b'S_TEXT/UTF8')
                      + element(0x22B59C, b'eng' if number == 4 else b'chi')
                      + (element(0x63A2, header) if codec == 'ass' else b'') + compression)
with tempfile.TemporaryDirectory() as tmp:
    ivf = pathlib.Path(tmp) / 'frame.ivf'
    subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'color=c=0x263850:s=640x360',
                    '-frames:v', '1', '-c:v', 'libvpx', '-f', 'ivf', str(ivf)], check=True)
    frame = ivf.read_bytes()[44:]
cluster = uint(0xE7, 0)
for second in range(10):
    cluster += element(0xA3, b'\x81' + struct.pack('>h', second * 1000) + b'\x80' + frame)
    if second in (1, 5):
        for track in (2, 3, 4):
            text = ('你好，世界！简体中文字幕' if second == 1 else '繁體字幕：魔法騎士！') if track != 4 else f'Plain {codec.upper()} subtitles, punctuation intact!'
            dialogue = (f'{second},0,Default,,0,0,0,,{text}' if codec == 'ass' else text).encode()
            if track != 4:
                dialogue = zlib.compress(dialogue)
                assert b'\0' in dialogue  # Media3 must not NUL-trim compressed data.
            block = bytes([0x80 | track]) + struct.pack('>h', second * 1000) + b'\0' + dialogue
            cluster += element(0xA0, element(0xA1, block) + uint(0x9B, 3000))
output = pathlib.Path(__file__).parent.parent / 'assets' / f'compressed-{codec}.mkv'
output.write_bytes(ebml + element(0x18538067, info + element(0x1654AE6B, tracks) + element(0x1F43B675, cluster)))
print(output, output.stat().st_size)
