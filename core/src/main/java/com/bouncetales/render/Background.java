package com.bouncetales.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.bouncetales.asset.LevelCatalog;

import java.util.Random;

/**
 * Fondo del nivel, traduccion de {@code n.a(int, int)} (rama {@code j == 4}).
 *
 * <p>El original pinta dos cosas antes del arbol de entidades:
 * <ol>
 *   <li>Un degradado vertical en 40 bandas ({@code p.f248c / 40}).</li>
 *   <li>Tres capas de siluetas con paralaje, colocadas con {@code n.a(short[], ...)}
 *       usando {@code f126a.setSeed(nivel + 1)}, de modo que la disposicion es fija para
 *       cada nivel.</li>
 * </ol>
 *
 * <p>Temas ({@code n.m39b(nivel)} y {@code n.c(nivel)}):
 * <pre>
 *   tema 0 (niveles 1..5)   : #529AE6 -&gt; #71D5FF  + arboles/islas verdes (imagen 28)
 *   tema 1 (niveles 6..10)  : #6ED5C9 -&gt; #9FFFFF  + setas teal (imagen 19)
 *   tema 2 (niveles 11..15) : #7610F4 -&gt; #02021A  + luna y colinas moradas (imagen 9)
 *   niveles 5, 10 y 15      : #B400BD -&gt; #FD8C13  (sustituye solo el degradado)
 * </pre>
 *
 * <p>La paralaje reproduce la formula entera del original: la X usa la camara absoluta y la
 * Y la desplazamiento respecto a la camara inicial ({@code F}), con el porcentaje de cada
 * capa ({@code i2}: 20/50/80 en los temas 0 y 1; 5/20/50 en el nocturno).
 */
public final class Background {

    private static final int[] TOP = {0x529AE6, 0x6ED5C9, 0x7610F4};
    private static final int[] BOTTOM = {0x71D5FF, 0x9FFFFF, 0x02021A};

    /** Paleta de los niveles 4, 9 y 14 ({@code n.c(f196f)}): morado -&gt; naranja. */
    private static final int SPECIAL_TOP = 0xB400BD;
    private static final int SPECIAL_BOTTOM = 0xFD8C13;

    private static final int BANDS = 40;

    /** Relleno bajo el horizonte de las colinas nocturnas ({@code i9} de las capas 114/113). */
    private static final int NIGHT_FAR_FILL = 0x15113C;
    private static final int NIGHT_NEAR_FILL = 0x02021A;

    /**
     * Una capa de {@code n.a(short[] sArr, int i2, int i3, int i4, int i5, int i6, int i7,
     * int i8, int i9, Graphics)}: los sprites se repiten horizontalmente y la capa se
     * desplaza a {@code i2}% de la velocidad de la camara.
     */
    private static final class Layer {
        /** Sprites candidatos ({@code sArr}); se elige uno al azar por copia. */
        final int[] sprites;
        /** {@code i2}: porcentaje de paralaje. */
        final int parallax;
        /** {@code i4}: separacion base entre copias. */
        final int spacing;
        /** {@code i5}: separacion aleatoria anadida. */
        final int spacingJitter;
        /** {@code i6}: altura base en pixeles de pantalla. */
        final int baseY;
        /** {@code i7}: variacion vertical aleatoria. */
        final int yJitter;
        /** {@code i8}: numero de copias. */
        final int count;
        /** {@code i9}: color de relleno bajo el horizonte (-1 = ninguno). */
        final int fill;

        final int[] x = new int[10];
        final int[] y = new int[10];
        final int[] sprite = new int[10];
        /** {@code f208s[i8]}: ancho total de una repeticion. */
        int totalWidth;

        Layer(int[] sprites, int parallax, int spacing, int spacingJitter,
              int baseY, int yJitter, int count, int fill) {
            this.sprites = sprites;
            this.parallax = parallax;
            this.spacing = spacing;
            this.spacingJitter = spacingJitter;
            this.baseY = baseY;
            this.yJitter = yJitter;
            this.count = count;
            this.fill = fill;
        }

