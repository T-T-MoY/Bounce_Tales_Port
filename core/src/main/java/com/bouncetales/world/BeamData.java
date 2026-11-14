package com.bouncetales.world;

/**
 * Payload del tag 10 ({@code i.a(byte[], int)}).
 *
 * <p>Ojo: el script de exportacion etiqueta los campos en el orden en que se leen del fichero,
 * no con su significado. El mapeo real es:
 * <pre>
 *   JSON x0 -&gt; x0 (extremo izquierdo)
 *   JSON x1 -&gt; y0 (altura de referencia del extremo izquierdo)
 *   JSON x2 -&gt; x1 (extremo derecho)
 *   JSON y  -&gt; y1 (altura de la linea de deteccion)
 * </pre>
 * y las fuerzas {@code p0..p3} se interpolan dentro de la caja.
 */
public final class BeamData {

    public float x0;
    public float y0;
    public float x1;
    public float y1;

    /** Fuerzas: p0/p1 en X, p2/p3 en Y (se interpolan con p3-&gt;p1 y p2-&gt;p0). */
    public float p0;
    public float p1;
    public float p2;
    public float p3;

    /** Color ARGB del haz (solo dibujo). */
    public int color;
}
