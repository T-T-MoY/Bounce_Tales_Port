package com.bouncetales.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Fondo del nivel. El original ({@code n.a(int, int)}, rama {@code j == 4}) pinta un
 * degradado vertical en 40 bandas antes de dibujar el arbol de entidades:
 *
 * <pre>
 *   tema 0 (niveles 0..4)   : #529AE6 -&gt; #71D5FF
 *   tema 1 (niveles 5..9)   : #6ED5C9 -&gt; #9FFFFF
 *   tema 2 (niveles 10..15) : #7610F4 -&gt; #02021A
 * </pre>
 *
 * y hay una paleta alternativa ({@code n.c(nivel)}) de #B400BD -&gt; #FD8C13.
 */
public final class Background {

    /** Orden real de ficheros de nivel ({@code n.v}). */
    private static final String[] LEVEL_FILES = {
            "bf", "bg", "bh", "bi", "br", "bj", "bk", "bl", "bm", "bs",
            "bn", "bo", "bp", "bq", "bt", "bu"
    };

    private static final int[] TOP = {0x529AE6, 0x6ED5C9, 0x7610F4};
    private static final int[] BOTTOM = {0x71D5FF, 0x9FFFFF, 0x02021A};

    private static final int BANDS = 40;

    private final Color top = new Color();
    private final Color bottom = new Color();
    private final Color band = new Color();

    public Background(int theme) {
        int t = Math.max(0, Math.min(TOP.length - 1, theme));
        set(top, TOP[t]);
        set(bottom, BOTTOM[t]);
    }

    /** Tema segun el nombre de fichero del nivel ({@code bf}, {@code bg}, ...). */
    public static Background forLevel(String levelName) {
        return new Background(themeForLevel(levelName));
    }

    public static int themeForLevel(String levelName) {
        for (int i = 0; i < LEVEL_FILES.length; i++) {
            if (LEVEL_FILES[i].equalsIgnoreCase(levelName)) {
                return i <= 4 ? 0 : (i <= 9 ? 1 : 2);
            }
        }
        return 0;
    }

    /** Pinta el degradado en pixeles logicos (Y de OpenGL, 0 abajo). */
    public void draw(ShapeRenderer shapes, int width, int height) {
        int bandHeight = height / BANDS;
        for (int i = 0; i < BANDS; i++) {
            float t = i / (float) (BANDS - 1);
            band.set(top).lerp(bottom, t);
            shapes.setColor(band);
            // i = 0 es la banda superior (Y de pantalla), que en OpenGL queda arriba.
            shapes.rect(0, height - (i + 1) * bandHeight, width, bandHeight + 1);
        }
    }

    private static void set(Color color, int rgb) {
        color.set(((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, 1f);
    }
}
