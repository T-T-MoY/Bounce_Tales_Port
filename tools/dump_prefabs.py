"""Decodifica los 'prefabs' de sprite (simples y compuestos) de un sprite bank."""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bounce_levels import Reader, load_index, chunk_data

files, objects, preload = load_index()
oid = int(sys.argv[1]) if len(sys.argv) > 1 else 8
o = objects[oid]
data = chunk_data(files, o["value"])
r = Reader(data)
n_img, base, n_desc, n_data, n_blocks = r.u8(), r.s16(), r.s16(), r.s16(), r.s16()
comp = [(r.s16(), r.s16() + 4) for _ in range(n_desc)]
blob = r.bytes(n_data)
print("blob %d bytes:" % len(blob), blob.hex())


def decode(off, depth=0):
    b = blob[off]
    wide = 2 if (b & 4) else 1
    kind = b & 3
    pad = "  " * depth
    if kind == 0:
        if wide == 1:
            w, h, ox, oy, sx, sy = blob[off + 1], blob[off + 2], blob[off + 3], blob[off + 4], blob[off + 5], blob[off + 6]
        else:
            w, h, ox, oy, sx, sy = [(blob[off + i] << 8) | blob[off + i + 1] for i in range(1, 13, 2)]
        img = (blob[off + 1 + 6 * wide] << 8) | blob[off + 2 + 6 * wide]
        print("%soff=%3d simple %dx%d off=(%d,%d) src=(%d,%d) img=%d"
              % (pad, off, w, h, ox, oy, sx, sy, img))
        return off + 3 + 6 * wide
    if kind == 1:
        n = (blob[off + 1 + 4 * wide] << 8) | blob[off + 2 + 4 * wide]
        p = off + 3 + 4 * wide
        print("%soff=%3d compuesto n=%d" % (pad, off, n))
        for _ in range(n):
            if wide == 2:
                dx = (blob[p] << 8) | blob[p + 1]
                dy = (blob[p + 2] << 8) | blob[p + 3]
                sid = (blob[p + 4] << 8) | blob[p + 5]
                p += 6
            else:
                dx, dy, sid = blob[p], blob[p + 1], blob[p + 2]
                p += 3
            print("%s   hijo dx=%d dy=%d sprite=%d" % (pad, dx, dy, sid))
        return p
    print("%soff=%3d tipo %d (desconocido)" % (pad, off, kind))
    return off + 1


for ident, off in comp:
    print("== sprite compuesto id=%d @%d" % (ident, off))
    decode(off)
