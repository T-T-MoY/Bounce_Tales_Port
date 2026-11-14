package com.bouncetales.world.entity;

import com.bouncetales.world.Entity;

/**
 * Coleccionable (tag 13, clase {@code a} del original).
 *
 * <p>{@code c.a(k)} caso 9: se toma la posicion <b>actual</b> de la bola en el espacio local
 * del coleccionable y, si {@code x*x + y*y < 45*45}, se recoge: el contador sube y la entidad
 * desaparece. El original lanza ademas una particula y un sonido; aqui el sonido lo pone
 * {@code SoundBank} desde {@code LevelEntities}.
 */
public final class Collectible {

    /** Radio de recogida en pixeles: {@code 2025 = 45*45}. */
    public static final float RADIUS = 45f;
    /** Sonido del original al recoger ({@code n.f160f.a(8, ...)}). */
    public static final int SOUND = 8;

    public final Entity entity;

    private boolean collected;

    public Collectible(Entity entity) {
        this.entity = entity;
    }

    public boolean isCollected() {
        return collected;
    }

    /**
     * @param localX posicion actual de la bola en el espacio local del coleccionable
     * @param localY idem
     * @return {@code true} si se acaba de recoger
     */
    public boolean tryCollect(float localX, float localY) {
        if (collected || entity.detached) {
            return false;
        }
        if (localX * localX + localY * localY >= RADIUS * RADIUS) {
            return false;
        }
        collected = true;
        entity.hidden = true;
        return true;
    }

    public void reset() {
        collected = false;
        entity.hidden = false;
    }
}
