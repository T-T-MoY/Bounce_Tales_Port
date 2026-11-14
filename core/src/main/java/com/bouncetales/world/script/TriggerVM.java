package com.bouncetales.world.script;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.bouncetales.physics.BallBody;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Geom;
import com.bouncetales.world.Level;
import com.bouncetales.world.entity.LevelEntities;

/**
 * Maquina virtual de triggers (tag 6, clase {@code g} del original).
 *
 * <p>Por frame hace lo mismo que {@code n.i()}:
 * <ol>
 *   <li>{@link #detect} ({@code g.a(g[], c)}): prueba el segmento que ha recorrido la bola
 *       contra la caja del trigger expandida por su radio y arma los triggers cuyo flanco
 *       (entrada o salida) coincide con {@code mode_a}.</li>
 *   <li>{@link #run} ({@code g.a(g[])}): ejecuta los programas de los triggers armados. Un
 *       opcode que devuelve {@code false} (esperar, mover, girar) deja el programa a medias y
 *       se reanuda en el frame siguiente.</li>
 * </ol>
 *
 * <p>Los opcodes y sus operandos son los de {@code g.a(byte[])}; los indices de variable
 * comparten el array {@code g.a} del original, que aqui es {@code vars} y tambien usa
 * {@code GameScreen} para el estado del nivel ({@code vars[0]}).
 */
public final class TriggerVM {

    /** Sonido: {@code n.m33a(id)}. */
    public interface SoundSink {
        void playSound(int id);
    }

    private static final int OP_SOUND = 0;
    private static final int OP_ANIMATE = 1;
    private static final int OP_SET_IDLE = 2;
    private static final int OP_SET_DISABLED = 3;
    private static final int OP_SET_ARMED = 4;
    private static final int OP_SET_PAUSED = 5;
    private static final int OP_WAIT = 6;
    private static final int OP_SET = 7;
    private static final int OP_ADD = 8;
    private static final int OP_SUB = 9;
    private static final int OP_MUL = 10;
    private static final int OP_DIV = 11;
    private static final int OP_EQ = 12;
    private static final int OP_NE = 13;
    private static final int OP_LT = 14;
    private static final int OP_GT = 15;
    private static final int OP_MOVE = 16;
    private static final int OP_ROTATE = 17;
    private static final int OP_SET_POS = 18;
    private static final int OP_REPARENT = 19;
    private static final int OP_DELETE = 20;
    private static final int OP_JUMP = 21;
    private static final int OP_NOP = 22;
    private static final int OP_END = 23;
    private static final int OP_IF_LEFT = 24;
    private static final int OP_CHECKPOINT = 25;
    private static final int OP_KICK = 26;
    private static final int OP_FORCE = 27;
    private static final int OP_VELOCITY = 28;
    private static final int OP_FLAGS = 29;
    private static final int OP_CAMERA = 30;
    private static final int OP_SPRING = 31;
    private static final int OP_SPRING_RESET = 32;

    private final Level level;
    private final LevelEntities entities;
    private final BallBody ball;
    private final int[] vars;
    private final SoundSink sounds;
    private final Array<Trigger> triggers = new Array<>();

    private final Vector2 prev = new Vector2();
    private final Vector2 cur = new Vector2();

    /** Indice de instruccion donde esta el salto de las comparaciones ({@code g.f59c}). */
    private int operandOffset;

    // --- efectos que consume GameScreen ---
    public Entity cameraTarget;
    public float springStiffness = 90f;
    public float springDamping = 140f;
    public boolean snapCamera;
    public boolean resetCameraSpring;
    public float checkpointX;
    public float checkpointY;

    private int lastLogged = -1;

    public TriggerVM(Level level, LevelEntities entities, BallBody ball, int[] vars,
                     SoundSink sounds) {
        this.level = level;
        this.entities = entities;
        this.ball = ball;
        this.vars = vars;
        this.sounds = sounds;
        for (Entity e : level.entities) {
            if (e.tag == Entity.TAG_TRIGGER && e.triggerData != null) {
                e.trigger = new Trigger(e);
                triggers.add(e.trigger);
            }
        }
    }

