"""Decode real screenrecord frames, rejecting the historical full-screen green flash.
This checks rendered pixels, not just Java animator callbacks. No frame synthesis.
"""
import json, pathlib, subprocess


def inspect_splash_pixels(video):
    frame_size = 90 * 160 * 3
    result = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(video), '-vf', 'scale=90:160',
                             '-vsync', '0', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90, check=True)
    raw = result.stdout
    if not raw or len(raw) % frame_size:
        raise AssertionError('Missing or incomplete splash recording: ' + str(video))
    count = len(raw) // frame_size
    matches, peak = [], 0.0
    # Exclude Android bars, but keep the entire content area including the medallion.
    for frame in range(count):
        base = frame * frame_size
        green, pixels = 0, 0
        for y in range(12, 148):
            for x in range(5, 85):
                pos = base + (y * 90 + x) * 3
                red, g, blue = raw[pos:pos+3]
                green += abs(red-33) <= 12 and abs(g-79) <= 12 and abs(blue-76) <= 12
                pixels += 1
        fraction = green / pixels
        peak = max(peak, fraction)
        if fraction > .90:
            matches.append(frame)
    return {'video': pathlib.Path(video).name, 'decodedFrames': count,
            'greenPlaceholderFrames': matches, 'maxGreenFraction': round(peak, 5)}


def verify_splash_pixels(video, record, out):
    result = inspect_splash_pixels(video)
    (out/(pathlib.Path(video).stem+'-pixels.json')).write_text(json.dumps(result, indent=2))
    record(pathlib.Path(video).stem+' never exposes the old green placeholder',
           not result['greenPlaceholderFrames'], result)
