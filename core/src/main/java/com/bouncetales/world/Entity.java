package com.bouncetales.world;

import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.bouncetales.world.entity.Beam;
import com.bouncetales.world.entity.Collectible;
import com.bouncetales.world.entity.Enemy;
import com.bouncetales.world.entity.Pad;
import com.bouncetales.world.script.Trigger;

/**
 * Entidad del nivel. Equivale a {@code k} del original: transform local + enlaces de arbol +
 * flags de dibujo. Las subclases del original ({@code q}, {@code g}, {@code s}, ...) se
 * representan aqui con campos opcionales porque el JSON ya viene tipado por {@code tag}.
 *
 * <p>Fase 3: la entidad tambien guarda su estado inicial (transform, padre, flags) para poder
 * reiniciar el nivel, y referencias a los objetos de comportamiento que la controlan
 * (coleccionable, pad, enemigo, haz de fuerza, trigger).
 */
public final class Entity {

    // Tags del formato de nivel (n.g() del original).
    public static final int TAG_OBJECT = 0;
    public static final int TAG_POLYGON = 4;
    public static final int TAG_TRIGGER = 6;
    public static final int TAG_BALL = 8;
    public static final int TAG_DECORATION = 9;
    public static final int TAG_BEAM = 10;
    public static final int TAG_TUBE = 11;
    public static final int TAG_PAD = 12;
    public static final int TAG_COLLECTIBLE = 13;
    public static final int TAG_BALL_DECORATION = 14;
    public static final int TAG_ENEMY = 15;

    public int index;
    public int tag;

    /** Enlaces del fichero (indices de entidad; -1 = ninguno). */
    public int linkParent = -1;
    public int linkChild = -1;

    /** {@code (m & 31) - 16}: a mayor valor, mas al fondo. */
    public int depth;
    public boolean solid;
    public boolean skipBranch;

    /** {@code m & 256}: flag libre del original; se conserva por fidelidad. */
    public boolean flag256;

    /** Transform local (16.16 -> float) y transform absoluta calculada al enlazar el arbol. */
    public final Affine2 local = new Affine2();
    public final Affine2 world = new Affine2();

    public Entity parent;
    public final Array<Entity> children = new Array<>();

    // --- estado inicial, para reiniciar el nivel ---
    public final Affine2 initialLocal = new Affine2();
    public Entity initialParent;
    public int initialDepth;
    public boolean initialSkipBranch;
    public boolean initialFlag256;

    /**
     * Entidad fuera del arbol (recogida, eliminada por un trigger o matada): no se dibuja,
     * no colisiona y no se actualiza. Equivale a reparentar a {@code k.f109b} en el original.
     */
    public boolean detached;
    /** Oculta sin desenlazar (los coleccionables recogidos). */
    public boolean hidden;

    public PolygonShape polygon;
    public DecorationList decorations;

    public int padSprite = -1;
    public int padStrength;

    public int enemyKind = -1;
    /** Puntos de patrulla locales {@code x0,y0,x1,y1} (tag 15). */
    public float[] enemyPath;

    public TriggerData triggerData;
    public BeamData beamData;

    /** Opcode 1 (animar decoracion): reinicia la animacion de la lista de sprites. */
    public float animationTime;

    public boolean ball;
    /** Tipo de bola (0 normal, 1 marmol, 2 rayada). Fase 1: siempre 0. */
    public int ballType;

    // --- comportamiento (lo rellenan LevelEntities y TriggerVM) ---
    public Collectible collectible;
    public Pad pad;
    public Enemy enemy;
    public Beam beam;
    public Trigger trigger;

    public float worldX() {
        return world.m02;
    }

    public float worldY() {
        return world.m12;
    }

    public void computeWorld() {
        if (parent == null) {
            world.set(local);
        } else {
            world.set(parent.world).mul(local);
        }
    }

    /** Recalcula este subarbol ({@code k.h()} + {@code k.l()}). */
    public void updateWorldRecursive() {
        computeWorld();
        for (Entity child : children) {
            child.updateWorldRecursive();
        }
    }

    /** Vuelve al transform con el que se cargo el nivel. */
    public void resetTransform() {
        local.set(initialLocal);
    }

    /** Lleva un punto de mundo al espacio local de esta entidad (inversa de {@link #world}). */
    public void worldToLocal(float wx, float wy, Vector2 out) {
        float dx = wx - world.m02;
        float dy = wy - world.m12;
        float det = world.m00 * world.m11 - world.m01 * world.m10;
        if (Math.abs(det) < 1e-9f) {
            out.set(0f, 0f);
            return;
        }
        float inv = 1f / det;
        out.x = (world.m11 * dx - world.m01 * dy) * inv;
        out.y = (-world.m10 * dx + world.m00 * dy) * inv;
    }

    /** Lleva un punto local a mundo. */
    public void localToWorld(float lx, float ly, Vector2 out) {
        out.x = world.m00 * lx + world.m01 * ly + world.m02;
        out.y = world.m10 * lx + world.m11 * ly + world.m12;
    }

    public boolean isAttachedTo(Entity rootEntity) {
        Entity e = this;
        while (e != null) {
            if (e == rootEntity) {
                return true;
            }
            e = e.parent;
        }
        return false;
    }
}
