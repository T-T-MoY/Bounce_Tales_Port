package com.bouncetales.world;

/**
 * Lista de sprites decorativos de una entidad (tag 9, clase {@code s}).
 * Las posiciones son coordenadas locales en unidades de mundo (no 16.16).
 */
public final class DecorationList {

    public final int[] spriteIds;
    public final float[] x;
    public final float[] y;

    public DecorationList(int[] spriteIds, float[] x, float[] y) {
        this.spriteIds = spriteIds;
        this.x = x;
        this.y = y;
    }

    public int size() {
        return spriteIds.length;
    }
}
