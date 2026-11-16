# Fuentes

`Roboto-Regular.ttf` se usa para toda la interfaz (`MenuScreen`, `SpriteViewerScreen`)
a traves de `com.bouncetales.render.FontManager`, que la rasteriza con FreeType a 48 px
y filtros lineales.

- Origen: https://github.com/googlefonts/roboto (`src/hinted/Roboto-Regular.ttf`)
- Licencia: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)

Para cambiar de tipografia basta con dejar otro `.ttf` en esta carpeta y actualizar
`FontManager.FONT_FILE`. Si el fichero falta, el juego cae a la fuente por defecto de
libGDX y lo registra como error en consola.
