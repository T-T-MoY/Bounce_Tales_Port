package com.bouncetales.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.bouncetales.world.DecorationList;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Level;
import com.bouncetales.world.PolygonShape;
import com.bouncetales.world.WaterData;
import com.bouncetales.world.entity.Enemy;
import com.bouncetales.world.entity.Tube;

import java.util.Comparator;

/**
 * Dibuja un nivel completo replicando el recorrido de {@code k.a(k raiz, ...)}:
 * <ol>
 *   <li>recorrido en profundidad de las entidades visibles,</li>
 *   <li>orden descendente por {@code depth} = {@code (m &amp; 31) - 16},</li>
 *   <li>dibujo en ese orden (mayor depth = mas al fondo).</li>
 * </ol>
 *
 * <p>Las animaciones se seleccionan con el reloj de render o el tiempo de cada entidad.
 */
public final class LevelRenderer implements Disposable {

    private static final int BALL_RADIUS = 20;          // c.f3a[0]
    private static final int BALL_COLOR = 15539236;     // c.u
    private static final int COLLECTIBLE_SPRITE = 208;  // a.java
    private static final int ENEMY_SPRITE = 467;        // m.java, enemigo tipo 0
    private static final int BLINK_ENEMY_SPRITE = 504;
    private static final float PATROL_FRAME_RATE = 20f;
    private static final float BLINK_FRAME_INTERVAL = 0.15f;

    /** Tubo lanzador: sprite y duraciones de las tres etapas ({@code l.f113a}). */
    private static final int TUBE_SPRITE = 48;
    private static final float[] TUBE_STAGE_MS = {500f, 100f, 300f};
    /** {@code c.f9a}: 32 rotaciones del sprite de piedra (marble), de 11,25 grados cada una. */
    private static final int[] STONE_FRAMES = {
            211, 212, 223, 234, 237, 238, 239, 240, 241, 242, 213, 214,
            215, 216, 217, 218, 219, 220, 221, 222, 224, 225, 226, 227,
            228, 229, 230, 231, 232, 233, 235, 236
    };

    /** {@code c.p}: colores de los seis gajos del balon. */
    private static final int[] BEACH_COLORS = {0xEDEDED, 0x0064BC, 0xEDEDED, 0xE11900, 0xEDEDED, 0xE8DF05};

    /** Centros de las caras de reposo ({@code c.f8c}) para f7c = 4..8. */
    private static final int[] IDLE_FACES = {458, 460, 454, 452, 456};

    private static final int FACE_DEAD = 20;     // p.a(width, height, 20)
    private static final int FACE_BLINK = 462;   // c.i31 = 462 cuando f7c == 2
    private static final int FACE_WAKE = 447;    // c.i31 = 447 cuando f7c == 3
    private static final float FACE_ANIM_TIME = 0.6f;

    private static final int MODE_NONE = 0;
    private static final int MODE_SHAPES = 1;
    private static final int MODE_SPRITES = 2;

    private final Comparator<Entity> depthDesc =
            (a, b) -> Integer.compare(renderDepth(b), renderDepth(a));

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final SpriteDrawer sprites;
    private final ScreenTransform transform;

    private final Array<Entity> visible = new Array<>();
    private final float[] tri = new float[6];

    private int mode = MODE_NONE;
    private boolean drawBall = true;
    private float time;

    /** Tiempo jugado del nivel ({@code n.f127a}), en segundos; se detiene al morir. */
    private float levelTime;

    /**
     * Mallas del tubo ({@code n.f198a}): 12 poligonos del banco {@code bu.json} en orden de
     * fichero. Los grupos A/B/C son fotogramas clave y el ultimo grupo D se usa como copia de
     * trabajo al principio (su geometria coincide con A).
     */
    private final Array<PolygonShape> tubeMeshes = new Array<>();
    private final float[][] tubeVertices = new float[3][];

    /** Cuando la fisica esta activa, la bola se dibuja en la posicion simulada. */
    private boolean ballOverride;
    private float ballX;
    private float ballY;
    private boolean ballInWater;

    /** Forma, giro y cara actuales de la bola ({@code c.f34d}, {@code c.n}, {@code c.f7c}). */
    private int ballType;
    private float ballAngle;
    private int ballFaceState;
    private float ballFaceTimer;
    private boolean ballDying;

    public LevelRenderer(SpriteBatch batch, ShapeRenderer shapes, SpriteDrawer sprites,
                         ScreenTransform transform) {
        this.batch = batch;
        this.shapes = shapes;
        this.sprites = sprites;
        this.transform = transform;
    }

    public void setDrawBall(boolean drawBall) {
        this.drawBall = drawBall;
    }

