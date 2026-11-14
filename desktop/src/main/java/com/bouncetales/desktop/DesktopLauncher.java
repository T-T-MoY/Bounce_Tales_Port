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
        config.setTitle("Bounce Tales (port)");
        // 240x320 * 2 = 480x640, el mismo aspecto que el original.
        config.setWindowedMode(480, 640);
        config.setResizable(true);
        // No permitir bajar de la resolucion nativa (1 pixel de PNG = 1 pixel de ventana).
        config.setWindowSizeLimits(240, 320, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setWindowIcon("images/e.png");
        new Lwjgl3Application(new BounceTalesGame(), config);
    }
}
