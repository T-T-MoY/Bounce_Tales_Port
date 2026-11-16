package com.bouncetales;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bouncetales.asset.LevelCatalog;
import com.bouncetales.asset.Prefab;
import com.bouncetales.asset.SpriteBank;
import com.bouncetales.asset.SpriteDescriptor;
import com.bouncetales.render.FontManager;
import com.bouncetales.render.ScreenTransform;
import com.bouncetales.render.SpriteDrawer;
import com.bouncetales.util.ScreenshotCapture;
import com.bouncetales.world.Entity;

/**
 * Visor de sprites para el artista: galeria de todos los sprites de {@code sprites.json}
 * (descriptores 0..325 y prefabs 326+) con su recorte, pivote y uso en los niveles.
 *
 * <p>Es una herramienta de desarrollo, no forma parte del juego:
 * <ul>
 *   <li>IZQ/DER (o A/D): sprite anterior/siguiente; ARR/ABA (W/S): +-10.</li>
 *   <li>+/-: zoom; 0: ajustar a la ventana; ESPACIO: siguiente fotograma de una animacion.</li>
 *   <li>G: rejilla de pixeles; ESC: volver al menu.</li>
 * </ul>
 *
 * <p>Siempre usa los atlas originales ({@code images/}, 1x) para que las coordenadas que
 * muestra sean las del fichero que el artista tiene que ampliar. Se abre desde el menu con
 * {@code V}; acepta {@code -Dbounce.sprite=<id>} para arrancar en un sprite concreto y
 * {@code -Dbounce.viewerKeys=...} para automatizar la navegacion en las pruebas.
 */
public final class SpriteViewerScreen implements Screen {

    private static final String SPRITE_BANK = "sprites/sprites.json";

    /** Lienzo propio del visor: mas ancho que el juego para la columna de informacion. */
    private static final int VIEW_WIDTH = 480;
    private static final int VIEW_HEIGHT = 320;

    private static final float MARGIN = 12f;
    private static final float HEADER_H = 30f;
    private static final float FOOTER_H = 24f;
    private static final float PANEL_W = 190f;
    private static final float ATLAS_H = 96f;

    private static final float GOLD_R = 1f;
    private static final float GOLD_G = 0.85f;
    private static final float GOLD_B = 0.3f;
    private static final float TEXT_R = 0.88f;
    private static final float TEXT_G = 0.94f;
    private static final float TEXT_B = 1f;

    /** Niveles de zoom (pixeles de pantalla por pixel de atlas). */
    private static final float[] ZOOMS = {
            0.25f, 0.5f, 1f, 2f, 3f, 4f, 6f, 8f, 12f, 16f
    };

    private final BounceTalesGame game;
    private final boolean inputEnabled;
    private final String screenshotPath;
    private final int screenshotFrame;
    /** {@code -Dbounce.viewerKeys=RRRZ}: despacha esas teclas una vez, para pruebas. */
    private final String scriptedKeys = System.getProperty("bounce.viewerKeys");

    private Viewport viewport;
    private OrthographicCamera camera;
    private float worldWidth = VIEW_WIDTH;
    private float worldHeight = VIEW_HEIGHT;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    /** Convierte las escalas de diseno (arial-15) a la fuente TrueType de 48 px. */
    private float textScale = 1f;
    private GlyphLayout glyphs;
    private SpriteBank bank;
    private SpriteDrawer drawer;
    private ScreenTransform transform;
    private Usage usage;

    private int[] ids = new int[0];
    private int index;
    private int currentId = -1;
    private Frame frame;
    private int frameIndex;
    private float zoom = 1f;
    private boolean autoZoom = true;
    private boolean grid = true;

    private boolean leaving;
    private boolean scriptedDone;
    private float inputArmDelay = 0.25f;
    private int renderedFrames;

    private final Rectangle previewArea = new Rectangle();
    private final Rectangle scissor = new Rectangle();

    private float panelX;
    private float previewX;
    private float previewY;
    private float previewW;
    private float previewH;
    private float atlasY;
    private float atlasH;

