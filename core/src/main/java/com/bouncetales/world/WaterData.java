package com.bouncetales.world;

/**
 * Payload del tag 10: cuerpo de agua o corriente (clase {@code i} del original).
 *
 * <p>El exportador de niveles llama {@code "beam"} a este bloque porque {@code i.a(byte[],int)}
 * lee los campos en crudo, pero el comportamiento real es el de {@code i.java}: una caja de
 * agua con superficie ondulada, corriente y amortiguacion.
 *
 * <p>Mapeo real de los campos del JSON:
 * <pre>
 *   JSON x0 -&gt; x0        extremo izquierdo
 *   JSON x1 -&gt; bottom    fondo del agua
 *   JSON x2 -&gt; x1        extremo derecho
 *   JSON y  -&gt; surface   linea de la superficie
 *   JSON p3 -&gt; currentLeftX      corriente horizontal en x0
 *   JSON p1 -&gt; currentRightX     corriente horizontal en x1
 *   JSON p2 -&gt; currentBottomY    corriente vertical en el fondo
 *   JSON p0 -&gt; currentSurfaceY   corriente vertical en la superficie
 * </pre>
 *
 * <p>{@code i.mo24a()} fija {@code f83b = surface}: la bola solo recibe la fuerza cuando su
 * parte inferior queda por debajo de esa linea. La caja de dibujo se extiende 30 px por
 * encima y por debajo.
 */
public final class WaterData {

    /** {@code i.f70a}: extremo izquierdo. */
    public float x0;
    /** {@code i.b}: extremo derecho. */
    public float x1;
    /** {@code i.d}: fondo. */
    public float bottom;
    /** {@code i.f71c}: linea de superficie ({@code i.f83b}). */
    public float surface;

    /** {@code i.f}: corriente horizontal en el extremo izquierdo. */
    public float currentLeftX;
    /** {@code i.f80d}: corriente horizontal en el extremo derecho. */
    public float currentRightX;
    /** {@code i.f81e}: corriente vertical en el fondo. */
    public float currentBottomY;
    /** {@code i.f79a}: corriente vertical en la superficie. */
    public float currentSurfaceY;

    /**
     * Color ARGB efectivo. El original ignora el color del fichero: si no es negro lo
     * sustituye por {@code 0x441111EE} y si es negro ({@code 0x44000000}) lo deja invisible.
     */
    public int colorArgb = 0x44000000;

    /**
     * {@code i.g == 1}: agua "solida" (rectangulo plano, sin ondas). En el fichero se marca
     * con el canal azul igual a 16.
     */
    public boolean solid;

    public float width() {
        return x1 - x0;
    }

    public float height() {
        return surface - bottom;
    }

    /** {@code i.f82d != 1140850688}: el agua se dibuja y amortigua; si no, es corriente invisible. */
    public boolean visible() {
        return colorArgb != 0x44000000;
    }
}
