package com.bouncetales.physics;

/**
 * Cuerpo de la bola del jugador, traduccion de {@code c.java} (clase {@code c}).
 *
 * <p>Unidades: posicion y radio en pixeles de mundo, velocidad en px/s, tiempo en segundos.
 * El mundo tiene <b>Y hacia arriba</b> (la gravedad es {@code -400}), al contrario que la
 * pantalla J2ME; el volteo de Y lo hace la matriz de render.
 *
 * <p>Formas ({@code c.f34d}): 0 normal, 1 piedra ({@code c.java} la llama marmol) y 2 balon
 * (rayada). El original cambia de forma al recoger el objeto 20 con {@code c.f()}, que avanza
 * el indice y vuelve a 0 al pasar de 2; {@link #cycleType()} replica ese ciclo.
 *
 * <p>El original separa tres acumuladores que aqui se conservan:
 * <ul>
 *   <li>{@link #forceX}/{@link #forceY}: fuerzas (px/s^2 * masa). Se integran como
 *       {@code v += F/masa * dt} y se ponen a cero tras cada paso.</li>
 *   <li>{@link #kickX}/{@link #kickY}: impulso instantaneo (px/s * masa). Se integra como
 *       {@code v += kick/masa}, <b>sin</b> multiplicar por {@code dt} (salto y pads).</li>
 *   <li>{@link #vx}/{@link #vy}: velocidad resultante, limitada a 999 px/s.</li>
 * </ul>
 *
 * <p>Constantes exactas de {@code c.java} (lineas 87-99):
 * <pre>
 *   gravedad   = -400
 *   masa       = {1.0, 1.4, 0.5}   // normal, piedra (mas pesada), balon (mas ligero)
 *   radio      = {20, 20, 20}
 *   rebote (b) = {0.6, 0.3, 0.6}   // resistencia de rodadura (ver SegmentSolver)
 *   friccion(c)= {0.1, 0.1, 0.1}
 *   aceleracion= {500, 350, 312.5}
 *   vel. max   = {280, 350, 150}   // el balon acelera poco: menos masa, mas control
 *   divisor aire = {1.5, 2.5, 1.2}
 *   salto (h)  = {288, 255, 162.5} // la piedra salta menos; el balon aun menos
 *   cos(55)    = 0.573576...       // d[t]
 * </pre>
 */
public final class BallBody {

    /** {@code c.f34d == 0}: bola normal. */
    public static final int TYPE_NORMAL = 0;
    /** {@code c.f34d == 1}: piedra/marmol, mas masa y menos salto. */
    public static final int TYPE_STONE = 1;
    /** {@code c.f34d == 2}: balon, menos masa y menos salto pero mas control. */
    public static final int TYPE_BEACH = 2;

    /** {@code c.m}. */
    public static final float GRAVITY = -400f;

    /** {@code c.f4a}: masa por tipo de bola (normal / piedra / balon). */
    public static final float[] MASS = {1.0f, 1.4f, 0.5f};

    /** {@code c.f3a}: radio por tipo de bola. */
    public static final int[] RADIUS = {20, 20, 20};

    /** {@code c.b}: en el original limita el impulso de rodadura (no es el rebote). */
    public static final float[] ROLL_RESISTANCE = {0.6f, 0.3f, 0.6f};

    /** {@code c.c}: sesgo de contacto (devuelve el 10 % de la velocidad normal). */
    public static final float[] FRICTION = {0.1f, 0.1f, 0.1f};

    /** {@code c.e}: aceleracion horizontal en el suelo. */
    public static final float[] ACCEL = {500f, 350f, 312.5f};

    /** {@code c.f}: velocidad horizontal maxima. */
    public static final float[] MAX_SPEED = {280f, 350f, 150f};

    /** {@code c.g}: divisor de la aceleracion en el aire. */
    public static final float[] AIR_DIVISOR = {1.5f, 2.5f, 1.2f};

    /** {@code c.h}: impulso de salto. */
    public static final float[] JUMP = {288f, 255f, 162.5f};

    /** {@code c.d[t] = cos(0.9599310755729675)} = cos(55 grados). */
    public static final float COS_55 = (float) Math.cos(0.9599310755729675d);

    /** Limite de velocidad del original ({@code c.d()}, linea 306). */
    public static final float MAX_SPEED_ABS = 999f;

    /** {@code c.f33o}: suavizado de la velocidad usada para el balanceo visual. */
    public static final float VISUAL_SMOOTHING = 0.5f;

    /** {@code c.f6b}: tiempo quieto (ms) antes de que la bola parpadee. */
    public static final float FACE_IDLE_DELAY = 3f;

    /** Umbral de velocidad de {@code c.d()}: por debajo se considera quieta. */
    public static final float FACE_IDLE_SPEED = 40f;