    public Array<Trigger> triggers() {
        return triggers;
    }

    /** {@code g.a(g[], c)}: deteccion de entrada/salida. */
    public void detect() {
        if (ball == null) {
            return;
        }
        int ballIndex = level.ball != null ? level.ball.index : -1;
        float radius = ball.radius();

        for (Trigger t : triggers) {
            if (t.state == Trigger.DISABLED || t.entity.detached || t.entity.hidden) {
                continue;
            }
            t.entity.worldToLocal(ball.prevX, ball.prevY, prev);
            t.entity.worldToLocal(ball.x, ball.y, cur);

            float minX = t.data.minX - radius;
            float minY = t.data.minY - radius;
            float maxX = t.data.maxX + radius;
            float maxY = t.data.maxY + radius;
            boolean hit = Geom.segmentHitsRect(prev.x, prev.y, cur.x, cur.y,
                    minX, minY, maxX, maxY);

            t.insidePrev = t.inside;
            t.inside = hit;

            boolean fire = t.data.modeA == 0
                    ? (t.inside && !t.insidePrev)
                    : (t.insidePrev && !t.inside);

            if (!fire) {
                continue;
            }
            if (t.data.filterObject > -1 && t.data.filterObject != ballIndex) {
                continue;
            }
            t.setState(Trigger.ARMED);
            if (t.state == Trigger.ARMED) {
                log(t, "trigger " + t.entity.index + " armado (entrada="
                        + (t.data.modeA == 0) + ", programas=" + t.programCount()
                        + ", pc=" + t.pc + ")");
            }
        }
    }

    private void log(Trigger t, String message) {
        if (lastLogged == t.entity.index) {
            return;
        }
        lastLogged = t.entity.index;
        Gdx.app.log("Trigger", message);
    }

    /** {@code g.a(g[])}: ejecuta los programas armados. */
    public void run(float dt) {
        int dtMs = Math.max(1, Math.round(dt * 1000f));

        for (Trigger t : triggers) {
            if (t.state != Trigger.ARMED || t.pc < 0 || t.pc >= t.programs.length) {
                continue;
            }
            if (!t.entity.isAttachedTo(level.root)) {
                continue;
            }
            boolean again;
            do {
                again = false;
                byte[] code = t.programs[t.pc];
                if (code.length == 0) {
                    t.pc++;
                    again = t.pc < t.programs.length;
                    continue;
                }
                if (!execute(t, code, dtMs)) {
                    break;
                }
                if (t.pc < -1) {
                    t.pc = -1;
                }
                t.pc++;
                if (t.pc >= t.programs.length) {
                    finish(t);
                } else {
                    again = true;
                }
            } while (again);
        }
    }

    private void finish(Trigger t) {
        clearCameraTarget();
        if (t.data.modeB == 1) {
            t.state = Trigger.IDLE;
            t.resetPrograms();
            log(t, "trigger " + t.entity.index + " completado (repetible)");
        } else {
            t.state = Trigger.DISABLED;
            log(t, "trigger " + t.entity.index + " completado (una vez)");
        }
    }

    private void clearCameraTarget() {
        if (cameraTarget != null) {
            cameraTarget = null;
            resetCameraSpring = true;
        }
    }

    // ------------------------------------------------------------------ opcodes

