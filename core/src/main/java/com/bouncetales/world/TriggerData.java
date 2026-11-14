package com.bouncetales.world;

/**
 * Payload del tag 6 ({@code g.a(byte[], int)}): caja de activacion + programa bytecode.
 *
 * <p>El JSON guarda la caja como {@code {min_x, max_y, max_x, min_y}} porque asi aparece en
 * el fichero; aqui se normaliza a los cuatro limites.
 */
public final class TriggerData {

    /** Caja local (pixeles). */
    public float minX;
    public float minY;
    public float maxX;
    public float maxY;

    /** Estado inicial: 0 idle, 1 deshabilitado, 2 armado, 3 pausado. */
    public int state;
    /** 0 = dispara al entrar, 1 = dispara al salir ({@code d}). */
    public int modeA;
    /** 1 = repetible, 0 = una sola vez ({@code e}). */
    public int modeB;
    /** Indice de la entidad que filtra (-1 = cualquiera). */
    public int filterObject;

    /** Programas (bytecode); cada uno es una lista de instrucciones. */
    public byte[][] programs;

    public float width() {
        return maxX - minX;
    }

    public float height() {
        return maxY - minY;
    }
}
