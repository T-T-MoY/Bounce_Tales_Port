"""
bounce_levels.py - Extractor / conversor a JSON de los niveles de Bounce Tales (J2ME).

Reconstruye:
  * el indice de recursos ("a")
  * los "chunks" empaquetados dentro de "b" (y los ficheros sueltos .png / .mid)
  * el formato contenedor de nivel (cabecera + registros [tipo][len][payload])
  * el formato de cada entidad (k = base, q = poligono, g = trigger, ...)

Uso:
    python tools/bounce_levels.py index
    python tools/bounce_levels.py dump bf
    python tools/bounce_levels.py json bf ../out/levels
"""
import json
import os
import struct
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resources")


# ----------------------------------------------------------------------------
# Helpers de lectura (big endian, igual que DataInputStream)
# ----------------------------------------------------------------------------
class Reader:
    def __init__(self, data, pos=0):
        self.d = data
        self.p = pos

    def u8(self):
        v = self.d[self.p]
        self.p += 1
        return v

    def s8(self):
        v = struct.unpack_from(">b", self.d, self.p)[0]
        self.p += 1
        return v

    def u16(self):
        v = struct.unpack_from(">H", self.d, self.p)[0]
        self.p += 2
        return v

    def s16(self):
        v = struct.unpack_from(">h", self.d, self.p)[0]
        self.p += 2
        return v

    def s32(self):
        v = struct.unpack_from(">i", self.d, self.p)[0]
        self.p += 4
        return v

    def utf(self):
        n = self.u16()
        raw = self.d[self.p:self.p + n]
        self.p += n
        return raw.decode("utf-8", "replace")

    def bytes(self, n):
        v = self.d[self.p:self.p + n]
        self.p += n
        return v


def read_bits_32(r, count, base, width):
    """k.a(int[], count, base, bArr, offset, bits): deltas empaquetados -> 16.16"""
    out = []
    acc = 0
    nbits = 0
    sign = 1 << (width - 1)
    mask = (1 << width) - 1
    while len(out) < count:
        acc |= r.u8() << nbits
        nbits += 8
        while nbits >= width and len(out) < count:
            v = acc & mask
            if v & sign:
                v |= ~mask
            out.append((v + base) << 16)
            nbits -= width
            acc >>= width
    return out


def read_bits_16(r, count, base, width):
    """k.a(short[], count, base, bArr, offset, bits): deltas empaquetados -> short"""
    out = []
    acc = 0
    nbits = 0
    sign = 1 << (width - 1)
    mask = (1 << width) - 1
    while len(out) < count:
        acc |= r.u8() << nbits
        nbits += 8
        while nbits >= width and len(out) < count:
            v = acc & mask
            if v & sign:
                v |= ~mask
            out.append(v + base)
            nbits -= width
            acc >>= width
    return out


# ----------------------------------------------------------------------------
# Indice de recursos ("a")
# ----------------------------------------------------------------------------
def load_index():
    data = open(os.path.join(RES, "a"), "rb").read()
    r = Reader(data)
    files = []
    for _ in range(r.u16()):
        files.append({"name": r.utf(), "off": r.s32(), "len": r.s32()})
    objects = []
    for _ in range(r.u16()):
        tipo = r.u8()
        nrefs = r.u8()
        v = r.s16()
        objects.append({"tipo": tipo, "value": v,
                        "refs": [r.s16() for _ in range(nrefs)]})
    preload = [(r.s16(), r.s16()) for _ in range(r.u16())]
    return files, objects, preload


def chunk_data(files, idx):
    f = files[idx]
    if f["off"] < 0:
        return open(os.path.join(RES, f["name"]), "rb").read()
    data = open(os.path.join(RES, f["name"]), "rb").read()
    return data[f["off"]:f["off"] + f["len"]]


# ----------------------------------------------------------------------------
# Entidades
# ----------------------------------------------------------------------------
TAG_NAMES = {
    4: "polygon", 5: "object", 6: "trigger", 7: "object", 8: "ball",
    9: "decoration", 10: "beam", 11: "tube", 12: "pad", 13: "goal",
    14: "ball_decoration", 15: "enemy",
}
# type ids reales (k.f98c) por clase
TAG_CLASS = {4: "q", 5: "k", 6: "g", 7: "k", 8: "c", 9: "s", 10: "i",
             11: "l", 12: "e", 13: "a", 14: "c", 15: "m"}


