# Bounce Tales — port a LibGDX

Port del juego J2ME *Bounce Tales* (Nokia) a LibGDX/Java. **Fases 1-7 completadas**: render,
física de la bola, triggers y entidades, menú de niveles, progresión automática, HUD, audio,
formas de la bola, animación de reposo y de muerte, agua y muros rompibles.

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

### Controles

| Tecla | Acción |
|-------|--------|
| `←` `→` / `A` `D` | moverse |
| `Espacio` / `↑` | saltar |
| `1` `2` `3` | forma normal / piedra / balón |
| `TAB` | cicla la forma (normal → piedra → balón) |
| `M` (menú) | silencia/activa la música |
| `F` (menú) | silencia/activa los efectos |
| `F1` | aristas de colisión |
| `ESC` | volver al menú |

Las formas siguen `c.f()`: la piedra pesa más, salta menos y rueda con más resistencia; el balón
pesa menos y salta menos, con una velocidad máxima mucho menor. La bola parpadea y pone caras si
se queda quieta 3 s sin entrada, y al morir rebota 1 s y cae 2 s antes de reiniciar el nivel.

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
| `bounce.auto=right\|left\|jump` | entrada automática para pruebas |
| `bounce.screenshot=...png` | captura y sale |
| `bounce.frames=N` | frame en el que capturar (por defecto 20) |
| `bounce.shape=0\|1\|2` | forma inicial (normal / piedra / balón) |
| `bounce.face=N` | fuerza la cara de reposo `f7c` (0-8) |
| `bounce.die=true` | dispara la animación de muerte al empezar |
| `bounce.muteMusic=true` | arranca con la música silenciada |
| `bounce.muteFx=true` | arranca con los efectos silenciados |
| `bounce.menuKeys=MF` | despacha esas teclas en el menú (pruebas sin teclado) |

Captura automática a PNG (útil para comparar sin abrir ventana). En modo captura se ignoran las
teclas (salvo `bounce.menuKeys`), así que el menú se puede fotografiar sin que arranque un nivel:

```bat
gradlew.bat desktop:run "-Dbounce.menu=true" "-Dbounce.screenshot=D:\tmp\menu.png" "-Dbounce.frames=20"
gradlew.bat desktop:run "-Dbounce.level=bf" "-Dbounce.screenshot=D:\tmp\bf.png" "-Dbounce.frames=20"
```

## Audio

`assets/sounds/` contiene los ocho MIDI originales convertidos a OGG (`28.ogg`–`35.ogg`):
música de menú, tres temas de nivel, dos temas alternativos, sonido de muerte (`33`) y jingle
de nivel completado (`34`). Ver `assets/sounds/README.md` para la tabla completa.

`SoundBank` carga música en streaming y la reproduce en bucle; los efectos del original
(ids 2, 8 y 56-88) no tienen fichero propio todavía, así que se registran como aviso y se
ignoran sin romper la partida. Las banderas estáticas `musicMuted`/`fxMuted` conservan el
muteo elegido en el menú (`M`/`F`) al entrar en un nivel. Para regenerar los OGG:

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
- Al recoger todos los coleccionables suena el jingle (id 34), el HUD muestra la victoria y,
  tras 2,2 s, se carga el siguiente nivel. En el último se vuelve al menú.
- El HUD muestra `NIVEL <n> <nombre>`, el icono de huevo (sprite 208) y `recogidos/total`.
- La música sigue `n.d()`: menú = 28, niveles = 29-31 según el grupo, 32/35 en los niveles 4/5
  de cada grupo y 34 al completar; el 33 es la muerte. Los ids 28-35 corresponden a
  `assets/sounds/<id>.ogg`.

## Formas, reposo y muerte (Fase 5)

- **Formas** (`c.f34d`, `BallBody`): `0` normal, `1` piedra y `2` balón. Se eligen con `1`,
  `2`, `3` o `TAB`, que replica el ciclo de `c.f()`. La física sale de las tablas de `c.java`
  (`masa = {1.0, 1.4, 0.5}`, `aceleración = {500, 350, 312.5}`, `vel. máx = {280, 350, 150}`,
  `salto = {288, 255, 162.5}`, resistencia `{0.6, 0.3, 0.6}`); `BallBody.type` selecciona la
  entrada en cada paso.
- **Sprites**: `LevelRenderer` dibuja la bola normal (círculo + sprites 17/28), la piedra con
  las 32 rotaciones de `c.f9a` (11,25° por frame) y el balón con los seis gajos de colores de
  `c.p` más el sprite 5.
- **Reposo/AFK** (`BallBody.updateFace`, `c.java` 335-368): tras 3 s con velocidad &lt; 40 y sin
  entrada, `f7c = 2` parpadea 600 ms y después elige caras 3..8 (600 ms la 3, 1500 ms el
  resto). El render usa los prefabs 462, 447 y 452-460; el log anota `AFK: cara N`.
