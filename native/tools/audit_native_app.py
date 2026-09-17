#!/usr/bin/env python3
"""Audit actual APK ZIP/DEX structure. This is not a device or full feature acceptance test."""
import hashlib
import json
import os
import pathlib
import struct
import sys
import zipfile


def uleb(data, pos):
    result = 0
    for shift in range(0, 35, 7):
        byte = data[pos]
        pos += 1
        result |= (byte & 127) << shift
        if byte < 128:
            return result, pos
    raise ValueError('Invalid DEX string length')


def dex_strings(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Missing DEX header')
    count, offset = struct.unpack_from('<II', data, 56)
    result = []
    for i in range(count):
        pointer = struct.unpack_from('<I', data, offset + i * 4)[0]
        _, start = uleb(data, pointer)
        stop = data.index(b'\0', start)
        result.append(data[start:stop])
    return result


def audit(path):
    with zipfile.ZipFile(path) as archive:
        damaged = archive.testzip()
        if damaged:
            raise ValueError(f'{path}: invalid ZIP member {damaged}')
        names = archive.namelist()
        forbidden_assets = [n for n in names if n.startswith('assets/') and n.lower().endswith(('.js', '.html', '.css'))]
        if forbidden_assets:
            raise ValueError(f'Unexpected web runtime assets: {forbidden_assets}')
        strings = set()
        for name in names:
            if name.startswith('classes') and name.endswith('.dex'):
                strings.update(dex_strings(archive.read(name)))
        # Require actual descriptor entries, not a source-file grep or a name appearing in documentation.
        forbidden_types = [v.decode('ascii') for v in strings if v in {
            b'Landroid/webkit/WebView;', b'Landroid/webkit/WebViewClient;',
            b'Landroid/webkit/WebChromeClient;', b'Lcom/facebook/react/ReactRootView;'}]
        if forbidden_types:
            raise ValueError(f'Unexpected web engine descriptors: {forbidden_types}')
        for entry in ('assets/images/companions/cat.webp', 'assets/images/luke-blossom.webp',
                      'assets/holidays/2023.json', 'assets/holidays/2026.json'):
            if entry not in names:
                raise ValueError(f'Application asset missing: {entry}')
        if 'AndroidManifest.xml' not in names or not strings:
            raise ValueError('Not a usable Android package structure')
    return {'file': str(path), 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'web_assets': forbidden_assets, 'web_engine_descriptors': forbidden_types,
            'zip_members': len(names)}


if __name__ == '__main__':
    root, output = map(pathlib.Path, sys.argv[1:3])
    targets = [root / 'debug/app-debug.apk', root / 'release/app-release-unsigned.apk']
    for target in targets:
        if not target.is_file():
            raise SystemExit(f'Missing expected built application: {target}')
    data = {'commit': os.environ.get('GITHUB_SHA'),
            'scope': 'Built application package audit; full features, Android 15 glyphs and physical device testing remain pending',
            'packages': [audit(p) for p in targets]}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(data, indent=2), encoding='utf-8')
    print(json.dumps(data, indent=2))
