package com.bouncetales.world;

/** Poligono del nivel (tag 4, clase {@code q}). */
public final class PolygonShape {

    /** Pares (x, y) en 16.16 convertidos a unidades de mundo; el ultimo repite el primero. */
    public final float[] vertices;
    /** Indices de triangulos (longitud multiple de 3). */
    public final short[] triangles;
    /** Color ARGB tal cual viene en el fichero (0xFFRRGGBB). */
    public final int color;
    public final short triggerId;

    public PolygonShape(float[] vertices, short[] triangles, int color, short triggerId) {
        this.vertices = vertices;
        this.triangles = triangles;
        this.color = color;
        this.triggerId = triggerId;
    }
}
