"""Generate the original antialiased nine-slice UI masks using only Python's standard library."""
from pathlib import Path
import math
import struct
import zlib

SIZE = 514
RADIUS = 256
OUTPUT = Path(__file__).resolve().parents[1] / "shared/src/main/resources/assets/habibclient/textures/gui"

def coverage(distance):
    return max(0.0, min(1.0, (1.0 - distance) * 32.0 + 0.5))

def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

OUTPUT.mkdir(parents=True, exist_ok=True)
for ring in (False, True):
    rows = bytearray()
    for y in range(SIZE):
        rows.append(0)
        for x in range(SIZE):
            dx = max(256.0 - (x + 0.5), (x + 0.5) - 258.0, 0.0)
            dy = max(256.0 - (y + 0.5), (y + 0.5) - 258.0, 0.0)
            distance = math.hypot(dx, dy) / RADIUS
            alpha = coverage(distance)
            if ring:
                alpha -= max(0.0, min(1.0, ((1.0 - distance) * 32.0 - 1.0) + 0.5))
            rows.extend((255, 255, 255, round(alpha * 255)))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
    name = "rounded-rim.png" if ring else "rounded-fill.png"
    (OUTPUT / name).write_bytes(png)
    (OUTPUT / (name + ".mcmeta")).write_text('{"texture":{"blur":true}}\n', encoding="utf-8")

# Copyright (c) 2026 Bloko. Licensed under the MIT License.
