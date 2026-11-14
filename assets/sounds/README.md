# Audio

Los `.ogg` de esta carpeta son los ocho MIDI originales de `resources/` renderizados con el
sintetizador Gervill del JDK y codificados a Vorbis con ffmpeg.

| Archivo | MIDI original | Uso en el original (`n.d()`) |
|---------|---------------|------------------------------|
| `28.ogg` | `aw.mid` | música del menú |
| `29.ogg` | `ax.mid` | niveles 1-5 (índices 0-4), salvo 4 y 5 |
| `30.ogg` | `ay.mid` | niveles 6-10 (índices 5-9), salvo 9 y 10 |
| `31.ogg` | `az.mid` | niveles 11-16 (índices 10-15), salvo 14 y 15 |
| `32.ogg` | `ba.mid` | tema alternativo de los niveles 4, 9 y 14 |
| `33.ogg` | `bb.mid` | jingle de nivel completado |
| `34.ogg` | `bc.mid` | jingle de game over |
| `35.ogg` | `bd.mid` | tema alternativo de los niveles 5, 10 y 15 |

`SoundBank` usa `sounds/<id>.ogg` tanto para música (`playMusic`) como para efectos
(`playSound`). Los efectos del original (ids 2, 8, 56-88) se sintetizaban en el móvil y no
tienen fichero propio: si faltan, `SoundBank` registra un aviso y continúa sin sonido.

## Regenerar

Con JDK 17+ y ffmpeg (con `libvorbis`) en el `PATH`:

```bat
python tools\convert_midi.py
```

Para convertir solo algunas pistas:

```bat
python tools\convert_midi.py --ids 28,29
```

Si no hay ffmpeg, el script deja WAV en su lugar; LibGDX también los carga, pero el objetivo
del proyecto es OGG.
