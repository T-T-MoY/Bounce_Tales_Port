package com.bouncetales;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bouncetales.asset.LevelCatalog;
import com.bouncetales.asset.LevelLoader;
import com.bouncetales.asset.ResourceIndex;
import com.bouncetales.asset.SpriteBank;
import com.bouncetales.audio.SoundBank;
import com.bouncetales.physics.BallBody;
import com.bouncetales.physics.CollisionWorld;
import com.bouncetales.render.Background;
import com.bouncetales.render.CameraSpring;
import com.bouncetales.render.LevelRenderer;
import com.bouncetales.render.ScreenTransform;
import com.bouncetales.render.SpriteDrawer;
import com.bouncetales.ui.Hud;
import com.bouncetales.util.ScreenshotCapture;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Level;
import com.bouncetales.world.entity.Collectible;
import com.bouncetales.world.entity.LevelEntities;
import com.bouncetales.world.entity.Pad;
import com.bouncetales.world.script.Trigger;
import com.bouncetales.world.script.TriggerVM;

/**
 * Pantalla de juego: simula la fisica de la bola y coordina entidades, triggers, camara y HUD.
 *
 * <p>Todo el dibujo se hace en el espacio de pantalla logico del original (240x320) y luego
 * el {@link FitViewport} lo escala a la ventana. La fisica trabaja en coordenadas de mundo
 * (Y hacia arriba) con un paso fijo de 1/60 s.
 *
 * <p>Propiedades de JVM utiles:
 * <ul>
 *   <li>{@code -Dbounce.level=bf}: nivel inicial.</li>
 *   <li>{@code -Dbounce.menu=false}: arranca directamente sin pasar por el menu.</li>
 *   <li>{@code -Dbounce.autocomplete=true}: completa el nivel al primer paso (pruebas).</li>
 *   <li>{@code -Dbounce.teleport=x,y}: posicion inicial de prueba para la bola.</li>
 *   <li>{@code -Dbounce.debug=true}: dibuja las aristas de colision en rojo (F1 lo alterna).</li>
 *   <li>{@code -Dbounce.trace=true}: traza posicion/velocidad cada 15 pasos.</li>
 *   <li>{@code -Dbounce.screenshot=... -Dbounce.frames=N}: captura y sale.</li>
 * </ul>
 */
public final class GameScreen implements Screen {

    /** Resolucion logica del original (Nokia 240x320). */
    public static final int SCREEN_WIDTH = 240;
    public static final int SCREEN_HEIGHT = 320;

    /** Paso fijo de simulacion: 1/60 s. */
    public static final float FIXED_STEP = 1f / 60f;
    /** Maximo de pasos por frame para no entrar en "spiral of death". */
    public static final int MAX_STEPS_PER_FRAME = 5;
    /** Igual que el clamp de {@code p.f241a}: 500 ms. */
    public static final float MAX_FRAME_DELTA = 0.25f;

    private static final String SPRITE_BANK = "sprites/sprites.json";
    private static final String RESOURCE_INDEX = "index/a";

    /** Tiempo que se muestra el mensaje de nivel completado antes de avanzar. */
    private static final float VICTORY_DELAY = 2.2f;

    private final BounceTalesGame game;
    private final String levelName;
    private final int levelIndex;
    private final String levelLabel;

    private Viewport viewport;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private ShapeRenderer shapes;

    private SpriteBank spriteBank;
    private Level level;
    private ScreenTransform transform;
    private SpriteDrawer spriteDrawer;
    private LevelRenderer levelRenderer;
    private Background background;

    private BallBody ball;
    private CollisionWorld collisionWorld;
    private CameraSpring cameraSpring;
    private LevelEntities levelEntities;
    private TriggerVM triggerVM;
    private SoundBank soundBank;
    private Hud hud;
    private final int[] vars = new int[256];

    private float accumulator;
    private int physicsSteps;
    private int lastContacts;
    private boolean victory;
    private float victoryTimer;
    private boolean advanceRequested;
    private boolean dying;
    private float deathTimer;

    private boolean debug;
    private boolean trace;

    /** Entrada automatica de prueba: {@code -Dbounce.auto=right|left|jump}. */
    private String auto;
    private float autoTime;

    /** Ruta de captura opcional (-Dbounce.screenshot=C:\ruta\bf.png). */
    private String screenshotPath;
    private int screenshotFrame = -1;
    private int frame;

    public GameScreen(String levelName) {
        this(null, levelName);
    }