    /** Duracion del parpadeo / animacion corta ({@code s = 600} ms). */
    public static final float FACE_BLINK_TIME = 0.6f;

    /** Duracion de las caras aleatorias ({@code s = 1500} ms). */
    public static final float FACE_EXPRESSION_TIME = 1.5f;

    /** Tipo de bola: 0 normal, 1 piedra, 2 balon ({@code c.f34d}). */
    public int type;

    /** {@code c.f7c}: cara de reposo (0 ninguna, 2 parpadeo, 3..8 expresiones). */
    public int faceState;

    /** {@code c.s}: tiempo restante de la cara actual, en segundos. */
    public float faceTimer;

    /** {@code c.f6b}: cuenta atras hasta el siguiente parpadeo, en segundos. */
    public float idleTimer = FACE_IDLE_DELAY;

    private final java.util.Random faceRandom = new java.util.Random();

    /** Posicion actual del centro en mundo. */
    public float x;
    public float y;

    /**
     * Posicion del frame anterior ({@code c.b}). La colision la reescribe al punto de
     * contacto en cada pasada, igual que {@code c.o()} hace con {@code this.b}.
     */
    public float prevX;
    public float prevY;

    public float vx;
    public float vy;

    /** Acumulador de fuerzas ({@code c.f13d/f14e}); se vacia en {@link #integrate(float)}. */
    public float forceX;
    public float forceY;

    /** Impulso instantaneo ({@code c.f15f/f16g}); se vacia en {@link #integrate(float)}. */
    public float kickX;
    public float kickY;

    /** {@code c.f37d}: hubo contacto en el ultimo frame. */
    public boolean onGround;

    /** La bola esta dentro de un cuerpo de agua visible. */
    public boolean inWater;

    /** La bola ha tocado agua o una corriente, incluso si es invisible. */
    public boolean touchedWater;

    /** {@code c.f35p}: tiempo sin contacto; a los 0.25 s se pierde {@link #onGround}. */
    public float contactTimer = 1f;

    /** Normal del ultimo contacto ({@code c.q}, {@code c.f36r}); empieza mirando arriba. */
    public float normalX = 0f;
    public float normalY = 1f;

    /** Velocidad suavizada para el balanceo visual ({@code c.j}, {@code c.k}). */
    public float visualVx;
    public float visualVy;

    /** Angulo de giro acumulado ({@code c.n}). */
    public float angle;

    public BallBody() {
        this(0);
    }

    public BallBody(int type) {
        setType(type);
    }

    /** Cambia de forma conservando posicion y velocidad, como {@code c.f()}. */
    public void setType(int type) {
        this.type = Math.max(TYPE_NORMAL, Math.min(MASS.length - 1, type));
    }

    /** {@code c.f()}: normal -&gt; piedra -&gt; balon -&gt; normal. */
    public void cycleType() {
        setType(type >= TYPE_BEACH ? TYPE_NORMAL : type + 1);
    }

    public float mass() {
        return MASS[type];
    }

    public float radius() {
        return RADIUS[type];
    }

    public float bounce() {
        return ROLL_RESISTANCE[type];
    }

    public float friction() {
        return FRICTION[type];
    }

