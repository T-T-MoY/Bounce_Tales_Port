package com.bouncetales.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.bouncetales.asset.SpriteBank;

/**
 * Ajustes globales de calidad grafica.
 *
 * <p>Los sprites originales viven en {@code assets/images/} y se dibujan 1:1 en el lienzo
 * logico de 240x320. Los "HD" son los mismos atlas ampliados 8x (mismos nombres de fichero,
 * mismas posiciones dentro del atlas) en {@code assets/images_hd/}; el juego solo cambia la
 * region de textura que muestrea y sigue dibujando al mismo tamano logico, asi que la fisica,
 * las colisiones y el encuadre no cambian.
 *
 * <p>La eleccion se guarda en {@link Preferences} para sobrevivir al cambio de nivel y al
 * reinicio. {@code -Dbounce.hd=true|false} fuerza el modo (util para pruebas desatendidas).
 */
public final class GraphicsSettings {

    /** Factor de ampliacion de los atlas HD. */
    public static final int HD_SCALE = 8;

    /** Carpeta de los sprites originales, relativa a {@code assets/}. */
    public static final String ORIGINAL_FOLDER = "images";

    /** Carpeta de los sprites HD, relativa a {@code assets/}. */
    public static final String HD_FOLDER = "images_hd";

    private static final String PREFS_NAME = "bounce-tales";
    private static final String KEY_HD = "hdSprites";
    private static final String SPRITE_BANK = "sprites/sprites.json";

    /** {@code null} mientras no se haya leido de {@link Preferences}. */
    private static Boolean hd;

    /** Cache de {@link #isHdAvailable()}: {@code null} mientras no se haya comprobado. */
    private static Boolean hdAvailable;

    private GraphicsSettings() {
    }

    /** {@code true} si el usuario eligio los sprites HD. */
    public static boolean isHd() {
        if (hd == null) {
            String forced = System.getProperty("bounce.hd");
            if (forced != null) {
                hd = Boolean.parseBoolean(forced);
            } else if (Gdx.app != null) {
                hd = Gdx.app.getPreferences(PREFS_NAME).getBoolean(KEY_HD, false);
            } else {
                hd = false;
            }
        }
        return hd;
    }

    /** Cambia el modo y lo guarda en {@link Preferences}. */
    public static void setHd(boolean value) {
        hd = value;
        if (Gdx.app != null) {
            Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
            prefs.putBoolean(KEY_HD, value);
            prefs.flush();
            Gdx.app.log("Sprites", value
                    ? "modo HD x" + HD_SCALE + " (" + HD_FOLDER + ")"
                    : "modo originales (" + ORIGINAL_FOLDER + ")");
        }
    }

    /** Alterna el modo y devuelve el valor resultante. */
    public static boolean toggleHd() {
        setHd(!isHd());
        return isHd();
    }

    /** Factor de escala que debe usar {@link SpriteBank}/{@link SpriteDrawer}. */
    public static int scale() {
        return isHd() ? HD_SCALE : 1;
    }

    /** Carpeta de imagenes que debe usar {@link SpriteBank}/{@link SpriteDrawer}. */
    public static String imageFolder() {
        return isHd() ? HD_FOLDER : ORIGINAL_FOLDER;
    }

    /**
     * Comprueba que existe la carpeta HD y que estan todos los PNG que referencia
     * {@code sprites.json}. El resultado se cachea: si se anaden imagenes con el juego
     * abierto hay que reiniciar.
     */
    public static boolean isHdAvailable() {
        if (hdAvailable == null) {
            hdAvailable = false;
            if (Gdx.files != null
                    && Gdx.files.internal(HD_FOLDER).exists()
                    && Gdx.files.internal(SPRITE_BANK).exists()) {
                SpriteBank bank = SpriteBank.parse(
                        Gdx.files.internal(SPRITE_BANK).readString("UTF-8"));
                hdAvailable = bank.hasAllImages(HD_FOLDER);
            }
        }
        return hdAvailable;
    }
}
