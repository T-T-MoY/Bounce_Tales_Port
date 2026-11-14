package com.bouncetales.render;

import com.bouncetales.physics.BallBody;

/**
 * Camara con muelle amortiguado, traduccion de {@code k.b(boolean)} (linea 584).
 *
 * <p>Objetivo en mundo:
 * <pre>
 *   objetivo = bola + (0, alto / (5*escala)) + adelanto
 * </pre>
 * El termino {@code alto/(5*escala)} es el desplazamiento vertical que deja la bola en el
 * tercio inferior de la pantalla ({@code i8 = (-alto/5)} transformado por la inversa de la
 * matriz de camara).
 *
 * <p>El adelanto reproduce el calculo entero del original. Con
 * {@code v} en px/s, {@code i2 = v/1000} (px/ms), {@code i4 = i2*128} y la inversa de la
 * matriz de pantalla ({@code 1/escala} con Y volteada):
 * <pre>
 *   adelantoX = 0.128*vx*(1 + 1/escala)
 *   adelantoY = 0.128*vy*(1/escala - 1)
 * </pre>
 *
 * <p>El muelle corre en subpasos fijos de 15 ms con la misma recurrencia entera:
 * <pre>
 *   camara += vel*15
 *   vel    += (15*90*(delta/64)) / 16384
 *   vel    -= (15*140*vel) / 16384
 * </pre>
 * donde {@code delta} es la distancia al objetivo con zona muerta de 5 px y se calcula una
 * sola vez por frame (igual que {@code i13/i14} en el original).
 */
public final class CameraSpring {

    /** Subpaso fijo del original: 15 ms. */
    public static final float STEP_MS = 15f;
    /** {@code p}: rigidez por defecto. */
    public static final float STIFFNESS = 90f;
    /** {@code q}: amortiguacion por defecto. */
    public static final float DAMPING = 140f;
    /** Zona muerta en pixeles de mundo ({@code 327680} en 16.16 = 5 px). */
    public static final float DEADZONE = 5f;
    /** {@code i2 << 7}: adelanto por velocidad (px/s * 0.128). */
    public static final float LEAD = 0.128f;

    private final ScreenTransform transform;

    /** Opcode 31/32: los triggers pueden cambiar el muelle. */
    private float stiffness = STIFFNESS;
    private float damping = DAMPING;

    /** Velocidad de la camara en px/ms (equivale a {@code k.n/k.o}). */
    private float velocityX;
    private float velocityY;

    private float accumulatorMs;

    public CameraSpring(ScreenTransform transform) {
        this.transform = transform;
    }

    public void setSpring(float stiffness, float damping) {
        this.stiffness = stiffness > 0f ? stiffness : STIFFNESS;
        this.damping = damping > 0f ? damping : DAMPING;
    }

    public float stiffness() {
        return stiffness;
    }

    public float damping() {
        return damping;
    }

    /** {@code k.b(true)}: coloca la camara en el objetivo y resetea el muelle. */
    public void snap(BallBody ball) {
        snapTo(ball.x, ball.y);
    }

    public void snapTo(float x, float y) {
        transform.cameraX = x;
        transform.cameraY = y + offsetY();
        resetMotion();
    }

    /** Descarta la inercia anterior cuando un trigger cambia o libera el foco de camara. */
    public void resetMotion() {
        velocityX = 0f;
        velocityY = 0f;
        accumulatorMs = 0f;
    }

    /** Desplazamiento vertical en mundo que deja la bola en el tercio inferior. */
    public float offsetY() {
        return transform.height / (5f * transform.scale);
    }

    /** Avanza el muelle. Debe llamarse una vez por paso de fisica. */
    public void update(BallBody ball, float dt) {
        update(ball.x, ball.y, ball.vx, ball.vy, dt);
    }

    /**
     * Avanza el muelle hacia un foco arbitrario (la bola, o la entidad que fije el opcode 30).
     */
    public void update(float focusX, float focusY, float focusVx, float focusVy, float dt) {
        float targetX = focusX + LEAD * focusVx * (1f + 1f / transform.scale);
        float targetY = focusY + offsetY()
                + LEAD * focusVy * (1f / transform.scale - 1f);

        float deltaX = targetX - transform.cameraX;
        float deltaY = targetY - transform.cameraY;
        if (Math.abs(deltaX) < DEADZONE) {
            deltaX = 0f;
        }
        if (Math.abs(deltaY) < DEADZONE) {
            deltaY = 0f;
        }

        accumulatorMs += dt * 1000f;
        while (accumulatorMs >= STEP_MS) {
            transform.cameraX += velocityX * STEP_MS;
            transform.cameraY += velocityY * STEP_MS;

            velocityX += (STEP_MS * stiffness * (deltaX / 64f)) / 16384f;
            velocityY += (STEP_MS * stiffness * (deltaY / 64f)) / 16384f;
            velocityX -= (STEP_MS * damping * velocityX) / 16384f;
            velocityY -= (STEP_MS * damping * velocityY) / 16384f;

            accumulatorMs -= STEP_MS;
        }
    }
}
