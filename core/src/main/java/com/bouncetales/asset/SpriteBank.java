package com.bouncetales.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Banco de sprites del juego, cargado desde {@code sprites.json} (lo genera
 * {@code tools/export_sprites.py}).
 *
 * <p>Equivale a las tablas estaticas del original:
 * <ul>
 *   <li>{@code f264a} (326 descriptores de 7 bytes) -&gt; {@link #descriptor(int)}</li>
 *   <li>{@code f262a} (214 prefabs) -&gt; {@link #prefab(int)}</li>
 *   <li>{@code f263a} (46 imagenes) -&gt; {@link #imageName(int)}</li>
 * </ul>
 *
 * <p>Los atlas pueden venir ampliados ({@code images_hd/}, factor 8): el banco guarda el
 * factor y la carpeta para que {@link com.bouncetales.render.SpriteDrawer} muestree la
 * region correcta sin tocar los tamanos logicos de los sprites.
 */
public final class SpriteBank {

    public static final int FIRST_PREFAB_ID = 326;

    private final IntMap<SpriteDescriptor> descriptors = new IntMap<>();
    private final IntMap<Prefab> prefabs = new IntMap<>();
    private final IntMap<String> imageNames = new IntMap<>();

    /** Factor de ampliacion de los atlas (1 = originales, 8 = HD). */
    private final int imageScale;
    /** Carpeta de imagenes relativa a {@code assets/} (por ejemplo {@code images_hd}). */
    private final String imageFolder;

    private SpriteBank(int imageScale, String imageFolder) {
        this.imageScale = Math.max(1, imageScale);
        this.imageFolder = imageFolder == null || imageFolder.isEmpty()
                ? "images" : imageFolder;
    }

    public static SpriteBank parse(String json) {
        return parse(json, 1, "images");
    }

    /**
     * @param imageScale factor de ampliacion de los atlas (1 = originales, 8 = HD)
     * @param imageFolder carpeta relativa a {@code assets/} donde estan los PNG
     */
    public static SpriteBank parse(String json, int imageScale, String imageFolder) {
        SpriteBank bank = new SpriteBank(imageScale, imageFolder);
        JsonValue root = new JsonReader().parse(json);

        JsonValue images = root.get("images");
        if (images != null) {
            for (JsonValue v = images.child; v != null; v = v.next) {
                bank.imageNames.put(Integer.parseInt(v.name), v.asString());
            }
        }

        JsonValue descs = root.get("descriptors");
        if (descs != null) {
            for (JsonValue v = descs.child; v != null; v = v.next) {
                int id = Integer.parseInt(v.name);
                bank.descriptors.put(id, new SpriteDescriptor(
                        id,
                        v.getInt("w"),
                        v.getInt("h"),
                        v.getInt("ox"),
                        v.getInt("oy"),
                        v.getInt("src_x"),
                        v.getInt("src_y"),
                        v.getInt("image")));
            }
        }

        JsonValue prefs = root.get("prefabs");
        if (prefs != null) {
            for (JsonValue v = prefs.child; v != null; v = v.next) {
                bank.prefabs.put(Integer.parseInt(v.name), parsePrefab(v));
            }
        }
        return bank;
    }

    private static Prefab parsePrefab(JsonValue v) {
        int id = Integer.parseInt(v.name);
        int kind = v.getInt("kind");
        Prefab p = new Prefab(id, kind, v.getInt("wide", 1));

        switch (kind) {
            case Prefab.SIMPLE: {
                JsonValue f = v.get("frame");
                p.width = f.getInt("w");
                p.height = f.getInt("h");
                p.offsetX = f.getInt("ox");
                p.offsetY = f.getInt("oy");
                p.srcX = f.getInt("src_x");
                p.srcY = f.getInt("src_y");
                p.imageId = f.getInt("image");
                break;
            }
            case Prefab.COMPOUND: {
                JsonValue box = v.get("box");
                p.boxWidth = box.getInt("w");
                p.boxHeight = box.getInt("h");
                p.boxOffsetX = box.getInt("ox");
                p.boxOffsetY = box.getInt("oy");

                JsonValue parts = v.get("parts");
                int n = parts.size;
                p.partDx = new int[n];
                p.partDy = new int[n];
                p.partSprite = new int[n];
                for (int i = 0; i < n; i++) {
                    JsonValue part = parts.get(i);
                    p.partDx[i] = part.getInt("dx");
                    p.partDy[i] = part.getInt("dy");
                    p.partSprite[i] = part.getInt("sprite");
                }
                break;
            }
            case Prefab.ANIMATION:
            default: {
                p.kind = Prefab.ANIMATION;
                JsonValue frames = v.get("frames");
                if (frames != null) {
                    int n = frames.size;
                    p.frames = new int[n];
                    for (int i = 0; i < n; i++) {
                        p.frames[i] = frames.get(i).asInt();
                    }
                }
                break;
            }
        }
        return p;
    }

    public SpriteDescriptor descriptor(int id) {
        return descriptors.get(id);
    }

    public Prefab prefab(int id) {
        return prefabs.get(id);
    }

    public String imageName(int imageId) {
        return imageNames.get(imageId);
    }

    /** Ruta completa ({@code carpeta/fichero.png}) de la imagen {@code imageId}. */
    public String imagePath(int imageId) {
        String name = imageNames.get(imageId);
        return name == null ? null : imageFolder + "/" + name;
    }

    /** Factor de ampliacion de los atlas (1 = originales, 8 = HD). */
    public int imageScale() {
        return imageScale;
    }

    /** Carpeta de imagenes relativa a {@code assets/}. */
    public String imageFolder() {
        return imageFolder;
    }

    /** {@code true} si existen todos los PNG del banco en {@link #imageFolder()}. */
    public boolean hasAllImages() {
        return hasAllImages(imageFolder);
    }

    /**
     * Comprueba que existen todos los PNG del banco en {@code folder}. No carga ninguna
     * textura, solo consulta el sistema de ficheros.
     */
    public boolean hasAllImages(String folder) {
        if (imageNames.size == 0 || Gdx.files == null) {
            return false;
        }
        for (String name : imageNames.values()) {
            if (!Gdx.files.internal(folder + "/" + name).exists()) {
                return false;
            }
        }
        return true;
    }

    public int descriptorCount() {
        return descriptors.size;
    }

    public int prefabCount() {
        return prefabs.size;
    }

    public int imageCount() {
        return imageNames.size;
    }
}