        /**
         * {@code f208s/f209t/f210u}: mismo orden de llamadas a {@link Random} que el
         * original. {@code spriteScratch} se comparte entre capas porque {@code f210u[0]}
         * nunca se reasigna (queda el valor de la capa anterior).
         */
        void layout(Random rnd, int[] spriteScratch) {
            x[0] = 0;
            y[0] = baseY + (yJitter != 0 ? rnd.nextInt() % yJitter : 0);
            for (int i = 1; i <= count; i++) {
                x[i] = x[i - 1]
                        + (spacingJitter != 0 ? Math.abs(rnd.nextInt() % spacingJitter) : 0)
                        + spacing;
                y[i] = baseY + (yJitter != 0 ? rnd.nextInt() % yJitter : 0);
                spriteScratch[i] = Math.abs(rnd.nextInt() % sprites.length);
            }
            for (int i = 0; i < count; i++) {
                // El original indexa con f210u[i] sin comprobar; se acota para evitar
                // un indice fuera de rango cuando cambia el numero de variantes.
                sprite[i] = sprites[spriteScratch[i] % sprites.length];
            }
            totalWidth = x[count];
        }
    }

    private final Color band = new Color();

    private final int topRgb;
    private final int bottomRgb;

    private final Layer[] layers;

    /** Semilla de {@code n.a(...)}: {@code f126a.setSeed(f196f + 1)}. */
    private final int seed;

    private SpriteDrawer sprites;

    /** {@code F}: camara Y al cargar el nivel, referencia de la paralaje vertical. */
    private float baseCameraY;
    private boolean baseCameraSet;

    public Background(int theme) {
        this(theme, false, -1);
    }

    public Background(int theme, boolean special) {
        this(theme, special, -1);
    }

    private Background(int theme, boolean special, int levelIndex) {
        int t = Math.max(0, Math.min(TOP.length - 1, theme));
        topRgb = special ? SPECIAL_TOP : TOP[t];
        bottomRgb = special ? SPECIAL_BOTTOM : BOTTOM[t];
        layers = buildLayers(t, levelIndex);
        seed = levelIndex + 1;
        if (layers.length > 0) {
            // El original coloca las capas una sola vez por nivel (f126a.setSeed(nivel+1)),
            // asi que la disposicion del fondo es fija y no cambia entre frames.
            layoutLayers();
        }
    }

    /** Tema segun el nombre de fichero del nivel ({@code bf}, {@code bg}, ...). */
    public static Background forLevel(String levelName) {
        return forLevel(levelName, null);
    }

    /** Fondo completo (degradado + capas de paralaje) de un nivel. */
    public static Background forLevel(String levelName, SpriteDrawer sprites) {
        int index = LevelCatalog.indexOf(levelName);
        int theme = index < 0 ? 0 : (index <= 4 ? 0 : (index <= 9 ? 1 : 2));
        // n.c(f196f) devuelve true para los indices 4, 9 y 14 (el quinto nivel de cada
        // grupo): el original sustituye el degradado por #B400BD -> #FD8C13.
        boolean special = index >= 0 && index % 5 == 4;
        Background background = new Background(theme, special, index);
        background.sprites = sprites;
        return background;
    }

    public static int themeForLevel(String levelName) {
        int index = LevelCatalog.indexOf(levelName);
        if (index < 0) {
            return 0;
        }
        return index <= 4 ? 0 : (index <= 9 ? 1 : 2);
    }

    /** Numero de capas de paralaje del tema (0 en el fondo del menu). */
    public int layerCount() {
        return layers.length;
    }

    /**
     * Tablas de capas por tema. Los valores son los literales de {@code n.a(int, int)}:
     * sprites, paralaje, separacion, jitter de separacion, Y base, jitter de Y, copias y
     * color de relleno. El ancho de los sprites se consulta al banco porque el original usa
     * {@code p.m64a(id, 0)} para las capas nocturnas.
     */
    private static Layer[] buildLayers(int theme, int levelIndex) {
        if (levelIndex < 0) {
            // Fondo del menu: solo degradado.
            return new Layer[0];
        }
        switch (theme) {
            case 1:
                return new Layer[]{
                        new Layer(new int[]{159, 160}, 20, 200, 140, -200, 200, 5, -1),
                        new Layer(new int[]{161}, 50, 100, 70, -70, 300, 5, -1),
                        new Layer(new int[]{373}, 80, 150, 100, -70, 300, 5, -1),
                };
            case 2:
                return new Layer[]{
                        new Layer(new int[]{353}, 5, 600, 100, -80, 50, 2, -1),
                        new Layer(new int[]{114}, 20, 240, 0, -50, 0, 3, NIGHT_FAR_FILL),
                        new Layer(new int[]{113}, 50, 240, 0, -30, 0, 3, NIGHT_NEAR_FILL),
                };
            case 0:
            default:
                return new Layer[]{
                        new Layer(new int[]{252}, 20, 200, 140, -200, 200, 5, -1),
                        new Layer(new int[]{251}, 50, 100, 70, -70, 300, 5, -1),
                        new Layer(new int[]{388}, 80, 150, 100, -70, 300, 5, -1),
                };
        }
    }