    /** @return {@code true} si el programa puede seguir con la instruccion siguiente. */
    private boolean execute(Trigger t, byte[] c, int dtMs) {
        int op = c[0] & 0xFF;
        switch (op) {
            case OP_SOUND:
                if (sounds != null) {
                    sounds.playSound(s16(c, 1));
                }
                return true;

            case OP_ANIMATE: {
                Entity e = level.find(s16(c, 1));
                if (e != null) {
                    e.animationTime = 0f;
                }
                return true;
            }

            case OP_SET_IDLE:
                setTriggerState(c[1] & 0xFF, Trigger.IDLE);
                return true;
            case OP_SET_DISABLED:
                setTriggerState(c[1] & 0xFF, Trigger.DISABLED);
                return true;
            case OP_SET_ARMED:
                setTriggerState(c[1] & 0xFF, Trigger.ARMED);
                return true;
            case OP_SET_PAUSED:
                setTriggerState(c[1] & 0xFF, Trigger.PAUSED);
                return true;

            case OP_WAIT: {
                int remaining = s16(c, 3);
                if (remaining >= 0) {
                    put16(c, 3, remaining - dtMs);
                    return false;
                }
                c[3] = c[1];
                c[4] = c[2];
                return true;
            }

            case OP_SET:
                if (c[1] == 1) {
                    setFloat(c[2] & 0xFF, floatOperand(c));
                } else if (c[1] == 2) {
                    vars[c[2] & 0xFF] = intOperand(c);
                }
                return true;
            case OP_ADD:
            case OP_SUB:
            case OP_MUL:
            case OP_DIV:
                return arithmetic(t, c, op);

            case OP_EQ:
            case OP_NE:
            case OP_LT:
            case OP_GT:
                return compare(t, c, op);

            case OP_MOVE:
                return move(t, c, dtMs);
            case OP_ROTATE:
                return rotate(t, c, dtMs);

            case OP_SET_POS:
                return setPosition(c);

            case OP_REPARENT: {
                Entity child = level.find(s16(c, 1));
                Entity parent = level.find(s16(c, 3));
                if (child != null && parent != null) {
                    level.reparent(child, parent);
                }
                return true;
            }

            case OP_DELETE: {
                Entity e = level.find(s16(c, 1));
                if (e != null) {
                    level.detach(e);
                }
                return true;
            }

            case OP_JUMP:
                t.pc = (c[1] & 0xFF) - 2;
                return true;

            case OP_NOP:
                return true;

            case OP_END:
                clearCameraTarget();
                if (t.data.modeB == 1) {
                    t.state = Trigger.IDLE;
                } else {
                    t.state = Trigger.DISABLED;
                }
                return false;

            case OP_IF_LEFT:
                // "continuar solo si el objeto no estaba dentro el frame anterior".
                return !t.insidePrev;

            case OP_CHECKPOINT:
                checkpointX = t.entity.worldX();
                checkpointY = t.entity.worldY();
                return true;

            case OP_KICK: {
                if (isBall(s16(c, 1))) {
                    ball.kickX += s16(c, 3);
                    ball.kickY += s16(c, 5);
                }
                return true;
            }
            case OP_FORCE: {
                if (isBall(s16(c, 1))) {
                    ball.forceX += s16(c, 3);
                    ball.forceY += s16(c, 5);
                }
                return true;
            }
            case OP_VELOCITY: {
                if (isBall(s16(c, 1))) {
                    ball.vx += s16(c, 3);
                    ball.vy += s16(c, 5);
                }
                return true;
            }

            case OP_FLAGS:
                return flags(c);

            case OP_CAMERA: {
                Entity target = level.find(s16(c, 1));
                if (target == level.ball) {
                    if (cameraTarget != null) {
                        resetCameraSpring = true;
                    }
                    cameraTarget = null;
                } else {
                    if (cameraTarget != target) {
                        resetCameraSpring = true;
                    }
                    cameraTarget = target;
                }
                return true;
            }

            case OP_SPRING:
                snapCamera = c[1] == 1;
                springStiffness = s16(c, 2);
                springDamping = s16(c, 4);
                return true;

            case OP_SPRING_RESET:
                clearCameraTarget();
                resetCameraSpring = true;
                snapCamera = false;
                springStiffness = 90f;
                springDamping = 140f;
                return true;

            default:
                return true;
        }
    }

    private void setTriggerState(int index, int state) {
        if (index >= 0 && index < triggers.size) {
            triggers.get(index).setState(state);
        }
    }

    private boolean isBall(int id) {
        return level.ball != null && level.ball.index == id;
    }

