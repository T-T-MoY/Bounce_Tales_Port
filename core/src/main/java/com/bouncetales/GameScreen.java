package com.bouncetales;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
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
import com.bouncetales.world.PolygonShape;
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

    /** {@code n.f129c = 3000}: duracion total de la animacion de muerte. */
    private static final float DEATH_TOTAL_TIME = 3f;

    /** Primer segundo de muerte: la bola oscila con la tabla {@code n.f151b}. */
    private static final float DEATH_BOUNCE_TIME = 1f;

    /** Caida posterior: {@code 22500 / 65536} px por ms (343 px/s). */
    private static final float DEATH_FALL_PER_MS = 22500f / 65536f;

    /** Tabla senoidal de {@code n.m30a()} ({@code f151b}); se usa como {@code valor/4} px. */
    private static final short[] DEATH_SINE = buildDeathSine();

    private final BounceTalesGame game;
    private final String levelName;
    private final int levelIndex;

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
    /** Tiempo jugado del nivel en segundos ({@code n.f127a}); se reinicia al morir. */
    private float levelTime;
    private boolean victory;
    private float victoryTimer;
    private boolean advanceRequested;
    private boolean dying;
    private float deathTimer;
    private float deathBaseY;
    private int lastFaceState;
    private boolean faceForced;

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
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        // Mundo logico 240x320 de alto; en 16:9 el viewport amplia el ancho (mas escenario).
        viewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT, camera);

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
        loadTubeMeshes();

        // --- Fase 2: fisica ---
        if (level.ball != null) {
            ball = new BallBody(level.ball.ballType);
            float drop = Float.parseFloat(System.getProperty("bounce.drop", "0"));
            ball.setPosition(level.ball.worldX(), level.ball.worldY() + drop);
            applyTeleport(ball);
            applyInitialShape();
            levelRenderer.setBallPosition(ball.x, ball.y);
        }
        collisionWorld = new CollisionWorld(level);
        if (ball != null && System.getProperty("bounce.teleport") == null
                && collisionWorld.snapUpToGround(ball)) {
            Gdx.app.log("Bounce", "spawn corregido: la bola nacia dentro del terreno -> y="
                    + String.format("%.2f", ball.y));
        }
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
        syncWorldSize();

        if (Boolean.getBoolean("bounce.die") && ball != null) {
            beginDeath();
        }
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

    /**
     * Carga el banco de mallas del tubo ({@code n.f198a}) desde {@code bu.json}. Son los doce
     * poligonos tag 4 en orden de fichero: tres grupos de fotogramas clave (A, B, C) y un
     * cuarto grupo D que solo sirve de copia de trabajo (su geometria coincide con A).
     */
    private void loadTubeMeshes() {
        Array<PolygonShape> meshes = new Array<>();
        String path = "levels/bu.json";
        if (Gdx.files.internal(path).exists()) {
            Level bank = LevelLoader.load(Gdx.files.internal(path).readString("UTF-8"));
            for (Entity entity : bank.entities) {
                if (entity.tag == Entity.TAG_POLYGON && entity.polygon != null) {
                    meshes.add(entity.polygon);
                }
            }
        }
        levelRenderer.setTubeMeshes(meshes);
        Gdx.app.log("Bounce", "mallas de tubo: " + meshes.size);
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

        if (!dying && !victory) {
            handleShapeInput();
        }
        updateBallFace(delta);

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
            if (deathTimer >= DEATH_TOTAL_TIME) {
                resetLevel();
            }
        }
        if (ball != null) {
            levelRenderer.setBallPosition(ball.x, dying ? deathBallY() : ball.y);
            levelRenderer.setBallInWater(ball.inWater);
            levelRenderer.setBallState(ball.type, ball.angle, ball.faceState,
                    ball.faceTimer, dying);
        }

        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        // Fondo: degradado de cielo del original (n.a, rama j == 4).
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        background.draw(shapes, (int) transform.width, (int) transform.height);
        shapes.end();

        levelRenderer.setLevelTime(levelTime);
        levelRenderer.render(level);

        if (debug) {
            drawDebug();
        }
        hud.render(batch, spriteDrawer,
                levelEntities.collected(), levelEntities.total(), levelTime, victory);

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
            levelTime += FIXED_STEP;
            boolean wasInWater = ball.inWater;
            boolean wasTouchedWater = ball.touchedWater;
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

            levelEntities.update(FIXED_STEP, ball);
            levelEntities.controlTube(FIXED_STEP, stepLeft, stepRight, stepJump);
            boolean ballHeld = levelEntities.isBallHeld();

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
            if (ballHeld) {
                collisionWorld.clearCarry();
            } else {
                collisionWorld.applyPlatformCarry(ball, FIXED_STEP);
            }
            lastContacts = ballHeld ? 0 : collisionWorld.resolve(ball, FIXED_STEP);
            ball.inWater = false;
            ball.touchedWater = false;
            int events = ballHeld ? 0 : levelEntities.interact(ball, FIXED_STEP, vars);
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
                if (entity.decorations != null) {
                    entity.decorations.update(FIXED_STEP);
                }
            }
            vars[3] = ball.type;
            vars[4] = (int) ball.speed();
            vars[5] = (int) ball.vx;
            vars[6] = (int) ball.vy;

            if (wasTouchedWater != ball.touchedWater) {
                String transition = ball.touchedWater ? "entrada" : "salida";
                boolean visibleWater = ball.touchedWater ? ball.inWater : wasInWater;
                float netForceX = ball.forceX;
                float netForceY = ball.forceY + BallBody.GRAVITY * ball.mass();
                Gdx.app.log("Water", transition + " del agua"
                        + (visibleWater ? " visible" : " / corriente invisible")
                        + ", fuerza neta=(" + String.format("%.1f", netForceX) + ", "
                        + String.format("%.1f", netForceY) + ")");
            }

            updateCamera();

            accumulator -= FIXED_STEP;
            steps++;
            physicsSteps++;

            if (vars[0] == 2 || levelEntities.allCollected()) {
                triggerVictory();
            } else if (ball.y < Math.min(-500f, collisionWorld.minY() - 300f)
                    || vars[0] == 1) {
                beginDeath();
            }

            if (trace && physicsSteps % 15 == 0) {
                Gdx.app.log("Trace", "t=" + String.format("%.2f", physicsSteps * FIXED_STEP)
                        + " pos=(" + String.format("%.2f", ball.x) + ", "
                        + String.format("%.2f", ball.y) + ")"
                        + " v=(" + String.format("%.2f", ball.vx) + ", "
                        + String.format("%.2f", ball.vy) + ")"
                        + " force=(" + String.format("%.1f", ball.forceX) + ", "
                        + String.format("%.1f", ball.forceY + BallBody.GRAVITY * ball.mass())
                        + ") water=" + ball.touchedWater
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

    /**
     * Muerte de {@code n.java} (estados 1 y 3): suena el id 33, se congelan la fisica y la
     * bola rebota durante 1 s con {@code f151b} y despues cae durante 2 s ({@code f129c}).
     */
    private void beginDeath() {
        if (dying || ball == null) {
            return;
        }
        dying = true;
        deathTimer = 0f;
        deathBaseY = ball.y;
        ball.vx = 0f;
        ball.vy = 0f;
        ball.faceState = 0;
        ball.faceTimer = 0f;
        soundBank.playSound(LevelCatalog.deathSoundId());
        Gdx.app.log("Bounce", "muerte: rebote y caida (" + DEATH_TOTAL_TIME + " s)");
    }

    /** {@code c.e()}: posicion Y de la bola durante la animacion de muerte. */
    private float deathBallY() {
        float ms = deathTimer * 1000f;
        if (ms <= DEATH_BOUNCE_TIME * 1000f) {
            int index = ((int) (ms / 5f)) % DEATH_SINE.length;
            return deathBaseY + DEATH_SINE[index] / 4f;
        }
        int index = 200;
        return deathBaseY + DEATH_SINE[index] / 4f
                - (ms - DEATH_BOUNCE_TIME * 1000f) * DEATH_FALL_PER_MS;
    }

    /** {@code n.m30a()}: tabla senoidal de 360 valores usada por la muerte. */
    private static short[] buildDeathSine() {
        short[] table = new short[360];
        int velocity = 20520;
        int position = 0;
        for (int i = 0; i < table.length; i++) {
            int sample = position / 57;
            velocity -= sample;
            position += velocity / 57;
            table[i] = (short) sample;
        }
        return table;
    }

    /** Teclas 1/2/3 para elegir forma y TAB para ciclar, como {@code c.f()}. */
    private void handleShapeInput() {
        if (ball == null) {
            return;
        }
        int wanted = -1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1)) {
            wanted = BallBody.TYPE_NORMAL;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2)) {
            wanted = BallBody.TYPE_STONE;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3)) {
            wanted = BallBody.TYPE_BEACH;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            ball.cycleType();
            logShape();
        } else if (wanted >= 0 && ball.type != wanted) {
            ball.setType(wanted);
            logShape();
        }
        if (wanted >= 0 || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            ball.faceState = 0;
            ball.faceTimer = 0f;
            ball.idleTimer = BallBody.FACE_IDLE_DELAY;
        }
    }

    private void logShape() {
        String name = ball.type == BallBody.TYPE_STONE ? "piedra"
                : ball.type == BallBody.TYPE_BEACH ? "balon" : "normal";
        Gdx.app.log("Bounce", "forma de la bola: " + ball.type + " (" + name
                + ", masa=" + ball.mass()
                + ", salto=" + (BallBody.JUMP[ball.type] / ball.mass())
                + ", aceleracion=" + (BallBody.ACCEL[ball.type] / ball.mass()) + ")");
    }

    /** Reposo ({@code f7c}) y aviso de cambio para poder verificar la animacion en el log. */
    private void updateBallFace(float delta) {
        if (ball == null || dying || victory || faceForced) {
            return;
        }
        boolean input = Gdx.input.isKeyPressed(Input.Keys.LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.A)
                || Gdx.input.isKeyPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.D)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyPressed(Input.Keys.UP);
        ball.updateFace(Math.min(delta, MAX_FRAME_DELTA), input);
        if (ball.faceState != lastFaceState) {
            lastFaceState = ball.faceState;
            if (ball.faceState != 0) {
                Gdx.app.log("Bounce", "AFK: cara " + ball.faceState
                        + " (" + String.format("%.2f", ball.faceTimer) + " s)");
            }
        }
    }

    /** {@code -Dbounce.shape=N} y {@code -Dbounce.face=N} para capturas de verificacion. */
    private void applyInitialShape() {
        String shape = System.getProperty("bounce.shape");
        if (shape != null) {
            int type = Integer.parseInt(shape);
            ball.setType(type);
            logShape();
        }
        String face = System.getProperty("bounce.face");
        if (face != null) {
            ball.faceState = Integer.parseInt(face);
            ball.faceTimer = 0.45f;
            faceForced = true;
            Gdx.app.log("Bounce", "cara forzada: " + ball.faceState);
        }
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
            if (entity.decorations != null) {
                entity.decorations.reset();
            }
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
        ball.faceState = 0;
        ball.faceTimer = 0f;
        ball.idleTimer = BallBody.FACE_IDLE_DELAY;
        collisionWorld.rebuild();
        collisionWorld.clearCarry();
        level.geometryDirty = false;
        if (System.getProperty("bounce.teleport") == null) {
            collisionWorld.snapUpToGround(ball);
        }
        cameraSpring.snap(ball);
        levelRenderer.setBallPosition(ball.x, ball.y);
        accumulator = 0f;
        victory = false;
        victoryTimer = 0f;
        advanceRequested = false;
        dying = false;
        deathTimer = 0f;
        deathBaseY = 0f;
        lastFaceState = 0;
        levelTime = 0f;
        Gdx.app.log("Bounce", "nivel reiniciado");
    }

    /**
     * Sincroniza el lienzo logico con el mundo que calcula el {@link ExtendViewport}.
     * En 16:9 el ancho crece (p. ej. 568.9 unidades) y el alto sigue en 320, de modo que
     * aparecen mas columnas de escenario a los lados sin deformar ni recortar la accion.
     */
    private void syncWorldSize() {
        if (viewport == null || transform == null) {
            return;
        }
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        transform.resize(w, h);
        if (hud != null) {
            hud.resize(Math.round(w), Math.round(h));
        }
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
        if (viewport != null) {
            viewport.update(width, height, true);
            syncWorldSize();
        }
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
