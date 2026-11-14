"""Exporta el banco de sprites completo a JSON (descriptores, prefabs y animaciones).

Formato verificado contra p.mo60a(DIS, -1/2) y p.a(x, y, spriteId):

  Sprite bank (objeto tipo 2 del indice):
      u8   imageCount          numero de PNG del objeto
      s16  firstImageId        id global de la primera imagen
      s16  prefabCount         numero de sprites compuestos
      s16  prefabDataSize      bytes del bloque de prefabs
      s16  descriptorCount     numero de descriptores de 7 bytes
      prefabCount   x (s16 spriteId, s16 offsetEnPrefabs)   offset += 4 al cargar
      prefabDataSize bytes   (se antepone [s16 firstImageId][s16 firstImageId+imageCount])
      descriptorCount x (s16 spriteId, 7 bytes)
          7 bytes = [w][h][offX s8][offY s8][srcX][srcY][imageIndex]

  Prefab (spriteId >= 326), primer byte = flags:
      kind = flags & 3        0 = simple, 1 = compuesto, 2/3 = animacion (lista de ids)
      wide = 2 if flags & 4 else 1     (valores s16 en vez de s8)
      simple    : 6 valores (w,h,offX,offY,srcX,srcY) + u16 imageIndex
      compuesto : 4 valores (w,h,offX,offY) + u16 n + n x (dx,dy,spriteId)
      animacion : u8 0, u8 n, n x u16 spriteId
                  (p.c(id, frame) lee el u16 en offset+3+frame*2)

Uso:
    python tools/export_sprites.py            -> out/sprites.json + resumen
    python tools/export_sprites.py salida.json
"""
import json
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bounce_levels import Reader, load_index, chunk_data  # noqa: E402

OUT_DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "out", "sprites.json")

FIRST_PREFAB_ID = 326


def sval(raw, wide):
    if wide == 1:
        return raw[0] - 256 if raw[0] > 127 else raw[0]
    return struct.unpack_from(">h", raw, 0)[0]


