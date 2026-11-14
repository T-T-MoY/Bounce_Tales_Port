package com.bouncetales.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Utilidad de captura automatica para las pruebas de humo.
 *
 * <p>Lee {@code -Dbounce.screenshot=...} y {@code -Dbounce.frames=N}. Si solo se indica la
 * ruta se captura en el frame 20. Cualquier pantalla puede consultar {@link #frameLimit()}
 * y llamar a {@link #capture(String)} al alcanzarlo para guardar el PNG y salir.
 */
public final class ScreenshotCapture {

    private static final int DEFAULT_FRAME = 20;

    private ScreenshotCapture() {
    }

    /** Ruta de salida o {@code null} si no se pidio captura. */
    public static String path() {
        return System.getProperty("bounce.screenshot");
    }

    /** Frame en el que capturar, o -1 si no hay captura configurada. */
    public static int frameLimit() {
        String frames = System.getProperty("bounce.frames");
        if (frames != null) {
            return Math.max(1, Integer.parseInt(frames));
        }
        return path() != null ? DEFAULT_FRAME : -1;
    }

    /** Escribe el framebuffer actual como PNG opaco (alfa 255). */
    public static void capture(String path) {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        // Las pantallas son opacas: forzamos alfa 255 para que el PNG no se vea translucido.
        for (int i = 3; i < pixels.length; i += 4) {
            pixels[i] = (byte) 0xFF;
        }
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.getPixels().clear();
        pixmap.getPixels().put(pixels);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(path), pixmap);
            Gdx.app.log("Capture", "captura escrita: " + path);
        } finally {
            pixmap.dispose();
        }
    }
}
