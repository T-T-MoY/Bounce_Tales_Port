package com.bouncetales.asset;

/**
 * Sprite "prefabricado" (id &gt;= 326).
 *
 * <p>Formato original ({@code p.a(int x, int y, int id)}):
 * <pre>
 *   flags = primerByte
 *   kind  = flags &amp; 3        0 = simple, 1 = compuesto, 2/3 = animacion
 *   wide  = (flags &amp; 4) ? 2 : 1      valores s16 en lugar de s8
 *
 *   simple    : 6 valores (w,h,offX,offY,srcX,srcY) + u16 imageIndex
 *   compuesto : 4 valores (w,h,offX,offY) + u16 n + n * (dx,dy,spriteId)
 *   animacion : u8 0, u8 n, n * u16 spriteId
 *               (p.c(id, frame) lee el u16 en offset + 3 + frame * 2)
 * </pre>
 *
 * Un compuesto dibuja cada hijo recursivamente en {@code (x+dx, y+dy)}; un hijo puede ser
 * otro prefab.
 */
public final class Prefab {

    public static final int SIMPLE = 0;
    public static final int COMPOUND = 1;
    public static final int ANIMATION = 2;

    public int id;
    public int kind;
    public int wide;

    /** SIMPLE: la geometria y la imagen. */
    public int width;
    public int height;
    public int offsetX;
    public int offsetY;
    public int srcX;
    public int srcY;
    public int imageId;

    /** COMPOUND: caja envolvente y lista de partes. */
    public int boxWidth;
    public int boxHeight;
    public int boxOffsetX;
    public int boxOffsetY;
    public int[] partDx = new int[0];
    public int[] partDy = new int[0];
    public int[] partSprite = new int[0];

    /** ANIMATION: ids de los fotogramas. */
    public int[] frames = new int[0];

    public Prefab(int id, int kind, int wide) {
        this.id = id;
        this.kind = kind;
        this.wide = wide;
    }
}
