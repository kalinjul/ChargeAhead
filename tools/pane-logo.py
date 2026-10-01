#!/usr/bin/env python3
"""Derives the car pane's transparent logo PNG from the two goldens rendered on black and on white.

Pure python: the goldens are RGBA PNGs from the screenshot tool, which paints a ground
no matter what, so alpha has to be recovered from the pair (alpha = 1 - (white - black)).
"""
import glob, struct, sys, zlib

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
GOLDENS = f"{ROOT}/ui-tests/src/screenshotTestDebug/reference/org/julakali/chargeahead/uitests/BrandPreviewsKt"
OUT = f"{ROOT}/phone-ui/src/main/res/drawable-nodpi/logo_powertrip_stacked.png"


def read_png(path):
    data = open(path, "rb").read()
    width, height, _, colour_type = struct.unpack(">IIBB", data[16:26])
    bpp = {2: 3, 6: 4}[colour_type]
    pos, idat = 8, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        if kind == b"IDAT":
            idat += data[pos + 8:pos + 8 + length]
        pos += 12 + length
    raw = zlib.decompress(idat)
    stride = width * bpp
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return width, height, bpp, rows


def write_png(path, width, height, rows):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    raw = b"".join(b"\0" + row for row in rows)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(png)


black = glob.glob(f"{GOLDENS}/CarPaneLogoOnBlack_*.png")[0]
white = glob.glob(f"{GOLDENS}/CarPaneLogoOnWhite_*.png")[0]
w, h, bpp, on_black = read_png(black)
_, _, _, on_white = read_png(white)
out = []
for rb, rw in zip(on_black, on_white):
    row = bytearray()
    for x in range(w):
        b = rb[x * bpp:x * bpp + 3]
        wv = rw[x * bpp:x * bpp + 3]
        alpha = max(0, min(255, 255 - round(sum(wv[i] - b[i] for i in range(3)) / 3)))
        if alpha == 0:
            row += b"\0\0\0\0"
        else:
            row += bytes(min(255, round(b[i] * 255 / alpha)) for i in range(3)) + bytes([alpha])
    out.append(bytes(row))
write_png(OUT, w, h, out)
print(f"{OUT}: {w}x{h}")
