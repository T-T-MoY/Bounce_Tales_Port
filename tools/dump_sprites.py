"""Dump del "sprite bank" (objetos tipo 2 del indice)."""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bounce_levels import Reader, load_index, chunk_data

files, objects, preload = load_index()
want = [int(x) for x in sys.argv[1:]] or [0, 8, 13]

for oid in want:
    o = objects[oid]
    data = chunk_data(files, o["value"])
    r = Reader(data)
    print("=== objeto %d  tipo=%d  chunk=%s len=%d  refs=%s"
          % (oid, o["tipo"], files[o["value"]]["name"], len(data), o["refs"]))
    n_img = r.u8()
    base = r.s16()
    n_desc = r.s16()
    n_png = r.s16()
    n_blocks = r.s16()
    print("  imagenes=%d  primerId=%d  descriptores=%d  bytesPNG=%d  bloques=%d"
          % (n_img, base, n_desc, n_png, n_blocks))
    comp = [(r.s16(), r.s16() + 4) for _ in range(n_desc)]
    print("  sprites compuestos (>=326) [id, offset]:", comp)
    png = r.bytes(n_png)
    print("  png inline:", len(png), png[:8].hex())
    desc = {}
    for _ in range(n_blocks):
        idx = r.s16() // 7
        raw = r.bytes(7)
        desc[idx] = (raw[0], raw[1], raw[2] - 256 if raw[2] > 127 else raw[2],
                     raw[3] - 256 if raw[3] > 127 else raw[3],
                     raw[4], raw[5], raw[6])
    for k in sorted(desc):
        w, h, ox, oy, sx, sy, im = desc[k]
        print("   sprite %3d: %2dx%-2d off=(%d,%d) src=(%d,%d) img=%d"
              % (k, w, h, ox, oy, sx, sy, im))
    print("  bytes sin usar:", len(data) - r.p)