- **Muerte** (`c.e()`, `n.java` 1371-1394): al morir suena el id 33, la física se congela y la
  bola rebota 1 s con la tabla senoidal `n.f151b` y luego cae 2 s a `22500/65536` px/ms con la
  cara 20. Al agotar los 3 s de `f129c` el nivel se reinicia.

Capturas de verificación: `out/phase5_stone.png`, `out/phase5_beach.png`, `out/phase5_afk.png`,
`out/phase5_death.png`, `out/phase5_death_reset.png` y `out/phase5_menu_keymute.png`.

## Agua y muros rompibles (Fase 6)

- El tag 10 se exporta como `"beam"`, pero representa `i.java`: agua/corriente. Se dibuja como
  un volumen azul translúcido con superficie ondulada; las entidades cuyo color original es
  negro son corrientes invisibles y no se dibujan.
- La corriente interpolada aporta fuerza y amortiguación. En `bg`, la pelota de playa (masa 0,5)
  oscila junto a la superficie (interfaz ≈587; centro de la bola ≈607 por su radio de 20), mientras
  las formas normal y piedra se hunden hasta la geometría del fondo. La traza indica entrada,
  salida y fuerza neta.
- El opcode 1 inicia la animación de decoración: sprite 334 se rompe con la secuencia 474 (5
  frames/750 ms), se desvanece durante 1500 ms y desaparece; 118→485 y 342→480 son loops. El
  opcode 29 marca la geometría como sucia cuando cambia `solid`, para reconstruir colisiones.
- Vars de triggers 3-6 se actualizan tras física e interacción: forma, rapidez, `vx` y `vy`.

Pruebas reproducibles (Windows, desde la raíz del proyecto):

```bat
gradlew.bat desktop:run -Dbounce.level=bm -Dbounce.teleport=1580,-560 -Dbounce.auto=right -Dbounce.shape=1 -Dbounce.trace=true
gradlew.bat desktop:run -Dbounce.level=bg -Dbounce.teleport=-800,680 -Dbounce.shape=2 -Dbounce.trace=true
gradlew.bat desktop:run -Dbounce.level=bg -Dbounce.teleport=-800,680 -Dbounce.shape=0 -Dbounce.trace=true
gradlew.bat desktop:run -Dbounce.level=bg -Dbounce.teleport=-800,680 -Dbounce.shape=1 -Dbounce.trace=true
```

Capturas automáticas usadas para esta fase: `out/phase6_wall.png`,
`out/phase6_water_beach.png`, `out/phase6_water_normal.png` y
`out/phase6_water_stone.png`. Para generar una captura durante una prueba, añade
`-Dbounce.screenshot=D:\ruta\captura.png -Dbounce.frames=240` (el número de frames se ajusta
según el tiempo que se quiera dejar simular).

## Tubos lanzadores (Fase 7)

- El tag 11 es la entidad original `l` (tubo), no un checkpoint. El exportador ya lo nombra
  `"tube"`; el cargador también acepta el antiguo campo `"checkpoint"` para mantener compatibles
  los JSON anteriores.
- La bola objetivo queda capturada dentro del tubo durante 900 ms. A los 600 ms sale por la boca
  (120 unidades en el eje local X) con velocidad dirigida según la matriz de orientación y
  magnitud `16 × power`; el cooldown es de 500 ms. El temporizador empieza al pulsar Espacio/↑;
  mientras espera, ←/→ orientan el tubo. Mientras está retenida, se suspenden física, colisiones
  e interacciones normales de la bola.
- El render dibuja el tubo orientado por su transform y un indicador de carga. El log `Tube`
  registra captura y lanzamiento con la velocidad aplicada.

Prueba en `bn` (tubo en la entidad 3, dirigido a la bola 188):

```bat
gradlew.bat desktop:run "-Dbounce.level=bn" "-Dbounce.teleport=1032,27" "-Dbounce.auto=rightjump" "-Dbounce.trace=true" "-Dbounce.screenshot=..\out\phase7_tube.png" "-Dbounce.frames=100"
```

Los niveles `bo`, `bp` y `bt` también contienen tubos. Para exportar de nuevo un nivel, ejecuta
`python tools\bounce_levels.py json bn out\levels` y copia el JSON generado a `assets\levels\`.

## Qué falta (siguientes fases)

- Efectos de sonido: sintetizar o muestrear los ids 2, 8 y 56-88 (ahora son no-op con aviso).
- Completar los comportamientos pendientes de enemigos/objetos y partículas (arco de brillo y
  puntos de `c.a(...)`).
- Sustituir la fuente de depuración por la tipografía del original y pulir el arte del menú.
- Guardar progreso/desbloqueo entre sesiones (las formas están todas desbloqueadas para probar).

## Regenerar datos

Los scripts de `tools/` leen `resources/` y escriben en `out/`:

```bat
python tools\bounce_levels.py json bf out\levels
python tools\export_sprites.py out\sprites.json
```

Después copia `out/levels/*.json` a `assets/levels/` y `out/sprites.json` a
`assets/sprites/sprites.json`.