    /** Coloca las capas una sola vez con {@code f126a.setSeed(nivel + 1)}. */
    private void layoutLayers() {
        Random rnd = new Random(seed);
        int[] spriteScratch = new int[10];
        for (Layer layer : layers) {
            layer.layout(rnd, spriteScratch);
        }
    }

    /** {@code draw} historico: solo el degradado (menu y fondos sin sprites). */
    public void draw(ShapeRenderer shapes, int width, int height) {
        drawGradient(shapes, width, height);
    }

    /** Pinta el degradado en pixeles logicos (Y de OpenGL, 0 abajo). */
    public void drawGradient(ShapeRenderer shapes, int width, int height) {
        if (height <= 0) {
            return;
        }
        int bandHeight = height / BANDS;
        if (bandHeight <= 0) {
            bandHeight = 1;
        }
        int r0 = (topRgb >> 16) & 0xFF;
        int g0 = (topRgb >> 8) & 0xFF;
        int b0 = topRgb & 0xFF;
        int r1 = (bottomRgb >> 16) & 0xFF;
        int g1 = (bottomRgb >> 8) & 0xFF;
        int b1 = bottomRgb & 0xFF;
        // i15 = 65536 / alto; cada banda avanza (delta * i15 * alto/40) >> 16.
        long stepR = (long) (r1 - r0) * (65536 / height) * bandHeight;
        long stepG = (long) (g1 - g0) * (65536 / height) * bandHeight;
        long stepB = (long) (b1 - b0) * (65536 / height) * bandHeight;
        for (int i = 0; i * bandHeight < height; i++) {
            int r = r0 + (int) ((i * stepR) >> 16);
            int g = g0 + (int) ((i * stepG) >> 16);
            int b = b0 + (int) ((i * stepB) >> 16);
            band.set(r / 255f, g / 255f, b / 255f, 1f);
            shapes.setColor(band);
            // i = 0 es la banda superior (Y de pantalla), que en OpenGL queda arriba.
            shapes.rect(0, height - (i + 1) * bandHeight, width, bandHeight + 1);
        }
    }

    /**
     * Dibuja las capas de paralaje. Debe llamarse con la proyeccion ya configurada y sin
     * ningun {@code batch} abierto; el metodo abre y cierra el lote por capa para poder
     * intercalar los rellenos bajo el horizonte con {@link ShapeRenderer}.
     */
    public void drawLayers(SpriteBatch batch, ShapeRenderer shapes, ScreenTransform transform) {
        if (layers.length == 0 || sprites == null || batch == null) {
            return;
        }
        if (!baseCameraSet) {
            baseCameraY = transform.cameraY;
            baseCameraSet = true;
        }
        float scale = transform.scale;
        int width = (int) transform.width;
        int height = (int) transform.height;
        // n.a(short[], ...) trabaja en 16.16: (k.f101f.c >> 16) es la camara en mundo y el
        // +33000 mantiene el modulo positivo cuando la camara esta a la izquierda del origen.
        int camX = (int) Math.floor(transform.cameraX);
        int cameraX = (int) ((camX + 33000) * scale);
        int cameraY = (int) ((transform.cameraY - baseCameraY) * scale);

        for (Layer layer : layers) {
            int total = layer.totalWidth;
            if (total <= 0) {
                continue;
            }
            int originX = total - ((cameraX * layer.parallax / 100) % total);
            int originY = (cameraY * layer.parallax / 100) + height;

            batch.begin();
            for (int i = 0; i < layer.count; i++) {
                int x = originX + layer.x[i];
                int y = originY + layer.y[i];
                int id = layer.sprite[i];
                if (i < 2) {
                    sprites.draw(batch, id, x, y);
                }
                if (i > 2) {
                    sprites.draw(batch, id, x - total * 2, y);
                }
                sprites.draw(batch, id, x - total, y);
            }
            batch.end();

            if (layer.fill != -1) {
                float horizon = height - (originY + layer.y[0]);
                if (horizon > 0f) {
                    shapes.begin(ShapeRenderer.ShapeType.Filled);
                    set(shapes, layer.fill);
                    shapes.rect(0f, 0f, width, horizon);
                    shapes.end();
                }
            }
        }
    }

    private static void set(ShapeRenderer shapes, int rgb) {
        shapes.setColor(((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, 1f);
    }
}
