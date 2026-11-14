package com.bouncetales.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;
import com.bouncetales.asset.Prefab;
import com.bouncetales.asset.SpriteBank;
import com.bouncetales.asset.SpriteDescriptor;

/**
 * Dibuja sprites por id, replicando {@code p.a(int x, int y, int id)} y
 * {@code p.b(int x, int y, int id, int frame)}.
 *
 * <p>Las coordenadas de entrada son pixeles de pantalla J2ME (Y hacia abajo), que es lo que
 * usa todo el codigo original. La conversion a la Y de OpenGL la hace el propio dibujante.
 */
public final class SpriteDrawer implements Disposable {

    private static final int MAX_PREFAB_DEPTH = 8;

    private final SpriteBank bank;
    private final ScreenTransform transform;

    private final IntMap<Texture> textures = new IntMap<>();
    private final LongMap<TextureRegion> regions = new LongMap<>();

    public SpriteDrawer(SpriteBank bank, ScreenTransform transform) {
        this.bank = bank;
        this.transform = transform;
    }

    /**
     * Dibuja un sprite cuyo pivote (offX/offY) queda en {@code (screenX, screenY)}.
     * Acepta tanto descriptores (id &lt; 326) como prefabs.
     */
    public void draw(SpriteBatch batch, int spriteId, float screenX, float screenY) {
        draw(batch, spriteId, screenX, screenY, 0);
    }

    private void draw(SpriteBatch batch, int spriteId, float screenX, float screenY, int depth) {
        if (spriteId < 0 || depth > MAX_PREFAB_DEPTH) {
            return;
        }

        if (spriteId < SpriteBank.FIRST_PREFAB_ID) {
            SpriteDescriptor d = bank.descriptor(spriteId);
            if (d == null) {
                return;
            }
            drawRegion(batch, d.imageId, d.srcX, d.srcY, d.width, d.height,
                    screenX - d.offsetX, screenY - d.offsetY);
            return;
        }

        Prefab p = bank.prefab(spriteId);
        if (p == null) {
            return;
        }

        switch (p.kind) {
            case Prefab.SIMPLE:
                drawRegion(batch, p.imageId, p.srcX, p.srcY, p.width, p.height,
                        screenX - p.offsetX, screenY - p.offsetY);
                break;
            case Prefab.COMPOUND:
                for (int i = 0; i < p.partSprite.length; i++) {
                    draw(batch, p.partSprite[i], screenX + p.partDx[i], screenY + p.partDy[i],
                            depth + 1);
                }
                break;
            case Prefab.ANIMATION:
            default:
                // Sin frame explicito se dibuja el primero (comportamiento de p.b(id, 0)).
                if (p.frames.length > 0) {
                    draw(batch, p.frames[0], screenX, screenY, depth + 1);
                }
                break;
        }
    }

    /**
     * Equivale a {@code p.b(x, y, id, frame)}: si {@code id} es una animacion usa el
     * fotograma pedido; si no, lo dibuja tal cual.
     */
    public void drawFrame(SpriteBatch batch, int spriteId, int frame, float screenX, float screenY) {
        draw(batch, frameSprite(spriteId, frame), screenX, screenY, 0);
    }

    /** Dibuja solo las primeras filas de un sprite de descriptor, manteniendo su pivote. */
    public void drawTopPart(SpriteBatch batch, int spriteId, float screenX, float anchorY,
                            int visibleHeight) {
        SpriteDescriptor d = bank.descriptor(spriteId);
        if (d == null) {
            return;
        }
        int height = Math.max(0, Math.min(visibleHeight, d.height));
        if (height == 0) {
            return;
        }
        drawRegion(batch, d.imageId, d.srcX, d.srcY, d.width, height,
                screenX - d.offsetX, anchorY - d.offsetY);
    }

    /** {@code p.c(int id, int frame)}: id del sprite del fotograma. */
    public int frameSprite(int spriteId, int frame) {
        Prefab p = bank.prefab(spriteId);
        if (p == null || p.kind != Prefab.ANIMATION || p.frames.length == 0) {
            return spriteId;
        }
        int f = frame % p.frames.length;
        if (f < 0) {
            f += p.frames.length;
        }
        return p.frames[f];
    }

    /** {@code p.m66b(int id)}: numero de fotogramas de una animacion. */
    public int frameCount(int spriteId) {
        Prefab p = bank.prefab(spriteId);
        return (p != null && p.kind == Prefab.ANIMATION && p.frames.length > 0)
                ? p.frames.length : 1;
    }

    /** Tamano en pixeles de pantalla del sprite {@code id} (0 si no existe). */
    public int width(int spriteId) {
        SpriteDescriptor d = bank.descriptor(spriteId);
        return d != null ? d.width : 0;
    }

    public int height(int spriteId) {
        SpriteDescriptor d = bank.descriptor(spriteId);
        return d != null ? d.height : 0;
    }

    private void drawRegion(SpriteBatch batch, int imageId, int srcX, int srcY, int w, int h,
                            float screenLeft, float screenTop) {
        if (w <= 0 || h <= 0) {
            return;
        }
        TextureRegion region = region(imageId, srcX, srcY, w, h);
        if (region == null) {
            return;
        }
        batch.draw(region, screenLeft, transform.glTop(screenTop, h), w, h);
    }

    private TextureRegion region(int imageId, int srcX, int srcY, int w, int h) {
        long key = ((long) imageId << 40) | ((long) (srcX & 0xFFF) << 28)
                | ((long) (srcY & 0xFFF) << 16) | ((w & 0xFF) << 8) | (h & 0xFF);
        TextureRegion cached = regions.get(key);
        if (cached != null) {
            return cached;
        }
        Texture texture = texture(imageId);
        if (texture == null) {
            return null;
        }
        TextureRegion region = new TextureRegion(texture, srcX, srcY, w, h);
        regions.put(key, region);
        return region;
    }

    private Texture texture(int imageId) {
        Texture cached = textures.get(imageId);
        if (cached != null) {
            return cached;
        }
        String name = bank.imageName(imageId);
        if (name == null) {
            return null;
        }
        Texture texture = new Texture(Gdx.files.internal("images/" + name));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.put(imageId, texture);
        return texture;
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) {
            t.dispose();
        }
        textures.clear();
        regions.clear();
    }
}
