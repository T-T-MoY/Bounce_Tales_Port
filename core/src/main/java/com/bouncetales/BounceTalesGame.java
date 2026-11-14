package com.bouncetales;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.bouncetales.asset.LevelCatalog;

/**
 * Punto de entrada comun a todas las plataformas.
 *
 * <p>Por defecto arranca en el menu principal. Propiedades utiles:
 * <ul>
 *   <li>{@code -Dbounce.level=bg}: arranca directamente en ese nivel (modo de pruebas).</li>
 *   <li>{@code -Dbounce.menu=true|false}: fuerza menu o juego directo.</li>
 *   <li>{@code -Dbounce.screenshot=... -Dbounce.frames=N}: captura y sale.</li>
 * </ul>
 */
public final class BounceTalesGame extends Game {

    private final String levelName;
    private final boolean explicitLevel;
    private final boolean menuRequested;

    public BounceTalesGame() {
        this(System.getProperty("bounce.level"));
    }

    public BounceTalesGame(String levelName) {
        this.explicitLevel = levelName != null;
        this.levelName = levelName != null ? levelName : "bf";
        this.menuRequested = Boolean.parseBoolean(System.getProperty("bounce.menu",
                explicitLevel ? "false" : "true"));
    }

    @Override
    public void create() {
        boolean screenshot = System.getProperty("bounce.screenshot") != null
                || System.getProperty("bounce.frames") != null;
        // Las capturas automatizadas siguen siendo directas salvo que se pida el menu.
        boolean menu = menuRequested && !(screenshot && !menuRequestedExplicitly());
        if (menu) {
            openMenu();
        } else {
            openLevel(levelName);
        }
    }

    private boolean menuRequestedExplicitly() {
        return System.getProperty("bounce.menu") != null
                && Boolean.parseBoolean(System.getProperty("bounce.menu"));
    }

    /** Cambia a la pantalla de menu principal. */
    public void openMenu() {
        swapScreen(new MenuScreen(this, levelName));
    }

    /** Carga un nivel concreto y libera la pantalla anterior. */
    public void openLevel(String name) {
        swapScreen(new GameScreen(this, name));
    }

    /** Llamado por {@link GameScreen} al recoger todos los coleccionables. */
    public void levelCompleted(String completedLevel) {
        String next = LevelCatalog.next(completedLevel);
        if (next != null) {
            Gdx.app.log("Bounce", "nivel completado: " + completedLevel
                    + " -> siguiente: " + next);
            openLevel(next);
        } else {
            Gdx.app.log("Bounce", "nivel completado: " + completedLevel
                    + " (ultimo nivel); volviendo al menu");
            openMenu();
        }
    }

    private void swapScreen(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null && previous != next) {
            previous.dispose();
        }
    }
}
