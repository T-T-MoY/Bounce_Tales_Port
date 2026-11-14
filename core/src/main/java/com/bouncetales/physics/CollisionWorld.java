package com.bouncetales.physics;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import com.bouncetales.render.ScreenTransform;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Level;
import com.bouncetales.world.PolygonShape;

/**
 * Mundo de colision de la bola contra los poligonos del nivel.
 *
 * <p>Traduce {@code c.a(k)} (linea 406) y {@code c.a(q, ...)} (linea 630):
 * <ol>
 *   <li>Barrido del centro de la bola desde {@link BallBody#prevX}/{@code prevY} hasta
 *       {@link BallBody#x}/{@code y}.</li>
 *   <li>Por cada arista, si la bola se mueve <b>contra</b> su normal exterior
 *       ({@code delta . n < 0}), se prueba el segmento de movimiento contra la arista
 *       desplazada por el radio (frontera de Minkowski) y los dos extremos como circulos
 *       de radio {@code r}.</li>
 *   <li>Se elige el contacto mas cercano al punto de partida; los contactos "profundos"
 *       (la bola ya estaba penetrando) tienen prioridad porque su distancia se niega.</li>
 *   <li>Se resuelve con {@link SegmentSolver#resolve} y se repite hasta 8 veces, igual que
 *       el bucle {@code i < 10} del original.</li>
 * </ol>
 *
 * <p>Los poligonos se precalculan en espacio de mundo. La convencion de winding del original
 * es <b>horaria</b> (en un mundo con Y hacia arriba): la normal exterior de la arista
 * {@code A -> B} es {@code (A.y - B.y, B.x - A.x)}. Los poligonos con winding contrario
 * simplemente no colisionan desde fuera, que es lo que hace el original.
 */
public final class CollisionWorld {

    /** Radio maximo de pasadas de resolucion ({@code i < 10} en el original: 8 pasadas). */
    public static final int MAX_PASSES = 8;

    /** Arista de un poligono en espacio de mundo. */
    public static final class Collider {
        public final Entity entity;
        public final float[] xs;
        public final float[] ys;
        public final float minX;
        public final float minY;
        public final float maxX;
        public final float maxY;

        /**
         * Factor de escala del radio. En el original el test se hace en espacio local con
         * radio 20; para poligonos escalados (solo 2 en {@code br}) se usa
         * {@code sqrt(|det|)} como equivalente en mundo.
         */
        public final float radiusScale;

        Collider(Entity entity, float[] xs, float[] ys,
                 float minX, float minY, float maxX, float maxY, float radiusScale) {
            this.entity = entity;
            this.xs = xs;
            this.ys = ys;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.radiusScale = radiusScale;
        }
    }

    private final Array<Collider> colliders = new Array<>();
    private final Level level;

    private final Contact best = new Contact();
    private final SegmentCollider.Hit hit = new SegmentCollider.Hit();
    private boolean found;
    private float bestDistance;

    public CollisionWorld(Level level) {
        this.level = level;
        rebuild();
    }

