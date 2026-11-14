package com.bouncetales.world.entity;

import com.badlogic.gdx.math.Vector2;
import com.bouncetales.physics.BallBody;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Geom;

/**
 * Pad de rebote (tag 12, clase {@code e} del original).
 *
 * <p>{@code e.mo24a()} declara la caja local {@code x in [-70,70]}, {@code y in [0,95]}, pero
 * {@code c.a(k)} caso 8 lo que prueba es el <b>segmento</b> que recorre el centro de la bola
 * contra la linea superior del pad ({@code y = 95}, de {@code x = -70} a {@code x = 70}).
 * Solo actua si la bola baja ({@code vy < 0}).
 *
 * <p>Impulso: {@code min(-vy * strength * 2 / 100, 400 * strength * 2 / 100)}; con
 * {@code strength = 75} el tope es 600 px/s.
 */
public final class Pad {

    public static final float MIN_X = -70f;
    public static final float MAX_X = 70f;
    public static final float SURFACE_Y = 95f;
    public static final int SOUND = 2;
    private static final int ANIMATION_FRAME_COUNT = 6;

    public final Entity entity;
    public final int strength;

    /** Tiempo restante de la animacion de compresion (ms). */
    public float animation;
    public float animationDuration;
    public float lastKick;

    private final Vector2 heldPosition = new Vector2();
    private float elapsed;
    private float launchAt;
    private float holdX;
    private boolean active;
    private boolean launched;

    public Pad(Entity entity) {
        this.entity = entity;
        this.strength = entity.padStrength;
    }

    /** @return {@code true} si la bola ha iniciado una compresion del pad. */
    public boolean tryBounce(BallBody ball, float prevLocalX, float prevLocalY,
                             float localX, float localY) {
        if (entity.detached) {
            return false;
        }
        if (active) {
            if (!launched) {
                holdBall(ball);
            }
            return false;
        }
        if (ball.vy >= 0f) {
            return false;
        }
        // Cruce de la linea y = SURFACE_Y dentro del ancho del pad.
        if (!(prevLocalY >= SURFACE_Y && localY <= SURFACE_Y)) {
            return false;
        }
        float t = prevLocalY == localY ? 0f : (prevLocalY - SURFACE_Y) / (prevLocalY - localY);
        float x = Geom.lerp(prevLocalX, localX, t);
        if (x < MIN_X || x > MAX_X) {
            return false;
        }

        float impactSpeed = -ball.vy;
        int durationSpeed = Math.max(100, Math.min(1000, (int) impactSpeed));
        float kick = Math.min(impactSpeed * strength * 2f / 100f,
                400f * strength * 2f / 100f);

        animationDuration = ((1100 - durationSpeed) >> 1) + 20;
        animation = animationDuration;
        elapsed = 0f;
        launchAt = animationDuration - animationDuration / ANIMATION_FRAME_COUNT;
        holdX = x;
        active = true;
        launched = false;
        lastKick = kick;

        ball.vx = 0f;
        ball.vy = 0f;
        ball.forceX = 0f;
        ball.forceY = 0f;
        ball.kickX = 0f;
        ball.kickY = 0f;
        ball.onGround = false;
        ball.contactTimer = 0f;
        holdBall(ball);
        return true;
    }

    public void update(float dt, BallBody ball) {
        if (!active || entity.detached || ball == null) {
            return;
        }

        elapsed = Math.min(animationDuration, elapsed + dt * 1000f);
        animation = animationDuration - elapsed;
        if (!launched && elapsed >= launchAt) {
            ball.kickY += lastKick * ball.mass();
            ball.onGround = false;
            ball.contactTimer = 0f;
            launched = true;
        }
        if (!launched) {
            holdBall(ball);
        }
        if (elapsed >= animationDuration) {
            active = false;
            animation = 0f;
            elapsed = 0f;
        }
    }

    /** El pad queda en el frame 0 en reposo; el frame avanza solo durante la compresion. */
    public int animationFrame(int frameCount) {
        if (!active || frameCount <= 1 || animationDuration <= 0f) {
            return 0;
        }
        return Math.min(frameCount - 1, (int) (elapsed * frameCount / animationDuration));
    }

    public boolean isHoldingBall() {
        return active && !launched && !entity.detached;
    }

    private void holdBall(BallBody ball) {
        entity.localToWorld(holdX, SURFACE_Y, heldPosition);
        ball.x = heldPosition.x;
        ball.y = heldPosition.y;
        ball.prevX = ball.x;
        ball.prevY = ball.y;
        ball.vx = 0f;
        ball.vy = 0f;
        ball.forceX = 0f;
        ball.forceY = 0f;
        ball.kickX = 0f;
        ball.kickY = 0f;
        ball.onGround = false;
        ball.contactTimer = 0f;
    }

    public void reset() {
        animation = 0f;
        animationDuration = 0f;
        lastKick = 0f;
        elapsed = 0f;
        launchAt = 0f;
        holdX = 0f;
        active = false;
        launched = false;
    }
}
