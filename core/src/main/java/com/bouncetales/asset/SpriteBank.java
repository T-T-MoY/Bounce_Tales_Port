package com.bouncetales.asset;

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
 */
public final class SpriteBank {

    public static final int FIRST_PREFAB_ID = 326;

    private final IntMap<SpriteDescriptor> descriptors = new IntMap<>();
    private final IntMap<Prefab> prefabs = new IntMap<>();
    private final IntMap<String> imageNames = new IntMap<>();

    private SpriteBank() {
    }

    public static SpriteBank parse(String json) {
        SpriteBank bank = new SpriteBank();
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
