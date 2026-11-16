package com.bouncetales.world.entity;

import com.badlogic.gdx.math.MathUtils;
import com.bouncetales.physics.BallBody;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Geom;
import com.bouncetales.world.Level;

/**
 * Enemigo (tag 15, clase {@code m} del original).
 *
 * <p>Cuatro comportamientos ({@code m.a}):
 * <ul>
 *   <li>0 {@link #KIND_PATROL}: patrulla entre dos puntos a 91.5 px/s y, si la bola esta
 *       entre medias, cambia de extremo.</li>
 *   <li>1 {@link #KIND_LETHAL}: letal; al tocarlo mata.</li>
 *   <li>2 {@link #KIND_BLINK}: parpadea (2 s visible, 5 s oculto) y <b>solo se mueve mientras
 *       esta invisible</b>, como {@code m.d()} lineas 258-292 y 351.</li>
 *   <li>3 {@link #KIND_CHASE}: persigue a la bola y mata.</li>
 * </ul>
 *
 * <p>Caja de colision local ({@code m.mo24a()}): {@code x in [-f120c[kind]/2, f120c[kind]/2]}
 * (el original usa {@code (-f120c) << 15}, es decir, media anchura en 16.16) e
 * {@code y in [0, f119b[kind]]}. Al igual que {@code c.java} (caso 10), el test bola-caja
 * incluye el radio de la bola. El topo tipo 2 muere al ser pisado igual que el tipo 0
 * ({@code m.e()}); de lado empuja sin matar, y enterrado solo se despierta.
 */
public final class Enemy {

    public static final int KIND_PATROL = 0;
    public static final int KIND_LETHAL = 1;
    public static final int KIND_BLINK = 2;
    public static final int KIND_CHASE = 3;

    /** {@code m.f118a}: velocidad en 16.16 px/ms -&gt; px/s. */
    public static final float[] SPEED = {
            6000f / 65536f * 1000f,
            4000f / 65536f * 1000f,
            4000f / 65536f * 1000f,
            10000f / 65536f * 1000f};

    /**
     * {@code m.f120c}: <b>anchura completa</b> de la caja local. La mitad se obtiene con
     * {@code f120c << 15} en 16.16 ({@code m.mo24a()}), por eso aqui se guarda ya la
     * semianchura ({@code f120c / 2}).
     */
    public static final int[] HALF_WIDTH = {25, 50, 50, 75};

    /** {@code m.f119b}: altura de la caja local. */
    public static final int[] HEIGHT = {100, 50, 80, 150};

    public static final int HIT_NONE = 0;
    public static final int HIT_STOMP = 1;
    public static final int HIT_DAMAGE = 2;
    public static final int HIT_PUSH = 3;
    public static final int HIT_WAKE = 4;

    /** Tiempo visible / oculto del tipo 2 ({@code m.w}). */
    private static final float BLINK_VISIBLE_MS = 2000f;
    private static final float BLINK_HIDDEN_MS = 5000f;
    /** Duracion del fundido ({@code m.f115b}). */
    private static final float FADE_MS = 800f;

    public final Entity entity;
    public final int kind;
    private final Level level;

    /** Extremos de patrulla en espacio local (posicion inicial + {@code enemyPath}). */
    private final float ax;
    private final float ay;
    private final float bx;
    private final float by;

    private boolean targetEnd;
    private boolean facingLeft;
    private float pushCooldownMs;

    public boolean alive = true;

    /** {@code m.f117d}: el enemigo tipo 2 esta en fase visible. */
    public boolean visible = true;
    /** {@code m.f}: 0 sin fundido, 1 apareciendo, 2 desapareciendo. */
    public int fadePhase;
    public float fade;
    public float fadeMax;
    public boolean blinking;

    private float blinkTimer = BLINK_VISIBLE_MS;

    public Enemy(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
        this.kind = MathUtils.clamp(entity.enemyKind, 0, 3);
        if (kind == KIND_BLINK) {
            fadePhase = 1;
            fadeMax = FADE_MS;
        }
        float[] path = entity.enemyPath;
        float baseX = entity.initialLocal.m02;
        float baseY = entity.initialLocal.m12;
        ax = baseX + (path != null ? path[0] : 0f);
        ay = baseY + (path != null ? path[1] : 0f);
        bx = baseX + (path != null ? path[2] : 0f);
        by = baseY + (path != null ? path[3] : 0f);
        facingLeft = ax > bx;
        entity.local.m02 = ax;
        entity.local.m12 = ay;
        entity.updateWorldRecursive();
    }

    public float halfWidth() {
        return HALF_WIDTH[kind];
    }

    public float height() {
        return HEIGHT[kind];
    }

    public boolean facingLeft() {
        return facingLeft;
    }

    public boolean isTransitioning() {
        return kind == KIND_BLINK && fadePhase != 0;
    }

    public int revealHeight() {
        if (!isTransitioning() || fadeMax <= 0f) {
            return 0;
        }
        return MathUtils.clamp((int) (fade * 61f / fadeMax), 0, 61);
    }

