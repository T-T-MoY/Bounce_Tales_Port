package com.bouncetales.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.bouncetales.render.SpriteDrawer;

/**
 * HUD del nivel: indicador de nivel, icono de coleccionable (sprite 208, el mismo que se
 * recoge en el nivel) y contador, ademas del mensaje de nivel completado.
 */
public final class Hud implements Disposable {

    /** Sprite del coleccionable en el nivel ({@code a.java}). */
    private static final int COLLECTIBLE_ICON = 208;
    private static final float ICON_CENTER_X = 218f;
    private static final float ICON_CENTER_Y = 12f;
    private static final float TEXT_BASELINE = 9f;
    private static final float SHADOW = 1f;

    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final int width;
    private final int height;

    public Hud(int width, int height) {
        this.width = width;
        this.height = height;
        font.getData().setScale(0.5f);
    }

    public void render(SpriteBatch batch, SpriteDrawer sprites, String levelLabel,
                       int collected, int total, boolean victory) {
        batch.begin();

        // Nivel actual arriba a la izquierda.
        drawShadowed(batch, levelLabel, 6f, height - TEXT_BASELINE);

        // Contador alineado a la derecha, con el icono del coleccionable al lado.
        String count = collected + "/" + total;
        layout.setText(font, count);
        float countX = ICON_CENTER_X - 11f - 4f - layout.width;
        drawShadowed(batch, layout, countX, height - TEXT_BASELINE);
        if (sprites != null) {
            sprites.draw(batch, COLLECTIBLE_ICON, ICON_CENTER_X, ICON_CENTER_Y);
        }

        if (victory) {
            font.getData().setScale(0.65f);
            font.setColor(1f, 0.86f, 0.2f, 1f);
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

    private void drawShadowed(SpriteBatch batch, String text, float x, float y) {
        layout.setText(font, text);
        drawShadowed(batch, layout, x, y);
    }

    private void drawShadowed(SpriteBatch batch, GlyphLayout text, float x, float y) {
        font.setColor(0f, 0f, 0f, 0.7f);
        font.draw(batch, text, x + SHADOW, y - SHADOW);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, x, y);
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
