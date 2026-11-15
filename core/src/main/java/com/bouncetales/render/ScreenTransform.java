package com.bouncetales.render;

import com.bouncetales.world.Entity;

/**
 * Transformacion mundo -&gt; pantalla del original ({@code k.f103g} + camara {@code k.f101f}).
 *
 * <p>La matriz de pantalla es fija:
 * <pre>
 *   f103g = [ 43266, 0, W/2 ]        (43266/65536 = 0.660, o 22306 si W &lt; 200)
 *           [ 0, -43266, H/2 ]
 * </pre>
 * y la camara se compone como {@code f103g * inversa(camara)}. El resultado es:
 * <pre>
 *   screenX =  (worldX - camX) * scale + W/2
 *   screenY = -(worldY - camY) * scale + H/2        (coordenadas J2ME: Y hacia abajo)
 * </pre>
 * El mundo tiene <b>Y hacia arriba</b> (la gravedad es {@code -400}): el signo negativo de
 * {@code screenY} es el que lo voltea para dibujar.
 *
 * <p>Todo el render se hace en pixeles logicos (240x320), asi que se reproduce el encuadre
 * del juego original independientemente del tamano de la ventana.
 */
public final class ScreenTransform {

    /** {@code k.f103g.a} para pantallas de 200 px o mas. */
    public static final float SCALE_LARGE_SCREEN = 43266f / 65536f;
    /** {@code k.f103g.a} para pantallas estrechas. */
    public static final float SCALE_SMALL_SCREEN = 22306f / 65536f;

    public float width;
    public float height;

    public float scale;
    public float cameraX;
    public float cameraY;

    public ScreenTransform(int width, int height) {
        this.width = width;
        this.height = height;
        this.scale = width >= 200 ? SCALE_LARGE_SCREEN : SCALE_SMALL_SCREEN;
    }

    /**
     * Ajusta el lienzo logico al tamano del mundo del viewport. Con {@code ExtendViewport} el
     * alto sigue siendo 320 y el ancho crece en ventanas 16:9, asi que se ve mas escenario
     * horizontal sin deformar nada. La escala (0.66) no cambia: el zoom es el del original.
     */
    public void resize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float screenX(float worldX) {
        return (worldX - cameraX) * scale + width * 0.5f;
    }

    public float screenY(float worldY) {
        return -(worldY - cameraY) * scale + height * 0.5f;
    }

    /** Convierte una Y de pantalla J2ME (0 arriba) a la Y de OpenGL (0 abajo). */
    public float glY(float screenY) {
        return height - screenY;
    }

    /** Igual que {@link #glY(float)} pero para la esquina superior de un rectangulo. */
    public float glTop(float screenYTop, float rectHeight) {
        return height - screenYTop - rectHeight;
    }

    /**
     * Coloca la camara como {@code k.b(true)}: centrada en la bola mas un desplazamiento
     * vertical de {@code H/5} unidades de pantalla (la bola queda en el tercio inferior).
     */
    public void centerOn(Entity ball) {
        cameraX = ball.worldX();
        cameraY = ball.worldY() + height / (5f * scale);
    }
}
