package com.bouncetales.asset;

/**
 * Un descriptor de sprite: los 7 bytes que el original guarda en {@code f264a} y que
 * {@code p.a(x, y, id)} usa para dibujar.
 *
 * <pre>
 *   u8 w, u8 h
 *   s8 offX, s8 offY     pivote: el sprite se dibuja en (x - offX, y - offY)
 *   u8 srcX, u8 srcY     esquina en el PNG
 *   u8 imageIndex        id de imagen GLOBAL (firstImageId + indice en refs[])
 * </pre>
 *
 * Los ids de sprite {@code 0..325} son descriptores simples; a partir de 326 son prefabs
 * ({@link Prefab}).
 */
public final class SpriteDescriptor {

    public final int id;
    public final int width;
    public final int height;
    public final int offsetX;
    public final int offsetY;
    public final int srcX;
    public final int srcY;
    public final int imageId;

    public SpriteDescriptor(int id, int width, int height, int offsetX, int offsetY,
                            int srcX, int srcY, int imageId) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.srcX = srcX;
        this.srcY = srcY;
        this.imageId = imageId;
    }

    @Override
    public String toString() {
        return "sprite " + id + " " + width + "x" + height
                + " off=(" + offsetX + "," + offsetY + ")"
                + " src=(" + srcX + "," + srcY + ") img=" + imageId;
    }
}