    /** Fija la posicion de mundo donde dibujar la bola (la que calcula {@code BallBody}). */
    public void setBallPosition(float x, float y) {
        this.ballOverride = true;
        this.ballX = x;
        this.ballY = y;
    }

    public void setBallInWater(boolean inWater) {
        this.ballInWater = inWater;
    }

    /**
     * Estado visual de la bola. Los parametros replican las ramas de
     * {@code c.a(Graphics, ...)}: forma ({@code f34d}), giro ({@code n}) y cara de reposo
     * ({@code f7c}). Durante la muerte ({@code g.a[0] == 3}) se superpone la cara 20.
     */
    public void setBallState(int type, float angle, int faceState, float faceTimer,
                             boolean dying) {
        this.ballType = type;
        this.ballAngle = angle;
        this.ballFaceState = faceState;
        this.ballFaceTimer = faceTimer;
        this.ballDying = dying;
    }

    public void clearBallPosition() {
        this.ballOverride = false;
    }

    public int visibleCount() {
        return visible.size;
    }

    public void addTime(float dt) {
        time += dt;
    }

    /** Tiempo jugado del nivel en segundos ({@code n.f127a / 1000}). */
    public void setLevelTime(float seconds) {
        this.levelTime = seconds;
    }

    /** Banco de mallas del tubo ({@code n.f198a}); se carga de {@code bu.json}. */
    public void setTubeMeshes(Array<PolygonShape> meshes) {
        tubeMeshes.clear();
        tubeMeshes.addAll(meshes);
    }

    public void render(Level level) {
        visible.clear();
        if (level.root != null) {
            collect(level.root);
        }
        visible.sort(depthDesc);

        mode = MODE_NONE;
        for (Entity e : visible) {
            switch (e.tag) {
                case Entity.TAG_POLYGON:
                    drawPolygon(e);
                    break;
                case Entity.TAG_DECORATION:
                    drawDecorations(e);
                    break;
                case Entity.TAG_WATER:
                    drawWater(e);
                    break;
                case Entity.TAG_PAD:
                    drawPad(e);
                    break;
                case Entity.TAG_COLLECTIBLE:
                    drawSingle(e, COLLECTIBLE_SPRITE);
                    break;
                case Entity.TAG_ENEMY:
                    drawEnemy(e);
                    break;
                case Entity.TAG_TUBE:
                    drawTube(e);
                    break;
                case Entity.TAG_BALL:
                    if (drawBall) {
                        drawPlayerBall(e);
                    }
                    break;
                case Entity.TAG_TRIGGER:
                case Entity.TAG_OBJECT:
                case Entity.TAG_BALL_DECORATION:
                default:
                    break;
            }
        }
        endMode();
    }

    /**
     * Recorrido en profundidad. Una entidad oculta o desenlazada no se dibuja (ni sus hijos);
     * {@code skip_branch} (bit 128 de {@code m}) solo oculta la entidad, como
     * {@code k.a(k, Graphics, ...)} linea 706.
     */
    private void collect(Entity e) {
        if (e.hidden || e.detached) {
            return;
        }
        if (!e.skipBranch) {
            visible.add(e);
        }
        for (Entity child : e.children) {
            collect(child);
        }
    }

    // ---------------------------------------------------------------- entidades

    private void drawPolygon(Entity e) {
        PolygonShape p = e.polygon;
        if (p == null || p.triangles.length < 3) {
            return;
        }
        useShapes();
        shapes.setColor(argbToFloat(p.color));

        int vertexCount = p.vertices.length / 2;
        for (int i = 0; i + 2 < p.triangles.length; i += 3) {
            boolean ok = true;
            for (int k = 0; k < 3; k++) {
                int index = p.triangles[i + k];
                if (index < 0 || index >= vertexCount) {
                    ok = false;
                    break;
                }
                float lx = p.vertices[index * 2];
                float ly = p.vertices[index * 2 + 1];
                float wx = e.world.m00 * lx + e.world.m01 * ly + e.world.m02;
                float wy = e.world.m10 * lx + e.world.m11 * ly + e.world.m12;
                tri[k * 2] = transform.screenX(wx);
                tri[k * 2 + 1] = transform.glY(transform.screenY(wy));
            }
            if (ok) {
                shapes.triangle(tri[0], tri[1], tri[2], tri[3], tri[4], tri[5]);
            }
        }
    }