    /** {@code m.d()}: movimiento y parpadeo. */
    public void update(float dt, BallBody ball) {
        if (!alive || entity.detached) {
            return;
        }
        float dtMs = dt * 1000f;
        pushCooldownMs = Math.max(0f, pushCooldownMs - dtMs);

        if (kind == KIND_BLINK) {
            updateBlink(dtMs);
        }

        // El tipo 2 solo avanza mientras esta invisible y sin fundido.
        if (kind != KIND_BLINK || (!visible && fadePhase == 0)) {
            move(dtMs, ball);
        }
    }

    private void updateBlink(float dtMs) {
        if (blinkTimer > 0f) {
            blinkTimer -= dtMs;
            if (blinkTimer <= 0f) {
                if (!visible) {
                    visible = true;
                    blinkTimer = BLINK_VISIBLE_MS;
                    fadePhase = 1;
                    fadeMax = FADE_MS;
                    fade = 0f;
                } else {
                    visible = false;
                    blinkTimer = BLINK_HIDDEN_MS;
                    fadePhase = 2;
                    fadeMax = FADE_MS;
                    fade = fadeMax;
                    blinking = true;
                }
            }
        }
        if (fadePhase == 1) {
            fade += dtMs;
            if (fade >= fadeMax) {
                if (!visible) {
                    fadePhase = 2;
                    fadeMax = 200f;
                    fade = fadeMax;
                } else {
                    fade = fadeMax;
                }
            }
        } else if (fadePhase == 2) {
            fade -= dtMs;
            if (fade <= 0f) {
                fadePhase = 0;
                fade = 0f;
                blinking = false;
            }
        }
    }

    private void move(float dtMs, BallBody ball) {
        float cx = entity.local.m02;
        float cy = entity.local.m12;
        float tx = targetEnd ? bx : ax;
        float ty = targetEnd ? by : ay;

        if (kind == KIND_PATROL && ball != null) {
            float otherX = targetEnd ? ax : bx;
            if (Math.abs(cx - tx) > 70f && Math.abs(cx - otherX) > 70f) {
                float ex = entity.worldX();
                float ballX = ball.x;
                if (Math.abs(ex - ballX) < 120f
                        && ((ex < ballX && tx < ex) || (ex > ballX && tx > ex))) {
                    targetEnd = !targetEnd;
                    tx = targetEnd ? bx : ax;
                    ty = targetEnd ? by : ay;
                }
            }
        }

        float dx = tx - cx;
        float dy = ty - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float step = SPEED[kind] * (dtMs / 1000f);

        boolean reachedX = false;
        boolean reachedY = false;
        if (dist > 1e-4f) {
            if (step >= dist) {
                cx = tx;
                cy = ty;
                reachedX = true;
                reachedY = true;
            } else {
                cx += dx / dist * step;
                cy += dy / dist * step;
            }
        } else {
            reachedX = true;
            reachedY = true;
        }
        if (reachedX && reachedY) {
            targetEnd = !targetEnd;
        }

        facingLeft = cx < entity.local.m02;
        entity.local.m02 = cx;
        entity.local.m12 = cy;
        entity.updateWorldRecursive();
    }

    /**
     * Interaccion con la bola, traduccion de {@code c.a(k)} caso 10.
     *
     * <pre>
     *   // Tercio superior (cabeza): solo tipos 0 y 2, y el topo solo si esta arriba o hundiendose
     *   if (kind==0 || (kind==2 &amp;&amp; (visible || blinking)))
     *       if (segmento toca [-hw, 2h/3]..[hw, h]) -&gt; m.e() = rebote + desaparecer
     *   else
     *       if (segmento toca [-hw,0]..[hw,h])           -&gt; m.c() = empuje / despertar / daño
     *       if (kind==2 &amp;&amp; toca [-hw,0]..[hw,h*f114a/f115b]) -&gt; m.b() = empuje del topo
     * </pre>
     *
     * <p>El test original ({@code c.a(int,int,int,int,int,int,int,int,int)}) incluye el radio
     * de la bola; aqui se aproxima inflando la caja por {@code ball.radius()}.
     *
     * @return {@link #HIT_STOMP}, {@link #HIT_DAMAGE}, {@link #HIT_PUSH}, {@link #HIT_WAKE}
     *         o {@link #HIT_NONE}
     */
    public int onBallContact(BallBody ball, float prevLocalX, float prevLocalY,
                             float localX, float localY) {
        if (!alive || entity.detached) {
            return HIT_NONE;
        }
        float hw = halfWidth();
        float h = height();
        float r = ball.radius();

        // Pisotón: la bola entra en el tercio superior (la cabeza). Igual que el original,
        // solo los tipos 0 y 2 son pisables, y el topo solo cuando esta arriba o hundiendose
        // (m.f117d == 1 || m.f116a).
        boolean canStomp = kind == KIND_PATROL
                || (kind == KIND_BLINK && (visible || blinking));
        if (canStomp && hitsBox(prevLocalX, prevLocalY, localX, localY,
                -hw, h * 2f / 3f, hw, h, r)) {
            bounce(ball);
            // m.e(): tanto el tipo 0 como el topo tipo 2 desaparecen al ser pisados.
            kill();
            return HIT_STOMP;
        }

        // Caja completa (m.c()).
        if (hitsBox(prevLocalX, prevLocalY, localX, localY, -hw, 0f, hw, h, r)) {
            switch (kind) {
                case KIND_LETHAL:
                case KIND_CHASE:
                    return HIT_DAMAGE;
                case KIND_BLINK:
                    // m.c() tipo 2: enterrado y quieto -> amago de salida (no empuja).
                    if (!visible && fadePhase == 0 && pushCooldownMs <= 0f) {
                        emerge();
                        return HIT_WAKE;
                    }
                    break;
                case KIND_PATROL:
                default:
                    push(ball);
                    return HIT_PUSH;
            }
        }

        // Zona revelada del topo (m.b()): solo la parte que ha salido del suelo.
        if (kind == KIND_BLINK && fadeMax > 0f) {
            float top = h * MathUtils.clamp(fade / fadeMax, 0f, 1f);
            if (hitsBox(prevLocalX, prevLocalY, localX, localY, -hw, 0f, hw, top, r)) {
                return pushBlink(ball) ? HIT_PUSH : HIT_NONE;
            }
        }
        return HIT_NONE;
    }

