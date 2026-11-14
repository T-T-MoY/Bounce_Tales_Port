# Bounce Tales — port a LibGDX

Port del juego J2ME *Bounce Tales* (Nokia) a LibGDX/Java. **Fase 1**: carga de nivel y render
estático (sin físicas ni triggers).

## Requisitos

- JDK 17 o superior (`JAVA_HOME` configurado). Probado con Temurin 21.
- No hace falta instalar Gradle: usa el wrapper incluido (`gradlew.bat`).

## Ejecutar

```bat
gradlew.bat desktop:run
```

Nivel por defecto: `bf` (el primero). Para otro nivel:

```bat
gradlew.bat desktop:run -Dbounce.level=bn
```

Niveles disponibles: `bf bg bh bi br bj bk bl bm bs bn bo bp bq bt bu`.

Captura automática a PNG (útil para comparar sin abrir ventana):

```bat
gradlew.bat desktop:run "-Dbounce.screenshot=D:\tmp\bf.png" "-Dbounce.frames=10"
```

## Estructura

```
bounce-tales/
  build.gradle            Gradle raíz (versión de libGDX, Java 17)
  settings.gradle
  core/                   lógica y render (independiente de la plataforma)
    src/main/java/com/bouncetales/
      BounceTalesGame.java    ApplicationListener
      GameScreen.java         Screen: carga + bucle de dibujo
      asset/                  SpriteBank, LevelLoader, ResourceIndex, Prefab...
      world/                  Level, Entity, PolygonShape, DecorationList
      render/                 ScreenTransform, SpriteDrawer, LevelRenderer, Background
  desktop/                lanzador LWJGL3 (DesktopLauncher)
  assets/                 recursos en runtime (Gdx.files.internal)
    images/               46 PNG del juego
    levels/               16 niveles convertidos a JSON
    sprites/sprites.json  banco de sprites (326 descriptores, 214 prefabs)
    index/a               índice de recursos original
  resources/              extracción original (entrada de tools/)
  tools/                  scripts Python de extracción (bounce_levels.py, export_sprites.py)
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

## Qué falta (siguientes fases)

- F2: colisiones y física de la bola (`c.java`), pads y rampas.
- F3: animaciones dependientes del tiempo (enemigos, pads, coleccionables).
- F4: VM de triggers (`g.java`).
- F5: enemigos, tuberías, partículas, HUD, audio y menús.
- Detalles de la bola: falta el arco de brillo y los puntos de `c.a(...)`; ahora se dibuja
  contorno + cuerpo + sprites 17/28.

## Regenerar datos

Los scripts de `tools/` leen `resources/` y escriben en `out/`:

```bat
python tools\bounce_levels.py json bf out\levels
python tools\export_sprites.py out\sprites.json
```

Después copia `out/levels/*.json` a `assets/levels/` y `out/sprites.json` a
`assets/sprites/sprites.json`.
