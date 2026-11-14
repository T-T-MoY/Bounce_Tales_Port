package com.bouncetales.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.bouncetales.world.DecorationList;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Level;
import com.bouncetales.world.PolygonShape;
import com.bouncetales.world.entity.Enemy;

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
    private static final float BEAM_HALF_WIDTH = 1.5f;

    private static final int MODE_NONE = 0;
    private static final int MODE_SHAPES = 1;
    private static final int MODE_SPRITES = 2;

    private static final Comparator<Entity> DEPTH_DESC =
            (a, b) -> Integer.compare(b.depth, a.depth);

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final SpriteDrawer sprites;
    private final ScreenTransform transform;

    private final Array<Entity> visible = new Array<>();
    private final float[] tri = new float[6];

    private int mode = MODE_NONE;
    private boolean drawBall = true;
    private float time;

    /** Cuando la fisica esta activa, la bola se dibuja en la posicion simulada. */
    private boolean ballOverride;
    private float ballX;
    private float ballY;

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

    public void clearBallPosition() {
        this.ballOverride = false;
    }

    public int visibleCount() {
        return visible.size;
    }

    public void addTime(float dt) {
        time += dt;
    }

    public void render(Level level) {
        visible.clear();
        if (level.root != null) {
            collect(level.root);
        }
        visible.sort(DEPTH_DESC);

        mode = MODE_NONE;
        for (Entity e : visible) {
            switch (e.tag) {
                case Entity.TAG_POLYGON:
                    drawPolygon(e);
                    break;
                case Entity.TAG_DECORATION:
                    drawDecorations(e);
                    break;
                case Entity.TAG_BEAM:
                    drawBeam(e);
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
                case Entity.TAG_BALL:
                    if (drawBall) {
                        drawPlayerBall(e);
                    }
                    break;
                case Entity.TAG_TRIGGER:
                case Entity.TAG_OBJECT:
                case Entity.TAG_TUBE:
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
        if (d == null || d.size() == 0) {
            return;
        }
        useSprites();
        for (int i = 0; i < d.size(); i++) {
            if (d.spriteIds[i] < 0) {
                continue;
            }
            float wx = e.world.m00 * d.x[i] + e.world.m01 * d.y[i] + e.world.m02;
            float wy = e.world.m10 * d.x[i] + e.world.m11 * d.y[i] + e.world.m12;
            sprites.draw(batch, d.spriteIds[i], transform.screenX(wx), transform.screenY(wy));
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

    private void drawBeam(Entity e) {
        if (e.beamData == null) {
            return;
        }

        float x0 = e.world.m00 * e.beamData.x0 + e.world.m01 * e.beamData.y0 + e.world.m02;
        float y0 = e.world.m10 * e.beamData.x0 + e.world.m11 * e.beamData.y0 + e.world.m12;
        float x1 = e.world.m00 * e.beamData.x1 + e.world.m01 * e.beamData.y1 + e.world.m02;
        float y1 = e.world.m10 * e.beamData.x1 + e.world.m11 * e.beamData.y1 + e.world.m12;

        float sx0 = transform.screenX(x0);
        float sy0 = transform.glY(transform.screenY(y0));
        float sx1 = transform.screenX(x1);
        float sy1 = transform.glY(transform.screenY(y1));
        float dx = sx1 - sx0;
        float dy = sy1 - sy0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-4f) {
            return;
        }

        float offsetX = -dy / length * BEAM_HALF_WIDTH;
        float offsetY = dx / length * BEAM_HALF_WIDTH;
        useShapes();
        shapes.setColor(argbToFloat(e.beamData.color));
        shapes.triangle(sx0 + offsetX, sy0 + offsetY,
                sx0 - offsetX, sy0 - offsetY, sx1 - offsetX, sy1 - offsetY);
        shapes.triangle(sx0 + offsetX, sy0 + offsetY,
                sx1 - offsetX, sy1 - offsetY, sx1 + offsetX, sy1 + offsetY);
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
                ? MathUtils.floor(time / BLINK_FRAME_INTERVAL) % sprites.frameCount(BLINK_ENEMY_SPRITE)
                : 0;
        sprites.drawFrame(batch, BLINK_ENEMY_SPRITE, frame, sx, sy);

        int visibleHeight = enemy.revealHeight();
        if (visibleHeight > 0) {
            int sprite = enemy.facingLeft() ? 189 : 194;
            sprites.drawTopPart(batch, sprite, sx, sy + 61f - visibleHeight, visibleHeight);
        }
    }

    /**
     * Bola del jugador, tipo 0: circulo negro (contorno), circulo de color y las capas
     * 17 (cara) y 28 (brillo) de {@code c.a(Graphics, ...)}.
     */
    private void drawPlayerBall(Entity e) {
        float wx = ballOverride ? ballX : e.worldX();
        float wy = ballOverride ? ballY : e.worldY();
        float sx = transform.screenX(wx);
        float sy = transform.screenY(wy);
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
