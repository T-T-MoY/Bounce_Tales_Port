package com.bouncetales.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.bouncetales.BounceTalesGame;

/** Lanzador de escritorio (LWJGL3). */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Bounce Tales (port)  -  F11: pantalla completa");
        // Ventana moderna 16:9. El mundo logico sigue siendo 240x320 de alto; el viewport
        // extiende el ancho para mostrar mas escenario sin deformar los sprites.
        if (Boolean.getBoolean("bounce.fullscreen")) {
            // Arranque directo en pantalla completa (util para pruebas de humo).
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            config.setWindowedMode(1280, 720);
        }
        config.setResizable(true);
        // No permitir bajar de 640x360 (2x el lienzo logico de alto).
        config.setWindowSizeLimits(640, 360, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setWindowIcon("images/e.png");
        new Lwjgl3Application(new BounceTalesGame(), config);
    }
}
