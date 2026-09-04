package whitelabeltest.editor;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import whitelabeltest.enemy.BulletSpeedPhase;
import whitelabeltest.enemy.FiringPatternDef;

import java.util.ArrayList;
import java.util.List;

/** A live-ticking preview of one FiringPatternDef's shot pattern - Play/Pause/Reset-controlled,
 *  driven by a JavaFX AnimationTimer, painted with plain Canvas shapes.
 *
 * This does NOT run the real game's own FiringPattern/EnemyBullet classes (PatternFactory.
 * createFiring() and friends) - that machinery turns out to be a dead end outside the real game:
 * every concrete bullet's init() eventually calls `new Sprite(textureRegion)`, whose constructor
 * (Sprite.<init> -> TextureRegion.setRegion(TextureRegion) -> setRegion(u,v,u2,v2)) unconditionally
 * dereferences that region's backing Texture to recompute its own width/height - there's no
 * "texture-less" region that survives this (found by actually running it: "Cannot invoke
 * Texture.getWidth() because this.texture is null" - even after routing around TWO earlier NPEs/
 * ClassCastExceptions the same way). And a REAL Texture can't be built here either: Texture's
 * constructors bottom out in GLTexture(int) allocating a handle via Gdx.gl.glGenTexture() -
 * Gdx.gl is only ever set by a running LibGDX backend (LWJGL3, headless, ...), none of which this
 * plain JavaFX process ever starts (see MovementPatternLibrary's own doc on the same GL-context
 * gap for movement, and PlayerPreviewView, which only ever gets away with a bare Sprite/Rectangle
 * because it NEVER constructs one from a TextureRegion). Embedding a real (even headless) LibGDX
 * backend just for a preview bullet's placeholder texture was judged not worth the fragility.
 *
 * Instead, this reimplements each pattern type's own spawn/motion shape directly against plain
 * doubles and a local PreviewBullet list - the same fireRate/spread/aim/sweep/orbit/wall formulas
 * PatternFactory.createFiring()'s dispatch already encodes (see each spawnXxx() method's own doc
 * for which real class it mirrors), just without needing a real Sprite/Texture/Animation to do it.
 * This is an approximation of the real bullets' exact physics (respects fireRate/spread/bulletSpeed/
 * bulletSize/bulletAcceleration/min-max speed; doesn't reproduce every last hitbox/collision nuance
 * a real EnemyBullet has, since none of that is visible in a preview anyway) - good enough to
 * sanity-check a pattern's shape and timing while authoring it; the in-game debug menu's own
 * PatternPreviewer remains the byte-for-byte-accurate way to see the real bullets fly. */
final class FiringPatternPreviewCanvas extends Canvas {
    private static final double WORLD_WIDTH = 9.0;
    private static final double WORLD_HEIGHT = 12.0;
    private static final double SCALE = 30;
    private static final float PLAYER_HALF_PERIOD = 1.6f; // seconds for one left->right (or right->left) sweep
    private static final float PLAYER_MARGIN = 0.6f;
    private static final float PLAYER_Y = 1.2f;
    private static final float ENEMY_Y = (float) WORLD_HEIGHT - 1.6f;
    private static final float MAX_STEP = 0.05f; // clamps a debugger-pause/tab-switch stall to a sane single step

    private enum Kind { STRAIGHT, SINE, LASER }

    private static final class PreviewBullet {
        Kind kind;
        float x, y;
        float angleDeg;
        float speed;
        float size;
        float age;
        // Mirrors SpeedRamp/SpeedProfile's own phase-cycling ramp exactly (see applyRamp()'s own
        // doc) - either a single {bulletAcceleration} phase held forever, or the pattern's own
        // authored bulletSpeedPhases sequence, whichever PatternFactory.speedProfile() would have
        // picked. `rampable` is false for Orbiting's bullets specifically - the real engine never
        // threads a SpeedProfile through OrbitingFiring at all (its own "speed" is a constant
        // center-drift vector, not a ramping travel speed - see OrbitingFiring's own doc).
        boolean rampable = true;
        float[] accelerations = { 0f };
        float[] durations = { -1f };
        boolean loop;
        float minSpeed, maxSpeed = Float.MAX_VALUE;
        int phaseIndex;
        float phaseTimer;
        // SINE-only: the straight-line path the wave is drawn around, plus how far along it this
        // bullet has actually traveled so far - accumulated frame-by-frame (not a closed-form
        // speed*age) specifically so a ramping speed (see above) bends this bullet's effective
        // wavelength the same way it would in the real game, instead of silently ignoring the ramp.
        float originX, originY, amplitude, frequency, traveled;
        // LASER-only: seconds remaining before this beam despawns, and its own rotation rate.
        float remaining, angularSpeed, length;
    }

