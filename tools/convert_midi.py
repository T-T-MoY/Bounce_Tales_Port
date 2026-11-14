#!/usr/bin/env python3
"""Convierte los MIDI originales de Bounce Tales a OGG para LibGDX.

El JDK no reproduce MIDI en LibGDX, pero si incluye el sintetizador Gervill. Este script:

1. renderiza cada `.mid` a WAV con `tools/MidiToOgg.java` (modo fuente, sin dependencias),
2. convierte el WAV a OGG Vorbis con ffmpeg,
3. deja el resultado en `assets/sounds/<id>.ogg`.

Los ids son los cargadores de audio del indice original (`index/a`):

    28 aw   29 ax   30 ay   31 az   32 ba   33 bb   34 bc   35 bd

Uso:
    python tools/convert_midi.py
    python tools/convert_midi.py --ids 28,29
    python tools/convert_midi.py --keep-wav

Requisitos: JDK 17+ y ffmpeg con libvorbis (si falta ffmpeg se deja el WAV y se avisa).
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "resources"
SOUNDS = ROOT / "assets" / "sounds"
JAVA_TOOL = ROOT / "tools" / "MidiToOgg.java"

# id de cargador -> nombre del MIDI original
TRACKS = {
    28: "aw",
    29: "ax",
    30: "ay",
    31: "az",
    32: "ba",
    33: "bb",
    34: "bc",
    35: "bd",
}

JAVA_FLAGS = ["--add-exports", "java.desktop/com.sun.media.sound=ALL-UNNAMED"]


def run(cmd: list[str]) -> None:
    print("  $ " + " ".join(str(c) for c in cmd))
    subprocess.run(cmd, check=True)


def render_wav(java: str, midi: Path, wav: Path) -> None:
    run([java, *JAVA_FLAGS, str(JAVA_TOOL), str(midi), str(wav)])


def main() -> int:
    parser = argparse.ArgumentParser(description="MIDI -> OGG para Bounce Tales")
    parser.add_argument("--ids", help="lista de ids separada por comas (por defecto: todos)")
    parser.add_argument("--keep-wav", action="store_true", help="conserva los WAV intermedios")
    parser.add_argument("--quality", type=int, default=5, help="calidad Vorbis 0-10 (defecto 5)")
    args = parser.parse_args()

    java = shutil.which("java")
    if not java:
        print("ERROR: no se encontro 'java' en PATH. Instala un JDK 17+.", file=sys.stderr)
        return 2
    if not JAVA_TOOL.exists():
        print(f"ERROR: falta {JAVA_TOOL}", file=sys.stderr)
        return 2

    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("AVISO: no se encontro ffmpeg. Se generaran WAV (LibGDX tambien los carga),")
        print("       pero el objetivo del proyecto es OGG. Instala ffmpeg con libvorbis.")

    wanted = sorted(TRACKS)
    if args.ids:
        try:
            wanted = [int(x.strip()) for x in args.ids.split(",") if x.strip()]
        except ValueError:
            print("ERROR: --ids debe ser una lista de numeros, p. ej. 28,29", file=sys.stderr)
            return 2

    missing = [i for i in wanted if i not in TRACKS]
    if missing:
        print(f"ERROR: ids desconocidos: {missing}. Validos: {sorted(TRACKS)}", file=sys.stderr)
        return 2

    SOUNDS.mkdir(parents=True, exist_ok=True)
    print(f"Salida: {SOUNDS}")

    failures = 0
    for track_id in wanted:
        name = TRACKS[track_id]
        midi = RESOURCES / f"{name}.mid"
        if not midi.exists():
            print(f"ERROR: falta {midi}", file=sys.stderr)
            failures += 1
            continue
        print(f"\n[{track_id}] {name}.mid -> {track_id}.ogg")
        with tempfile.TemporaryDirectory(prefix="bounce-midi-") as tmp:
            wav = Path(tmp) / f"{name}.wav"
            try:
                render_wav(java, midi, wav)
                if ffmpeg:
                    ogg = SOUNDS / f"{track_id}.ogg"
                    run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                         "-i", str(wav), "-c:a", "libvorbis", "-q:a", str(args.quality),
                         str(ogg)])
                    print(f"  OK: {ogg} ({ogg.stat().st_size} bytes)")
                else:
                    fallback = SOUNDS / f"{track_id}.wav"
                    shutil.copyfile(wav, fallback)
                    print(f"  OK (sin ffmpeg): {fallback} ({fallback.stat().st_size} bytes)")
                if args.keep_wav and ffmpeg:
                    keep = SOUNDS / f"{name}.wav"
                    shutil.copyfile(wav, keep)
            except subprocess.CalledProcessError as exc:
                print(f"  ERROR: {exc}", file=sys.stderr)
                failures += 1

    print("\nHecho." if failures == 0 else f"\nTerminado con {failures} error(es).")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
