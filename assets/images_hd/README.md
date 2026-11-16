# Sprites HD

Esta carpeta es opcional. Si existe y contiene **todos** los PNG que usa
`assets/sprites/sprites.json`, el juego puede dibujar los sprites ampliados 8x.

## Formato

- Un PNG por cada imagen de `assets/images/`, con **el mismo nombre** (`c.png`,
  `d.png`, ... `au.png`).
- Cada atlas debe ser exactamente el original **escalado x8**: el contenido, la
  posicion de cada sprite dentro del atlas y el canal alfa deben coincidir. El
  juego multiplica por 8 las coordenadas de recorte (`src_x`, `src_y`, `w`, `h`)
  pero sigue dibujando al tamano logico original.
- No hace falta tocar `sprites.json` ni los niveles.

Ejemplo con un atlas de 422x208 (imagen `l.png`): el HD debe medir 3376x1664.

## Como se activa

En el menu principal, tecla `G` (o clic sobre la linea `G: sprites ...`).
La eleccion se guarda en `Preferences` (`~/.prefs/bounce-tales`, clave
`hdSprites`) y se mantiene al cambiar de nivel y al reiniciar.

## Fallback

Si falta la carpeta o algun PNG, el juego avisa por consola
(`[Sprites] faltan imagenes ...`) y vuelve a los originales automaticamente.

## Prueba rapida

Para comprobar el pipeline sin arte definitivo se puede generar una copia
ampliada 8x con interpolacion nearest-neighbor y usarla como placeholder.
