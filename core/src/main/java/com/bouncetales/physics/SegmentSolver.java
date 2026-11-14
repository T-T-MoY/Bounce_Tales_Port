package com.bouncetales.physics;

/**
 * Resolucion de un contacto, traduccion de {@code c.o()} (linea 671).
 *
 * <p>El original guarda el contacto sobre el borde <b>desplazado por el radio</b>
 * (frontera de Minkowski): {@code Contact.point} es el punto donde debe quedar el
 * <em>centro</em> de la bola. Con esa convencion, {@code o()} hace:
 *
 * <ol>
 *   <li>Calcula {@code d = pos - puntoContacto}.</li>
 *   <li>Quita la componente normal y ademas un 10 % extra:
 *       {@code tangencial = d - n*(d.n)*(1+friccion)}.</li>
 *   <li>Nueva posicion {@code = puntoContacto + tangencial + n*0.01}.</li>
 *   <li>Velocidad: {@code v' = v - n*(v.n)*(1+friccion)}. Es decir, la componente normal
 *       se invierte solo un 10 % (no un 60 %).</li>
 *   <li>Resistencia de rodadura: el impulso maximo es
 *       {@code J = rebote * masa * 400 * n.y} (el 400 viene de {@code -n.gravedad}).
 *       Si el impulso necesario para cancelar {@code v'} en un frame
 *       ({@code |v'|*masa/dt}) es menor que {@code J}, se cancela; si no, se aplica
 *       {@code J} en sentido contrario a {@code v'}. Se guarda en el acumulador de fuerzas,
 *       de modo que actua en el frame siguiente.</li>
 *   <li>Marca suelo, normal de contacto y reinicia el temporizador {@code f35p}.</li>
 * </ol>
 *
 * <p>En {@code c.o()} el termino {@code f9/f10} (que seria un resto de movimiento) vale 0
 * para todas las entidades del juego: se calcula como la diferencia entre la matriz local y
 * la matriz de mundo, que son la misma porque la raiz del nivel no tiene transformacion.
 * Por eso aqui no aparece.
 */
public final class SegmentSolver {

    private SegmentSolver() {
    }

    /**
     * Resuelve un contacto sobre la bola.
     *
     * @param ball cuerpo a corregir
     * @param c    contacto con normal unitaria y punto sobre el borde desplazado
     * @param dt   duracion del paso (s); se usa para comparar el impulso de cancelacion
     */
    public static void resolve(BallBody ball, Contact c, float dt) {
        float nx = c.normalX;
        float ny = c.normalY;
        float friction = ball.friction();

        // --- posicion (c.o(), lineas 710-722) ---
        float dx = ball.x - c.pointX;
        float dy = ball.y - c.pointY;
        float dn = dx * nx + dy * ny;
        float tx = dx - dn * nx * (1f + friction);
        float ty = dy - dn * ny * (1f + friction);
        float newX = c.pointX + tx + nx * 0.01f;
        float newY = c.pointY + ty + ny * 0.01f;

        // --- velocidad (c.o(), lineas 723-730) ---
        float vn = ball.vx * nx + ball.vy * ny;
        float nvx = ball.vx - vn * nx * (1f + friction);
        float nvy = ball.vy - vn * ny * (1f + friction);
        ball.vx = nvx;
        ball.vy = nvy;

        // --- resistencia de rodadura (c.o(), lineas 736-748) ---
        // f39 = b * masa * (-(n . gravedad)) ; gravedad = (0, -400)
        float impulse = ball.bounce() * ball.mass() * 400f * ny;
        float massOverDt = ball.mass() / dt;
        float cancelX = nvx * massOverDt;
        float cancelY = nvy * massOverDt;
        if (cancelX * cancelX + cancelY * cancelY < impulse * impulse) {
            // El impulso de cancelacion es menor: se frena del todo.
            ball.forceX -= cancelX;
            ball.forceY -= cancelY;
        } else {
            float mag = (float) Math.sqrt(nvx * nvx + nvy * nvy);
            if (mag > 1e-6f) {
                ball.forceX -= nvx / mag * impulse;
                ball.forceY -= nvy / mag * impulse;
            }
        }

        // --- balanceo visual (c.o(), lineas 749-750) ---
        ball.visualVx += (nvx - ball.visualVx) * BallBody.VISUAL_SMOOTHING;
        ball.visualVy += (nvy - ball.visualVy) * BallBody.VISUAL_SMOOTHING;

        // --- estado de contacto (c.o(), lineas 751-758) ---
        ball.contactTimer = 0f;
        ball.onGround = true;
        ball.normalX = nx;
        ball.normalY = ny;

        // El original reescribe la posicion anterior al punto de contacto (this.b = f18).
        ball.prevX = c.pointX;
        ball.prevY = c.pointY;
        ball.x = newX;
        ball.y = newY;
    }
}
