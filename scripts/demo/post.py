# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Final videos from the raw recordings: captions, a ring on each click; MP4 (H.264) and an animated WebP preview."""
import json, subprocess, sys, os
S = os.environ.get('CORRIGO_DEMO_WORK', os.path.expanduser('~/.cache/corrigo-demo/work'))
REC = f'{S}/demo/rec'
FONT = '/usr/share/fonts/TTF/OpenSans-Bold.ttf'

def duration(path):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                                capture_output=True, text=True).stdout)

def ring():
    path = f'{REC}/ring.png'
    if not os.path.exists(path):
        subprocess.run(['magick', '-size', '72x72', 'xc:none', '-fill', 'none', '-stroke', '#3d8bfdcc', '-strokewidth', '5',
                        '-draw', 'circle 36,36 36,8', path], check=True)
    return path

def esc(text):
    return text.replace('\\', '\\\\').replace(':', '\\:').replace("'", "’").replace('%', '\\%')

def build(name, out_dir):
    meta = json.load(open(f'{REC}/{name}.json'))
    raw = f'{REC}/{name}.raw.mp4'
    wall = meta['stop'] - meta['popen']
    delay = max(0.0, wall - duration(raw))            # ffmpeg started recording this late after popen
    to_video = lambda t: t - meta['popen'] - delay       # wall time -> time in the raw video
    start = max(0.0, to_video(meta['start']) - 0.3)     # cut before the first action
    end = to_video(meta['stop'])
    filters = []
    inputs = ['-i', raw]
    clicks = meta['clicks']
    if clicks:
        inputs += ['-i', ring()]
    chain = '[0:v]'
    for i, (t, x, y) in enumerate(clicks):
        a = to_video(t) - start
        label = f'[c{i}]'
        filters.append(f"{chain}[1:v]overlay=x={x - 36}:y={y - 36}:enable='between(t,{a:.2f},{a + 0.45:.2f})'{label}")
        chain = label
    for (a, b, text) in meta['captions']:
        a, b = to_video(a) - start, to_video(b) - start
        filters.append(f"{chain}drawtext=fontfile={FONT}:text='{esc(text)}':fontsize=38:fontcolor=white:box=1:boxcolor=0x101418@0.72:"
                       f"boxborderw=22:x=(w-text_w)/2:y=h-150:enable='between(t,{a:.2f},{b:.2f})'[t{len(filters)}]")
        chain = f'[t{len(filters) - 1}]'
    graph = ';'.join(filters)
    os.makedirs(out_dir, exist_ok=True)
    mp4 = f'{out_dir}/{name}.mp4'
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{start:.2f}', '-to', f'{end:.2f}', *inputs]
    # -ss before the first input shifts its timestamps to 0: the filter times above are relative to the cut
    if graph:
        cmd += ['-filter_complex', graph, '-map', chain]
    cmd += ['-c:v', 'libx264', '-preset', 'slow', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-an', mp4]
    subprocess.run(cmd, check=True)
    webp = f'{out_dir}/{name}.webp'
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', mp4, '-vf', 'fps=12,scale=1280:-1:flags=lanczos', '-loop', '0',
                    '-c:v', 'libwebp_anim', '-quality', '80', '-compression_level', '6', webp], check=True)
    for f in (mp4, webp): print(f, os.path.getsize(f) // 1024, 'kB')

if __name__ == '__main__':
    build(sys.argv[1], sys.argv[2])