def read_prefab(blob, off, resolve, depth=0):
    """Decodifica un prefab y devuelve (dict, siguienteOffset)."""
    flag = blob[off]
    wide = 2 if (flag & 4) else 1
    kind = flag & 3
    p = off + 1
    node = {"offset": off, "flags": flag, "kind": kind, "wide": wide}

    def take():
        nonlocal p
        raw = blob[p:p + wide]
        p += wide
        return sval(raw, wide)

    if kind == 0:
        node["type"] = "simple"
        node["frame"] = {
            "w": take(), "h": take(), "ox": take(), "oy": take(),
            "src_x": take(), "src_y": take(),
            "image": struct.unpack_from(">H", blob, p)[0],
        }
        p += 2
        return node, p

    if kind == 1:
        node["type"] = "compound"
        node["box"] = {"w": take(), "h": take(), "ox": take(), "oy": take()}
        n = struct.unpack_from(">H", blob, p)[0]
        p += 2
        parts = []
        for _ in range(n):
            dx, dy, sid = take(), take(), take()
            part = {"dx": dx, "dy": dy, "sprite": sid}
            if resolve:
                part["resolved"] = resolve(sid, depth + 1)
            parts.append(part)
        node["parts"] = parts
        return node, p

    # kind 2/3: alias / animacion.  Layout: [flags][0][n u8][n x u16 spriteId]
    # p.c(id, frame) lee el u16 en offset+3+frame*2, y p.b(x,y,id,frame) lo dibuja.
    node["type"] = "animation"
    count = blob[off + 2]
    ids = []
    for i in range(count):
        ids.append(struct.unpack_from(">H", blob, off + 3 + i * 2)[0])
    node["frames"] = ids
    return node, off + 3 + count * 2


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else OUT_DEFAULT
    files, objects, _ = load_index()

    descriptors = {}   # id -> descriptor global
    prefabs = {}       # id -> dict
    banks = []

    # ---- 1. leer todos los bancos de sprites ----
    for oid, obj in enumerate(objects):
        if obj["tipo"] != 2:
            continue
        data = chunk_data(files, obj["value"])
        r = Reader(data)
        n_img = r.u8()
        base = r.s16()
        n_prefab = r.s16()
        prefab_size = r.s16()
        n_desc = r.s16()
        prefab_refs = [(r.s16(), r.s16() + 4) for _ in range(n_prefab)]

        blob = struct.pack(">hh", base, base + n_img) + r.bytes(prefab_size)

        desc_ids = []
        for _ in range(n_desc):
            sid = r.s16()
            raw = r.bytes(7)
            desc_ids.append(sid)
            descriptors[sid] = {
                "w": raw[0], "h": raw[1],
                "ox": sval(raw[2:3], 1), "oy": sval(raw[3:4], 1),
                "src_x": raw[4], "src_y": raw[5],
                "image": raw[6],
            }

        pngs = {}
        for gid in range(base, base + n_img):
            idx = gid - base
            pngs[gid] = files[obj["refs"][idx]]["name"] if idx < len(obj["refs"]) else None

        for sid, off in prefab_refs:
            node, _ = read_prefab(blob, off, None)
            prefabs[sid] = node

        banks.append({
            "object": oid,
            "chunk": files[obj["value"]]["name"],
            "first_image": base,
            "image_count": n_img,
            "png": pngs,
            "descriptors": desc_ids,
            "prefabs": [sid for sid, _ in prefab_refs],
        })

    # ---- 2. resolver prefabs (ahora que existen todos los descriptores) ----
    def resolve(sid, depth=0):
        if sid in descriptors:
            d = descriptors[sid]
            return {"sprite": sid, "w": d["w"], "h": d["h"],
                    "ox": d["ox"], "oy": d["oy"], "image": d["image"]}
        if sid in prefabs:
            return {"sprite": sid, "prefab": prefabs[sid].get("type")}
        return {"sprite": sid, "missing": True}

    prefab_trailing = 0
    for oid, obj in enumerate(objects):
        if obj["tipo"] != 2:
            continue
        data = chunk_data(files, obj["value"])
        r = Reader(data)
        n_img = r.u8()
        base = r.s16()
        n_prefab = r.s16()
        prefab_size = r.s16()
        r.s16()
        prefab_refs = [(r.s16(), r.s16() + 4) for _ in range(n_prefab)]
        blob = struct.pack(">hh", base, base + n_img) + r.bytes(prefab_size)
        offs = sorted(off for _, off in prefab_refs)
        for sid, off in prefab_refs:
            node, end = read_prefab(blob, off, resolve)
            idx = offs.index(off)
            nxt = offs[idx + 1] if idx + 1 < len(offs) else len(blob)
            if end != nxt:
                # bytes finales aun no identificados (2..16); el codigo de dibujo
                # (p.a(x,y,id)) no los lee, se conservan para no perder informacion.
                node["trailing"] = list(blob[end:nxt])
                prefab_trailing += 1
            prefabs[sid] = node

    # ---- 3. animaciones: ids consecutivos definidos en el mismo banco ----
    animations = []
    for bank in banks:
        ids = sorted(bank["descriptors"])
        run = [ids[0]] if ids else []
        for sid in ids[1:]:
            if sid == run[-1] + 1:
                run.append(sid)
            else:
                if len(run) > 1:
                    animations.append({"bank": bank["object"], "frames": run})
                run = [sid]
        if len(run) > 1:
            animations.append({"bank": bank["object"], "frames": run})

    # ---- 4. imagenes: id global -> png ----
    images = {}
    for bank in banks:
        images.update({str(k): v for k, v in bank["png"].items()})

    doc = {
        "images": images,
        "descriptors": {str(k): v for k, v in sorted(descriptors.items())},
        "prefabs": {str(k): v for k, v in sorted(prefabs.items())},
        "animations": animations,
        "banks": banks,
    }

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, indent=1, ensure_ascii=False)

    print("escrito %s" % out_path)
    print("  imagenes:     %d" % len(images))
    print("  descriptores: %d" % len(descriptors))
    print("  prefabs:      %d" % len(prefabs))
    print("  animaciones:  %d" % len(animations))
    print("  prefabs con bytes finales sin identificar: %d" % prefab_trailing)
    for a in animations[:12]:
        print("   banco %-2d frames %d..%d (%d)"
              % (a["bank"], a["frames"][0], a["frames"][-1], len(a["frames"])))


if __name__ == "__main__":
    main()
