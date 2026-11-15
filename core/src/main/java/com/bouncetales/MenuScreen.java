package com.bouncetales;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bouncetales.asset.LevelCatalog;
import com.bouncetales.audio.SoundBank;
import com.bouncetales.render.Background;
import com.bouncetales.util.ScreenshotCapture;

import java.util.List;

/**
 * Menu principal: lista los niveles en el orden del original y permite empezar cualquiera.
 *
 * <p>Se dibuja en el mismo lienzo logico 240x320 que el juego, con teclado (flechas/WASD,
 * Enter/Espacio) y raton. La musica del menu es la pista 28 ({@code aw.mid} convertida a OGG).
 */
public final class MenuScreen implements Screen {

    private static final int SCREEN_WIDTH = GameScreen.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = GameScreen.SCREEN_HEIGHT;

    private static final int VISIBLE_ROWS = 11;
    private static final int ROW_HEIGHT = 16;
    private static final int LIST_TOP = 78;

    private static final float TITLE_Y = 40f;
    private static final float SUBTITLE_Y = 60f;

    /** Teclas que atiende el menu; todas pasan por {@link #handleKey(int)}. */
    private static final int[] MENU_KEYS = {
            Input.Keys.UP, Input.Keys.W, Input.Keys.DOWN, Input.Keys.S,
            Input.Keys.PAGE_UP, Input.Keys.PAGE_DOWN,
            Input.Keys.ENTER, Input.Keys.SPACE, Input.Keys.NUMPAD_ENTER,
            Input.Keys.ESCAPE, Input.Keys.M, Input.Keys.F
    };

    private final BounceTalesGame game;
    private final List<String> levels = LevelCatalog.levels();
    private final Vector2 pointer = new Vector2();
    private final boolean inputEnabled;
    private final String screenshotPath;
    private final int screenshotFrame;

    private int selected;
    private int firstVisible;
    private int frame;
    private boolean starting;
    /** Evita que un evento de raton/tecla del arranque de la ventana active el menu. */
    private float inputArmDelay = 0.25f;
    /** {@code -Dbounce.menuKeys=M,F}: despacha esas teclas una vez, para pruebas. */
    private final String scriptedKeys = System.getProperty("bounce.menuKeys");
    private boolean scriptedDone;

    private Viewport viewport;
    private OrthographicCamera camera;
    private float worldWidth = SCREEN_WIDTH;
    private float worldHeight = SCREEN_HEIGHT;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout layout;
    private Background background;
    private SoundBank soundBank;

