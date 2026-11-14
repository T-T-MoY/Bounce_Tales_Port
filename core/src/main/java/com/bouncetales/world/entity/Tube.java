package com.bouncetales.world.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.bouncetales.physics.BallBody;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Geom;
import com.bouncetales.world.TubeData;

/**
 * Tubo lanzador (tag 11, clase {@code l} del original).
 *
 * <p>Al tocar su caja local de 80x80, captura la bola durante 900 ms. A los 600 ms la
 * coloca en la boca y le aplica la potencia firmada en la dirección del eje local X.
 */
public final class Tube {

    public static final float HALF_EXTENT = 40f;
    public static final float HOLD_TIME = 0.9f;
    public static final float LAUNCH_REMAINING = 0.3f;
    public static final float COOLDOWN = 0.5f;
    private static final float HOLD_OFFSET_Y = 35f;
    private static final float OUTLET_DISTANCE = 120f;
    private static final float POWER_SCALE = 16f;

    public final Entity entity;
    public final int targetObject;
    public final int power;

    private final Vector2 previous = new Vector2();
    private final Vector2 current = new Vector2();
    private final Vector2 point = new Vector2();
    private float remaining;
    private float cooldown;
    private boolean holding;
    private boolean charging;
    private boolean launched;

    public Tube(Entity entity) {
        this.entity = entity;
        TubeData data = entity.tubeData;
        this.targetObject = data.targetObject;
        this.power = data.power;
    }

    public boolean tryCapture(BallBody ball, int ballIndex) {
        if (entity.detached || holding || cooldown > 0f || targetObject != ballIndex) {
            return false;
        }

        entity.worldToLocal(ball.prevX, ball.prevY, previous);
        entity.worldToLocal(ball.x, ball.y, current);
        float determinant = Math.abs(entity.world.m00 * entity.world.m11
                - entity.world.m01 * entity.world.m10);
        float localRadius = ball.radius() / (float) Math.sqrt(Math.max(1e-6f, determinant));
        float extent = HALF_EXTENT + localRadius;
        if (!Geom.segmentHitsRect(previous.x, previous.y, current.x, current.y,
                -extent, -extent, extent, extent)) {
            return false;
        }

        holding = true;
        launched = false;
        remaining = 0f;
        holdBall(ball);
        Gdx.app.log("Tube", "captura entidad " + entity.index + ", objetivo "
                + targetObject + ", potencia " + power);
        return true;
    }

    public void update(float dt, BallBody ball) {
        cooldown = Math.max(0f, cooldown - dt);
        if (!holding || entity.detached || ball == null) {
            return;
        }

        if (!charging) {
            holdBall(ball);
            return;
        }

        remaining = Math.max(0f, remaining - dt);
        if (!launched && remaining <= LAUNCH_REMAINING) {
            launch(ball);
        } else if (!launched) {
            holdBall(ball);
        }

        if (remaining <= 0f) {
            charging = false;
        }
    }

    public void control(float dt, boolean rotateLeft, boolean rotateRight, boolean fire) {
        if (!holding || entity.detached) {
            return;
        }
        if (!charging) {
            if (rotateLeft != rotateRight) {
                rotate((rotateLeft ? 1f : -1f) * 3f * dt);
            }
            if (fire) {
                charging = true;
                remaining = HOLD_TIME;
            }
        }
    }

    public boolean isHoldingBall() {
        return holding && !launched && !entity.detached;
    }

    public boolean isCharging() {
        return charging && !entity.detached;
    }

    public float chargeProgress() {
        return charging ? 1f - remaining / HOLD_TIME : 0f;
    }

    public void reset() {
        remaining = 0f;
        cooldown = 0f;
        holding = false;
        charging = false;
        launched = false;
    }

    private void rotate(float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float m00 = entity.local.m00;
        float m01 = entity.local.m01;
        float m10 = entity.local.m10;
        float m11 = entity.local.m11;
        entity.local.m00 = m00 * cos + m01 * sin;
        entity.local.m01 = -m00 * sin + m01 * cos;
        entity.local.m10 = m10 * cos + m11 * sin;
        entity.local.m11 = -m10 * sin + m11 * cos;
        entity.updateWorldRecursive();
    }

    private void holdBall(BallBody ball) {
        point.set(entity.world.m02, entity.world.m12 + HOLD_OFFSET_Y);
        stopBallAt(ball, point.x, point.y);
    }

    private void launch(BallBody ball) {
        entity.localToWorld(OUTLET_DISTANCE, 0f, point);
        stopBallAt(ball, point.x, point.y);
        ball.vx = power * POWER_SCALE * entity.world.m00;
        ball.vy = power * POWER_SCALE * entity.world.m10;
        holding = false;
        launched = true;
        cooldown = COOLDOWN;
        Gdx.app.log("Tube", "lanzamiento entidad " + entity.index + " velocidad=("
                + String.format("%.1f", ball.vx) + ", "
                + String.format("%.1f", ball.vy) + ")");
    }

    private static void stopBallAt(BallBody ball, float x, float y) {
        ball.setPosition(x, y);
        ball.forceX = 0f;
        ball.forceY = 0f;
        ball.kickX = 0f;
        ball.kickY = 0f;
        ball.onGround = false;
        ball.contactTimer = 0f;
    }
}
