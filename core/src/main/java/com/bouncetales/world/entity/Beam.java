package com.bouncetales.world.entity;

import com.bouncetales.physics.BallBody;
import com.bouncetales.world.BeamData;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Geom;

/**
 * Haz de fuerza (tag 10, clase {@code i} del original). <b>No es una rampa solida</b>: el
 * original no lo incluye en el bucle de colision de poligonos, sino que aplica fuerzas y
 * amortigua la velocidad de la bola dentro de su caja.
 *
 * <p>{@code i.a(int,int,c)}:
 * <pre>
 *   fx = (x - minX) / (maxX - minX)
 *   fy = (y - minY) / (maxY - minY)
 *   forceX += lerp(p3, p1, fx) &lt;&lt; 5
 *   forceY += lerp(p2, p0, fy) &lt;&lt; 5
 *   v -= v * dt/400
 * </pre>
 * La caja se extiende 30 px por encima y por debajo de la linea {@code y1}, y solo actua
 * cuando la bola esta por debajo de esa linea ({@code i.f83b}).
 */
public final class Beam {

    /** Margen vertical de la caja ({@code 1966080 / 65536 = 30}). */
    public static final float PADDING = 30f;

    public final Entity entity;
    public final BeamData data;

    public Beam(Entity entity) {
        this.entity = entity;
        this.data = entity.beamData;
    }

    public boolean contains(float localX, float localY) {
        return localX >= data.x0 && localX <= data.x1 && localY <= data.y1;
    }

    /** {@code i.a(int i, int i2, c cVar)}. */
    public void apply(BallBody ball, float localX, float localY, float dt) {
        if (entity.detached || data == null || !contains(localX, localY)) {
            return;
        }
        float width = data.x1 - data.x0;
        float height = (data.y1 + PADDING) - (data.y0 - PADDING);
        float fx = width == 0f ? 0f : (localX - data.x0) / width;
        float fy = height == 0f ? 0f : (localY - (data.y0 - PADDING)) / height;
        fx = clamp01(fx);
        fy = clamp01(fy);

        float forceX = Geom.lerp(data.p3, data.p1, fx) * 32f;
        float forceY = Geom.lerp(data.p2, data.p0, fy) * 32f;
        ball.forceX += forceX;
        ball.forceY += forceY;

        // Amortiguacion global de velocidad (f10a/f11b): v -= v * (1/mass) * dt * 0.0014
        float k = (dt * 1000f) * 0.0014f / ball.mass();
        ball.vx -= ball.vx * k;
        ball.vy -= ball.vy * k;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return v > 1f ? 1f : v;
    }
}
