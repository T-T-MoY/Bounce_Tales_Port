package com.bouncetales.asset;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bouncetales.world.DecorationList;
import com.bouncetales.world.Entity;
import com.bouncetales.world.Level;
import com.bouncetales.world.PolygonShape;
import com.bouncetales.world.TriggerData;
import com.bouncetales.world.TubeData;
import com.bouncetales.world.WaterData;

/**
 * Carga un nivel desde el JSON que genera {@code tools/bounce_levels.py}.
 *
 * <p>El JSON es una traduccion 1:1 del contenedor binario:
 * <pre>
 *   cabecera (14 B): s16 @8 = n objetos, s16 @12 = n triggers
 *   registros [u8 tag][u16 longitud][payload]
 * </pre>
 * Por eso el loader solo tiene que reconstruir el arbol y los payloads; no hay logica de
 * decodificacion binaria aqui.
 */
public final class LevelLoader {

    private LevelLoader() {
    }

    public static Level load(String json) {
        JsonValue root = new JsonReader().parse(json);
        JsonValue header = root.get("header");

        Level level = new Level(
                root.getString("source", "?"),
                header == null ? 0 : header.getInt("object_count", 0),
                header == null ? 0 : header.getInt("trigger_count", 0));

        JsonValue objects = root.get("objects");
        if (objects != null) {
            for (JsonValue v = objects.child; v != null; v = v.next) {
                level.entities.add(parseEntity(v));
            }
        }

        // Enlaces padre/hijo. Se hace despues de crear todas las entidades porque el padre
        // puede aparecer mas tarde en el fichero.
        for (Entity e : level.entities) {
            int p = e.linkParent;
            if (p >= 0 && p < level.entities.size && p != e.index) {
                e.parent = level.entities.get(p);
            }
            e.initialParent = e.parent;
            e.initialLocal.set(e.local);
            e.initialDepth = e.depth;
            e.initialSkipBranch = e.skipBranch;
            e.initialFlag256 = e.flag256;
        }

        level.link();
        return level;
    }

    private static Entity parseEntity(JsonValue v) {
        Entity e = new Entity();
        e.index = v.getInt("index", 0);
        e.tag = v.getInt("tag", 0);
        e.depth = v.getInt("depth", 0);
        e.solid = v.getBoolean("solid", false);
        e.skipBranch = v.getBoolean("skip_branch", false);
        e.linkParent = v.getInt("link_parent", -1);
        e.linkChild = v.getInt("link_child", -1);

        parseTransform(v.get("transform"), e);

        JsonValue polygon = v.get("polygon");
        if (polygon != null) {
            e.polygon = parsePolygon(polygon);
        }

        JsonValue sprites = v.get("sprites");
        if (sprites != null) {
            e.decorations = parseDecorations(sprites);
        }

        JsonValue pad = v.get("pad");
        if (pad != null) {
            e.padSprite = pad.getInt("sprite", -1);
            e.padStrength = pad.getInt("strength", 0);
        }

        JsonValue enemy = v.get("enemy");
        if (enemy != null) {
            e.enemyKind = enemy.getInt("kind", -1);
            e.enemyPath = new float[]{
                    enemy.getFloat("x0", 0f), enemy.getFloat("y0", 0f),
                    enemy.getFloat("x1", 0f), enemy.getFloat("y1", 0f)};
        }

        JsonValue trigger = v.get("trigger");
        if (trigger != null) {
            e.triggerData = parseTrigger(trigger);
        }

        JsonValue water = v.get("beam");
        if (water != null) {
            // El exportador llama "beam" al tag 10, pero la clase original es `i`: agua.
            e.waterData = parseWater(water);
        }

        JsonValue tube = v.get("tube");
        if (tube == null) {
            // Admite los niveles exportados antes de corregir el nombre del tag 11.
            tube = v.get("checkpoint");
        }
        if (e.tag == Entity.TAG_TUBE && tube != null) {
            e.tubeData = parseTube(tube);
        }

        e.ball = e.tag == Entity.TAG_BALL;
        if (e.ball) {
            e.ballType = 0;
        }
        return e;
    }

    private static TubeData parseTube(JsonValue t) {
        TubeData d = new TubeData();
        d.targetObject = t.getInt("target_object", -1);
        d.power = t.getInt("power", 0);
        return d;
    }

    /** {@code g.a(byte[], int)}. */
    private static TriggerData parseTrigger(JsonValue t) {
        TriggerData d = new TriggerData();
        JsonValue box = t.get("box");
        d.minX = box.getFloat("min_x", 0f);
        d.minY = box.getFloat("min_y", 0f);
        d.maxX = box.getFloat("max_x", 0f);
        d.maxY = box.getFloat("max_y", 0f);
        d.state = t.getInt("state", 0);
        d.modeA = t.getInt("mode_a", 0);
        d.modeB = t.getInt("mode_b", 0);
        d.filterObject = t.getInt("filter_object", -1);

        JsonValue programs = t.get("programs");
        d.programs = new byte[programs == null ? 0 : programs.size][];
        if (programs != null) {
            int i = 0;
            for (JsonValue p = programs.child; p != null; p = p.next) {
                byte[] code = new byte[p.size];
                int j = 0;
                for (JsonValue b = p.child; b != null; b = b.next) {
                    code[j++] = (byte) (b.asInt() & 0xFF);
                }
                d.programs[i++] = code;
            }
        }
        return d;
    }