def read_base(r, nb):
    """k.a(byte[], int). Devuelve dict con transform + flags. nb = bytes totales."""
    start = r.p
    e = {
        "link_parent": r.s16(),
        "link_child": r.s16(),
    }
    flags = r.u8()
    tr = {"flags": flags}
    if (flags & 7) == 7:
        tr["matrix"] = [r.s32() / 65536.0 for _ in range(6)]
    else:
        if flags & 1:
            tr["x"] = r.s16()
            tr["y"] = r.s16()
        if flags & 2:
            tr["rotation"] = r.s32() / 65536.0
        if flags & 4:
            tr["scale_x"] = r.s32() / 65536.0
            tr["scale_y"] = r.s32() / 65536.0
    e["transform"] = tr
    m = r.s32()
    e["flags_m"] = m
    e["depth"] = (m & 31) - 16
    e["solid"] = bool(m & 32)
    e["skip_branch"] = bool(m & 128)
    e["flag256"] = bool(m & 256)
    e["_consumed"] = r.p - start
    return e


def read_polygon(r, e):
    """q.a(): contorno de vertices + lista de triangulos + id de trigger."""
    start = r.p
    count = r.u16() + 1
    tris = r.u16()
    color = list(r.bytes(4))
    width = r.u8()
    base_x = r.s16()
    xs = read_bits_32(r, count - 1, base_x, width)
    base_y = r.s16()
    ys = read_bits_32(r, count - 1, base_y, width)
    idx_w = r.u8()
    indices = read_bits_16(r, tris, 0, idx_w)
    trigger = r.s16()
    xs.append(xs[0])
    ys.append(ys[0])
    e["polygon"] = {
        "vertices": [[xs[i] / 65536.0, ys[i] / 65536.0] for i in range(count)],
        "triangles": [indices[i:i + 3] for i in range(0, tris, 3)],
        "color_argb": color,
        "vertex_bits": width,
        "trigger_id": trigger,
    }
    e["_consumed"] += r.p - start
    return e


def read_sprite_list(r, e):
    """s.a(): lista de sprites con posiciones codificadas en delta."""
    start = r.p
    n = r.u8()
    sprites = []
    if n > 0:
        width = r.u8()
        bx = r.s16()
        by = r.s16()
        xs = read_bits_16(r, n, bx, width) if width > 0 else [bx] * n
        ys = read_bits_16(r, n, by, width) if width > 0 else [by] * n
        ids = read_bits_16(r, n, 0, 16)
        sprites = [{"x": xs[i], "y": ys[i], "sprite": ids[i]} for i in range(n)]
    e["sprites"] = sprites
    e["_consumed"] += r.p - start
    return e


def read_beam(r, e):
    """i.a(): segmento (x0..x1 a una altura y) con propiedades de rebote."""
    start = r.p
    e["beam"] = {
        "x0": r.s16(), "x1": r.s16(), "x2": r.s16(), "y": r.s16(),
        "p0": r.s8(), "p1": r.s8(), "p2": r.s8(), "p3": r.s8(),
        "_skip": r.u8(),
        "color": list(r.bytes(3)),
    }
    e["_consumed"] += r.p - start
    return e


def read_trigger(r, e):
    """g.a(): caja de activacion + programa bytecode."""
    start = r.p
    box = [r.s16() for _ in range(4)]
    state = r.u8()
    mode_a = r.u8()
    mode_b = r.u8()
    filtro = r.s16()
    nprog = r.u8()
    programs = []
    for _ in range(nprog):
        n = r.u8()
        programs.append(list(r.bytes(n)))
    e["trigger"] = {
        "box": {"min_x": box[0], "max_y": box[1], "max_x": box[2], "min_y": box[3]},
        "state": state,
        "mode_a": mode_a,
        "mode_b": mode_b,
        "filter_object": filtro,
        "programs": programs,
    }
    e["_consumed"] += r.p - start
    return e


def read_tube(r, e):
    start = r.p
    e["tube"] = {"target_object": r.s16(), "power": r.s8()}
    e["_consumed"] += r.p - start
    return e


def read_pad(r, e):
    start = r.p
    e["pad"] = {"sprite": r.u16(), "strength": r.s8()}
    e["_consumed"] += r.p - start
    return e


def read_enemy(r, e):
    start = r.p
    e["enemy"] = {
        "x0": r.s16(), "y0": r.s16(), "x1": r.s16(), "y1": r.s16(),
        "kind": r.u8(),
    }
    e["_consumed"] += r.p - start
    return e