    public GameScreen(BounceTalesGame game, String levelName) {
        this.game = game;
        this.levelName = levelName;
        this.levelIndex = LevelCatalog.indexOf(levelName);
        this.levelLabel = levelIndex < 0
                ? "NIVEL " + levelName.toUpperCase()
                : "NIVEL " + (levelIndex + 1) + "  " + levelName.toUpperCase();
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(SCREEN_WIDTH, SCREEN_HEIGHT, camera);

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();

        spriteBank = SpriteBank.parse(Gdx.files.internal(SPRITE_BANK).readString("UTF-8"));
        Gdx.app.log("Bounce", "sprites: " + spriteBank.imageCount() + " imagenes, "
                + spriteBank.descriptorCount() + " descriptores, "
                + spriteBank.prefabCount() + " prefabs");

        level = LevelLoader.load(
                Gdx.files.internal("levels/" + levelName + ".json").readString("UTF-8"));
        Gdx.app.log("Bounce", "nivel '" + levelName + "': " + level.entities.size
                + " entidades, bola=" + (level.ball != null
                        ? "(" + level.ball.worldX() + ", " + level.ball.worldY() + ")" : "no"));

        loadResourceIndex();

        soundBank = new SoundBank();
        soundBank.playMusic(LevelCatalog.musicId(levelIndex));
        levelEntities = new LevelEntities(level);

        transform = new ScreenTransform(SCREEN_WIDTH, SCREEN_HEIGHT);
        if (level.ball != null) {
            transform.centerOn(level.ball);
        } else if (level.root != null) {
            transform.cameraX = level.root.worldX();
            transform.cameraY = level.root.worldY();
        }

        background = Background.forLevel(levelName);
        spriteDrawer = new SpriteDrawer(spriteBank, transform);
        levelRenderer = new LevelRenderer(batch, shapes, spriteDrawer, transform);

        // --- Fase 2: fisica ---
        if (level.ball != null) {
            ball = new BallBody(level.ball.ballType);
            float drop = Float.parseFloat(System.getProperty("bounce.drop", "0"));
            ball.setPosition(level.ball.worldX(), level.ball.worldY() + drop);
            applyTeleport(ball);
            levelRenderer.setBallPosition(ball.x, ball.y);
        }
        collisionWorld = new CollisionWorld(level);
        cameraSpring = new CameraSpring(transform);
        triggerVM = new TriggerVM(level, levelEntities, ball, vars, soundBank);
        hud = new Hud(SCREEN_WIDTH, SCREEN_HEIGHT);
        if (ball != null) {
            cameraSpring.snap(ball);
        }
        Gdx.app.log("Bounce", "fisica: " + collisionWorld.colliderCount()
                + " poligonos de colision, bola r=" + (ball != null ? ball.radius() : 0));

        debug = Boolean.getBoolean("bounce.debug");
        trace = Boolean.getBoolean("bounce.trace");
        auto = System.getProperty("bounce.auto");
        if (Boolean.getBoolean("bounce.autocomplete")) {
            vars[0] = 2;
            Gdx.app.log("Bounce", "autocomplete activo: el nivel se completara al primer paso");
        }
        Gdx.app.log("Bounce", "debug=" + debug + " trace=" + trace + " auto=" + auto);

        screenshotPath = ScreenshotCapture.path();
        screenshotFrame = ScreenshotCapture.frameLimit();
        if (screenshotFrame > 0) {
            Gdx.app.log("Bounce", "salida en el frame " + screenshotFrame
                    + (screenshotPath != null ? " -> " + screenshotPath : ""));
        }

        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
    }

    /** Se carga solo como comprobacion del parser binario (no se usa aun para renderizar). */
    private void loadResourceIndex() {
        if (!Gdx.files.internal(RESOURCE_INDEX).exists()) {
            return;
        }
        ResourceIndex index = ResourceIndex.parse(
                Gdx.files.internal(RESOURCE_INDEX).readBytes());
        String levelFile = index.levelFileName(0);
        Gdx.app.log("Bounce", "indice: " + index.files.size() + " ficheros, "
                + index.objects.size() + " objetos, nivel 0 -> " + levelFile);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && game != null) {
            game.openMenu();
            return;
        }