    private boolean arithmetic(Trigger t, byte[] c, int op) {
        int index = c[2] & 0xFF;
        if (c[1] == 1) {
            float v = floatOperand(c);
            float current = Float.intBitsToFloat(vars[index]);
            switch (op) {
                case OP_ADD:
                    setFloat(index, current + v);
                    break;
                case OP_SUB:
                    setFloat(index, current - v);
                    break;
                case OP_MUL:
                    setFloat(index, current * v);
                    break;
                default:
                    setFloat(index, v == 0f ? 0f : current / v);
                    break;
            }
        } else if (c[1] == 2) {
            int v = intOperand(c);
            switch (op) {
                case OP_ADD:
                    vars[index] += v;
                    break;
                case OP_SUB:
                    vars[index] -= v;
                    break;
                case OP_MUL:
                    vars[index] *= v;
                    break;
                default:
                    if (v != 0) {
                        vars[index] /= v;
                    }
                    break;
            }
        }
        return true;
    }

    private boolean compare(Trigger t, byte[] c, int op) {
        int index = c[2] & 0xFF;
        boolean condition;
        if (c[1] == 2) {
            int v = intOperand(c);
            int current = vars[index];
            condition = op == OP_EQ ? current == v
                    : op == OP_NE ? current != v
                    : op == OP_LT ? current < v
                    : current > v;
        } else if (c[1] == 1) {
            float v = floatOperand(c);
            float current = Float.intBitsToFloat(vars[index]);
            condition = op == OP_EQ ? current == v
                    : op == OP_NE ? current != v
                    : op == OP_LT ? current < v
                    : current > v;
        } else {
            condition = false;
        }
        if (condition) {
            return true;
        }
        // El bytecode guarda el destino como pc+1; el bucle incrementa despues.
        t.pc = (c[operandOffset] & 0xFF) - 2;
        return true;
    }

    /** {@code case 16}: desplaza una entidad a velocidad constante durante un tiempo. */
    private boolean move(Trigger t, byte[] c, int dtMs) {
        Entity e = level.find(s16(c, 1));
        if (e == null) {
            return true;
        }
        int remaining = s32(c, 15);
        if (remaining - dtMs <= 0) {
            System.arraycopy(c, 11, c, 15, 4);
            dtMs = remaining;
        }
        float dx = s32(c, 3) / 65536f;
        float dy = s32(c, 7) / 65536f;
        e.local.m02 += dx * dtMs;
        e.local.m12 += dy * dtMs;
        e.updateWorldRecursive();
        level.geometryDirty = true;

        int left = remaining - dtMs;
        if (left <= 0) {
            return true;
        }
        put32(c, 15, left);
        return false;
    }

    /** {@code case 17}: gira una entidad durante un tiempo (interpolacion absoluta). */
    private boolean rotate(Trigger t, byte[] c, int dtMs) {
        Entity e = level.find(s16(c, 1));
        if (e == null) {
            return true;
        }
        int total = s32(c, 7);
        int remaining = s32(c, 11);
        if (remaining == total) {
            put32(c, 15, Float.floatToRawIntBits(e.local.m00));
            put32(c, 19, Float.floatToRawIntBits(e.local.m01));
            put32(c, 23, Float.floatToRawIntBits(e.local.m10));
            put32(c, 27, Float.floatToRawIntBits(e.local.m11));
        }
        if (remaining - dtMs <= 0) {
            System.arraycopy(c, 7, c, 11, 4);
            dtMs = remaining;
        }
        float progress = total == 0 ? 1f : (total - (remaining - dtMs)) / (float) total;
        float angle = (s32(c, 3) / 65536f) * progress;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float a0 = Float.intBitsToFloat(s32(c, 15));
        float b0 = Float.intBitsToFloat(s32(c, 19));
        float d0 = Float.intBitsToFloat(s32(c, 23));
        float e0 = Float.intBitsToFloat(s32(c, 27));
        e.local.m00 = a0 * cos + b0 * sin;
        e.local.m01 = -a0 * sin + b0 * cos;
        e.local.m10 = d0 * cos + e0 * sin;
        e.local.m11 = -d0 * sin + e0 * cos;
        e.updateWorldRecursive();
        level.geometryDirty = true;

        int left = remaining - dtMs;
        if (left <= 0) {
            return true;
        }
        put32(c, 11, left);
        return false;
    }