    private void drawDecorations(Entity e) {
        DecorationList d = e.decorations;
        if (d == null || d.hidden || d.size() == 0) {
            return;
        }
        useSprites();
        for (int i = 0; i < d.size(); i++) {
            if (d.spriteIds[i] < 0) {
                continue;
            }
            float wx = e.world.m00 * d.x[i] + e.world.m01 * d.y[i] + e.world.m02;
            float wy = e.world.m10 * d.x[i] + e.world.m11 * d.y[i] + e.world.m12;
            int animationId = d.animationId(i);
            float sx = transform.screenX(wx);
            float sy = transform.screenY(wy);
            if (animationId < 0) {
                sprites.draw(batch, d.spriteIds[i], sx, sy);
            } else {
                batch.setColor(1f, 1f, 1f, d.opacity(i));
                sprites.drawFrame(batch, animationId,
                        d.animationFrame(i, sprites.frameCount(animationId)), sx, sy);
                batch.setColor(Color.WHITE);
            }
        }
    }

    private void drawPad(Entity e) {
        if (e.padSprite < 0) {
            return;
        }
        useSprites();
        float sx = transform.screenX(e.worldX());
        float sy = transform.screenY(e.worldY());
        int frame = e.pad != null
                ? e.pad.animationFrame(sprites.frameCount(e.padSprite)) : 0;
        sprites.drawFrame(batch, e.padSprite, frame, sx, sy);
    }

    /**
     * Tubo lanzador ({@code l.a(Graphics, ...)}): tres mallas poligonales (negra, gris y
     * amarilla) que se interpolan entre los fotogramas clave A -&gt; B -&gt; C -&gt; A segun el
     * tiempo de carga ({@code l.f113a = {500, 100, 300}} ms) y, encima, el sprite 48.
     */
    private void drawTube(Entity e) {
        float elapsed = e.tube != null && e.tube.isCharging()
                ? e.tube.chargeProgress() * Tube.HOLD_TIME * 1000f : 0f;
        int stage = 0;
        float stageStart = 0f;
        if (elapsed >= TUBE_STAGE_MS[0]) {
            stage = 1;
            stageStart = TUBE_STAGE_MS[0];
        }
        if (elapsed >= TUBE_STAGE_MS[0] + TUBE_STAGE_MS[1]) {
            stage = 2;
            stageStart = TUBE_STAGE_MS[0] + TUBE_STAGE_MS[1];
        }
        float f = MathUtils.clamp((elapsed - stageStart) / TUBE_STAGE_MS[stage], 0f, 1f);
        int from = stage * 3;
        int to = ((stage + 1) % 3) * 3;

        useShapes();
        for (int part = 0; part < 3; part++) {
            drawTubePart(e, part, from + part, to + part, f);
        }

        useSprites();
        sprites.draw(batch, TUBE_SPRITE,
                transform.screenX(e.worldX()), transform.screenY(e.worldY()));
    }

    /** Interpola un poligono del tubo entre dos fotogramas clave y lo dibuja. */
    private void drawTubePart(Entity e, int part, int fromIndex, int toIndex, float f) {
        if (fromIndex >= tubeMeshes.size || toIndex >= tubeMeshes.size) {
            return;
        }
        PolygonShape a = tubeMeshes.get(fromIndex);
        PolygonShape b = tubeMeshes.get(toIndex);
        int vertexCount = Math.min(a.vertices.length, b.vertices.length) / 2;
        float[] scratch = tubeVertices[part];
        if (scratch == null || scratch.length < vertexCount * 2) {
            scratch = new float[vertexCount * 2];
            tubeVertices[part] = scratch;
        }
        for (int i = 0; i < vertexCount * 2; i++) {
            scratch[i] = a.vertices[i] * (1f - f) + b.vertices[i] * f;
        }

        shapes.setColor(argbToFloat(a.color));
        for (int i = 0; i + 2 < a.triangles.length; i += 3) {
            boolean ok = true;
            for (int k = 0; k < 3; k++) {
                int index = a.triangles[i + k];
                if (index < 0 || index >= vertexCount) {
                    ok = false;
                    break;
                }
                float lx = scratch[index * 2];
                float ly = scratch[index * 2 + 1];
                float wx = e.world.m00 * lx + e.world.m01 * ly + e.world.m02;
                float wy = e.world.m10 * lx + e.world.m11 * ly + e.world.m12;
                tri[k * 2] = transform.screenX(wx);
                tri[k * 2 + 1] = transform.glY(transform.screenY(wy));
            }
            if (ok) {
                shapes.triangle(tri[0], tri[1], tri[2], tri[3], tri[4], tri[5]);
            }
        }
    }

