package com.bouncetales.world.entity;

import com.badlogic.gdx.math.Vector2;
import com.bouncetales.physics.BallBody;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Geom;
import com.bouncetales.world.WaterData;

/**
 * Agua y corrientes (tag 10, clase {@code i} del original).
 *
 * <p>La deteccion esta en {@code c.java}, caso 6 del recorrido de colisiones:
 * <pre>
 *   if (a(f70a &lt;&lt; 16, f71c &lt;&lt; 16, (b - f70a) &lt;&lt; 16, 0, ...)) {
 *       iVar.a(z, this.f11b, f3a[f34d], this);   // salpicadura y freno visual
 *   }
 *   if (i9 - i2 &lt; iVar.f83b) {
 *       iVar.a(i8, i9 - i2, this);               // fuerza, amortiguacion y profundidad
 *   }
 * </pre>
 *
 * <p>{@code i.a(int,int,c)} aplica una corriente interpolada dentro de la caja y amortigua la
 * velocidad con {@code 1/masa}. Esa es la razon de que el balon flote: la misma corriente
 * ascendente (p. ej. 9 &lt;&lt; 5 = 288) supera la gravedad para masa 0.5
 * ({@code 288 / 0.5 = 576 &gt; 400}), pero no para masa 1.0 ni 1.4. Ademas el original solo
 * aplica el agua de un cuerpo por frame ({@code n.f152a}).
 */
public final class Water {

    /** Margen vertical de la caja de dibujo/deteccion ({@code 1966080 / 65536}). */
    public static final float PADDING = 30f;

    /** Separacion entre muestras de la superficie ondulada, en unidades locales. */
    private static final float WAVE_STEP = 2f;

    public final Entity entity;
    public final WaterData data;

    /** Ondas de la superficie (desplazamiento vertical en mundo, positivo = cresta). */
    private final float[] wave;
    private final float[] waveVelocity;
    private final Vector2 prev = new Vector2();
    private final Vector2 cur = new Vector2();
    private final Vector2 tmp = new Vector2();

    public Water(Entity entity) {
        this.entity = entity;
        this.data = entity.waterData;
        int samples = data == null ? 0
                : Math.max(8, (int) Math.ceil(Math.max(1f, data.width()) / WAVE_STEP) + 1);
        this.wave = new float[samples];
        this.waveVelocity = new float[samples];
    }

    public int sampleCount() {
        return wave.length;
    }

    /** Altura de la onda en la muestra {@code i} (unidades locales/mundo). */
    public float waveHeight(int i) {
        return wave[i];
    }

    public boolean visible() {
        return data != null && data.visible();
    }

    /**
     * Avanza la simulacion de la superficie. Las ondas nacen al entrar o salir del agua y se
     * propagan con una ecuacion de onda discretizada, igual que {@code i.f72a} en el original.
     */
    public void update(float dt) {
        if (data == null || data.solid || !data.visible() || wave.length < 3) {
            return;
        }
        float damp = Math.max(0f, 1f - 2.2f * dt);
        for (int i = 1; i < wave.length - 1; i++) {
            float laplacian = wave[i - 1] + wave[i + 1] - 2f * wave[i];
            waveVelocity[i] += laplacian * 150f * dt;
            waveVelocity[i] *= damp;
            wave[i] += waveVelocity[i] * dt;
            if (wave[i] > 28f) {
                wave[i] = 28f;
            } else if (wave[i] < -28f) {
                wave[i] = -28f;
            }
        }
        wave[0] = 0f;
        wave[wave.length - 1] = 0f;
    }

    /**
     * {@code i.a(int i, int i2, c cVar)}: aplica corriente y amortiguacion si la bola esta
     * dentro del agua. Tambien detecta el cruce de la superficie para la salpicadura
     * ({@code i.a(int,float,int,c)}).
     *
     * @return {@code true} si este cuerpo ha aplicado fuerza (el original solo permite uno
     *         por frame, {@code n.f152a})
     */
    public boolean apply(BallBody ball, float dt) {
        if (entity.detached || data == null) {
            return false;
        }
        entity.worldToLocal(ball.x, ball.y, cur);
        entity.worldToLocal(ball.prevX, ball.prevY, prev);

        float det = Math.abs(entity.world.m00 * entity.world.m11
                - entity.world.m01 * entity.world.m10);
        float radius = det > 1e-6f ? ball.radius() * (float) Math.sqrt(det) : ball.radius();

        boolean belowSurface = cur.y - radius < data.surface;
        boolean overlapsX = cur.x + radius > data.x0 && cur.x - radius < data.x1;
        boolean overlapsBottom = cur.y + radius > data.bottom - PADDING;
        if (!belowSurface || !overlapsX || !overlapsBottom) {
            return false;
        }

        // Salpicadura: el original prueba el segmento recorrido contra la linea de superficie.
        if (data.visible() && crossesSurface(prev.y, cur.y, prev.x, cur.x, radius)) {
            splash(ball, Math.abs(ball.vy));
            ball.visualVx /= 3f;
            ball.visualVy /= 3f;
        }

        float tx = clamp01((cur.x - data.x0) / Math.max(1e-6f, data.width()));
        float ty = clamp01((cur.y - (data.bottom - PADDING))
                / Math.max(1e-6f, (data.surface + PADDING) - (data.bottom - PADDING)));

        ball.forceX += Geom.lerp(data.currentLeftX, data.currentRightX, tx) * 32f;
        ball.forceY += Geom.lerp(data.currentBottomY, data.currentSurfaceY, ty) * 32f;

        if (data.visible()) {
            // c.f10a/f11b -= v * (1/masa) * dtMs * 0.0014
            float k = (dt * 1000f) * 0.0014f / ball.mass();
            ball.vx -= ball.vx * k;
            ball.vy -= ball.vy * k;
            ball.inWater = true;
        }
        ball.touchedWater = true;
        return true;
    }

    private boolean crossesSurface(float prevY, float curY, float prevX, float curX, float radius) {
        if (prevY == curY) {
            return false;
        }
        if ((prevY - data.surface) * (curY - data.surface) > 0f) {
            return false;
        }
        float t = (prevY - data.surface) / (prevY - curY);
        float x = Geom.lerp(prevX, curX, t);
        return x >= data.x0 - radius && x <= data.x1 + radius;
    }

    private void splash(BallBody ball, float speed) {
        if (wave.length < 3) {
            return;
        }
        entity.worldToLocal(ball.x, ball.y, tmp);
        float t = clamp01((tmp.x - data.x0) / Math.max(1e-6f, data.width()));
        int center = Math.round(t * (wave.length - 1));
        float amplitude = Math.min(26f, 3f + speed * 0.09f);
        for (int offset = -3; offset <= 3; offset++) {
            int i = center + offset;
            if (i <= 0 || i >= wave.length - 1) {
                continue;
            }
            float falloff = 1f - Math.abs(offset) / 4f;
            wave[i] += amplitude * falloff;
            waveVelocity[i] += amplitude * 1.5f * falloff;
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return v > 1f ? 1f : v;
    }
}
