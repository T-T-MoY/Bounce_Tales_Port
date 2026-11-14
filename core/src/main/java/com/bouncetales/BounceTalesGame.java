package com.bouncetales;

import com.badlogic.gdx.Game;

/**
 * Punto de entrada comun a todas las plataformas.
 *
 * <p>El nivel inicial se puede cambiar con {@code -Dbounce.level=bg} (por defecto {@code bf}).
 */
public final class BounceTalesGame extends Game {

    private final String levelName;

    public BounceTalesGame() {
        this(System.getProperty("bounce.level", "bf"));
    }

    public BounceTalesGame(String levelName) {
        this.levelName = levelName;
    }

    @Override
    public void create() {
        setScreen(new GameScreen(levelName));
    }
}
