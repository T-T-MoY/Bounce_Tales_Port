package com.bouncetales.world.entity;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.bouncetales.physics.BallBody;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Level;
/**
 * Contenedor de las entidades interactivas de un nivel (tags 10-15).
 *
 * <p>Reproduce el orden del original: primero se actualizan las entidades
 * ({@code k.d()}), despues los triggers y la colision, y por ultimo la interaccion con la
 * bola ({@code c.a(k)}), que es donde se recogen coleccionables, rebotan los pads y matan los
 * enemigos.
 */
public final class LevelEntities {

    public static final int EVENT_COLLECTED = 1;
    public static final int EVENT_PAD = 2;
    public static final int EVENT_DEATH = 4;
    public static final int EVENT_STOMP = 8;

    public final Array<Collectible> collectibles = new Array<>();
    public final Array<Pad> pads = new Array<>();
    public final Array<Enemy> enemies = new Array<>();
    public final Array<Water> waters = new Array<>();
    public final Array<Tube> tubes = new Array<>();

    private final Level level;
    private final Vector2 prev = new Vector2();
    private final Vector2 cur = new Vector2();

    private int collected;

    public LevelEntities(Level level) {
        this.level = level;
        for (Entity e : level.entities) {
            switch (e.tag) {
                case Entity.TAG_COLLECTIBLE:
                    e.collectible = new Collectible(e);
                    collectibles.add(e.collectible);
                    break;
                case Entity.TAG_PAD:
                    e.pad = new Pad(e);
                    pads.add(e.pad);
                    break;
                case Entity.TAG_ENEMY:
                    e.enemy = new Enemy(e, level);
                    enemies.add(e.enemy);
                    break;
                case Entity.TAG_WATER:
                    if (e.waterData != null) {
                        e.water = new Water(e);
                        waters.add(e.water);
                    }
                    break;
                case Entity.TAG_TUBE:
                    if (e.tubeData != null) {
                        e.tube = new Tube(e);
                        tubes.add(e.tube);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public int total() {
        return collectibles.size;
    }

    public int collected() {
        return collected;
    }

    public boolean allCollected() {
        return !collectibles.isEmpty() && collected >= collectibles.size;
    }

    /** {@code k.d()} de cada entidad interactiva. */
    public void update(float dt, BallBody ball) {
        for (Water water : waters) {
            water.update(dt);
        }
        for (Pad pad : pads) {
            pad.update(dt, ball);
        }
        for (Enemy enemy : enemies) {
            enemy.update(dt, ball);
        }
        for (Tube tube : tubes) {
            tube.update(dt, ball);
        }
    }

    public void controlTube(float dt, boolean rotateLeft, boolean rotateRight, boolean fire) {
        for (Tube tube : tubes) {
            if (tube.isHoldingBall()) {
                tube.control(dt, rotateLeft, rotateRight, fire);
                return;
            }
        }
    }

    public boolean isBallHeld() {
        for (Pad pad : pads) {
            if (pad.isHoldingBall()) {
                return true;
            }
        }
        for (Tube tube : tubes) {
            if (tube.isHoldingBall()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code c.a(k)}: interaccion bola-entidad usando las posiciones locales anterior y
     * actual (el original interpola el segmento del frame).
     *
     * @return mascara de {@code EVENT_*}
     */
    public int interact(BallBody ball, float dt, int[] vars) {
        int events = 0;

        for (Collectible c : collectibles) {
            if (c.isCollected()) {
                continue;
            }
            c.entity.worldToLocal(ball.x, ball.y, cur);
            if (c.tryCollect(cur.x, cur.y)) {
                collected++;
                events |= EVENT_COLLECTED;
            }
        }

        for (Pad p : pads) {
            if (p.entity.detached) {
                continue;
            }
            p.entity.worldToLocal(ball.prevX, ball.prevY, prev);
            p.entity.worldToLocal(ball.x, ball.y, cur);
            if (p.tryBounce(ball, prev.x, prev.y, cur.x, cur.y)) {
                events |= EVENT_PAD;
            }
        }

        for (Enemy e : enemies) {
            if (!e.alive || e.entity.detached) {
                continue;
            }
            e.entity.worldToLocal(ball.prevX, ball.prevY, prev);
            e.entity.worldToLocal(ball.x, ball.y, cur);
            int hit = e.onBallContact(ball, prev.x, prev.y, cur.x, cur.y);
            if (hit == Enemy.HIT_STOMP) {
                events |= EVENT_STOMP;
            } else if (hit == Enemy.HIT_DAMAGE) {
                if (vars != null) {
                    vars[0] = 1;
                }
                events |= EVENT_DEATH;
            }
        }

        for (Water w : waters) {
            if (w.entity.detached) {
                continue;
            }
            // n.f152a: el original solo deja que un cuerpo de agua actuue por frame.
            if (w.apply(ball, dt)) {
                break;
            }
        }

        if (level.ball != null) {
            for (Tube tube : tubes) {
                if (tube.tryCapture(ball, level.ball.index)) {
                    break;
                }
            }
        }

        return events;
    }

    public void reset() {
        collected = 0;
        for (Collectible c : collectibles) {
            c.reset();
        }
        for (Pad p : pads) {
            p.reset();
        }
        for (Enemy e : enemies) {
            e.reset();
        }
        for (Tube tube : tubes) {
            tube.reset();
        }
    }

    public Level level() {
        return level;
    }
}