    /** Coloca la bola como {@code c.a(int, int)}: posicion, sin velocidad ni contacto. */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.vx = 0f;
        this.vy = 0f;
        this.forceX = 0f;
        this.forceY = 0f;
        this.kickX = 0f;
        this.kickY = 0f;
        this.visualVx = 0f;
        this.visualVy = 0f;
        this.angle = 0f;
        this.onGround = false;
        this.inWater = false;
        this.touchedWater = false;
        this.contactTimer = 1f;
        this.normalX = 0f;
        this.normalY = 1f;
        this.faceState = 0;
        this.faceTimer = 0f;
        this.idleTimer = FACE_IDLE_DELAY;
    }

    /**
     * Prepara un paso: guarda la posicion anterior ({@code c.b = c.c}) y acumula la gravedad
     * ({@code c.d()}, lineas 250-252: {@code f14e += m * masa}).
     */
    public void beginStep() {
        prevX = x;
        prevY = y;
        inWater = false;
        touchedWater = false;
        forceX += 0f * mass();
        forceY += GRAVITY * mass();
    }

    /** {@code c.b()}: aceleracion hacia la izquierda (tecla 3). */
    public void accelerateLeft() {
        float boost = 0f;
        if (onGround) {
            if (vx > -MAX_SPEED[type] - boost) {
                forceX -= ACCEL[type] + boost;
            }
        } else if (vx > -MAX_SPEED[type] - boost) {
            forceX -= (ACCEL[type] + boost) / AIR_DIVISOR[type];
        }
    }

    /** {@code c.c()}: aceleracion hacia la derecha (tecla 4). */
    public void accelerateRight() {
        float boost = 0f;
        if (onGround) {
            if (vx < MAX_SPEED[type] + boost) {
                forceX += ACCEL[type] + boost;
            }
        } else if (vx < MAX_SPEED[type] + boost) {
            forceX += (ACCEL[type] + boost) / AIR_DIVISOR[type];
        }
    }

    /**
     * {@code c.a(boolean)}: salto. La condicion real del original es
     * {@code if (this.f36r <= d[t] || !this.f37d) return;}, es decir, exige que la
     * <b>componente Y de la normal</b> supere {@code cos(55 grados)} y que la bola este en
     * contacto. No usa el temporizador {@code f35p} (eso era un error del informe).
     *
     * @param soft {@code true} aplica medio impulso (usado por algunos triggers)
     * @return {@code true} si el salto se aplico
     */
    public boolean jump(boolean soft) {
        if (normalY <= COS_55 || !onGround) {
            return false;
        }
        kickY += soft ? JUMP[type] / 2f : JUMP[type];
        onGround = false;
        return true;
    }

    /** {@code c.a(false)}: salto completo. */
    public boolean jump() {
        return jump(false);
    }

    /**
     * Integracion de {@code c.d()} (lineas 270-311):
     * <pre>
     *   v += F / masa * dt
     *   v += kick / masa
     *   pos += v * dt
     *   F = 0; kick = 0
     *   |v| &lt;= 999
     * </pre>
     * Tambien acumula {@code f35p} y pierde el contacto a los 0.25 s.
     */
    public void integrate(float dt) {
        float invMass = 1f / mass();
        vx += forceX * invMass * dt;
        vy += forceY * invMass * dt;
        vx += kickX * invMass;
        vy += kickY * invMass;

        x += vx * dt;
        y += vy * dt;

        forceX = 0f;
        forceY = 0f;
        kickX = 0f;
        kickY = 0f;

        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed > MAX_SPEED_ABS) {
            float k = MAX_SPEED_ABS / speed;
            vx *= k;
            vy *= k;
        }

        contactTimer += dt;
        if (contactTimer > 0.25f) {
            onGround = false;
        }
    }

    /**
     * Segunda acumulacion de {@code f35p} que el original hace al final de {@code c.a(k)}
     * (lineas 624-627), despues de resolver colisiones.
     */
    public void postCollision(float dt) {
        contactTimer += dt;
        if (contactTimer > 0.25f) {
            onGround = false;
        }
    }

    /**
     * Animacion de reposo de {@code c.d()} (lineas 335-368).
     *
     * <p>Si no hay entrada y la velocidad es menor que 40, {@code f6b} cuenta los 3 s de
     * espera. Al agotarse se activa {@code f7c = 2} (parpadeo de 600 ms); cuando termina se
     * elige una expresion aleatoria 3..8, con 600 ms para la 3 y 1500 ms para el resto.
     * Cualquier movimiento o tecla vuelve a {@code f7c = 0} y reinicia la espera.
     */
    public void updateFace(float dt, boolean inputActive) {
        boolean moving = Math.abs(vx) >= FACE_IDLE_SPEED
                || Math.abs(vy) >= FACE_IDLE_SPEED;
        if (inputActive || moving) {
            faceState = 0;
            faceTimer = 0f;
            idleTimer = FACE_IDLE_DELAY;
            return;
        }
        if (faceTimer > 0f) {
            faceTimer -= dt;
            if (faceTimer <= 0f) {
                if (faceState < 2 || faceState > 8) {
                    faceState = 0;
                    faceTimer = 0f;
                    idleTimer = FACE_IDLE_DELAY;
                } else {
                    int next = faceRandom.nextInt(3) == 0 ? 3 : 3 + faceRandom.nextInt(6);
                    faceState = next;
                    faceTimer = next == 3 ? FACE_BLINK_TIME : FACE_EXPRESSION_TIME;
                }
            }
            return;
        }
        idleTimer -= dt;
        if (idleTimer <= 0f) {
            faceState = 2;
            faceTimer = FACE_BLINK_TIME;
            idleTimer = FACE_IDLE_DELAY;
        }
    }

    public float speed() {
        return (float) Math.sqrt(vx * vx + vy * vy);
    }

    /**
     * Actualiza la velocidad suavizada y el angulo de giro. En el original se calcula en
     * {@code c.d()} a partir de la velocidad tangencial tras el contacto.
     */
    public void updateVisual(float dt) {
        float speed = (float) Math.sqrt(visualVx * visualVx + visualVy * visualVy);
        if (speed > 1e-5f && radius() > 0f) {
            float spin = speed / radius() * dt;
            if (visualVx < 0f) {
                spin = -spin;
            }
            angle += spin;
        }
    }
}