    public SpriteViewerScreen(BounceTalesGame game) {
        this.game = game;
        String screenshot = System.getProperty("bounce.screenshot");
        String frames = System.getProperty("bounce.frames");
        this.inputEnabled = screenshot == null && frames == null;
        this.screenshotPath = ScreenshotCapture.path();
        this.screenshotFrame = ScreenshotCapture.frameLimit();
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(VIEW_WIDTH, VIEW_HEIGHT, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        camera.update();
        syncWorldSize();
        layout();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        FontManager.UiFont uiFont = FontManager.createUiFont();
        font = uiFont.font;
        textScale = uiFont.baseScale;
        glyphs = new GlyphLayout();

        transform = new ScreenTransform((int) worldWidth, (int) worldHeight);
        String json = Gdx.files.internal(SPRITE_BANK).readString("UTF-8");
        bank = SpriteBank.parse(json);
        drawer = new SpriteDrawer(bank, transform);
        ids = bank.spriteIds();

        int initial = Integer.getInteger("bounce.sprite", -1);
        if (initial >= 0) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == initial) {
                    index = i;
                    break;
                }
            }
        }
        usage = Usage.cached(json);
        refresh();

        Gdx.app.log("Viewer", "sprites: " + ids.length + " (" + bank.descriptorCount()
                + " descriptores, " + bank.prefabCount() + " prefabs); uso indexado en "
                + usage.levelCount + " niveles");
    }

    @Override
    public void render(float delta) {
        if (leaving) {
            return;
        }
        inputArmDelay -= delta;
        if (inputEnabled && inputArmDelay <= 0f) {
            handleInput();
            if (leaving) {
                return;
            }
        }
        if (!scriptedDone && scriptedKeys != null && inputArmDelay <= 0f) {
            dispatchScriptedKeys();
            if (leaving) {
                return;
            }
        }

        ScreenUtils.clear(0.045f, 0.055f, 0.085f, 1f);
        viewport.apply();
        camera.zoom = 1f;
        camera.position.set(worldWidth * 0.5f, worldHeight * 0.5f, 0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        drawChrome();
        drawPreview();
        drawAtlas();
        drawTexts();

        renderedFrames++;
        if (screenshotFrame > 0 && renderedFrames >= screenshotFrame) {
            if (screenshotPath != null) {
                ScreenshotCapture.capture(screenshotPath);
            }
            Gdx.app.exit();
        }
    }

    // ------------------------------------------------------------------ navegacion

    private void move(int delta) {
        if (ids.length == 0) {
            return;
        }
        index = Math.floorMod(index + delta, ids.length);
        refresh();
    }

    private void refresh() {
        if (ids.length == 0) {
            currentId = -1;
            frame = new Frame(-1, 0, 0, 16, 16, 0, 0, false);
            return;
        }
        index = Math.floorMod(index, ids.length);
        currentId = ids[index];
        frameIndex = 0;
        frame = frameFor(currentId, 0, 0);
        if (frame == null) {
            frame = new Frame(-1, 0, 0, 16, 16, 0, 0, false);
        }
        if (autoZoom) {
            autoFit();
        }
    }

    private void changeFrame(int delta) {
        int count = drawer.frameCount(currentId);
        if (count <= 1) {
            return;
        }
        frameIndex = Math.floorMod(frameIndex + delta, count);
        Frame next = frameFor(currentId, frameIndex, 0);
        if (next != null) {
            frame = next;
            if (autoZoom) {
                autoFit();
            }
        }
    }

    private void zoomStep(int direction) {
        autoZoom = false;
        int nearest = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < ZOOMS.length; i++) {
            float d = Math.abs(ZOOMS[i] - zoom);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        int next = Math.max(0, Math.min(ZOOMS.length - 1, nearest + direction));
        zoom = ZOOMS[next];
    }

    private void autoFit() {
        float availW = Math.max(16f, previewW - 16f);
        float availH = Math.max(16f, previewH - 16f);
        float best = ZOOMS[0];
        for (float z : ZOOMS) {
            if (frame.width * z <= availW && frame.height * z <= availH) {
                best = z;
            }
        }
        zoom = best;
    }

    private void back() {
        if (leaving) {
            return;
        }
        leaving = true;
        Gdx.app.log("Viewer", "volviendo al menu");
        game.openMenu();
    }

    // ------------------------------------------------------------------ entrada

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            back();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)
                || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            move(-1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            move(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)
                || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            move(-10);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
                || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            move(10);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) {
            move(-50);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) {
            move(50);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.HOME)) {
            index = 0;
            refresh();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.END)) {
            index = Math.max(0, ids.length - 1);
            refresh();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS)
                || Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ADD)) {
            zoomStep(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            zoomStep(-1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0)
                || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_0)) {
            autoZoom = true;
            autoFit();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            changeFrame(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            grid = !grid;
        }
    }

    private void dispatchScriptedKeys() {
        scriptedDone = true;
        for (int i = 0; i < scriptedKeys.length(); i++) {
            char c = Character.toUpperCase(scriptedKeys.charAt(i));
            if (c == ',' || c == ' ') {
                continue;
            }
            Gdx.app.log("Viewer", "tecla simulada: " + c);
            switch (c) {
                case 'L':
                    move(-1);
                    break;
                case 'R':
                    move(1);
                    break;
                case 'U':
                    move(-10);
                    break;
                case 'D':
                    move(10);
                    break;
                case 'F':
                    changeFrame(1);
                    break;
                case 'Z':
                    zoomStep(1);
                    break;
                case 'X':
                    zoomStep(-1);
                    break;
                case '0':
                    autoZoom = true;
                    autoFit();
                    break;
                case 'G':
                    grid = !grid;
                    break;
                case 'Q':
                    back();
                    return;
                default:
                    Gdx.app.log("Viewer", "tecla simulada desconocida: " + c);
                    break;
            }
            if (leaving) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    private void drawChrome() {
        enableBlending();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.045f, 0.055f, 0.085f, 1f);
        shapes.rect(0f, 0f, worldWidth, worldHeight);

        shapes.setColor(0f, 0f, 0f, 0.35f);
        shapes.rect(0f, worldHeight - HEADER_H, worldWidth, HEADER_H);
        shapes.rect(0f, 0f, worldWidth, FOOTER_H);

        shapes.setColor(1f, 1f, 1f, 0.035f);
        shapes.rect(panelX - 6f, previewY - 4f, PANEL_W + 12f, previewH + 8f);

        shapes.setColor(1f, 0.83f, 0.25f, 0.75f);
        shapes.rect(MARGIN, worldHeight - HEADER_H, worldWidth - MARGIN * 2f, 1f);
        shapes.setColor(0.25f, 0.45f, 0.6f, 0.75f);
        shapes.rect(MARGIN, FOOTER_H, worldWidth - MARGIN * 2f, 1f);
        shapes.end();

        enableBlending();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.3f, 0.5f, 0.65f, 0.9f);
        shapes.rect(previewX, previewY, previewW, previewH);
        shapes.rect(panelX, atlasY, PANEL_W, atlasH);
        shapes.end();
    }

    /**
     * Dibuja el sprite ampliado. Se usa una camara con {@code zoom = 1 / zoom} y desplazada
     * para que el centro del recorte caiga en el centro de la caja de vista previa; el
     * {@link SpriteDrawer} sigue dibujando a tamano logico 1:1 y es la camara la que amplia.
     */
    private void drawPreview() {
        float oldZoom = camera.zoom;
        float oldX = camera.position.x;
        float oldY = camera.position.y;

        // Recorte fijo de la caja (se calcula con la camara sin zoom).
        camera.zoom = 1f;
        camera.position.set(worldWidth * 0.5f, worldHeight * 0.5f, 0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        ScissorStack.calculateScissors(camera, 0f, 0f,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                batch.getTransformMatrix(), previewArea, scissor);
        boolean clipped = ScissorStack.pushScissors(scissor);

        float bx = previewX + previewW * 0.5f;
        float by = previewY + previewH * 0.5f;

        camera.zoom = 1f / zoom;
        camera.position.set(
                bx - (bx - worldWidth * 0.5f) * camera.zoom,
                by - (by - worldHeight * 0.5f) * camera.zoom, 0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        float cropLeft = bx - frame.width * 0.5f;
        float cropBottom = by - frame.height * 0.5f;
        float cropTop = by + frame.height * 0.5f;

        if (grid && zoom >= 1.5f) {
            enableBlending();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 1f, 1f, 0.14f);
            for (int i = 0; i <= frame.width; i++) {
                shapes.line(cropLeft + i, cropBottom, cropLeft + i, cropTop);
            }
            for (int j = 0; j <= frame.height; j++) {
                shapes.line(cropLeft, cropBottom + j, cropLeft + frame.width, cropBottom + j);
            }
            shapes.setColor(1f, 0.83f, 0.25f, 0.35f);
            for (int i = 0; i <= frame.width; i += 8) {
                shapes.line(cropLeft + i, cropBottom, cropLeft + i, cropTop);
            }
            for (int j = 0; j <= frame.height; j += 8) {
                shapes.line(cropLeft, cropBottom + j, cropLeft + frame.width, cropBottom + j);
            }
            shapes.end();
        }

        float screenX = bx - frame.width * 0.5f + frame.offsetX;
        float screenY = (worldHeight - by) - frame.height * 0.5f + frame.offsetY;

        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f);
        if (currentId >= 0) {
            drawer.drawFrame(batch, currentId, frameIndex, screenX, screenY);
        }
        batch.end();

        enableBlending();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.4f, 0.9f, 1f, 0.9f);
        shapes.rect(cropLeft, cropBottom, frame.width, frame.height);
        float pivotX = cropLeft + frame.offsetX;
        float pivotY = cropTop - frame.offsetY;
        float arm = 5f / zoom;
        shapes.setColor(1f, 0.3f, 0.9f, 1f);
        shapes.line(pivotX - arm, pivotY, pivotX + arm, pivotY);
        shapes.line(pivotX, pivotY - arm, pivotX, pivotY + arm);
        shapes.end();

        if (clipped) {
            ScissorStack.popScissors();
        }

        camera.zoom = oldZoom;
        camera.position.set(oldX, oldY, 0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);
    }

    /** Miniatura del atlas completo con el recorte actual resaltado. */
    private void drawAtlas() {
        if (frame.imageId < 0) {
            return;
        }
        Texture texture = drawer.atlasTexture(frame.imageId);
        if (texture == null) {
            return;
        }
        float pad = 6f;
        float fit = Math.min((PANEL_W - pad * 2f) / texture.getWidth(),
                (atlasH - pad * 2f) / texture.getHeight());
        float drawW = texture.getWidth() * fit;
        float drawH = texture.getHeight() * fit;
        float drawX = panelX + (PANEL_W - drawW) * 0.5f;
        float drawY = atlasY + (atlasH - drawH) * 0.5f;

        batch.begin();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(texture, drawX, drawY, drawW, drawH);
        batch.end();

        if (frame.compound) {
            return;
        }
        int scale = drawer.imageScale();
        float rx = drawX + frame.srcX * scale * fit;
        float ry = drawY + drawH - (frame.srcY * scale + frame.height * scale) * fit;
        float rw = frame.width * scale * fit;
        float rh = frame.height * scale * fit;

        enableBlending();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 0.83f, 0.25f, 0.22f);
        shapes.rect(rx, ry, rw, rh);
        shapes.end();

        enableBlending();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 0.83f, 0.25f, 1f);
        shapes.rect(rx, ry, rw, rh);
        shapes.end();
    }

    private void drawTexts() {
        batch.begin();

        font.getData().setScale(0.68f * textScale);
        font.setColor(GOLD_R, GOLD_G, GOLD_B, 1f);
        font.draw(batch, "VISOR DE SPRITES", MARGIN, worldHeight - 11f);

        font.getData().setScale(0.52f * textScale);
        font.setColor(0.85f, 0.95f, 1f, 1f);
        centered("Sprite " + (ids.length == 0 ? 0 : index + 1) + " / " + ids.length,
                worldHeight - 11f);

        font.getData().setScale(0.42f * textScale);
        font.setColor(0.55f, 0.8f, 0.95f, 1f);
        String mode = "ORIGINALES 1x";
        glyphs.setText(font, mode);
        font.draw(batch, glyphs, worldWidth - MARGIN - glyphs.width, worldHeight - 11f);

        font.getData().setScale(0.36f * textScale);
        font.setColor(0.75f, 0.85f, 0.9f, 1f);
        centered("IZQ/DER navegar   ARR/ABA +-10   +/- zoom   0 ajustar"
                + "   ESPACIO frame   G rejilla   ESC volver", 13f);

        drawInfo();

        font.getData().setScale(0.4f * textScale);
        font.setColor(0.55f, 0.8f, 0.95f, 1f);
        font.draw(batch, "Recorte en el atlas (images)", panelX + 8f,
                atlasY + atlasH + 11f);

        batch.end();
    }

    private void drawInfo() {
        float x = panelX + 8f;
        float y = worldHeight - HEADER_H - 6f;

        if (currentId < 0) {
            infoLine(x, y, "Sin sprites", GOLD_R, GOLD_G, GOLD_B);
            return;
        }

        if (currentId < SpriteBank.FIRST_PREFAB_ID) {
            SpriteDescriptor d = bank.descriptor(currentId);
            if (d == null) {
                return;
            }
            y = infoLine(x, y, "ID " + d.id + "  descriptor", GOLD_R, GOLD_G, GOLD_B);
            y = infoLine(x, y, "Imagen: " + nameOf(d.imageId) + "  (#" + d.imageId + ")",
                    TEXT_R, TEXT_G, TEXT_B);
            y = infoLine(x, y, "Recorte: " + d.srcX + ", " + d.srcY,
                    TEXT_R, TEXT_G, TEXT_B);
            y = infoLine(x, y, "Tamano: " + d.width + " x " + d.height,
                    TEXT_R, TEXT_G, TEXT_B);
            y = infoLine(x, y, "Pivote: " + d.offsetX + ", " + d.offsetY,
                    TEXT_R, TEXT_G, TEXT_B);
        } else {
            Prefab p = bank.prefab(currentId);
            if (p == null) {
                return;
            }
            y = infoLine(x, y, "ID " + p.id + "  prefab", GOLD_R, GOLD_G, GOLD_B);
            y = infoLine(x, y, "Tipo: " + kindName(p.kind), TEXT_R, TEXT_G, TEXT_B);
            if (p.kind == Prefab.SIMPLE) {
                y = infoLine(x, y, "Imagen: " + nameOf(p.imageId) + "  (#" + p.imageId + ")",
                        TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Recorte: " + p.srcX + ", " + p.srcY,
                        TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Tamano: " + p.width + " x " + p.height,
                        TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Pivote: " + p.offsetX + ", " + p.offsetY,
                        TEXT_R, TEXT_G, TEXT_B);
            } else if (p.kind == Prefab.COMPOUND) {
                y = infoLine(x, y, "Caja: " + p.boxWidth + " x " + p.boxHeight,
                        TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Partes: " + p.partSprite.length,
                        TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Pivote caja: " + p.boxOffsetX + ", " + p.boxOffsetY,
                        TEXT_R, TEXT_G, TEXT_B);
            } else {
                int count = Math.max(1, p.frames.length);
                y = infoLine(x, y, "Frames: " + count, TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Frame: " + (frameIndex + 1) + " / " + count,
                        TEXT_R, TEXT_G, TEXT_B);
                y = infoLine(x, y, "Base: " + (p.frames.length > 0
                        ? p.frames[frameIndex] : "-"), TEXT_R, TEXT_G, TEXT_B);
            }
        }

        Texture texture = frame.imageId >= 0 ? drawer.atlasTexture(frame.imageId) : null;
        y = infoLine(x, y, "Atlas: " + (texture != null
                ? texture.getWidth() + " x " + texture.getHeight() : "?"),
                TEXT_R, TEXT_G, TEXT_B);
        y = infoLine(x, y, clip("Uso: " + usageLevels(currentId), 46),
                TEXT_R, TEXT_G, TEXT_B);
        String prefabs = usagePrefabs(currentId);
        if (prefabs != null) {
            infoLine(x, y, clip("En prefabs: " + prefabs, 46), TEXT_R, TEXT_G, TEXT_B);
        }
    }

    private float infoLine(float x, float y, String text, float r, float g, float b) {
        font.getData().setScale(0.44f * textScale);
        font.setColor(r, g, b, 1f);
        font.draw(batch, text, x, y);
        return y - 13f;
    }

    private void centered(String text, float y) {
        glyphs.setText(font, text);
        font.draw(batch, glyphs, (worldWidth - glyphs.width) * 0.5f, y);
    }

    /** SpriteBatch desactiva la mezcla al terminar; ShapeRenderer no la reactiva solo. */
    private static void enableBlending() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ------------------------------------------------------------------ datos

    private String nameOf(int imageId) {
        String name = bank.imageName(imageId);
        return name != null ? name : "?";
    }

    private static String kindName(int kind) {
        switch (kind) {
            case Prefab.SIMPLE:
                return "simple";
            case Prefab.COMPOUND:
                return "compuesto";
            default:
                return "animacion";
        }
    }

    private String usageLevels(int id) {
        Array<String> list = usage.levels.get(id);
        if (list == null || list.size == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        int max = Math.min(2, list.size);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        if (list.size > max) {
            sb.append(" +").append(list.size - max);
        }
        return sb.toString();
    }

    private String usagePrefabs(int id) {
        Array<Integer> list = usage.prefabs.get(id);
        if (list == null || list.size == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int max = Math.min(3, list.size);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        if (list.size > max) {
            sb.append(" +").append(list.size - max);
        }
        return sb.toString();
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    /** Geometria del recorte que se esta mostrando (descriptor o fotograma de un prefab). */
    private Frame frameFor(int id, int animationFrame, int depth) {
        if (id < 0 || depth > 8) {
            return null;
        }
        if (id < SpriteBank.FIRST_PREFAB_ID) {
            SpriteDescriptor d = bank.descriptor(id);
            return d == null ? null : new Frame(d.imageId, d.srcX, d.srcY,
                    d.width, d.height, d.offsetX, d.offsetY, false);
        }
        Prefab p = bank.prefab(id);
        if (p == null) {
            return null;
        }
        switch (p.kind) {
            case Prefab.SIMPLE:
                return new Frame(p.imageId, p.srcX, p.srcY, p.width, p.height,
                        p.offsetX, p.offsetY, false);
            case Prefab.COMPOUND: {
                int image = p.partSprite.length > 0
                        ? primaryImageId(p.partSprite[0], depth + 1) : -1;
                return new Frame(image, 0, 0, Math.max(1, p.boxWidth),
                        Math.max(1, p.boxHeight), p.boxOffsetX, p.boxOffsetY, true);
            }
            default:
                if (p.frames.length == 0) {
                    return null;
                }
                int child = p.frames[Math.floorMod(animationFrame, p.frames.length)];
                return frameFor(child, 0, depth + 1);
        }
    }

    private int primaryImageId(int id, int depth) {
        if (id < 0 || depth > 8) {
            return -1;
        }
        if (id < SpriteBank.FIRST_PREFAB_ID) {
            SpriteDescriptor d = bank.descriptor(id);
            return d == null ? -1 : d.imageId;
        }
        Prefab p = bank.prefab(id);
        if (p == null) {
            return -1;
        }
        switch (p.kind) {
            case Prefab.SIMPLE:
                return p.imageId;
            case Prefab.COMPOUND:
                return p.partSprite.length > 0
                        ? primaryImageId(p.partSprite[0], depth + 1) : -1;
            default:
                return p.frames.length > 0
                        ? primaryImageId(p.frames[0], depth + 1) : -1;
        }
    }

    private static String tagName(int tag) {
        switch (tag) {
            case Entity.TAG_OBJECT:
                return "objeto";
            case Entity.TAG_POLYGON:
                return "poligono";
            case Entity.TAG_TRIGGER:
                return "trigger";
            case Entity.TAG_BALL:
                return "bola";
            case Entity.TAG_DECORATION:
                return "decorado";
            case Entity.TAG_WATER:
                return "agua";
            case Entity.TAG_TUBE:
                return "tubo";
            case Entity.TAG_PAD:
                return "pad";
            case Entity.TAG_COLLECTIBLE:
                return "coleccionable";
            case Entity.TAG_BALL_DECORATION:
                return "bola-decorado";
            case Entity.TAG_ENEMY:
                return "enemigo";
            default:
                return "tag" + tag;
        }
    }

    private static final class Frame {
        final int imageId;
        final int srcX;
        final int srcY;
        final int width;
        final int height;
        final int offsetX;
        final int offsetY;
        final boolean compound;

        Frame(int imageId, int srcX, int srcY, int width, int height,
              int offsetX, int offsetY, boolean compound) {
            this.imageId = imageId;
            this.srcX = srcX;
            this.srcY = srcY;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.compound = compound;
        }
    }

    /**
     * Indice de uso: que niveles (y con que tag) y que prefabs referencian cada sprite.
     * Se construye una sola vez y se cachea entre aperturas del visor.
     */
    private static final class Usage {

        private static Usage cached;

        final IntMap<Array<String>> levels = new IntMap<>();
        final IntMap<Array<Integer>> prefabs = new IntMap<>();
        int levelCount;

        static Usage cached(String spritesJson) {
            if (cached == null) {
                cached = build(spritesJson);
            }
            return cached;
        }

        private static Usage build(String spritesJson) {
            Usage usage = new Usage();
            JsonValue root = new JsonReader().parse(spritesJson);
            JsonValue prefabs = root.get("prefabs");
            if (prefabs != null) {
                for (JsonValue v = prefabs.child; v != null; v = v.next) {
                    int prefabId = Integer.parseInt(v.name);
                    int kind = v.getInt("kind", Prefab.SIMPLE);
                    if (kind == Prefab.COMPOUND) {
                        JsonValue parts = v.get("parts");
                        if (parts != null) {
                            for (JsonValue p = parts.child; p != null; p = p.next) {
                                usage.addPrefab(p.getInt("sprite", -1), prefabId);
                            }
                        }
                    } else if (kind != Prefab.SIMPLE) {
                        JsonValue frames = v.get("frames");
                        if (frames != null) {
                            for (JsonValue f = frames.child; f != null; f = f.next) {
                                usage.addPrefab(f.asInt(), prefabId);
                            }
                        }
                    }
                }
            }

            if (Gdx.files != null) {
                for (String level : LevelCatalog.levels()) {
                    FileHandle file = Gdx.files.internal("levels/" + level + ".json");
                    if (!file.exists()) {
                        continue;
                    }
                    usage.levelCount++;
                    JsonValue levelJson = new JsonReader().parse(file);
                    JsonValue objects = levelJson.get("objects");
                    if (objects == null) {
                        continue;
                    }
                    for (JsonValue o = objects.child; o != null; o = o.next) {
                        int tag = o.getInt("tag", 0);
                        JsonValue sprites = o.get("sprites");
                        if (sprites != null) {
                            for (JsonValue s = sprites.child; s != null; s = s.next) {
                                usage.addLevel(s.getInt("sprite", -1),
                                        level + "/" + tagName(tag));
                            }
                        }
                        JsonValue pad = o.get("pad");
                        if (pad != null) {
                            usage.addLevel(pad.getInt("sprite", -1), level + "/pad");
                        }
                    }
                }
            }
            return usage;
        }

        private void addLevel(int id, String use) {
            if (id < 0) {
                return;
            }
            Array<String> list = levels.get(id);
            if (list == null) {
                list = new Array<>();
                levels.put(id, list);
            }
            if (!list.contains(use, false)) {
                list.add(use);
            }
        }

        private void addPrefab(int id, int prefabId) {
            if (id < 0) {
                return;
            }
            Array<Integer> list = prefabs.get(id);
            if (list == null) {
                list = new Array<>();
                prefabs.put(id, list);
            }
            if (!list.contains(prefabId, false)) {
                list.add(prefabId);
            }
        }
    }

    // ------------------------------------------------------------------ layout / ciclo de vida

    private void syncWorldSize() {
        worldWidth = viewport.getWorldWidth();
        worldHeight = viewport.getWorldHeight();
    }

    private void layout() {
        panelX = worldWidth - MARGIN - PANEL_W;
        previewX = MARGIN;
        previewY = FOOTER_H + 6f;
        previewW = Math.max(40f, panelX - MARGIN - 12f);
        previewH = Math.max(40f, worldHeight - HEADER_H - previewY);
        atlasY = previewY;
        atlasH = ATLAS_H;
        previewArea.set(previewX, previewY, previewW, previewH);
    }

    @Override
    public void resize(int width, int height) {
        if (viewport == null) {
            return;
        }
        viewport.update(width, height, true);
        syncWorldSize();
        layout();
        if (transform != null) {
            transform.resize(worldWidth, worldHeight);
        }
        if (autoZoom) {
            autoFit();
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
        if (drawer != null) {
            drawer.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
    }
}
