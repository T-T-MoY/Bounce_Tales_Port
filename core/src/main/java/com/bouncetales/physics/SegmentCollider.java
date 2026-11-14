package com.bouncetales.physics;

/**
 * Primitivas de colisión del original, en coma flotante.
 *
 * <p>Traduce literalmente los dos tests de {@code c.java}:
 * <ul>
 *   <li>{@link #rayCircle} = {@code c.c(...)} (línea 764): barrido de un punto (el centro de la
 *       bola) contra un círculo de radio {@code r} (un vértice del polígono). Es el test de
 *       "círculo contra extremo".</li>
 *   <li>{@link #segmentSegment} = {@code c.a(...)} (línea 801): intersección del segmento de
 *       movimiento de la bola contra un borde del polígono ya desplazado por {@code r}.</li>
 * </ul>
 *
 * <p>La fórmula pedida del original es la de {@code rayCircle}:
 * <pre>
 *   t = (d · u) &gt;&gt; 16          (d = centro - vértice, u = dirección del movimiento)
 *   q = t² - |d|² + r²
 *   s = -t - sqrt(q)           (distancia hasta entrar en el círculo)
 * </pre>
 * con el caso {@code |d|² - r² <= 0} (ya dentro) resuelto proyectando sobre la normal.
 */
public final class SegmentCollider {

    /** Resultado reutilizable de un test. */
    public static final class Hit {
        /** Punto de contacto: donde debe quedar el centro de la bola. */
        public float x;
        public float y;
        /** Parámetro 0..1 sobre el segmento de movimiento. */
        public float t;
        /** Parámetro 0..1 sobre el borde (sólo en {@link #segmentSegment}). */
        public float u;
        /** {@code true} si la bola ya estaba penetrando. */
        public boolean deep;

        public void clear() {
            x = y = t = u = 0f;
            deep = false;
        }
    }

    private SegmentCollider() {
    }

    /**
     * {@code c.c(...)}: barrido del punto {@code (px,py)} a lo largo de {@code (dx,dy)*len}
     * contra un círculo de radio {@code r} centrado en {@code (cx,cy)}.
     *
     * @param dx dirección unitaria X del movimiento
     * @param dy dirección unitaria Y del movimiento
     * @param len longitud del movimiento (px)
     */
    public static boolean rayCircle(float px, float py, float dx, float dy, float len,
                                    float cx, float cy, float r, Hit out) {
        float ax = px - cx;
        float ay = py - cy;

        // t = (d · u) >> 16 ; sólo interesa el lado "activo" (t < 0).
        float t = ax * dx + ay * dy;
        if (t >= 0f) {
            return false;
        }

        // j4 = |d|² - r²
        float d2 = ax * ax + ay * ay - r * r;
        if (d2 <= 0f) {
            // Penetración: proyecta el centro sobre el círculo.
            float d = (float) Math.sqrt(ax * ax + ay * ay);
            if (d < 1e-5f) {
                out.x = cx + dx * r;
                out.y = cy + dy * r;
            } else {
                out.x = cx + ax / d * r;
                out.y = cy + ay / d * r;
            }
            out.t = 0f;
            out.u = 0f;
            out.deep = true;
            return true;
        }

        // q = t² - |d|² + r² ; s = -t - sqrt(q)
        float q = t * t - d2;
        if (q < 0f) {
            return false;
        }
        float s = -t - (float) Math.sqrt(q);
        if (s > len) {
            return false;
        }

        out.t = len > 1e-5f ? s / len : 0f;
        out.x = px + dx * s;
        out.y = py + dy * s;
        out.u = 0f;
        out.deep = false;
        return true;
    }

    /**
     * {@code c.a(...)}: intersección del segmento de movimiento {@code P -> P+delta} con el
     * borde {@code C -> D} (ya desplazado por el radio). Devuelve {@code t} (sobre el
     * movimiento) y {@code u} (sobre la arista).
     *
     * <p>Traducción literal de {@code c.java} (línea 801), donde:
     * <pre>
     *   s = cross(C-P, delta) / cross(delta, e)   // parámetro sobre la arista (j4)
     *   t = cross(C-P, e)     / cross(delta, e)   // parámetro sobre el movimiento (j5)
     *   e = D - C
     * </pre>
     * El último parámetro {@code r2} es el radio al cuadrado y se usa sólo en el caso
     * "profundo" ({@code s < 0}): si el punto de arranque de la bola proyecta sobre la arista
     * y queda a menos de {@code r}, se devuelve ese punto con {@code deep = true}.
     */
    public static boolean segmentSegment(float ax, float ay, float bx, float by,
                                         float cx, float cy, float dx, float dy,
                                         float r2, Hit out) {
        float ex = dx - cx;             // e = D - C
        float ey = dy - cy;
        float mx = bx - ax;             // delta = P1 - P0
        float my = by - ay;

        // denom = cross(delta, e)
        float denom = mx * ey - my * ex;
        if (denom == 0f) {
            return false;
        }

        float cpx = cx - ax;            // C - P0
        float cpy = cy - ay;

        // s = cross(C - P0, delta) / denom   (j4: parámetro sobre la arista)
        float s = (cpx * my - cpy * mx) / denom;
        if (s < 0f || s > 1f) {
            if (s >= 0f) {
                return false;
            }
            // Caso profundo: P0 proyectado sobre la arista, dentro del radio.
            float proj = cpx * ex + cpy * ey;      // dot(P0 - C, e)
            if (proj <= 0f) {
                return false;
            }
            float len2 = ex * ex + ey * ey;
            if (proj >= len2) {
                return false;
            }
            float k = proj / len2;
            float px = cx + ex * k;
            float py = cy + ey * k;
            float ddx = px - ax;
            float ddy = py - ay;
            if (ddx * ddx + ddy * ddy > r2) {
                return false;
            }
            out.t = 0f;
            out.u = k;
            out.x = px;
            out.y = py;
            out.deep = true;
            return true;
        }

        // t = cross(C - P0, e) / denom   (j5: parámetro sobre el movimiento)
        float t = (cpx * ey - cpy * ex) / denom;
        if (t < 0f || t > 1f) {
            return false;
        }

        out.t = t;
        out.u = s;
        out.x = ax + mx * t;
        out.y = ay + my * t;
        out.deep = false;
        return true;
    }

    /** Punto del segmento {@code A-B} más cercano a {@code P} (para el test estático). */
    public static void closestPoint(float px, float py, float ax, float ay, float bx, float by,
                                    Hit out) {
        float ex = bx - ax;
        float ey = by - ay;
        float len2 = ex * ex + ey * ey;
        float u = len2 > 1e-8f ? ((px - ax) * ex + (py - ay) * ey) / len2 : 0f;
        u = Math.max(0f, Math.min(1f, u));
        out.u = u;
        out.x = ax + ex * u;
        out.y = ay + ey * u;
        out.t = 0f;
    }
}
