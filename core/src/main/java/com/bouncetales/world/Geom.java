package com.bouncetales.world;

/**
 * Utilidades geometricas compartidas por las entidades de la fase 3.
 *
 * <p>{@link #segmentHitsRect} reproduce {@code k.a(int,int,int,int,int,int,int,int)}: el
 * original comprueba si el segmento recorrido por la bola en el frame (posicion anterior -&gt;
 * posicion actual) toca una caja, en lugar de mirar solo la posicion final. Asi no se
 * atraviesan pads ni enemigos a gran velocidad.
 */
public final class Geom {

    private Geom() {
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Segmento ({@code px,py})-({@code cx,cy}) contra la caja {@code [minX,maxX] x [minY,maxY]}
     * (recorte de Liang-Barsky). Un segmento de longitud cero se reduce a un test de punto.
     */
    public static boolean segmentHitsRect(float px, float py, float cx, float cy,
                                          float minX, float minY, float maxX, float maxY) {
        if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
            return true;
        }
        if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
            return true;
        }

        float dx = cx - px;
        float dy = cy - py;
        float t0 = 0f;
        float t1 = 1f;

        if (Math.abs(dx) < 1e-9f) {
            if (px < minX || px > maxX) {
                return false;
            }
        } else {
            float a = (minX - px) / dx;
            float b = (maxX - px) / dx;
            if (a > b) {
                float tmp = a;
                a = b;
                b = tmp;
            }
            if (a > t0) {
                t0 = a;
            }
            if (b < t1) {
                t1 = b;
            }
            if (t0 > t1) {
                return false;
            }
        }

        if (Math.abs(dy) < 1e-9f) {
            return py >= minY && py <= maxY;
        }
        float a = (minY - py) / dy;
        float b = (maxY - py) / dy;
        if (a > b) {
            float tmp = a;
            a = b;
            b = tmp;
        }
        if (a > t0) {
            t0 = a;
        }
        if (b < t1) {
            t1 = b;
        }
        return t0 <= t1;
    }
}