DECODERS = {
    4: read_polygon,
    6: read_trigger,
    9: read_sprite_list,
    10: read_beam,
    11: read_tube,
    12: read_pad,
    15: read_enemy,
}


def parse_container(data, source=""):
    """Cabecera de 14 bytes + registros [tipo:1][longitud:2][payload] terminados en 0x7F."""
    hdr_count = struct.unpack_from(">h", data, 8)[0]
    hdr_triggers = struct.unpack_from(">h", data, 12)[0]
    out = {
        "source": source,
        "header": {
            "raw_0_8": list(data[0:8]),
            "object_count": hdr_count,
            "trigger_count": hdr_triggers,
        },
        "objects": [],
        "errors": [],
    }
    pos = 14
    tag = data[pos]
    pos += 1
    while tag != 0x7F:
        if pos + 2 > len(data):
            out["errors"].append("fin de fichero inesperado (tag 0x%02x)" % tag)
            break
        n = struct.unpack_from(">H", data, pos)[0]
        payload = data[pos + 2:pos + 2 + n]
        pos += 2 + n
        r = Reader(payload)
        e = read_base(r, n)
        e["tag"] = tag
        e["class"] = TAG_CLASS.get(tag, "k")
        e["kind"] = TAG_NAMES.get(tag, "desconocido")
        dec = DECODERS.get(tag)
        if dec:
            try:
                dec(r, e)
            except Exception as ex:  # noqa: BLE001
                out["errors"].append("tag %d: %s" % (tag, ex))
        used = e.pop("_consumed")
        if used != n:
            out["errors"].append(
                "tag %d: consumidos %d de %d bytes" % (tag, used, n))
            e["unparsed_bytes"] = list(payload[used:]) if 0 <= used <= n else None
        e["index"] = len(out["objects"])
        out["objects"].append(e)
        if pos >= len(data):
            out["errors"].append("sin terminador 0x7F")
            break
        tag = data[pos]
        pos += 1
    out["trailing_bytes"] = len(data) - pos
    return out


# ----------------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------------
def main():
    files, objects, preload = load_index()
    cmd = sys.argv[1] if len(sys.argv) > 1 else "index"

    if cmd == "index":
        print("== ficheros (%d) ==" % len(files))
        for i, f in enumerate(files):
            print("  %3d  %-8s off=%-6d len=%d" % (i, f["name"], f["off"], f["len"]))
        print("== objetos (%d) ==" % len(objects))
        for i, o in enumerate(objects):
            print("  %3d  tipo=%-2d v=%-4d refs=%s" % (i, o["tipo"], o["value"], o["refs"]))
        print("== preload ==", preload)
        return

    name = sys.argv[2]
    chunk = [i for i, f in enumerate(files) if f["name"] == name and f["off"] >= 0][0]
    data = chunk_data(files, chunk)
    lvl = parse_container(data, name)

    if cmd == "dump":
        h = lvl["header"]
        print("%s: %d objetos declarados, %d triggers, %d bytes"
              % (name, h["object_count"], h["trigger_count"], len(data)))
        for o in lvl["objects"]:
            extra = ""
            if "polygon" in o:
                extra = " verts=%d tris=%d color=%s trig=%s" % (
                    len(o["polygon"]["vertices"]), len(o["polygon"]["triangles"]),
                    o["polygon"]["color_argb"], o["polygon"]["trigger_id"])
            elif "trigger" in o:
                extra = " box=%s prog=%s" % (o["trigger"]["box"],
                                             [len(p) for p in o["trigger"]["programs"]])
            elif "sprites" in o:
                extra = " sprites=%s" % [s["sprite"] for s in o["sprites"]]
            elif "enemy" in o:
                extra = " enemy=%s" % o["enemy"]
            elif "pad" in o:
                extra = " pad=%s" % o["pad"]
            print("  #%-3d tag=%-2d %-16s depth=%-3d %s%s"
                  % (o["index"], o["tag"], o["kind"], o["depth"],
                     o["transform"], extra))
        print("errores:", lvl["errors"] or "ninguno",
              "| bytes sobrantes:", lvl["trailing_bytes"])
        return

    if cmd == "json":
        outdir = sys.argv[3] if len(sys.argv) > 3 else "out"
        os.makedirs(outdir, exist_ok=True)
        path = os.path.join(outdir, name + ".json")
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(lvl, fh, indent=1, ensure_ascii=False)
        print("escrito", path, "objetos:", len(lvl["objects"]),
              "errores:", lvl["errors"] or "ninguno")
        return


if __name__ == "__main__":
    main()