    /**
     * Segmento de la bola contra una caja local, inflada por el radio de la bola para
     * aproximar el test de capsula de {@code c.a(...)}.
     */
    private static boolean hitsBox(float px, float py, float cx, float cy,
                                   float minX, float minY, float maxX, float maxY,
                                   float radius) {
        return Geom.segmentHitsRect(px, py, cx, cy,
                minX - radius, minY - radius, maxX + radius, maxY + radius);
    }

    /** {@code m.g()}: rebote al pisar al enemigo. */
    private static void bounce(BallBody ball) {
        if (ball.vy < 0f) {
            ball.vy = -ball.vy * 0.5f;
            ball.vx *= 0.7f;
        } else {
            ball.vx = -ball.vx * 0.5f;
            ball.vy *= 0.7f;
        }
    }

    /** {@code m.f()}: el enemigo desaparece al ser pisado (tipos 0 y 2). */
    private void kill() {
        alive = false;
        level.detach(entity);
    }

    /** {@code m.c()} tipo 0: empuja la bola sin hacer daño. */
    private void push(BallBody ball) {
        if (entity.worldX() < ball.x) {
            ball.kickX += 500f * ball.mass();
        } else {
            ball.kickX -= 500f * ball.mass();
        }
        ball.vy = 0f;
        ball.vx = 0f;
    }

    /** {@code m.c()} tipo 2: inicia la animacion corta del topo enterrado. */
    private void emerge() {
        fade = 0f;
        fadePhase = 1;
        fadeMax = 200f;
    }

    /**
     * {@code m.b()} tipo 2: empuje del topo.
     *
     * <ul>
     *   <li>Arriba ({@code f117d == 1}): empuje horizontal continuo de 100. Se fija la
     *       velocidad horizontal (solo si la bola no se aleja ya mas rapido) en vez de sumar
     *       impulso cada frame, que era lo que la aceleraba sin control.</li>
     *   <li>Emergiendo ({@code f == 1}) y sin cooldown: patada lateral de 200 y salto de 400
     *       una vez cada 500 ms ({@code m.v}).</li>
     *   <li>Hundiendose o enterrado: no empuja.</li>
     * </ul>
     *
     * <p>No se anula {@code vx}/{@code vy}: el original los pone a cero y suma el impulso,
     * pero eso dejaba la bola "pegada" al topo. Aqui se conserva la velocidad y solo se
     * aplica el empuje, de modo que la bola nunca se congela.
     *
     * @return {@code true} si aplico algun empuje
     */
    private boolean pushBlink(BallBody ball) {
        boolean pushRight = entity.worldX() < ball.x;
        float dir = pushRight ? 1f : -1f;

        if (visible) {
            float shove = 100f / ball.mass();
            if (dir * ball.vx < shove) {
                ball.vx = dir * shove;
            }
            return true;
        }
        if (fadePhase != 1 || pushCooldownMs > 0f) {
            return false;
        }
        pushCooldownMs = 500f;
        ball.kickX += dir * 200f * ball.mass();
        ball.kickY += 400f * ball.mass();
        return true;
    }

    public void reset() {
        alive = true;
        visible = true;
        fadePhase = kind == KIND_BLINK ? 1 : 0;
        fade = 0f;
        fadeMax = kind == KIND_BLINK ? FADE_MS : 0f;
        blinking = false;
        pushCooldownMs = 0f;
        blinkTimer = BLINK_VISIBLE_MS;
        targetEnd = false;
        facingLeft = ax > bx;
        entity.local.m02 = ax;
        entity.local.m12 = ay;
        entity.updateWorldRecursive();
    }
}