    private void drawWater(Entity e) {
        WaterData data = e.waterData;
        if (data == null || !data.visible()) {
            return;
        }

        int samples = e.water == null ? 2 : Math.max(2, e.water.sampleCount());
        float width = data.x1 - data.x0;
        if (width <= 0f || data.surface <= data.bottom) {
            return;
        }

        useShapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setColor(0.07f, 0.07f, 0.93f, 68f / 255f);

        float previousX = data.x0;
        float previousSurface = surfaceY(e, data, 0);
        for (int i = 1; i < samples; i++) {
            float localX = data.x0 + width * i / (samples - 1f);
            float surface = surfaceY(e, data, i);
            drawWaterSegment(e, previousX, previousSurface, localX, surface, data.bottom);
            previousX = localX;
            previousSurface = surface;
        }
    }

    private float surfaceY(Entity e, WaterData data, int sample) {
        float wave = e.water != null && e.water.sampleCount() > 0 && !data.solid
                ? e.water.waveHeight(Math.min(sample, e.water.sampleCount() - 1)) : 0f;
        return data.surface + wave;
    }

    private void drawWaterSegment(Entity e, float x0, float surface0,
                                  float x1, float surface1, float bottom) {
        float[] points = tri;
        waterPoint(e, x0, surface0, points, 0);
        waterPoint(e, x0, bottom, points, 2);
        waterPoint(e, x1, bottom, points, 4);
        shapes.triangle(points[0], points[1], points[2], points[3], points[4], points[5]);

        waterPoint(e, x0, surface0, points, 0);
        waterPoint(e, x1, bottom, points, 2);
        waterPoint(e, x1, surface1, points, 4);
        shapes.triangle(points[0], points[1], points[2], points[3], points[4], points[5]);

        float dx = points[4] - points[0];
        float dy = points[5] - points[1];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 1e-4f) {
            float halfWidth = 0.75f;
            float nx = -dy / length * halfWidth;
            float ny = dx / length * halfWidth;
            shapes.setColor(0.45f, 0.72f, 1f, 0.7f);
            shapes.triangle(points[0] + nx, points[1] + ny,
                    points[0] - nx, points[1] - ny, points[4] - nx, points[5] - ny);
            shapes.triangle(points[0] + nx, points[1] + ny,
                    points[4] - nx, points[5] - ny, points[4] + nx, points[5] + ny);
            shapes.setColor(0.07f, 0.07f, 0.93f, 68f / 255f);
        }
    }

    private void waterPoint(Entity e, float localX, float localY, float[] points, int offset) {
        float wx = e.world.m00 * localX + e.world.m01 * localY + e.world.m02;
        float wy = e.world.m10 * localX + e.world.m11 * localY + e.world.m12;
        points[offset] = transform.screenX(wx);
        points[offset + 1] = transform.glY(transform.screenY(wy));
    }

    private void drawSingle(Entity e, int spriteId) {
        useSprites();
        sprites.draw(batch, spriteId,
                transform.screenX(e.worldX()), transform.screenY(e.worldY()));
    }

    private void drawEnemy(Entity e) {
        useSprites();
        float sx = transform.screenX(e.worldX());
        float sy = transform.screenY(e.worldY());
        if (e.enemy == null) {
            sprites.draw(batch, ENEMY_SPRITE, sx, sy);
            return;
        }

        if (e.enemy.kind == Enemy.KIND_BLINK) {
            drawBlinkEnemy(e.enemy, sx, sy);
            return;
        }
        int frame = MathUtils.floor(time * PATROL_FRAME_RATE);
        sprites.drawFrame(batch, ENEMY_SPRITE, frame, sx, sy);
    }

    private void drawBlinkEnemy(Enemy enemy, float sx, float sy) {
        boolean transitioning = enemy.isTransitioning();
        int frame = !transitioning
                ? MathUtils.floor(levelTime / BLINK_FRAME_INTERVAL)
                        % sprites.frameCount(BLINK_ENEMY_SPRITE)
                : 0;
        sprites.drawFrame(batch, BLINK_ENEMY_SPRITE, frame, sx, sy);

        int visibleHeight = enemy.revealHeight();
        if (visibleHeight > 0) {
            int sprite = enemy.facingLeft() ? 189 : 194;
            sprites.drawTopPart(batch, sprite, sx, sy + 61f - visibleHeight, visibleHeight);
        }
    }

    /**
     * Bola del jugador. Replica las tres ramas de {@code c.a(Graphics, ...)}:
     * <ul>
     *   <li>0 normal: circulo negro, circulo rojo y las capas 17 (cara) y 28 (brillo).</li>
     *   <li>1 piedra: 32 rotaciones de las grietas ({@code f9a}), una por cada 11,25 grados.</li>
     *   <li>2 balon: seis gajos de color girando, nucleo desplazado y sprite 5.</li>
     * </ul>
     * Despues se superpone la cara de reposo ({@code f7c}) o la cara de muerte.
     */
    private void drawPlayerBall(Entity e) {
        float wx = ballOverride ? ballX : e.worldX();
        float wy = ballOverride ? ballY : e.worldY();
        float sx = transform.screenX(wx);
        float sy = transform.screenY(wy);

        if (ballType == 1) {
            drawStoneBall(sx, sy);
        } else if (ballType == 2) {
            drawBeachBall(sx, sy);
        } else {
            drawNormalBall(sx, sy);
        }
        drawBallFace(sx, sy);
    }

    private void drawNormalBall(float sx, float sy) {
        float cy = transform.glY(sy);
        int radius = (int) (BALL_RADIUS * transform.scale) + 1;

        useShapes();
        shapes.setColor(0f, 0f, 0f, 1f);
        shapes.circle(sx, cy, radius + 2f, 24);
        shapes.setColor(argbToFloat(BALL_COLOR));
        shapes.circle(sx, cy, radius, 24);

        useSprites();
        sprites.draw(batch, 17, sx, sy);
        sprites.draw(batch, 28, sx, sy);
    }

    private void drawStoneBall(float sx, float sy) {
        float degrees = (float) Math.toDegrees(ballAngle);
        degrees %= 360f;
        if (degrees < 0f) {
            degrees += 360f;
        }
        int frame = 31 - (int) (degrees / 11.25f);
        frame = Math.max(0, Math.min(STONE_FRAMES.length - 1, frame));
        useSprites();
        sprites.draw(batch, STONE_FRAMES[frame], sx, sy);
    }

    private void drawBeachBall(float sx, float sy) {
        float cy = transform.glY(sy);
        float radius = BALL_RADIUS * transform.scale + 1f;
        int degrees = (int) Math.toDegrees(ballAngle);

        useShapes();
        shapes.setColor(0f, 0f, 0f, 1f);
        shapes.circle(sx, cy, radius + 2f, 32);
        for (int i = 0; i < BEACH_COLORS.length; i++) {
            shapes.setColor(argbToFloat(BEACH_COLORS[i]));
            shapes.arc(sx, cy, radius, 60f * i - degrees, 60f, 8);
        }

        // Nucleo desplazado arriba-izquierda (i6/i7 en c.java).
        float inner = radius * 0.25f;
        shapes.setColor(argbToFloat(BEACH_COLORS[0]));
        shapes.circle(sx - inner, cy + inner, inner, 16);

        useSprites();
        sprites.draw(batch, 5, sx, sy);
    }

    private void drawBallFace(float sx, float sy) {
        if (ballDying || ballFaceState == 1) {
            useSprites();
            sprites.draw(batch, FACE_DEAD, sx, sy);
            return;
        }
        if (ballFaceState == 2 || ballFaceState == 3) {
            int animation = ballFaceState == 3 ? FACE_WAKE : FACE_BLINK;
            int frames = sprites.frameCount(animation);
            int elapsed = (int) (Math.max(0f, ballFaceTimer) * frames / FACE_ANIM_TIME);
            int frame = (frames - 1) - Math.max(0, Math.min(frames - 1, elapsed));
            useSprites();
            sprites.drawFrame(batch, animation, frame, sx, sy);
        } else if (ballFaceState >= 4 && ballFaceState <= 8) {
            useSprites();
            sprites.draw(batch, IDLE_FACES[ballFaceState - 4], sx, sy);
        }
    }

    // ---------------------------------------------------------------- estado de dibujo

    private void useShapes() {
        if (mode == MODE_SPRITES) {
            batch.end();
            mode = MODE_NONE;
        }
        if (mode == MODE_NONE) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            mode = MODE_SHAPES;
        }
    }

    private void useSprites() {
        if (mode == MODE_SHAPES) {
            shapes.end();
            mode = MODE_NONE;
        }
        if (mode == MODE_NONE) {
            batch.begin();
            mode = MODE_SPRITES;
        }
    }

    private void endMode() {
        if (mode == MODE_SHAPES) {
            shapes.end();
        } else if (mode == MODE_SPRITES) {
            batch.end();
        }
        mode = MODE_NONE;
    }

    private int renderDepth(Entity e) {
        return e.tag == Entity.TAG_BALL && ballInWater ? 8 : e.depth;
    }

    /** ARGB 0xAARRGGBB -&gt; Color de libGDX. J2ME no usa alfa por color: todo es opaco. */
    private static Color argbToFloat(int argb) {
        return new Color(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                1f);
    }

    @Override
    public void dispose() {
        // batch/shapes/sprites los libera GameScreen (son compartidos).
    }
}
