# Bounce Tales — port a LibGDX

Port del juego J2ME *Bounce Tales* (Nokia) a LibGDX/Java. **Fases 1-4 completadas**: render,
física de la bola, triggers y entidades, menú de niveles, progresión automática, HUD y audio.

## Requisitos

- JDK 17 o superior (`JAVA_HOME` configurado). Probado con Temurin 21.
- No hace falta instalar Gradle: usa el wrapper incluido (`gradlew.bat`).
- Opcional para regenerar audio: `ffmpeg` con `libvorbis` en el `PATH`.

## Ejecutar

```bat
gradlew.bat desktop:run
```

Arranca en el **menú principal**, que lista los 16 niveles en el orden original y reproduce la
música del menú. Dentro de un nivel, `ESC` vuelve al menú.

Para arrancar directamente en un nivel (modo de pruebas):

```bat
gradlew.bat desktop:run -Dbounce.level=bn
```

Para forzar menú o juego directo (por encima de la detección anterior):

```bat
gradlew.bat desktop:run -Dbounce.menu=true
gradlew.bat desktop:run -Dbounce.level=bg -Dbounce.menu=false
```

Niveles disponibles: `bf bg bh bi br bj bk bl bm bs bn bo bp bq bt bu`.

Opciones de prueba útiles (se pasan con `-D`):

| Propiedad | Efecto |
|-----------|--------|
| `bounce.level=bg` | arranca directamente en ese nivel |
| `bounce.menu=true\|false` | fuerza menú o juego directo |
| `bounce.autocomplete=true` | completa el nivel al primer paso y encadena al siguiente |
| `bounce.teleport=x,y` | recoloca la bola al empezar |
| `bounce.drop=N` | deja caer la bola `N` unidades al empezar |
| `bounce.debug=true` | dibuja las aristas de colisión (F1 lo alterna) |
| `bounce.trace=true` | traza posición/velocidad cada 15 pasos |
| `bounce.screenshot=...png` | captura y sale |
| `bounce.frames=N` | frame en el que capturar (por defecto 20) |

Captura automática a PNG (útil para comparar sin abrir ventana). En modo captura se ignoran las
teclas, así que el menú se puede fotografiar sin que arranque un nivel:

```bat
gradlew.bat desktop:run "-Dbounce.menu=true" "-Dbounce.screenshot=D:\tmp\menu.png" "-Dbounce.frames=20"
gradlew.bat desktop:run "-Dbounce.level=bf" "-Dbounce.screenshot=D:\tmp\bf.png" "-Dbounce.frames=20"
```

## Audio

`assets/sounds/` contiene los ocho MIDI originales convertidos a OGG (`28.ogg`–`35.ogg`):
música de menú, tres temas de nivel, dos temas alternativos, jingle de nivel completado y jingle
de game over. Ver `assets/sounds/README.md` para la tabla completa.

`SoundBank` carga música en streaming y la reproduce en bucle; los efectos del original
(ids 2, 8 y 56-88) no tienen fichero propio todavía, así que se registran como aviso y se
ignoran sin romper la partida. Para regenerar los OGG:

```bat
python tools\convert_midi.py
```

## Estructura

```
bounce-tales/
  build.gradle            Gradle raíz (versión de libGDX, Java 17)
  settings.gradle
  core/                   lógica y render (independiente de la plataforma)
    src/main/java/com/bouncetales/
      BounceTalesGame.java    menú, juego y transición entre niveles
      MenuScreen.java         Screen: selección de nivel
      GameScreen.java         Screen: carga + bucle de dibujo
      asset/                  SpriteBank, LevelLoader, ResourceIndex, LevelCatalog...
      audio/                  SoundBank (música en streaming y efectos)
      physics/                BallBody, CollisionWorld
      world/                  Level, Entity, PolygonShape, entidades y triggers
      render/                 ScreenTransform, SpriteDrawer, LevelRenderer, Background
      ui/                     Hud (nivel, contador, icono de coleccionable)
      util/                   ScreenshotCapture
  desktop/                lanzador LWJGL3 (DesktopLauncher)
  assets/                 recursos en runtime (Gdx.files.internal)
    images/               46 PNG del juego
    levels/               16 niveles convertidos a JSON
    sprites/sprites.json  banco de sprites (326 descriptores, 214 prefabs)
    sounds/               28.ogg-35.ogg (MIDI original convertido) + README
    index/a               índice de recursos original
  resources/              extracción original (entrada de tools/)
  tools/                  extractores Python y convert_midi.py / MidiToOgg.java
  out/                    capturas y JSON generados
```

## Cómo está montado el render

1. **Espacio de pantalla lógico 240×320** (`FitViewport`). La ventana solo escala ese lienzo, así
   que los sprites se dibujan a tamaño nativo (1 px de PNG = 1 px lógico) y el encuadre coincide
   con el original.
2. **Cámara**: replica `k.f103g` (`scale = 43266/65536 ≈ 0.6602`, Y invertida) y `k.f101f`
   (`centerOn(bola)` con la bola en el 70 % de la altura). Ver `ScreenTransform`.
3. **Orden de dibujo**: recorrido DFS del árbol de entidades y orden **descendente** por
   `depth = (m & 31) - 16` (mayor depth = más al fondo), igual que `k.a(...)`.
4. **Polígonos**: triángulos rellenos (`fillTriangle` en el original → `ShapeRenderer.triangle`).
5. **Sprites**: `p.a(x, y, id)` → el sprite se dibuja en `(x - offX, y - offY)`; los prefabs
   compuestos y las animaciones se resuelven recursivamente (`SpriteDrawer`).
6. **Fondo**: degradado vertical de 40 bandas por tema de nivel (`Background`, de `n.a(...)`).

## Menú, progresión y HUD

- El orden de niveles sale de `index/a` (`ResourceIndex.LEVEL_OBJECTS`): `bf, bg, bh, bi, br,
  bj, bk, bl, bm, bs, bn, bo, bp, bq, bt, bu`. `LevelCatalog` lo cachea y lo usa el menú, el
  fondo y la progresión.
- Al recoger todos los coleccionables suena el jingle (id 33), el HUD muestra la victoria y,
  tras 2,2 s, se carga el siguiente nivel. En el último se vuelve al menú.
- El HUD muestra `NIVEL <n> <nombre>`, el icono de huevo (sprite 208) y `recogidos/total`.
- La música sigue `n.d()`: menú = 28, niveles = 29-31 según el grupo, 32/35 en los niveles 4/5
  de cada grupo y 33 al completar. Los ids 28-35 corresponden a `assets/sounds/<id>.ogg`.

## Qué falta (siguientes fases)

- Efectos de sonido: sintetizar o muestrear los ids 2, 8 y 56-88 (ahora son no-op con aviso).
- Enemigos y objetos que aún no tienen comportamiento completo, partículas y detalles de la bola
  (arco de brillo y puntos de `c.a(...)`).
- Sustituir la fuente de depuración por la tipografía del original y pulir el arte del menú.
- Guardar progreso/desbloqueo entre sesiones.

## Regenerar datos

Los scripts de `tools/` leen `resources/` y escriben en `out/`:

```bat
python tools\bounce_levels.py json bf out\levels
python tools\export_sprites.py out\sprites.json
```

Después copia `out/levels/*.json` a `assets/levels/` y `out/sprites.json` a
`assets/sprites/sprites.json`.
