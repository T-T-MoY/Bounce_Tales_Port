package com.bouncetales.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;

/**
 * Fuente de la interfaz (menu y herramientas) generada con FreeType desde
 * {@code assets/fonts/Roboto-Regular.ttf}.
 *
 * <p>Se rasteriza a {@value #GENERATED_SIZE} px y se dibuja con {@link #BASE_SCALE}
 * (~0,31), de modo que ocupa el mismo espacio logico que el {@code BitmapFont()} por
 * defecto (arial-15) pero conserva resolucion de sobra cuando el viewport amplia el
 * lienzo. Los filtros {@link Texture.TextureFilter#Linear} evitan el aspecto pixelado.
 *
 * <p>La pantalla es duena de la fuente devuelta: {@link UiFont#dispose()} libera tambien
 * la textura del atlas. No se comparte entre pantallas porque {@code BounceTalesGame}
 * libera la pantalla anterior al cambiar.
 */
public final class FontManager {

    /** Ruta dentro de {@code assets/}. */
    public static final String FONT_FILE = "fonts/Roboto-Regular.ttf";

    /** Tamano con el que se rasteriza la fuente (px de la textura). */
    public static final int GENERATED_SIZE = 48;

    /**
     * Factor que convierte las escalas pensadas para el {@code BitmapFont()} por defecto
     * (arial-15) a esta fuente de 48 px: {@code 15 / 48}.
     */
    public static final float BASE_SCALE = 15f / GENERATED_SIZE;

    /**
     * ASCII imprimible mas los caracteres en espanol que pueden aparecer en la interfaz.
     * Se escriben con escapes para no depender de la codificacion del fichero fuente.
     */
    private static final String CHARS = FreeTypeFontGenerator.DEFAULT_CHARS
            + "\u00e1\u00e9\u00ed\u00f3\u00fa"      // a e i o u con tilde
            + "\u00c1\u00c9\u00cd\u00d3\u00da"      // mayusculas con tilde
            + "\u00f1\u00d1\u00fc\u00dc"            // enie y dieresis
            + "\u00bf\u00a1\u00ba\u00aa";           // signos de apertura y ordinales

    private FontManager() {
    }

    /**
     * Genera la fuente de la interfaz. Si el TTF no esta o FreeType falla, cae a la fuente
     * por defecto de libGDX ({@code baseScale = 1}) para que el juego siga arrancando.
     */
    public static UiFont createUiFont() {
        if (!Gdx.files.internal(FONT_FILE).exists()) {
            Gdx.app.error("Font", "falta assets/" + FONT_FILE
                    + "; se usa la fuente por defecto de libGDX");
            return new UiFont(new BitmapFont(), 1f);
        }
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(Gdx.files.internal(FONT_FILE));
            FreeTypeFontGenerator.FreeTypeFontParameter parameter =
                    new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = GENERATED_SIZE;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.characters = CHARS;
            parameter.color = Color.WHITE;

            BitmapFont font = generator.generateFont(parameter);
            // Sin snapping a pixeles enteros: el viewport ya amplia el lienzo logico.
            font.setUseIntegerPositions(false);

            Gdx.app.log("Font", FONT_FILE + " @" + GENERATED_SIZE + " px, escala base "
                    + BASE_SCALE + ", atlas " + font.getRegion().getTexture().getWidth()
                    + "x" + font.getRegion().getTexture().getHeight());
            return new UiFont(font, BASE_SCALE);
        } catch (RuntimeException e) {
            Gdx.app.error("Font", "no se pudo generar " + FONT_FILE
                    + "; se usa la fuente por defecto de libGDX", e);
            return new UiFont(new BitmapFont(), 1f);
        } finally {
            if (generator != null) {
                generator.dispose();
            }
        }
    }

    /** Fuente generada y el factor de escala que esperan las pantallas. */
    public static final class UiFont implements Disposable {

        public final BitmapFont font;
        /** Multiplicador para las escalas antiguas (pensadas para arial-15). */
        public final float baseScale;

        UiFont(BitmapFont font, float baseScale) {
            this.font = font;
            this.baseScale = baseScale;
        }

        @Override
        public void dispose() {
            font.dispose();
        }
    }
}
