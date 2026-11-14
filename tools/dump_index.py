"""Parser de prueba para el indice de recursos '/a' de Bounce Tales (J2ME).

Formato (deducido de p.m57e() en p.java):
  short count
  count * { UTF nombre, int offset, int longitud }
  short count2
  count2 * { byte tipo, byte nrefs, short ?? , nrefs * short refs }
  short count3
  count3 * { short id, short recurso }
"""
import struct
import sys

data = open(sys.argv[1] if len(sys.argv) > 1 else "resources/a", "rb").read()
p = 0


def u8():
    global p
    v = data[p]
    p += 1
    return v


def u16():
    global p
    v = struct.unpack_from(">H", data, p)[0]
    p += 2
    return v


def s16():
    global p
    v = struct.unpack_from(">h", data, p)[0]
    p += 2
    return v


def s32():
    global p
    v = struct.unpack_from(">i", data, p)[0]
    p += 4
    return v


def utf():
    global p
    n = u16()
    raw = data[p:p + n]
    p += n
    return raw.decode("utf-8", "replace")


n = u16()
print("recursos:", n)
recursos = []
for i in range(n):
    nombre = utf()
    off = s32()
    ln = s32()
    recursos.append((nombre, off, ln))
    print(f"  [{i:3}] {nombre:<10} off={off:<7} len={ln}")

n2 = u16()
print("objetos indexados:", n2)
for i in range(n2):
    tipo = u8()
    nrefs = u8()
    v = s16()
    refs = [s16() for _ in range(nrefs)]
    print(f"  [{i:3}] tipo={tipo:<3} v={v:<6} refs={refs}")

n3 = u16()
print("preload pairs:", n3)
for i in range(n3):
    print("   ", s16(), "->", s16())

print("bytes consumidos:", p, "de", len(data))