    /**
     * Recalcula la geometria de colision. Hace falta cuando un trigger mueve, reenlaza o
     * elimina una entidad ({@code k.h()} + {@code k.l()} en el original).
     */
    public void rebuild() {
        colliders.clear();
        if (level == null) {
            return;
        }
        for (Entity e : level.entities) {
            if (e.tag != Entity.TAG_POLYGON || e.polygon == null || e.solid || e.detached) {
                continue;
            }
            PolygonShape p = e.polygon;
            int n = p.vertices.length / 2;
            if (n < 3) {
                continue;
            }
            float[] xs = new float[n];
            float[] ys = new float[n];
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float lx = p.vertices[i * 2];
                float ly = p.vertices[i * 2 + 1];
                float wx = e.world.m00 * lx + e.world.m01 * ly + e.world.m02;
                float wy = e.world.m10 * lx + e.world.m11 * ly + e.world.m12;
                xs[i] = wx;
                ys[i] = wy;
                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wy < minY) minY = wy;
                if (wy > maxY) maxY = wy;
            }
            float det = Math.abs(e.world.m00 * e.world.m11 - e.world.m01 * e.world.m10);
            float radiusScale = det > 1e-6f ? (float) Math.sqrt(det) : 1f;
            colliders.add(new Collider(e, xs, ys, minX, minY, maxX, maxY, radiusScale));
        }
    }

    /** Limite inferior del nivel en mundo (para detectar la caida al vacio). */
    public float minY() {
        float min = Float.MAX_VALUE;
        for (Collider c : colliders) {
            if (c.minY < min) {
                min = c.minY;
            }
        }
        return min == Float.MAX_VALUE ? -500f : min;
    }

    public int colliderCount() {
        return colliders.size;
    }

    public Collider collider(int index) {
        return colliders.get(index);
    }

    /**
     * Resuelve todas las colisiones del paso actual.
     *
     * @return numero de contactos resueltos (0 si la bola esta libre)
     */
    public int resolve(BallBody ball, float dt) {
        if (level != null && level.geometryDirty) {
            rebuild();
            level.geometryDirty = false;
        }
        int contacts = 0;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (!findContact(ball)) {
                break;
            }
            SegmentSolver.resolve(ball, best, dt);
            contacts++;
        }
        return contacts;
    }

    /**
     * Busca el contacto mas cercano del barrido actual. Deja el resultado en {@link #best}.
     *
     * @return {@code true} si hay contacto
     */
    public boolean findContact(BallBody ball) {
        found = false;
        bestDistance = Float.MAX_VALUE;

        float prevX = ball.prevX;
        float prevY = ball.prevY;
        float curX = ball.x;
        float curY = ball.y;
        float deltaX = curX - prevX;
        float deltaY = curY - prevY;
        float len = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float dirX = len > 1e-6f ? deltaX / len : 0f;
        float dirY = len > 1e-6f ? deltaY / len : 0f;

        float baseRadius = ball.radius();

        // Bounding box del barrido, expandida por el radio.
        float sweepMinX = Math.min(prevX, curX) - baseRadius;
        float sweepMinY = Math.min(prevY, curY) - baseRadius;
        float sweepMaxX = Math.max(prevX, curX) + baseRadius;
        float sweepMaxY = Math.max(prevY, curY) + baseRadius;

        for (int ci = 0; ci < colliders.size; ci++) {
            Collider c = colliders.get(ci);
            if (c.maxX < sweepMinX || c.minX > sweepMaxX
                    || c.maxY < sweepMinY || c.minY > sweepMaxY) {
                continue;
            }

            float radius = baseRadius * c.radiusScale;
            float radius2 = radius * radius;
            int n = c.xs.length;

            // El ultimo vertice repite el primero: aristas = n - 1.
            for (int i = 0; i + 1 < n; i++) {
                float ax = c.xs[i];
                float ay = c.ys[i];
                float bx = c.xs[i + 1];
                float by = c.ys[i + 1];

                // Rechazo rapido por caja de la arista.
                float edgeMinX = Math.min(ax, bx);
                float edgeMaxX = Math.max(ax, bx);
                float edgeMinY = Math.min(ay, by);
                float edgeMaxY = Math.max(ay, by);
                if (edgeMaxX < sweepMinX || edgeMinX > sweepMaxX
                        || edgeMaxY < sweepMinY || edgeMinY > sweepMaxY) {
                    continue;
                }

                float ex = bx - ax;
                float ey = by - ay;
                float edgeLen2 = ex * ex + ey * ey;
                if (edgeLen2 < 1e-6f) {
                    continue;
                }
                float edgeLen = (float) Math.sqrt(edgeLen2);

                // Normal exterior para poligonos horarios: (-ey, ex) = (A.y-B.y, B.x-A.x).
                float rawNx = -ey;
                float rawNy = ex;

                // Solo si la bola se mueve contra la superficie.
                if (deltaX * rawNx + deltaY * rawNy >= 0f) {
                    continue;
                }

                float nx = rawNx / edgeLen;
                float ny = rawNy / edgeLen;

                // Arista desplazada por el radio (frontera de Minkowski).
                float tax = ax + nx * radius;
                float tay = ay + ny * radius;
                float tbx = bx + nx * radius;
                float tby = by + ny * radius;

                if (SegmentCollider.segmentSegment(prevX, prevY, curX, curY,
                        tax, tay, tbx, tby, radius2, hit)) {
                    float px = prevX + deltaX * hit.t;
                    float py = prevY + deltaY * hit.t;
                    consider(px, py, nx, ny, distance2(px, py, prevX, prevY), hit.deep);
                }

                // Extremos como circulos de radio r (c.c(...)).
                if (len > 1e-6f) {
                    if (SegmentCollider.rayCircle(prevX, prevY, dirX, dirY, len,
                            ax, ay, radius, hit)) {
                        float vnx = hit.x - ax;
                        float vny = hit.y - ay;
                        normalizeAndConsider(hit.x, hit.y, vnx, vny,
                                prevX, prevY, hit.deep);
                    }
                    if (SegmentCollider.rayCircle(prevX, prevY, dirX, dirY, len,
                            bx, by, radius, hit)) {
                        float vnx = hit.x - bx;
                        float vny = hit.y - by;
                        normalizeAndConsider(hit.x, hit.y, vnx, vny,
                                prevX, prevY, hit.deep);
                    }
                }
            }
        }

        return found;
    }

    private void normalizeAndConsider(float px, float py, float nx, float ny,
                                      float prevX, float prevY, boolean deep) {
        float len = (float) Math.sqrt(nx * nx + ny * ny);
        if (len < 1e-6f) {
            return;
        }
        consider(px, py, nx / len, ny / len, distance2(px, py, prevX, prevY), deep);
    }

    private void consider(float px, float py, float nx, float ny, float d2, boolean deep) {
        // En c.o() los contactos profundos tienen prioridad: su distancia se niega.
        float key = deep ? -d2 : d2;
        if (!found || key < bestDistance) {
            found = true;
            bestDistance = key;
            best.set(px, py, nx, ny, d2, deep);
        }
    }

    private static float distance2(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** Contacto encontrado por la ultima llamada a {@link #findContact}. */
    public Contact contact() {
        return best;
    }

    /**
     * Dibuja en rojo todas las aristas de colision (modo debug). Debe llamarse con la
     * proyeccion ya configurada y sin ningun {@code batch}/{@code shapes} abierto.
     */
    public void debugDraw(ShapeRenderer shapes, ScreenTransform transform) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 0f, 0f, 1f);
        for (Collider c : colliders) {
            int n = c.xs.length;
            for (int i = 0; i + 1 < n; i++) {
                shapes.line(
                        transform.screenX(c.xs[i]), transform.glY(transform.screenY(c.ys[i])),
                        transform.screenX(c.xs[i + 1]), transform.glY(transform.screenY(c.ys[i + 1])));
            }
        }
        shapes.end();
    }
}
