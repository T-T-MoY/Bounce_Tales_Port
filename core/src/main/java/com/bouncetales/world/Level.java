package com.bouncetales.world;

import com.badlogic.gdx.utils.Array;

/**
 * Nivel cargado: lista de entidades en orden de fichero + arbol resuelto.
 *
 * <p>Fase 3: ademas del enlace inicial, el nivel sabe desenlazar ({@code k.j()}), reenlazar
 * ({@code k.b(k)}) y reiniciar todos los transforms, que es lo que necesitan los triggers y
 * el reinicio tras morir.
 */
public final class Level {

    public final String source;
    public final int declaredObjects;
    public final int declaredTriggers;
    public final Array<Entity> entities = new Array<>();

    /** Primera entidad: la raiz del arbol (indice 0). */
    public Entity root;
    /** La bola del jugador (tag 8), si el nivel la tiene. */
    public Entity ball;

    /**
     * Se pone a {@code true} cuando un trigger mueve, reenlaza o elimina una entidad: la
     * geometria de colision tiene que recalcularse antes del siguiente paso.
     */
    public boolean geometryDirty;

    public Level(String source, int declaredObjects, int declaredTriggers) {
        this.source = source;
        this.declaredObjects = declaredObjects;
        this.declaredTriggers = declaredTriggers;
    }

    /**
     * Reconstruye el arbol como {@code k.a(k[])}: el padre es {@code link_parent} y los
     * hermanos quedan en orden de indice (el {@code link_child} del fichero es siempre
     * {@code indice - 1}).
     */
    public void link() {
        if (entities.size == 0) {
            return;
        }
        for (Entity e : entities) {
            e.children.clear();
        }
        for (Entity e : entities) {
            if (e.parent != null) {
                e.parent.children.add(e);
            }
        }
        root = entities.first();
        ball = null;
        for (Entity e : entities) {
            if (e.ball) {
                ball = e;
                break;
            }
        }
        computeAll();
    }

    public void computeAll() {
        if (root != null) {
            computeWorld(root);
        }
    }

    private void computeWorld(Entity e) {
        e.computeWorld();
        for (Entity child : e.children) {
            computeWorld(child);
        }
    }

    /** {@code k.m22a(short)}: en el original el id es el orden de construccion = indice. */
    public Entity find(int id) {
        if (id < 0 || id >= entities.size) {
            return null;
        }
        return entities.get(id);
    }

    /** {@code k.j()}: saca la entidad del arbol (deja de dibujarse y de colisionar). */
    public void detach(Entity e) {
        if (e == null) {
            return;
        }
        if (e.parent != null) {
            e.parent.children.removeValue(e, true);
            e.parent = null;
        }
        e.detached = true;
        e.hidden = true;
        geometryDirty = true;
    }

    /** {@code k.b(k)}: enlaza {@code child} como ultimo hijo de {@code parent}. */
    public void reparent(Entity child, Entity parent) {
        if (child == null || parent == null || child == parent) {
            return;
        }
        if (child.isAttachedTo(parent)) {
            return;
        }
        if (child.parent != null) {
            child.parent.children.removeValue(child, true);
        }
        child.parent = parent;
        parent.children.add(child);
        child.detached = false;
        child.hidden = false;
        child.updateWorldRecursive();
        geometryDirty = true;
    }

    /** Devuelve todas las entidades a su estado de carga (transform, padre y flags). */
    public void reset() {
        for (Entity e : entities) {
            e.local.set(e.initialLocal);
            e.parent = e.initialParent;
            e.depth = e.initialDepth;
            e.skipBranch = e.initialSkipBranch;
            e.flag256 = e.initialFlag256;
            e.detached = false;
            e.hidden = false;
        }
        geometryDirty = true;
        link();
    }
}