    /** {@code case 18}: coloca una entidad en una posicion absoluta o en la de otra. */
    private boolean setPosition(byte[] c) {
        Entity e = level.find(s16(c, 1));
        if (e == null) {
            return true;
        }
        int other = s16(c, 3);
        if (other < 0) {
            e.local.m02 = s32(c, 5) / 65536f;
            e.local.m12 = s32(c, 9) / 65536f;
        } else {
            Entity src = level.find(other);
            if (src != null) {
                float sx = src.worldX();
                float sy = src.worldY();
                // El original pone a cero la traslacion local, calcula el mundo y aplica la
                // inversa para dejar la entidad exactamente encima de la otra.
                e.local.m02 = 0f;
                e.local.m12 = 0f;
                e.computeWorld();
                float det = e.world.m00 * e.world.m11 - e.world.m01 * e.world.m10;
                if (Math.abs(det) > 1e-9f) {
                    float inv = 1f / det;
                    float dx = sx - e.world.m02;
                    float dy = sy - e.world.m12;
                    e.local.m02 = (e.world.m11 * dx - e.world.m01 * dy) * inv;
                    e.local.m12 = (-e.world.m10 * dx + e.world.m00 * dy) * inv;
                }
            }
        }
        e.updateWorldRecursive();
        level.geometryDirty = true;
        return true;
    }

    /** {@code case 29}: cambia depth / skip_branch / flag256 de una entidad. */
    private boolean flags(byte[] c) {
        Entity e = level.find(s16(c, 1));
        if (e == null) {
            return true;
        }
        int mask = s32(c, 3);
        int value = s32(c, 7);
        if ((mask & 1) != 0) {
            e.depth = (value & 31) - 16;
        }
        if ((mask & 32) != 0) {
            e.solid = (value & 32) != 0;
        }
        if ((mask & 128) != 0) {
            e.skipBranch = (value & 128) != 0;
        }
        if ((mask & 256) != 0) {
            e.flag256 = (value & 256) != 0;
        }
        return true;
    }

    // ------------------------------------------------------------------ variables

    private void setFloat(int index, float value) {
        if (index >= 0 && index < vars.length) {
            vars[index] = Float.floatToRawIntBits(value);
        }
    }

    /** {@code g.c(byte[], 3)}. */
    private int intOperand(byte[] c) {
        int type = c[3] & 0xFF;
        operandOffset = 4;
        if (type == 2) {
            operandOffset = 5;
            return vars[c[4] & 0xFF];
        }
        if (type == 32) {
            operandOffset = 8;
            return s32(c, 4);
        }
        return 0;
    }

    /** {@code g.a(byte[], 3)}. */
    private float floatOperand(byte[] c) {
        int type = c[3] & 0xFF;
        operandOffset = 4;
        if (type == 1 || type == 2) {
            operandOffset = 5;
            return Float.intBitsToFloat(vars[c[4] & 0xFF]);
        }
        if (type == 16) {
            operandOffset = 8;
            return Float.intBitsToFloat(s32(c, 4));
        }
        if (type == 32) {
            operandOffset = 8;
            return s32(c, 4);
        }
        return 0f;
    }

    private static int s16(byte[] b, int i) {
        return (short) ((b[i] << 8) | (b[i + 1] & 0xFF));
    }

    private static int s32(byte[] b, int i) {
        return (b[i] << 24) | ((b[i + 1] & 0xFF) << 16)
                | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    private static void put16(byte[] b, int i, int value) {
        b[i] = (byte) (value >> 8);
        b[i + 1] = (byte) value;
    }

    private static void put32(byte[] b, int i, int value) {
        b[i] = (byte) (value >>> 24);
        b[i + 1] = (byte) (value >> 16);
        b[i + 2] = (byte) (value >> 8);
        b[i + 3] = (byte) value;
    }

    public void reset() {
        for (Trigger t : triggers) {
            t.reset();
        }
        cameraTarget = null;
        springStiffness = 90f;
        springDamping = 140f;
        snapCamera = false;
        lastLogged = -1;
    }
}
