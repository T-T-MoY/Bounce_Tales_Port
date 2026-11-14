package com.bouncetales.physics;

/**
 * Contacto entre la bola y una superficie.
 *
 * <p>El punto de contacto no está sobre la superficie del polígono, sino sobre el borde
 * <b>desplazado por el radio</b> (frontera de Minkowski). Es decir: es el punto donde debe
 * quedar el <b>centro</b> de la bola. Así lo hace el original en {@code c.o()}: coloca
 * {@code c.c/c.f} en ese punto.
 */
public final class Contact {

    public float pointX;
    public float pointY;

    /** Normal de la superficie, unitaria y apuntando hacia la bola. */
    public float normalX;
    public float normalY;

    /** Distancia recorrida hasta el contacto (para elegir el más cercano). */
    public float distance;

    /** {@code true} si la bola ya estaba penetrando antes de moverse (contacto profundo). */
    public boolean deep;

    public void set(float pointX, float pointY, float normalX, float normalY,
                    float distance, boolean deep) {
        this.pointX = pointX;
        this.pointY = pointY;
        this.normalX = normalX;
        this.normalY = normalY;
        this.distance = distance;
        this.deep = deep;
    }
}
