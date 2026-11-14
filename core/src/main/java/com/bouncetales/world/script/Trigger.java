package com.bouncetales.world.script;

import com.bouncetales.world.Entity;
import com.bouncetales.world.TriggerData;

/**
 * Trigger del nivel (tag 6, clase {@code g} del original).
 *
 * <p>Maquina de estados de {@code g.a(int)}:
 * <pre>
 *   0 idle      --(2)--&gt; 2 armado     (pc = 0)
 *   0 idle      --(1)--&gt; 1 deshabilitado
 *   1 disabled  --(0)--&gt; 0 idle        (reinicia los programas)
 *   2 armado    --(1)--&gt; 1 deshabilitado
 *   2 armado    --(3)--&gt; 3 pausado
 *   3 pausado   --(2)--&gt; 2 armado
 * </pre>
 *
 * <p>Los programas se guardan como copia mutable porque el original escribe en el propio
 * bytecode los contadores de espera/movimiento ({@code g.b()}).
 */
public final class Trigger {

    public static final int IDLE = 0;
    public static final int DISABLED = 1;
    public static final int ARMED = 2;
    public static final int PAUSED = 3;

    public final Entity entity;
    public final TriggerData data;

    /** Copia mutable de los programas. */
    public final byte[][] programs;

    public int state;
    /** {@code g}: programa actual; -1 = ninguno. */
    public int pc = -1;

    /** La bola esta dentro este frame / el anterior. */
    public boolean inside;
    public boolean insidePrev;

    public Trigger(Entity entity) {
        this.entity = entity;
        this.data = entity.triggerData;
        this.programs = new byte[data.programs.length][];
        for (int i = 0; i < programs.length; i++) {
            programs[i] = data.programs[i].clone();
        }
        this.state = data.state;
        if (state == ARMED) {
            pc = 0;
        }
    }

    public int programCount() {
        return programs.length;
    }

    /** {@code g.a(int)}. */
    public void setState(int target) {
        switch (state) {
            case IDLE:
                if (target == ARMED) {
                    state = ARMED;
                    pc = 0;
                } else if (target == DISABLED) {
                    state = DISABLED;
                }
                break;
            case DISABLED:
                if (target == IDLE) {
                    state = IDLE;
                    resetPrograms();
                }
                break;
            case ARMED:
                if (target == DISABLED) {
                    state = DISABLED;
                } else if (target == PAUSED) {
                    state = PAUSED;
                }
                break;
            case PAUSED:
                if (target == ARMED) {
                    state = ARMED;
                }
                break;
            default:
                break;
        }
    }

    /** {@code g.b()}: devuelve los programas a su estado de carga. */
    public void resetPrograms() {
        for (int i = 0; i < programs.length; i++) {
            System.arraycopy(data.programs[i], 0, programs[i], 0, programs[i].length);
        }
        pc = -1;
    }

    public void reset() {
        resetPrograms();
        state = data.state;
        if (state == ARMED) {
            pc = 0;
        }
        inside = false;
        insidePrev = false;
    }

    public boolean isArmed() {
        return state == ARMED;
    }
}
