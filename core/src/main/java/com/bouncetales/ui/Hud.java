package com.bouncetales.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/** HUD sencillo para el progreso de coleccionables y el estado de victoria. */
public final class Hud implements Disposable {

    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final int width;
    private final int height;

    public Hud(int width, int height) {
        this.width = width;
        this.height = height;
        font.getData().setScale(0.5f);
    }

    public void render(SpriteBatch batch, int collected, int total, boolean victory) {
        batch.begin();
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, collected + "/" + total, 8f, height - 10f);
        if (victory) {
            layout.setText(font, "NIVEL COMPLETADO!");
            font.draw(batch, layout, (width - layout.width) * 0.5f,
                    (height + layout.height) * 0.5f);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
