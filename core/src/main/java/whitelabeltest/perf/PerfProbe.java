package whitelabeltest.perf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.profiling.GLProfiler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opt-in per-frame performance log - off unless the game is launched with -Dperf.log=<csv path> (see
 *  lwjgl3/build.gradle's -PperfLog). When on, every frame writes one CSV row: how long each part of the
 *  frame took on the CPU, how long the frame waited outside render() (buffer swap, vsync, the FPS limiter
 *  and any GPU back-pressure), GPU work submitted (draw calls, texture binds, shader switches, from
 *  libGDX's GLProfiler), what was on screen, sounds started, and garbage-collection activity. When off,
 *  every call here is a single static boolean check.
 *
 *  Sections are timed with begin()/end() pairs from Main/GameController; nesting is fine (a section's time
 *  includes anything timed inside it). */
public final class PerfProbe {
    public enum Section { UPDATE, TRIGGERS, BACKGROUND_UPDATE, ENTITY_UPDATE, COLLISIONS, DRAW, BACKGROUND_DRAW, FEEDBACK_DRAW, ENTITY_DRAW, HUD_DRAW }

    public static final boolean ENABLED = System.getProperty("perf.log") != null;
    // -Dperf.invincible: the player ignores hits (see GameController.applyPlayerHit()) - for profiling a whole stage
    // off a replay that no longer plays back in sync.
    public static final boolean INVINCIBLE = System.getProperty("perf.invincible") != null;

    private static final Section[] SECTIONS = Section.values();
    private static final long[] sectionNanos = new long[SECTIONS.length];
    private static final long[] sectionStart = new long[SECTIONS.length];
    private static GLProfiler glProfiler;
    private static BufferedWriter out;
    private static List<GarbageCollectorMXBean> collectors;
    private static long lastGcCount, lastGcMillis;
    private static long frameStartNanos = -1, renderEndNanos = -1;
    private static long frameIndex;
    private static int sounds;
    private static int enemies, enemyBullets, playerBullets, gems, effects;
    private static float stageDistance = Float.NaN;

    private PerfProbe() {}

    /** Call once the GL context exists (Main.create()). */
    public static void init() {
        if (!ENABLED) return;
        try {
            Path path = Path.of(System.getProperty("perf.log"));
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            out = Files.newBufferedWriter(path);
            StringBuilder header = new StringBuilder("frame,frameMs,outsideRenderMs");
            for (Section s : SECTIONS) header.append(',').append(s.name().toLowerCase()).append("Ms");
            header.append(",drawCalls,textureBinds,shaderSwitches,vertices,enemies,enemyBullets,playerBullets,gems,effects,sounds,gcCount,gcMs,heapMB,distance");
            out.write(header.toString());
            out.newLine();
        } catch (IOException e) {
            Gdx.app.error("PerfProbe", "can't open perf log", e);
            return;
        }
        glProfiler = new GLProfiler(Gdx.graphics);
        glProfiler.enable();
        collectors = ManagementFactory.getGarbageCollectorMXBeans();
        long[] gc = gcTotals();
        lastGcCount = gc[0];
        lastGcMillis = gc[1];
        Gdx.app.log("PerfProbe", "logging per-frame performance to " + System.getProperty("perf.log")
            + " | display mode " + Gdx.graphics.getDisplayMode());
    }

    /** Top of Main.render(). */
    public static void frameStart() {
        if (!ENABLED || out == null) return;
        long now = System.nanoTime();
        if (frameStartNanos >= 0) writeRow(now);
        frameStartNanos = now;
        java.util.Arrays.fill(sectionNanos, 0L);
        sounds = 0;
        glProfiler.reset();
    }

    /** Bottom of Main.render() - everything after this until the next frameStart() is outside render(). */
    public static void frameEnd() {
        if (!ENABLED) return;
        renderEndNanos = System.nanoTime();
    }

    public static void begin(Section s) {
        if (ENABLED) sectionStart[s.ordinal()] = System.nanoTime();
    }

    public static void end(Section s) {
        if (ENABLED) sectionNanos[s.ordinal()] += System.nanoTime() - sectionStart[s.ordinal()];
    }

    public static void soundStarted() {
        if (ENABLED) sounds++;
    }

    public static void counts(int enemies, int enemyBullets, int playerBullets, int gems, int effects, float distance) {
        if (!ENABLED) return;
        PerfProbe.enemies = enemies;
        PerfProbe.enemyBullets = enemyBullets;
        PerfProbe.playerBullets = playerBullets;
        PerfProbe.gems = gems;
        PerfProbe.effects = effects;
        PerfProbe.stageDistance = distance;
    }

    private static void writeRow(long now) {
        long[] gc = gcTotals();
        StringBuilder row = new StringBuilder(256);
        row.append(frameIndex++).append(',').append(ms(now - frameStartNanos)).append(',')
            .append(renderEndNanos >= frameStartNanos ? ms(now - renderEndNanos) : "");
        for (int i = 0; i < SECTIONS.length; i++) row.append(',').append(ms(sectionNanos[i]));
        Runtime rt = Runtime.getRuntime();
        row.append(',').append(glProfiler.getDrawCalls()).append(',').append(glProfiler.getTextureBindings())
            .append(',').append(glProfiler.getShaderSwitches()).append(',').append((long) glProfiler.getVertexCount().total)
            .append(',').append(enemies).append(',').append(enemyBullets).append(',').append(playerBullets)
            .append(',').append(gems).append(',').append(effects).append(',').append(sounds)
            .append(',').append(gc[0] - lastGcCount).append(',').append(gc[1] - lastGcMillis)
            .append(',').append((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024))
            .append(',').append(Float.isNaN(stageDistance) ? "" : String.valueOf(stageDistance));
        lastGcCount = gc[0];
        lastGcMillis = gc[1];
        try {
            out.write(row.toString());
            out.newLine();
            if (frameIndex % 600 == 0) out.flush();
        } catch (IOException ignored) {
            // a failed log row is never worth interrupting the game over
        }
    }

    private static String ms(long nanos) {
        return String.valueOf(Math.round(nanos / 10_000.0) / 100.0);
    }

    private static long[] gcTotals() {
        long count = 0, millis = 0;
        for (GarbageCollectorMXBean gc : collectors) {
            count += Math.max(0, gc.getCollectionCount());
            millis += Math.max(0, gc.getCollectionTime());
        }
        return new long[]{count, millis};
    }

    /** Main.dispose(). */
    public static void close() {
        if (out == null) return;
        try {
            out.flush();
            out.close();
        } catch (IOException ignored) {
        }
        out = null;
    }
}
