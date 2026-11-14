package com.bouncetales.asset;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orden real de los niveles y su musica, tal y como los encadena el original.
 *
 * <p>El indice {@code index/a} guarda los niveles en el orden de {@link ResourceIndex#LEVEL_OBJECTS}
 * ({@code n.v} en el original): {@code bf, bg, bh, bi, br, bj, ...}. Este orden es el que usa el
 * menu y el avance automatico al completar un nivel.
 *
 * <p>La musica sigue {@code n.d()}: tres grupos de cinco niveles, con tema propio en el cuarto y
 * quinto nivel de cada grupo. Los ids 28..35 son los cargadores de audio del indice:
 * <pre>
 *   28 aw   29 ax   30 ay   31 az   32 ba   33 bb   34 bc   35 bd
 * </pre>
 * Los ocho ficheros se convierten a {@code assets/sounds/<id>.ogg}.
 */
public final class LevelCatalog {

    private static final String RESOURCE_INDEX = "index/a";

    /** Fallback si falta {@code index/a} (mismo orden que {@code n.v}). */
    private static final String[] FALLBACK = {
            "bf", "bg", "bh", "bi", "br", "bj", "bk", "bl", "bm", "bs",
            "bn", "bo", "bp", "bq", "bt", "bu"
    };

    private static List<String> cached;

    private LevelCatalog() {
    }

    /** Niveles jugables en orden de progresion. */
    public static synchronized List<String> levels() {
        if (cached != null) {
            return cached;
        }
        List<String> result = new ArrayList<>();
        try {
            if (Gdx.files != null && Gdx.files.internal(RESOURCE_INDEX).exists()) {
                ResourceIndex index = ResourceIndex.parse(
                        Gdx.files.internal(RESOURCE_INDEX).readBytes());
                for (int i = 0; i < ResourceIndex.LEVEL_OBJECTS.length; i++) {
                    String name = index.levelFileName(i);
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    if (Gdx.files.internal("levels/" + name + ".json").exists()) {
                        result.add(name);
                    }
                }
            }
        } catch (RuntimeException e) {
            log("No se pudo leer " + RESOURCE_INDEX + "; se usa la lista por defecto: " + e);
        }
        if (result.isEmpty()) {
            Collections.addAll(result, FALLBACK);
        }
        cached = Collections.unmodifiableList(result);
        return cached;
    }

    /** Indice 0-based del nivel, o {@code -1} si no existe. */
    public static int indexOf(String levelName) {
        if (levelName == null) {
            return -1;
        }
        List<String> levels = levels();
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).equalsIgnoreCase(levelName)) {
                return i;
            }
        }
        return -1;
    }

    /** Siguiente nivel de la progresion, o {@code null} en el ultimo. */
    public static String next(String levelName) {
        int index = indexOf(levelName);
        if (index < 0 || index + 1 >= levels().size()) {
            return null;
        }
        return levels().get(index + 1);
    }

    /** Numero de nivel mostrado al jugador (1-based, -1 si se desconoce). */
    public static int number(String levelName) {
        int index = indexOf(levelName);
        return index < 0 ? -1 : index + 1;
    }

    /**
     * Musica de fondo del original ({@code n.d()}). Devuelve el id de cargador 28..35, que
     * corresponde a {@code sounds/<id>.ogg}.
     */
    public static int musicId(int levelIndex) {
        if (levelIndex < 0) {
            return 29;
        }
        int group = levelIndex <= 4 ? 0 : (levelIndex <= 9 ? 1 : 2);
        int id = group == 0 ? 29 : (group == 1 ? 30 : 31);
        int withinGroup = levelIndex % 5;
        if (withinGroup == 3) {
            id = 32;
        } else if (withinGroup == 4) {
            id = 35;
        }
        return id;
    }

    /** Musica del menu (cargador 28, {@code aw.mid}). */
    public static int menuMusicId() {
        return 28;
    }

    /** Jingle de nivel completado (cargador 33, {@code bb.mid}). */
    public static int levelCompleteJingleId() {
        return 33;
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("LevelCatalog", message);
        } else {
            System.out.println("LevelCatalog: " + message);
        }
    }
}