        ScreenUtils.clear(0f, 0f, 0f, 1f);
        viewport.apply();
        camera.update();

        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debug = !debug;
        }

        if (victory) {
            victoryTimer += Math.min(delta, MAX_FRAME_DELTA);
            if (!advanceRequested && game != null && victoryTimer >= VICTORY_DELAY) {
                advanceRequested = true;
                game.levelCompleted(levelName);
                return;
            }
        }

        stepPhysics(Math.min(delta, MAX_FRAME_DELTA));
        levelRenderer.addTime(Math.min(delta, MAX_FRAME_DELTA));
        if (dying) {
            deathTimer += Math.min(delta, MAX_FRAME_DELTA);
            if (deathTimer >= 1f) {
                resetLevel();
            }
        }
        if (ball != null) {
            levelRenderer.setBallPosition(ball.x, ball.y);
        }

        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        // Fondo: degradado de cielo del original (n.a, rama j == 4).
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        background.draw(shapes, SCREEN_WIDTH, SCREEN_HEIGHT);
        shapes.end();

        levelRenderer.render(level);

        if (debug) {
            drawDebug();
        }
        drawDeathEffect();
        hud.render(batch, spriteDrawer, levelLabel,
                levelEntities.collected(), levelEntities.total(), victory);

        frame++;
        if (screenshotFrame > 0 && frame >= screenshotFrame) {
            if (screenshotPath != null) {
                ScreenshotCapture.capture(screenshotPath);
            }
            screenshotFrame = -1;
            Gdx.app.exit();
        }
    }

    /**
     * Bucle de paso fijo. Lee el input una vez por frame y lo aplica en cada paso, como el
     * original (que consulta las teclas antes de {@code c.d()}).
     */
    private void stepPhysics(float frameDelta) {
        if (ball == null || victory || dying) {
            return;
        }

        boolean left = Gdx.input.isKeyPressed(Input.Keys.LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.D);
        boolean jump = Gdx.input.isKeyPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyPressed(Input.Keys.UP);

        accumulator += frameDelta;
        int steps = 0;
        while (accumulator >= FIXED_STEP && steps < MAX_STEPS_PER_FRAME) {
            levelEntities.update(FIXED_STEP, ball);
            boolean ballHeld = levelEntities.isBallHeld();

            boolean stepLeft = left;
            boolean stepRight = right;
            boolean stepJump = jump;
            if (auto != null) {
                autoTime += FIXED_STEP;
                if (auto.startsWith("left")) {
                    stepLeft = true;
                } else if (auto.startsWith("right")) {
                    stepRight = true;
                }
                // "jump", "leftjump" y "rightjump" saltan periodicamente.
                if (auto.endsWith("jump")) {
                    stepJump = (autoTime % 1.2f) < 0.2f;
                } else {
                    stepJump = false;
                }
            }

            ball.beginStep();
            if (!ballHeld) {
                if (stepLeft) {
                    ball.accelerateLeft();
                }
                if (stepRight) {
                    ball.accelerateRight();
                }
                if (stepJump) {
                    ball.jump();
                }
                ball.integrate(FIXED_STEP);
            }

            triggerVM.detect();
            triggerVM.run(FIXED_STEP);
            lastContacts = collisionWorld.resolve(ball, FIXED_STEP);
            int events = levelEntities.interact(ball, FIXED_STEP, vars);
            if ((events & LevelEntities.EVENT_COLLECTED) != 0) {
                soundBank.playSound(Collectible.SOUND);
            }
            if ((events & LevelEntities.EVENT_PAD) != 0) {
                soundBank.playSound(Pad.SOUND);
            }
            ball.postCollision(FIXED_STEP);
            ball.updateVisual(FIXED_STEP);
            for (Entity entity : level.entities) {
                entity.animationTime += FIXED_STEP;
            }

            updateCamera();

            accumulator -= FIXED_STEP;
            steps++;
            physicsSteps++;

            if (vars[0] == 2 || levelEntities.allCollected()) {
                triggerVictory();
            } else if (ball.y < Math.min(-500f, collisionWorld.minY() - 300f)
                    || vars[0] == 1) {
                dying = true;
                deathTimer = 0f;
            }

            if (trace && physicsSteps % 15 == 0) {
                Gdx.app.log("Trace", "t=" + String.format("%.2f", physicsSteps * FIXED_STEP)
                        + " pos=(" + String.format("%.2f", ball.x) + ", "
                        + String.format("%.2f", ball.y) + ")"
                        + " v=(" + String.format("%.2f", ball.vx) + ", "
                        + String.format("%.2f", ball.vy) + ")"
                        + " ground=" + ball.onGround
                        + " n=(" + String.format("%.2f", ball.normalX) + ", "
                        + String.format("%.2f", ball.normalY) + ")"
                        + " contacts=" + lastContacts);
            }
            if (victory || dying) {
                break;
            }
        }
        // Si se han agotado los pasos, descartamos el retraso restante.
        if (steps == MAX_STEPS_PER_FRAME) {
            accumulator = 0f;
        }
    }

    /** Se completa el nivel: jingle, mensaje y avance automatico tras {@link #VICTORY_DELAY}. */
    private void triggerVictory() {
        if (victory) {
            return;
        }
        victory = true;
        victoryTimer = 0f;
        advanceRequested = false;
        soundBank.stopMusic();
        soundBank.playSound(LevelCatalog.levelCompleteJingleId());
        Gdx.app.log("Bounce", "nivel completado: " + levelName
                + " (" + levelEntities.collected() + "/" + levelEntities.total() + ")");
    }

    private void updateCamera() {
        Entity target = triggerVM.cameraTarget;
        float focusX = ball.x;
        float focusY = ball.y;
        float focusVx = ball.vx;
        float focusVy = ball.vy;
        if (target != null && !target.detached) {
            if (target == level.ball) {
                focusX = ball.x;
                focusY = ball.y;
            } else {
                focusX = target.worldX();
                focusY = target.worldY();
                focusVx = 0f;
                focusVy = 0f;
            }
        }

        cameraSpring.setSpring(triggerVM.springStiffness, triggerVM.springDamping);
        if (triggerVM.resetCameraSpring) {
            cameraSpring.resetMotion();
            triggerVM.resetCameraSpring = false;
        }
        if (triggerVM.snapCamera) {
            cameraSpring.snapTo(focusX, focusY);
            triggerVM.snapCamera = false;
        } else {
            cameraSpring.update(focusX, focusY, focusVx, focusVy, FIXED_STEP);
        }
    }

    private void drawDeathEffect() {
        if (!dying || deathTimer >= 0.5f) {
            return;
        }
        float progress = deathTimer / 0.5f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(deathTimer * Math.PI * 16f);
        float alpha = (0.18f + 0.32f * pulse) * (1f - progress);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.8f, 0f, 0f, alpha);
        shapes.rect(0f, 0f, SCREEN_WIDTH, SCREEN_HEIGHT);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void applyTeleport(BallBody body) {
        String teleport = System.getProperty("bounce.teleport");
        if (teleport == null) {
            return;
        }
        String[] coordinates = teleport.split(",", -1);
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("bounce.teleport debe tener formato x,y");
        }
        float x = Float.parseFloat(coordinates[0].trim());
        float y = Float.parseFloat(coordinates[1].trim());
        body.setPosition(x, y);
        Gdx.app.log("Bounce", "teleport: (" + x + ", " + y + ")");
    }

    private void resetLevel() {
        level.reset();
        levelEntities.reset();
        for (Trigger trigger : triggerVM.triggers()) {
            trigger.reset();
        }
        for (Entity entity : level.entities) {
            entity.animationTime = 0f;
        }
        for (int i = 0; i < vars.length; i++) {
            vars[i] = 0;
        }
        triggerVM.cameraTarget = null;
        triggerVM.springStiffness = CameraSpring.STIFFNESS;
        triggerVM.springDamping = CameraSpring.DAMPING;
        triggerVM.snapCamera = false;
        triggerVM.resetCameraSpring = false;
        ball.setPosition(level.ball.worldX(), level.ball.worldY());
        collisionWorld.rebuild();
        level.geometryDirty = false;
        cameraSpring.snap(ball);
        levelRenderer.setBallPosition(ball.x, ball.y);
        accumulator = 0f;
        victory = false;
        victoryTimer = 0f;
        advanceRequested = false;
        dying = false;
        deathTimer = 0f;
        Gdx.app.log("Bounce", "nivel reiniciado");
    }

    /** Aristas de colision en rojo, circulo de la bola en verde y normal en amarillo. */
    private void drawDebug() {
        if (collisionWorld != null) {
            collisionWorld.debugDraw(shapes, transform);
        }
        if (ball == null) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Line);
        float sx = transform.screenX(ball.x);
        float sy = transform.glY(transform.screenY(ball.y));
        float radius = ball.radius() * transform.scale;

        shapes.setColor(0f, 1f, 0f, 1f);
        shapes.circle(sx, sy, radius, 24);

        shapes.setColor(1f, 1f, 0f, 1f);
        shapes.line(sx, sy,
                sx + ball.normalX * radius, sy + ball.normalY * radius);
        shapes.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        if (hud != null) {
            hud.dispose();
        }
        if (soundBank != null) {
            soundBank.dispose();
        }
        if (levelRenderer != null) {
            levelRenderer.dispose();
        }
        if (spriteDrawer != null) {
            spriteDrawer.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
    }
}