    public MenuScreen(BounceTalesGame game, String initialLevel) {
        this.game = game;
        int index = LevelCatalog.indexOf(initialLevel);
        this.selected = index < 0 ? 0 : index;
        String screenshot = System.getProperty("bounce.screenshot");
        String frames = System.getProperty("bounce.frames");
        this.inputEnabled = screenshot == null && frames == null;
        this.screenshotPath = ScreenshotCapture.path();
        this.screenshotFrame = ScreenshotCapture.frameLimit();
        clampScroll();
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        // Igual que el juego: alto logico 320 y ancho extendido en ventanas 16:9.
        viewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT, camera);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        layout = new GlyphLayout();
        background = new Background(2);
        soundBank = new SoundBank();
        soundBank.playMusic(LevelCatalog.menuMusicId());
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        syncWorldSize();
        Gdx.app.log("Menu", "niveles: " + levels.size() + ", seleccionado: "
                + (selected + 1) + " (" + levels.get(selected) + ")");
    }

    @Override
    public void render(float delta) {
        if (starting) {
            return;
        }
        inputArmDelay -= delta;
        if (inputEnabled && inputArmDelay <= 0f) {
            handleInput();
            if (starting) {
                return;
            }
        }
        if (!scriptedDone && scriptedKeys != null && inputArmDelay <= 0f) {
            dispatchScriptedKeys();
        }

        ScreenUtils.clear(0f, 0f, 0f, 1f);
        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        background.draw(shapes, (int) worldWidth, (int) worldHeight);
        // Banda superior e inferior para separar el menu del fondo.
        shapes.setColor(0f, 0f, 0f, 0.28f);
        shapes.rect(0, worldHeight - 72, worldWidth, 72);
        shapes.rect(0, 0, worldWidth, 54);
        shapes.setColor(1f, 0.83f, 0.25f, 0.85f);
        shapes.rect(20, worldHeight - 73, worldWidth - 40, 1);
        shapes.end();

        batch.begin();
        font.getData().setScale(1.2f);
        font.setColor(1f, 0.83f, 0.25f, 1f);
        drawCentered("BOUNCE TALES", TITLE_Y);
        font.getData().setScale(0.55f);
        font.setColor(0.85f, 0.95f, 1f, 1f);
        int lastVisible = Math.min(firstVisible + VISIBLE_ROWS, levels.size());
        drawCentered("Selecciona un nivel  (" + (firstVisible + 1) + "-"
                + lastVisible + " de " + levels.size() + ")", SUBTITLE_Y);

        // La lista conserva su columna de 240 px centrada en el lienzo extendido.
        float contentX = (worldWidth - SCREEN_WIDTH) * 0.5f;
        font.getData().setScale(0.5f);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = firstVisible + row;
            if (index >= levels.size()) {
                break;
            }
            float baseline = LIST_TOP + row * ROW_HEIGHT + 12f;
            float glY = worldHeight - baseline;
            String name = levels.get(index);
            String label = String.format("%02d  %s", index + 1, name.toUpperCase());
            boolean isSelected = index == selected;
            if (isSelected) {
                font.setColor(1f, 0.86f, 0.25f, 1f);
                font.draw(batch, "> " + label, contentX + 28f, glY);
                font.draw(batch, "<", contentX + 200f, glY);
            } else {
                font.setColor(0.78f, 0.88f, 0.95f, 1f);
                font.draw(batch, "  " + label, contentX + 28f, glY);
            }
        }

        // Ayuda y opciones de audio.
        font.getData().setScale(0.4f);
        font.setColor(0.75f, 0.85f, 0.9f, 1f);
        drawCentered("FLECHAS / W S: elegir    ENTER: jugar", 270f);
        drawAudioOptions(286f);
        drawCentered("F11: pantalla completa    ESC: salir", 302f);
        batch.end();

        frame++;
        if (screenshotFrame > 0 && frame >= screenshotFrame) {
            if (screenshotPath != null) {
                ScreenshotCapture.capture(screenshotPath);
            }
            Gdx.app.exit();
        }
    }

    private void handleInput() {
        for (int key : MENU_KEYS) {
            if (Gdx.input.isKeyJustPressed(key)) {
                handleKey(key);
            }
        }
        if (Gdx.input.justTouched() && viewport != null) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(pointer);
            float topY = worldHeight - pointer.y;
            int row = (int) ((topY - LIST_TOP) / ROW_HEIGHT);
            if (row >= 0 && row < VISIBLE_ROWS) {
                int index = firstVisible + row;
                if (index < levels.size()) {
                    if (index == selected) {
                        startSelected("raton");
                    } else {
                        selected = index;
                        clampScroll();
                    }
                }
            }
        }
    }

    /** Reparte una tecla ya pulsada: navegacion, arranque y opciones de audio. */
    private void handleKey(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
            case Input.Keys.W:
                move(-1);
                break;
            case Input.Keys.DOWN:
            case Input.Keys.S:
                move(1);
                break;
            case Input.Keys.PAGE_UP:
                move(-VISIBLE_ROWS);
                break;
            case Input.Keys.PAGE_DOWN:
                move(VISIBLE_ROWS);
                break;
            case Input.Keys.ENTER:
            case Input.Keys.SPACE:
            case Input.Keys.NUMPAD_ENTER:
                startSelected("teclado");
                break;
            case Input.Keys.ESCAPE:
                Gdx.app.exit();
                break;
            case Input.Keys.M:
                soundBank.toggleMusicMuted();
                break;
            case Input.Keys.F:
                soundBank.toggleFxMuted();
                break;
            default:
                break;
        }
    }

    /**
     * Envia una vez las teclas del sistema {@code bounce.menuKeys} (por ejemplo {@code MF} o
     * {@code M,F}) por el mismo despachador que usa el teclado real. Sirve para verificar el
     * muteo en entornos donde no se puede inyectar entrada al sistema operativo.
     */
    private void dispatchScriptedKeys() {
        scriptedDone = true;
        for (int i = 0; i < scriptedKeys.length(); i++) {
            char c = Character.toUpperCase(scriptedKeys.charAt(i));
            if (c == ',' || c == ' ') {
                continue;
            }
            int key = Input.Keys.valueOf(String.valueOf(c));
            if (key < 0) {
                Gdx.app.log("Menu", "tecla simulada desconocida: " + c);
                continue;
            }
            Gdx.app.log("Menu", "tecla simulada: " + c);
            handleKey(key);
        }
    }

    private void move(int delta) {
        selected += delta;
        if (selected < 0) {
            selected = levels.size() - 1;
        } else if (selected >= levels.size()) {
            selected = 0;
        }
        clampScroll();
    }

    private void clampScroll() {
        if (selected < firstVisible) {
            firstVisible = selected;
        } else if (selected >= firstVisible + VISIBLE_ROWS) {
            firstVisible = selected - VISIBLE_ROWS + 1;
        }
        if (firstVisible < 0) {
            firstVisible = 0;
        }
        int maxFirst = Math.max(0, levels.size() - VISIBLE_ROWS);
        if (firstVisible > maxFirst) {
            firstVisible = maxFirst;
        }
    }

    private void startSelected(String source) {
        if (starting || selected < 0 || selected >= levels.size()) {
            return;
        }
        starting = true;
        String level = levels.get(selected);
        Gdx.app.log("Menu", "iniciando nivel " + (selected + 1) + ": " + level
                + " (" + source + ")");
        game.openLevel(level);
    }

    private void drawCentered(String text, float topY) {
        layout.setText(font, text);
        font.draw(batch, layout, (worldWidth - layout.width) * 0.5f,
                worldHeight - topY);
    }

    /** Linea de opciones de audio; en rojo si alguna esta silenciada. */
    private void drawAudioOptions(float topY) {
        String music = SoundBank.isMusicMuted() ? "OFF" : "ON";
        String fx = SoundBank.isFxMuted() ? "OFF" : "ON";
        boolean muted = SoundBank.isMusicMuted() || SoundBank.isFxMuted();
        font.setColor(muted ? 0.98f : 0.6f,
                muted ? 0.55f : 0.92f,
                muted ? 0.45f : 0.65f, 1f);
        drawCentered("M: musica " + music + "    F: efectos " + fx, topY);
    }

    /** Lee el tamano del mundo logico que ha calculado el {@link ExtendViewport}. */
    private void syncWorldSize() {
        if (viewport == null) {
            return;
        }
        worldWidth = viewport.getWorldWidth();
        worldHeight = viewport.getWorldHeight();
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
        if (soundBank != null) {
            soundBank.dispose();
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