    /** One pattern's own running state (fire timer, and for Sequence/Combined, its children's own
     *  running state) - built fresh by rebuild() every time `def` changes, since a Sequence's
     *  current stage/a leaf's shootTimer wouldn't mean anything against a differently-shaped tree. */
    private static final class RunningPattern {
        FiringPatternDef def;
        float timer;
        float stageTime;
        int stageIndex;
        List<RunningPattern> children;
    }

    private final List<PreviewBullet> bullets = new ArrayList<>();
    private final AnimationTimer timer;

    private FiringPatternDef def;
    private RunningPattern running;
    private float elapsed;
    private float playerX, playerY = PLAYER_Y;
    private float enemyX, enemyY = ENEMY_Y;
    private boolean playing;
    private long lastNanos = -1;

    FiringPatternPreviewCanvas() {
        super(WORLD_WIDTH * SCALE, WORLD_HEIGHT * SCALE);
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNanos < 0) { lastNanos = now; return; }
                float delta = Math.min((now - lastNanos) / 1_000_000_000f, MAX_STEP);
                lastNanos = now;
                step(delta);
                redraw();
            }
        };
        layout(0f);
        redraw();
    }

    /** Loads a new/edited pattern and restarts the preview from a clean slate. Auto-plays,
     *  matching the reference screenshots' own default (their pause icon is shown highlighted,
     *  implying playback already running when the Weapon Editor opens). */
    void setPattern(FiringPatternDef def) {
        this.def = def;
        rebuild();
        play();
    }

    /** Same reset-and-rebuild as setPattern(), for a field edit on the pattern ALREADY showing.
     *  Deliberately does NOT force playback back on: a paused, mid-tweak session (nudging a value
     *  while eyeballing one frame) shouldn't leap back into motion under the user on every
     *  keystroke. */
    void onFieldChanged() {
        rebuild();
        redraw();
    }

    void play() {
        playing = true;
        lastNanos = -1;
        timer.start();
    }

    void pause() {
        playing = false;
        timer.stop();
    }

    void reset() {
        pause();
        rebuild();
        redraw();
    }

    private void rebuild() {
        bullets.clear();
        elapsed = 0f;
        running = def != null ? buildRunning(def) : null;
        layout(0f);
    }

    private RunningPattern buildRunning(FiringPatternDef def) {
        RunningPattern r = new RunningPattern();
        r.def = def;
        if (("Sequence".equals(def.type) || "Combined".equals(def.type)) && def.patterns != null) {
            r.children = new ArrayList<>();
            for (FiringPatternDef sub : def.patterns) r.children.add(buildRunning(sub));
        }
        return r;
    }

    private void step(float delta) {
        if (!playing) return;
        elapsed += delta;
        layout(delta);
        if (running != null) stepPattern(running, delta);

        for (int i = bullets.size() - 1; i >= 0; i--) {
            PreviewBullet b = bullets.get(i);
            b.age += delta;
            advance(b, delta);
            if (isOffWorld(b) || (b.kind == Kind.LASER && b.remaining <= 0f)) bullets.remove(i);
        }
    }

    /** Positions the enemy (X, fixed) and player (O, sweeping) for this frame - split out from
     *  step() so rebuild() can establish a correct AT-REST layout (delta 0, no elapsed time) the
     *  instant a pattern loads, before Play is ever pressed. */
    private void layout(float delta) {
        enemyX = (float) WORLD_WIDTH / 2f;
        enemyY = ENEMY_Y;

        float half = (float) WORLD_WIDTH / 2f - PLAYER_MARGIN;
        float phase = (elapsed % (PLAYER_HALF_PERIOD * 2f)) / PLAYER_HALF_PERIOD;
        float triangle = phase <= 1f ? phase : 2f - phase; // 0 -> 1 -> 0, i.e. left -> right -> left
        playerX = (float) WORLD_WIDTH / 2f - half + triangle * half * 2f;
        playerY = PLAYER_Y;
    }

    // --- Pattern tree stepping ---------------------------------------------------------------

    private void stepPattern(RunningPattern r, float delta) {
        FiringPatternDef d = r.def;
        String type = d.type == null ? "None" : d.type;
        switch (type) {
            case "Sequence" -> {
                if (r.children == null || r.children.isEmpty()) return;
                RunningPattern active = r.children.get(r.stageIndex % r.children.size());
                stepPattern(active, delta);
                r.stageTime += delta;
                float dur = active.def.duration > 0 ? active.def.duration : 3.0f;
                if (r.stageTime >= dur) {
                    r.stageTime = 0f;
                    r.stageIndex = (r.stageIndex + 1) % r.children.size();
                }
            }
            case "Combined" -> {
                if (r.children == null) return;
                for (RunningPattern child : r.children) stepPattern(child, delta);
            }
            default -> stepLeaf(r, d, type, delta);
        }
    }

    private void stepLeaf(RunningPattern r, FiringPatternDef d, String type, float delta) {
        switch (type) {
            case "None", "SpawnEnemy" -> {} // no visible bullets to preview
            case "Wall", "PolkaDot" -> {
                float rate = d.fireRate > 0 ? d.fireRate : 0.3f;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnWallRow(d); }
            }
            case "Orbiting" -> {
                float rate = d.fireRate > 0 ? d.fireRate : 1f;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnOrbitBullet(d); }
            }
            case "Sweep" -> {
                float rate = d.fireRate > 0 ? d.fireRate : 0.12f;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnSweepShot(d); }
            }
            case "Laser" -> {
                float rate = d.fireRate > 0 ? d.fireRate : (d.duration > 0 ? d.duration + 0.5f : 2f);
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnLaser(d); }
            }
            case "RadialNearMiss" -> {
                float rate = d.fireRate > 0 ? d.fireRate : 1.5f;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnRadialVolley(d); }
            }
            case "SineWave" -> {
                float rate = d.fireRate > 0 ? d.fireRate : 0.5f;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnSineShot(d); }
            }
            default -> { // Aimed, AimedAtPoint, QuarterCircle, BurstAimed, SelfDestruct, ExplodingAimed
                float rate = d.fireRate > 0 ? d.fireRate : defaultRate(type);
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnAimedFamily(d, type); }
            }
        }
    }

    private static float defaultRate(String type) {
        return switch (type) {
            case "SelfDestruct" -> 3.0f;
            case "ExplodingAimed" -> 2.0f;
            case "BurstAimed" -> 1.2f;
            default -> 1.0f; // Aimed/AimedAtPoint/QuarterCircle
        };
    }

    // --- Spawners - each mirrors one FiringPattern subclass's own shape/timing (see
    // PatternFactory.createFiring()'s dispatch for the real formula each is modeled on) ----------

    /** Aimed/AimedAtPoint/QuarterCircle/BurstAimed/SelfDestruct/ExplodingAimed all reduce to "one
     *  or more straight shots on a fixed bearing, chosen once at spawn" for preview purposes -
     *  QuarterCircleFiring's own spread fan (see PatternFactory's "QuarterCircle" case) is the
     *  only one of these that's actually a multi-bullet volley. */
    private void spawnAimedFamily(FiringPatternDef d, String type) {
        float speed = d.bulletSpeed > 0 ? d.bulletSpeed : 5f;
        float size = d.bulletSize > 0 ? d.bulletSize : 0.25f;
        if ("QuarterCircle".equals(type)) {
            float spread = d.spreadDegrees > 0 ? d.spreadDegrees : 90f;
            int count = Math.max(d.numBullets > 0 ? d.numBullets : 9, 1);
            float base = !Float.isNaN(d.quarterCircleFixedAngle) ? d.quarterCircleFixedAngle : aimAngle(d);
            for (int i = 0; i < count; i++) {
                float t = count == 1 ? 0.5f : (float) i / (count - 1);
                spawnStraight(d, base - spread / 2f + spread * t, speed, size);
            }
            return;
        }
        float angle = "AimedAtPoint".equals(type)
            ? (float) Math.toDegrees(Math.atan2((Float.isNaN(d.targetY) ? 0 : d.targetY) - emitterY(d),
                                                 (Float.isNaN(d.targetX) ? 0 : d.targetX) - emitterX(d)))
            : aimAngle(d);
        spawnStraight(d, angle, speed, size);
    }

    private void spawnSweepShot(FiringPatternDef d) {
        float start = !Float.isNaN(d.sweepStartAngle) ? d.sweepStartAngle : 45f;
        float end = !Float.isNaN(d.sweepEndAngle) ? d.sweepEndAngle : 135f;
        float duration = d.sweepDuration > 0 ? d.sweepDuration : 3f;
        float t = (elapsed % duration) / duration;
        float angle = start + (end - start) * t;
        spawnStraight(d, angle, d.bulletSpeed > 0 ? d.bulletSpeed : 5f, d.bulletSize > 0 ? d.bulletSize : 0.25f);
    }

    private void spawnSineShot(FiringPatternDef d) {
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.SINE;
        b.x = b.originX = emitterX(d);
        b.y = b.originY = emitterY(d);
        b.angleDeg = aimAngle(d);
        b.speed = d.bulletSpeed > 0 ? d.bulletSpeed : 5f;
        applySpeedProfile(b, d);
        b.size = d.bulletSize > 0 ? d.bulletSize : 0.2f;
        b.amplitude = d.amplitude > 0 ? d.amplitude : 0.6f;
        b.frequency = d.frequency > 0 ? d.frequency : 2f;
        bullets.add(b);
    }

    private void spawnOrbitBullet(FiringPatternDef d) {
        float radius = d.orbitRadius > 0 ? d.orbitRadius : 1.5f;
        float orbitSpeed = d.orbitSpeed != 0 ? d.orbitSpeed : 2f;
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.SINE; // reuses the "origin + phase" evaluation; amplitude/frequency repurposed below
        b.originX = emitterX(d);
        b.originY = emitterY(d) - radius; // drifts downward, orbiting as it goes - visually distinct from a straight sine shot
        b.angleDeg = -90f; // downward
        b.speed = d.bulletSpeed > 0 ? d.bulletSpeed : 4f;
        b.rampable = false; // OrbitingFiring never threads a SpeedProfile through its bullets - see PreviewBullet.rampable's own doc
        b.size = d.bulletSize > 0 ? d.bulletSize : 0.5f;
        b.amplitude = radius;
        b.frequency = orbitSpeed / (float) (Math.PI * 2);
        b.x = b.originX;
        b.y = b.originY;
        bullets.add(b);
    }

    private void spawnWallRow(FiringPatternDef d) {
        float margin = d.wallMarginX > 0 ? d.wallMarginX : 0.25f;
        float spacing = d.wallSpacing > 0 ? d.wallSpacing : 0.4f;
        int gapStart = Math.max(d.gapLaneStart, 0);
        int gapCount = d.gapLaneCount >= 0 ? d.gapLaneCount : 1;
        float speed = d.bulletSpeed > 0 ? d.bulletSpeed : 5f;
        float size = d.bulletSize > 0 ? d.bulletSize : 0.25f;
        int lane = 0;
        for (float x = margin; x < WORLD_WIDTH - margin; x += spacing, lane++) {
            if (lane >= gapStart && lane < gapStart + gapCount) continue;
            PreviewBullet b = new PreviewBullet();
            b.kind = Kind.STRAIGHT;
            b.x = x;
            b.y = emitterY(d);
            b.angleDeg = -90f;
            b.speed = speed;
            applySpeedProfile(b, d);
            b.size = size;
            bullets.add(b);
        }
    }

    private void spawnRadialVolley(FiringPatternDef d) {
        int count = d.numBullets > 0 ? d.numBullets : 16;
        float speed = d.bulletSpeed > 0 ? d.bulletSpeed : 4f;
        float size = d.bulletSize > 0 ? d.bulletSize : 0.3f;
        for (int i = 0; i < count; i++) {
            float angle = 360f * i / count;
            spawnStraight(d, angle, speed, size);
        }
    }

    private void spawnLaser(FiringPatternDef d) {
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.LASER;
        b.x = emitterX(d);
        b.y = emitterY(d);
        b.angleDeg = !Float.isNaN(d.fireAngle) ? d.fireAngle : -90f;
        b.angularSpeed = d.angularSpeed;
        b.length = d.length > 0 ? d.length : 8f;
        b.size = d.bulletSize > 0 ? d.bulletSize : 0.3f;
        b.remaining = d.duration > 0 ? d.duration : 1.5f;
        bullets.add(b);
    }

    private void spawnStraight(FiringPatternDef d, float angleDeg, float speed, float size) {
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.STRAIGHT;
        b.x = emitterX(d);
        b.y = emitterY(d);
        b.angleDeg = angleDeg;
        b.speed = speed;
        applySpeedProfile(b, d);
        b.size = size;
        bullets.add(b);
    }

    /** Builds `b`'s own ramp state from whichever speed-change spec PatternFactory.speedProfile()
     *  would have picked for `d` - its authored bulletSpeedPhases sequence if it set one (wholesale,
     *  never merged with the flat fields below - see FiringPatternDef.bulletSpeedPhases's own doc),
     *  otherwise a single phase held forever from its flat bulletAcceleration. minSpeed/maxSpeed
     *  mirror PatternFactory.bulletMinSpeed()/bulletMaxSpeed()'s own defaults (0 = "can decelerate
     *  to a stop but not reverse", unbounded above unless set) - this preview has no BulletDef to
     *  fall back to (bulletId is authored but not resolved here), so only `d`'s own fields matter. */
    private static void applySpeedProfile(PreviewBullet b, FiringPatternDef d) {
        b.minSpeed = d.bulletMinSpeed > 0 ? d.bulletMinSpeed : 0f;
        b.maxSpeed = d.bulletMaxSpeed > 0 ? d.bulletMaxSpeed : Float.MAX_VALUE;
        if (d.bulletSpeedPhases != null && d.bulletSpeedPhases.size > 0) {
            int n = d.bulletSpeedPhases.size;
            b.accelerations = new float[n];
            b.durations = new float[n];
            for (int i = 0; i < n; i++) {
                BulletSpeedPhase phase = d.bulletSpeedPhases.get(i);
                b.accelerations[i] = phase.acceleration;
                b.durations[i] = phase.duration;
            }
            b.loop = d.bulletSpeedPhasesLoop;
        } else {
            b.accelerations = new float[]{ d.bulletAcceleration };
            b.durations = new float[]{ -1f };
            b.loop = false;
        }
        b.phaseIndex = 0;
        b.phaseTimer = 0f;
    }

    private float emitterX(FiringPatternDef d) { return enemyX + d.offsetX; }
    private float emitterY(FiringPatternDef d) { return enemyY + d.offsetY; }

    private float aimAngle(FiringPatternDef d) {
        float tx = playerX + d.targetOffsetX;
        float ty = playerY + d.targetOffsetY;
        return (float) Math.toDegrees(Math.atan2(ty - emitterY(d), tx - emitterX(d)));
    }

    private void advance(PreviewBullet b, float delta) {
        switch (b.kind) {
            case STRAIGHT -> {
                b.speed = applyRamp(b, delta);
                double rad = Math.toRadians(b.angleDeg);
                b.x += (float) Math.cos(rad) * b.speed * delta;
                b.y += (float) Math.sin(rad) * b.speed * delta;
            }
            case SINE -> {
                b.speed = applyRamp(b, delta);
                b.traveled += b.speed * delta;
                double rad = Math.toRadians(b.angleDeg);
                float baseX = b.originX + (float) Math.cos(rad) * b.traveled;
                float baseY = b.originY + (float) Math.sin(rad) * b.traveled;
                double perpRad = rad + Math.PI / 2;
                float wave = b.amplitude * (float) Math.sin(b.frequency * b.age * Math.PI * 2);
                b.x = baseX + (float) Math.cos(perpRad) * wave;
                b.y = baseY + (float) Math.sin(perpRad) * wave;
            }
            case LASER -> {
                b.remaining -= delta;
                b.angleDeg += b.angularSpeed * delta;
            }
        }
    }

    /** Same phase-cycling ramp SpeedRamp.apply() drives in the real game (see that class's own
     *  doc) - advances phaseTimer, walks through zero-duration phases and, on the last phase,
     *  either wraps back to phase 0 (loop) or holds there, then applies that phase's own
     *  acceleration for this frame, clamped to minSpeed/maxSpeed. A no-op (returns the current
     *  speed unchanged) for a bullet marked !rampable - see PreviewBullet.rampable's own doc. */
    private static float applyRamp(PreviewBullet b, float delta) {
        if (!b.rampable) return b.speed;
        float duration = b.durations[b.phaseIndex];
        if (duration > 0f) {
            b.phaseTimer += delta;
            while (b.phaseTimer >= duration) {
                boolean isLastPhase = b.phaseIndex == b.accelerations.length - 1;
                if (isLastPhase && !b.loop) break;
                b.phaseTimer -= duration;
                b.phaseIndex = (b.phaseIndex + 1) % b.accelerations.length;
                duration = b.durations[b.phaseIndex];
                if (duration <= 0f) break;
            }
        }
        float updated = b.speed + b.accelerations[b.phaseIndex] * delta;
        return Math.max(b.minSpeed, Math.min(b.maxSpeed, updated));
    }

    private boolean isOffWorld(PreviewBullet b) {
        return b.x < -2f || b.x > WORLD_WIDTH + 2f || b.y < -2f || b.y > WORLD_HEIGHT + 2f;
    }

    // --- Drawing --------------------------------------------------------------------------------

    private void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        g.setFill(Color.rgb(10, 10, 14));
        g.fillRect(0, 0, w, h);

        // Enemy (X)
        double ex = worldToCanvasX(enemyX);
        double ey = worldToCanvasY(enemyY);
        g.setStroke(Color.ORANGE);
        g.setLineWidth(2.5);
        double xSize = 9;
        g.strokeLine(ex - xSize, ey - xSize, ex + xSize, ey + xSize);
        g.strokeLine(ex - xSize, ey + xSize, ex + xSize, ey - xSize);

        // Player (O)
        double px = worldToCanvasX(playerX);
        double py = worldToCanvasY(playerY);
        g.setStroke(Color.LIGHTGREEN);
        g.setLineWidth(2.5);
        double oRadius = 8;
        g.strokeOval(px - oRadius, py - oRadius, oRadius * 2, oRadius * 2);

        // Bullets
        for (PreviewBullet b : bullets) {
            double bx = worldToCanvasX(b.x);
            double by = worldToCanvasY(b.y);
            if (b.kind == Kind.LASER) {
                g.setStroke(Color.rgb(255, 80, 80));
                g.setLineWidth(Math.max(b.size * SCALE, 2));
                double rad = Math.toRadians(b.angleDeg);
                g.strokeLine(bx, by, bx + Math.cos(rad) * b.length * SCALE, by - Math.sin(rad) * b.length * SCALE);
            } else {
                g.setFill(Color.rgb(255, 140, 60));
                double radius = Math.max(b.size * SCALE / 2f, 2);
                g.fillOval(bx - radius, by - radius, radius * 2, radius * 2);
            }
        }

        if (def == null) {
            g.setFill(Color.LIGHTGRAY);
            g.setFont(Font.font(11));
            g.fillText("Select a pattern to preview.", 10, h - 10);
        }
    }

    private double worldToCanvasX(double worldX) { return worldX * SCALE; }
    private double worldToCanvasY(double worldY) { return getHeight() - worldY * SCALE; }
}
