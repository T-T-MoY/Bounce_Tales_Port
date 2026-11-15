package com.bouncetales.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.bouncetales.render.SpriteDrawer;

/**
 * HUD del nivel, calcado de {@code n.java} lineas 1564-1578:
 *
 * <pre>
 *   huevo (102) en (0,0)          contador de huevos a x=32, y=7
 *   separador (101) tras el contador, total a continuacion
 *   reloj (103) en (W,0)          minutos:segundos alineados a la derecha en W-32
 * </pre>
 *
 * <p>Los digitos son los sprites 90..99; el separador el 101, los dos puntos el 100. El
 * tiempo se muestra como {@code minutos:segundos} con dos cifras en los segundos.
 */
public final class Hud implements Disposable {

    private static final int EGG_ICON = 102;
    private static final int CLOCK_ICON = 103;
    private static final int SEPARATOR = 101;
    private static final int COLON = 100;
    private static final int FIRST_DIGIT = 90;

    private static final int LEFT_ALIGN = 4;
    private static final int RIGHT_ALIGN = 8;
    /** {@code p.a(iA2, 7, 100, 24)}: alinea los dos puntos a la derecha. */
    private static final int COLON_ALIGN = 24;

    private static final float COUNT_X = 32f;
    private static final float TEXT_Y = 7f;
    private static final float SEPARATOR_GAP = 9f;
    /** {@code i27 = ((W - 25) - 6) - 1}. */
    private static final float TIME_RIGHT_MARGIN = 32f;
    private static final float COLON_GAP = 5f;
    private static final float SHADOW = 1f;

    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private int width;
    private int height;

    public Hud(int width, int height) {
        this.width = width;
        this.height = height;
        font.getData().setScale(0.5f);
    }

    /** Ajusta los anclajes del HUD al nuevo lienzo logico (viewport extendido). */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void render(SpriteBatch batch, SpriteDrawer sprites, int collected, int total,
                       float levelTime, boolean victory) {
        batch.begin();

        // Huevo recogido / total.
        sprites.draw(batch, EGG_ICON, 0f, 0f);
        float countWidth = drawNumber(batch, sprites, COUNT_X, TEXT_Y, collected,
                LEFT_ALIGN, false);
        float separatorX = COUNT_X + countWidth;
        sprites.draw(batch, SEPARATOR, separatorX, TEXT_Y);
        drawNumber(batch, sprites, separatorX + SEPARATOR_GAP, TEXT_Y, total,
                LEFT_ALIGN, false);

        // Reloj y tiempo jugado (minutos:segundos).
        sprites.draw(batch, CLOCK_ICON, width, 0f);
        int totalSeconds = Math.max(0, (int) levelTime);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        float timeRight = width - TIME_RIGHT_MARGIN;
        float secondsWidth = drawNumber(batch, sprites, timeRight, TEXT_Y, seconds,
                RIGHT_ALIGN, true);
        float colonX = timeRight - secondsWidth;
        sprites.drawBox(batch, COLON, colonX, TEXT_Y, COLON_ALIGN);
        drawNumber(batch, sprites, colonX - COLON_GAP, TEXT_Y, minutes,
                RIGHT_ALIGN, false);

        if (victory) {
            font.getData().setScale(0.65f);
            String title = "NIVEL COMPLETADO!";
            layout.setText(font, title);
            float x = (width - layout.width) * 0.5f;
            float y = (height + layout.height) * 0.5f;
            font.setColor(0f, 0f, 0f, 0.65f);
            font.draw(batch, layout, x + SHADOW, y - SHADOW);
            font.setColor(1f, 0.86f, 0.2f, 1f);
            font.draw(batch, layout, x, y);

            font.getData().setScale(0.45f);
            font.setColor(1f, 1f, 1f, 1f);
            layout.setText(font, "preparando el siguiente nivel...");
            font.draw(batch, layout, (width - layout.width) * 0.5f, y - 18f);
        }

        batch.end();
    }

    /**
     * Dibuja un numero con los sprites 90..99 y devuelve su anchura.
     *
     * <p>Traduce {@code n.a(int x, int y, int valor, int flags, boolean ceroInicial)}:
     * {@code flags == 4} alinea a la izquierda; cualquier otro valor alinea a la derecha.
     */
    private static float drawNumber(SpriteBatch batch, SpriteDrawer sprites, float x, float y,
                                    int value, int flags, boolean padZero) {
        float numberWidth = measure(sprites, value, padZero);
        float right = flags == LEFT_ALIGN ? x + numberWidth : x;
        drawDigits(batch, sprites, right, y, value, padZero);
        return numberWidth;
    }

    private static float measure(SpriteDrawer sprites, int value, boolean padZero) {
        float total = 0f;
        if (value == 0) {
            total += sprites.width(FIRST_DIGIT);
        } else {
            for (int v = value; v != 0; v /= 10) {
                total += sprites.width(FIRST_DIGIT + v % 10);
            }
        }
        if (value < 10 && padZero) {
            total += sprites.width(FIRST_DIGIT);
        }
        return total;
    }

    /** Dibuja los digitos de derecha a izquierda terminando en {@code right}. */
    private static void drawDigits(SpriteBatch batch, SpriteDrawer sprites, float right,
                                   float y, int value, boolean padZero) {
        float cursor = right;
        if (value == 0) {
            cursor -= sprites.width(FIRST_DIGIT);
            sprites.drawBox(batch, FIRST_DIGIT, cursor, y, 0);
        } else {
            for (int v = value; v != 0; v /= 10) {
                int digit = FIRST_DIGIT + v % 10;
                cursor -= sprites.width(digit);
                sprites.drawBox(batch, digit, cursor, y, 0);
            }
        }
        if (value < 10 && padZero) {
            cursor -= sprites.width(FIRST_DIGIT);
            sprites.drawBox(batch, FIRST_DIGIT, cursor, y, 0);
        }
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