    /**
     * {@code i.a(byte[], int)}. El exportador guarda los campos en orden de lectura, asi que
     * el segundo (x1 en el JSON) es el fondo, el cuarto (y) la superficie y los p0..p3 son
     * las corrientes que el original interpola dentro de la caja.
     */
    private static WaterData parseWater(JsonValue b) {
        WaterData d = new WaterData();
        d.x0 = b.getFloat("x0", 0f);
        d.bottom = b.getFloat("x1", 0f);
        d.x1 = b.getFloat("x2", 0f);
        d.surface = b.getFloat("y", 0f);
        d.currentSurfaceY = b.getFloat("p0", 0f);
        d.currentRightX = b.getFloat("p1", 0f);
        d.currentBottomY = b.getFloat("p2", 0f);
        d.currentLeftX = b.getFloat("p3", 0f);

        JsonValue color = b.get("color");
        int rgb = 0;
        if (color != null && color.size >= 3) {
            rgb = ((color.get(0).asInt() & 0xFF) << 16)
                    | ((color.get(1).asInt() & 0xFF) << 8)
                    | (color.get(2).asInt() & 0xFF);
        }
        // i.a(...): si el color no es negro se fuerza 0x441111EE; si es negro queda invisible.
        d.colorArgb = rgb == 0 ? 0x44000000 : 0x441111EE;
        // i.g == 1 cuando el canal azul del fichero vale 16: agua plana sin ondas.
        d.solid = color != null && color.size >= 3 && (color.get(2).asInt() & 0xFF) == 16;
        return d;
    }

    private static void parseTransform(JsonValue t, Entity e) {
        e.local.idt();
        if (t == null) {
            return;
        }
        int flags = t.getInt("flags", 0);
        if ((flags & 7) == 7) {
            JsonValue m = t.get("matrix");
            e.local.m00 = m.get(0).asFloat();
            e.local.m01 = m.get(1).asFloat();
            e.local.m02 = m.get(2).asFloat();
            e.local.m10 = m.get(3).asFloat();
            e.local.m11 = m.get(4).asFloat();
            e.local.m12 = m.get(5).asFloat();
            return;
        }

        // Reproduce k.a(byte[], int): la rotacion escribe la matriz completa y la escala
        // sobrescribe solo la diagonal (por eso, si vienen las dos, la rotacion se pierde).
        if ((flags & 2) != 0) {
            float rot = t.getFloat("rotation", 0f);
            float c = MathUtils.cos(rot);
            float s = MathUtils.sin(rot);
            e.local.m00 = c;
            e.local.m01 = -s;
            e.local.m10 = s;
            e.local.m11 = c;
        }
        if ((flags & 4) != 0) {
            e.local.m00 = t.getFloat("scale_x", 1f);
            e.local.m11 = t.getFloat("scale_y", 1f);
        }
        if ((flags & 1) != 0) {
            e.local.m02 = t.getFloat("x", 0f);
            e.local.m12 = t.getFloat("y", 0f);
        }
    }

    private static PolygonShape parsePolygon(JsonValue p) {
        JsonValue verts = p.get("vertices");
        float[] vertices = new float[verts.size * 2];
        int i = 0;
        for (JsonValue vert = verts.child; vert != null; vert = vert.next) {
            vertices[i++] = vert.get(0).asFloat();
            vertices[i++] = vert.get(1).asFloat();
        }

        JsonValue tris = p.get("triangles");
        short[] triangles = new short[tris.size * 3];
        i = 0;
        for (JsonValue tri = tris.child; tri != null; tri = tri.next) {
            triangles[i++] = (short) tri.get(0).asInt();
            triangles[i++] = (short) tri.get(1).asInt();
            triangles[i++] = (short) tri.get(2).asInt();
        }

        JsonValue color = p.get("color_argb");
        int argb = ((color.get(0).asInt() & 0xFF) << 24)
                | ((color.get(1).asInt() & 0xFF) << 16)
                | ((color.get(2).asInt() & 0xFF) << 8)
                | (color.get(3).asInt() & 0xFF);

        return new PolygonShape(vertices, triangles, argb, (short) p.getInt("trigger_id", -1));
    }

    private static DecorationList parseDecorations(JsonValue list) {
        int n = list.size;
        int[] ids = new int[n];
        float[] xs = new float[n];
        float[] ys = new float[n];
        int i = 0;
        for (JsonValue s = list.child; s != null; s = s.next) {
            ids[i] = s.getInt("sprite", -1);
            xs[i] = s.getFloat("x", 0f);
            ys[i] = s.getFloat("y", 0f);
            i++;
        }
        return new DecorationList(ids, xs, ys);
    }
}
