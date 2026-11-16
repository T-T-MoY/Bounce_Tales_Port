package com.bouncetales.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
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
 *
 * <p>Con sprites HD ({@code images_hd/}, factor {@link GraphicsSettings#HD_SCALE}) se
 * muestrea una region {@code scale} veces mayor del atlas, pero se sigue dibujando al mismo
 * tamano logico: el encuadre, la fisica y las colisiones no cambian. Si falta la carpeta HD
 * o algun PNG, se cae automaticamente a los originales.
 */
public final class SpriteDrawer implements Disposable {

    private static final int MAX_PREFAB_DEPTH = 8;

    private final SpriteBank bank;
    private final ScreenTransform transform;

    /** Factor de ampliacion del atlas que se esta usando (1 = originales). */
    private final int imageScale;
    /** Carpeta de imagenes que se esta usando ({@code images} o {@code images_hd}). */
    private final String imageFolder;

    private final IntMap<Texture> textures = new IntMap<>();
    private final LongMap<TextureRegion> regions = new LongMap<>();
    /** Evita repetir el aviso de fallback para la misma imagen. */
    private final IntSet warnedImages = new IntSet();

    public SpriteDrawer(SpriteBank bank, ScreenTransform transform) {
        this(bank, transform, bank.imageScale(), bank.imageFolder());
    }

    /**
     * @param imageScale factor de ampliacion del atlas (1 = originales, 8 = HD)
     * @param imageFolder carpeta relativa a {@code assets/} con los PNG
     */
    public SpriteDrawer(SpriteBank bank, ScreenTransform transform,
                        int imageScale, String imageFolder) {
        this.bank = bank;
        this.transform = transform;

        int scale = Math.max(1, imageScale);
        String folder = imageFolder == null || imageFolder.isEmpty()
                ? GraphicsSettings.ORIGINAL_FOLDER : imageFolder;
        if (scale > 1 && !bank.hasAllImages(folder)) {
            log("faltan imagenes en assets/" + folder
                    + "; se usaran los sprites originales");
            scale = 1;
            folder = GraphicsSettings.ORIGINAL_FOLDER;
        }
        this.imageScale = scale;
        this.imageFolder = folder;
        if (Gdx.app != null) {
            Gdx.app.log("Sprites", scale > 1
                    ? "HD x" + scale + " desde assets/" + folder
                    : "originales desde assets/" + folder);
        }
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

    /**
     * Equivale a {@code p.a(x, y, id, flags)}: coloca la <b>caja</b> del sprite en
     * {@code (x, y)} (sin aplicar el pivote) y la desplaza segun los flags de alineacion:
     * {@code 1} = centrar en X, {@code 8} = alinear a la derecha, {@code 2} = centrar en Y,
     * {@code 32} = alinear abajo.
     */
    public void drawBox(SpriteBatch batch, int spriteId, float x, float y, int flags) {
        if (spriteId < 0) {
            return;
        }
        if (spriteId >= SpriteBank.FIRST_PREFAB_ID) {
            draw(batch, spriteId, x, y);
            return;
        }
        SpriteDescriptor d = bank.descriptor(spriteId);
        if (d == null) {
            return;
        }
        float left = x;
        float top = y;
        if ((flags & 8) != 0) {
            left -= d.width;
        } else if ((flags & 1) != 0) {
            left -= d.width * 0.5f;
        }
        if ((flags & 32) != 0) {
            top -= d.height;
        } else if ((flags & 2) != 0) {
            top -= d.height * 0.5f;
        }
        drawRegion(batch, d.imageId, d.srcX, d.srcY, d.width, d.height, left, top);
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
        Texture texture = texture(imageId);
        if (texture == null) {
            return null;
        }
        int scale = imageScale;
        if (scale > 1 && (srcX * scale + w * scale > texture.getWidth()
                || srcY * scale + h * scale > texture.getHeight())) {
            // El atlas no esta realmente ampliado (o le faltan trozos): ese sprite se
            // muestrea 1:1 en vez de romper el dibujo.
            warnOnce(imageId, "la imagen " + imageId + " no esta a x" + imageScale
                    + "; se dibuja a escala original");
            scale = 1;
        }
        long key = regionKey(imageId, srcX, srcY, w, h, scale);
        TextureRegion cached = regions.get(key);
        if (cached != null) {
            return cached;
        }
        TextureRegion region = new TextureRegion(texture,
                srcX * scale, srcY * scale, w * scale, h * scale);
        regions.put(key, region);
        return region;
    }

    /**
     * Clave de cache de regiones: 6 bits de imagen, 13 de {@code srcX/srcY}, 11 de
     * ancho/alto y un bit para la escala efectiva (1x u 8x).
     */
    private static long regionKey(int imageId, int srcX, int srcY, int w, int h, int scale) {
        return ((long) (imageId & 0x3F) << 49)
                | ((long) (srcX & 0x1FFF) << 36)
                | ((long) (srcY & 0x1FFF) << 23)
                | ((long) (w & 0x7FF) << 12)
                | ((long) (h & 0x7FF) << 1)
                | (scale > 1 ? 1L : 0L);
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
        String path = imageFolder + "/" + name;
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists() && imageScale > 1) {
            warnOnce(imageId, "falta " + path + "; se usa la version original");
            file = Gdx.files.internal(GraphicsSettings.ORIGINAL_FOLDER + "/" + name);
        }
        if (!file.exists()) {
            log("no existe la imagen " + path);
            return null;
        }
        Texture texture = imageScale > 1
                ? new Texture(file, true)
                : new Texture(file);
        if (imageScale > 1) {
            // HD: el atlas se reduce (8x -> tamano logico) al dibujar, asi que conviene
            // filtrar linealmente y usar mipmaps para evitar aliasing.
            texture.setFilter(Texture.TextureFilter.MipMapLinearNearest,
                    Texture.TextureFilter.Linear);
        } else {
            texture.setFilter(Texture.TextureFilter.Nearest,
                    Texture.TextureFilter.Nearest);
        }
        textures.put(imageId, texture);
        return texture;
    }

    /**
     * Textura completa del atlas {@code imageId} (se carga si hace falta). Pensado para
     * herramientas de desarrollo como el visor de sprites; el juego no la usa.
     */
    public Texture atlasTexture(int imageId) {
        return texture(imageId);
    }

    /** Factor de ampliacion efectivo (1 si se cayo a los originales). */
    public int imageScale() {
        return imageScale;
    }

    /** Carpeta de imagenes efectiva ({@code images} o {@code images_hd}). */
    public String imageFolder() {
        return imageFolder;
    }

    private void warnOnce(int imageId, String message) {
        if (warnedImages.add(imageId)) {
            log(message);
        }
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.error("Sprites", message);
        }
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
