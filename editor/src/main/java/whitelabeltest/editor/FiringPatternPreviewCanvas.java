package whitelabeltest.editor;

import com.badlogic.gdx.utils.Json;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import whitelabeltest.enemy.BulletDef;
import whitelabeltest.enemy.BulletSpeedPhase;
import whitelabeltest.enemy.FiringPatternDef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live preview of one FiringPatternDef (Play/Pause/Reset, AnimationTimer, plain Canvas drawing).
 *
 *  The real FiringPattern/EnemyBullet classes can't run here: bullets build Sprites, which need a
 *  Texture, which needs a GL context this JavaFX process doesn't have. So this re-implements each
 *  pattern type's spawn timing and bullet motion with plain floats (each spawnXxx() names the real
 *  class it mirrors), drawing the real bullet art via JavaFX Images. It's an approximation; the
 *  in-game debug PatternPreviewer shows the exact behaviour. */
final class FiringPatternPreviewCanvas extends Canvas {
    private static final double WORLD_WIDTH = 9.0;
    private static final double WORLD_HEIGHT = 12.0;
    private static final double SCALE = 30;
    private static final float PLAYER_HALF_PERIOD = 1.6f; // seconds for one left->right (or right->left) sweep
    private static final float PLAYER_MARGIN = 0.6f;
    private static final float PLAYER_Y = 1.2f;
    private static final float ENEMY_Y = (float) WORLD_HEIGHT - 1.6f;
    private static final float MAX_STEP = 0.05f; // clamps a debugger-pause/tab-switch stall to a sane single step

    // One per bullet motion: STRAIGHT (Aimed/Drifter), SINE, FEATHER, ORBIT, EXPLODING, SHAPE, LASER.
    private enum Kind { STRAIGHT, SINE, FEATHER, ORBIT, EXPLODING, SHAPE, LASER }

    /** A leaf pattern's bullet sprite sheet as a JavaFX Image, resolved once per rebuild(). */
    private static final class BulletSprite {
        final Image sheet;
        final double frameW, frameH; // one frame's own pixel size within the sheet
        final int columns;
        final int frameCount;
        final float frameDuration;

        BulletSprite(Image sheet, double frameW, double frameH, int columns, int frameCount, float frameDuration) {
            this.sheet = sheet;
            this.frameW = frameW;
            this.frameH = frameH;
            this.columns = columns;
            this.frameCount = frameCount;
            this.frameDuration = frameDuration;
        }

        double aspect() { return frameH / frameW; }
    }

    private static final class PreviewBullet {
        Kind kind;
        float x, y;
        float angleDeg;
        float speed;
        float size;
        float age;
        // Null = draw a plain dot.
        BulletSprite sprite;
        // SpeedRamp state. Orbiting bullets never ramp.
        boolean rampable = true;
        float[] accelerations = { 0f };
        float[] durations = { -1f };
        boolean loop;
        float minSpeed, maxSpeed = Float.MAX_VALUE;
        int phaseIndex;
        float phaseTimer;
        // SINE/FEATHER/ORBIT/SHAPE: origin and distance traveled (integrated, so ramps apply).
        // amplitude/frequency: sway (rad/s) or orbit radius/angular speed.
        float originX, originY, amplitude, frequency, traveled;
        // ORBIT: center drift velocity and starting phase.
        float velocityX, velocityY, phase;
        // SHAPE: see ShapeBullet.
        float offsetX, offsetY, formScale, formTime, spreadSpeed, spreadAcceleration, driftSpeed;
        // EXPLODING: already re-aimed.
        boolean aimed;
        // Sprite facing; usually the heading (not for FEATHER / SHAPE).
        float facingDeg;
        // LASER: time left, rotation rate, length.
        float remaining, angularSpeed, length;
    }

    /** Runtime state mirroring the pattern tree; rebuilt whenever the definition changes. */
    private static final class RunningPattern {
        FiringPatternDef def;
        float timer;
        float stageTime;
        int stageIndex;
        List<RunningPattern> children;
        boolean bursting;
        float burstTimer;
        int currentBurstShot;
        // Sweep ping-pong position (0 = start angle, 1 = end angle) and direction.
        float sweepT;
        float sweepDirection = 1f;
        // Wall/RadialNearMiss volleys fired, PolkaDot rows fired, SelfDestruct's one-shot latch.
        int volleysFired;
        // Null for Sequence/Combined or when no texture resolves.
        BulletSprite sprite;
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

    /** Loads a pattern, restarts and auto-plays. */
    void setPattern(FiringPatternDef def) {
        this.def = def;
        rebuild();
        play();
    }

    /** Restarts after a field edit without un-pausing. */
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
        // Re-read every rebuild (cheap) to pick up bullet edits made elsewhere in the editor.
        bulletDefsById = loadBulletDefs();
        running = def != null ? buildRunning(def) : null;
        layout(0f);
    }

    private RunningPattern buildRunning(FiringPatternDef def) {
        RunningPattern r = new RunningPattern();
        r.def = def;
        if (("Sequence".equals(def.type) || "Combined".equals(def.type)) && def.patterns != null) {
            r.children = new ArrayList<>();
            for (FiringPatternDef sub : def.patterns) r.children.add(buildRunning(sub));
        } else {
            r.sprite = resolveBulletSprite(def);
        }
        resetRunning(r);
        return r;
    }

    /** Resets a subtree like the real reset(): Sweep, SineWave, Feather, Orbiting and Shape fire
     *  immediately; the rest wait one fireRate. Sequences reset each stage as they leave it. */
    private void resetRunning(RunningPattern r) {
        FiringPatternDef d = r.def;
        String type = d.type == null ? "None" : d.type;
        r.stageIndex = 0;
        r.stageTime = 0f;
        r.bursting = false;
        r.burstTimer = 0f;
        r.currentBurstShot = 0;
        r.sweepT = 0f;
        r.sweepDirection = 1f;
        r.volleysFired = 0;
        switch (type) {
            case "Sweep", "SineWave", "Feather", "Orbiting" -> r.timer = d.fireRate;
            case "Shape" -> r.timer = shapeFireRate(d);
            case "BurstAimed" -> r.timer = d.phaseOffset;
            default -> r.timer = 0f;
        }
        if (r.children != null) for (RunningPattern child : r.children) resetRunning(child);
    }

    private static float shapeFireRate(FiringPatternDef d) { return d.fireRate > 0 ? d.fireRate : 1f; }

    // --- Bullet sprite resolution ---

    private Map<String, BulletDef> bulletDefsById = new HashMap<>();

    /** The BulletDef `d.bulletId` references, or null. sizeOf()/speedOf() and applySpeedProfile()
     *  mirror PatternFactory.bulletSize()/bulletSpeed()/speedProfile(): a pattern's own field wins,
     *  else the BulletDef's, else the per-type default the caller passes. */
    private BulletDef bulletDefOf(FiringPatternDef d) {
        return d.bulletId != null ? bulletDefsById.get(d.bulletId) : null;
    }

    private float sizeOf(FiringPatternDef d, float fallback) {
        if (d.bulletSize > 0) return d.bulletSize;
        BulletDef bd = bulletDefOf(d);
        return bd != null && bd.bulletSize > 0 ? bd.bulletSize : fallback;
    }

    private float speedOf(FiringPatternDef d, float fallback) {
        if (d.bulletSpeed > 0) return d.bulletSpeed;
        BulletDef bd = bulletDefOf(d);
        return bd != null && bd.bulletSpeed > 0 ? bd.bulletSpeed : fallback;
    }

    /** Pattern, then BulletDef, then defaults (no enemy fallback here). Null if there's no texture
     *  or the file is missing. */
    private BulletSprite resolveBulletSprite(FiringPatternDef d) {
        BulletDef bulletDef = bulletDefOf(d);
        String texturePath = d.bulletTexture != null ? d.bulletTexture
            : (bulletDef != null ? bulletDef.bulletTexture : null);
        if (texturePath == null) return null;
        Image sheet = EnemySpriteImages.loadImage(texturePath);
        if (sheet == null) return null;

        int frameCount = d.bulletFrameCount > 0 ? d.bulletFrameCount
            : (bulletDef != null && bulletDef.bulletFrameCount > 0 ? bulletDef.bulletFrameCount : FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT);
        int columns = d.bulletColumns >= 0 ? d.bulletColumns
            : (bulletDef != null && bulletDef.bulletColumns >= 0 ? bulletDef.bulletColumns : FiringPatternDef.DEFAULT_BULLET_COLUMNS);
        int rows = d.bulletRows > 0 ? d.bulletRows
            : (bulletDef != null && bulletDef.bulletRows > 0 ? bulletDef.bulletRows : FiringPatternDef.DEFAULT_BULLET_ROWS);
        float frameDuration = d.bulletFrameDuration > 0 ? d.bulletFrameDuration
            : (bulletDef != null && bulletDef.bulletFrameDuration > 0 ? bulletDef.bulletFrameDuration : FiringPatternDef.DEFAULT_BULLET_FRAME_DURATION);

        int cols = Math.max(columns > 0 ? columns : frameCount, 1);
        int rws = Math.max(rows, 1);
        double frameW = sheet.getWidth() / cols;
        double frameH = sheet.getHeight() / rws;
        if (frameW <= 0 || frameH <= 0) return null;
        return new BulletSprite(sheet, frameW, frameH, cols, Math.max(frameCount, 1), frameDuration > 0 ? frameDuration : FiringPatternDef.DEFAULT_BULLET_FRAME_DURATION);
    }

    /** Reads data/bullets.json with plain file I/O (no Gdx.files outside the game). */
    private static Map<String, BulletDef> loadBulletDefs() {
        Map<String, BulletDef> map = new HashMap<>();
        Path path = Path.of("data/bullets.json");
        if (!Files.exists(path)) return map;
        try {
            String text = Files.readString(path);
            Json json = new Json();
            @SuppressWarnings("unchecked")
            com.badlogic.gdx.utils.Array<BulletDef> defs = json.fromJson(com.badlogic.gdx.utils.Array.class, BulletDef.class, text);
            for (BulletDef d : defs) if (d.id != null) map.put(d.id, d);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path.toAbsolutePath(), e);
        }
        return map;
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

    /** Places the enemy (fixed) and player (sweeping). Also called with delta 0 on load. */
    private void layout(float delta) {
        enemyX = (float) WORLD_WIDTH / 2f;
        enemyY = ENEMY_Y;

        float half = (float) WORLD_WIDTH / 2f - PLAYER_MARGIN;
        float phase = (elapsed % (PLAYER_HALF_PERIOD * 2f)) / PLAYER_HALF_PERIOD;
        float triangle = phase <= 1f ? phase : 2f - phase; // 0 -> 1 -> 0, i.e. left -> right -> left
        playerX = (float) WORLD_WIDTH / 2f - half + triangle * half * 2f;
        playerY = PLAYER_Y;
    }

    // --- Pattern tree stepping ---

    private void stepPattern(RunningPattern r, float delta) {
        FiringPatternDef d = r.def;
        String type = d.type == null ? "None" : d.type;
        switch (type) {
            case "Sequence" -> {
                // As SequencedFiringPattern: advance the clock, reset and leave an expired stage,
                // run the current one.
                if (r.children == null || r.children.isEmpty()) return;
                RunningPattern active = r.children.get(r.stageIndex % r.children.size());
                r.stageTime += delta;
                float dur = active.def.duration > 0 ? active.def.duration : 3.0f;
                if (r.stageTime >= dur) {
                    r.stageTime = 0f;
                    resetRunning(active);
                    r.stageIndex = (r.stageIndex + 1) % r.children.size();
                    active = r.children.get(r.stageIndex);
                }
                stepPattern(active, delta);
            }
            case "Combined" -> {
                if (r.children == null) return;
                for (RunningPattern child : r.children) stepPattern(child, delta);
            }
            default -> stepLeaf(r, d, type, delta);
        }
    }

    /** One frame of a leaf pattern, mirroring each type's real update() timing. */
    private void stepLeaf(RunningPattern r, FiringPatternDef d, String type, float delta) {
        switch (type) {
            case "None", "SpawnEnemy" -> {} // no visible bullets to preview
            case "Wall" -> { // WallFiring: first volley immediately, one volley per gapLaneSequence entry (or just one)
                int totalVolleys = d.gapLaneSequence != null && d.gapLaneSequence.length > 0 ? d.gapLaneSequence.length : 1;
                if (r.volleysFired >= totalVolleys) return;
                if (r.volleysFired > 0) {
                    r.timer += delta;
                    if (r.timer < (d.fireRate > 0 ? d.fireRate : 0.3f)) return;
                    r.timer = 0f;
                }
                int gapStart = totalVolleys > 1 ? d.gapLaneSequence[r.volleysFired] : Math.max(d.gapLaneStart, 0);
                r.volleysFired++;
                spawnLaneRow(d, r.sprite, lane -> lane < gapStart || lane >= gapStart + (d.gapLaneCount >= 0 ? d.gapLaneCount : 1));
            }
            case "PolkaDot" -> { // PolkaDotFiring: alternating even/odd lane rows forever, first row immediately
                if (r.volleysFired > 0) {
                    r.timer += delta;
                    if (r.timer < (d.fireRate > 0 ? d.fireRate : 0.3f)) return;
                    r.timer = 0f;
                }
                boolean evenRow = r.volleysFired % 2 == 0;
                r.volleysFired++;
                spawnLaneRow(d, r.sprite, lane -> (lane % 2 == 0) == evenRow);
            }
            case "Orbiting" -> {
                if (fireReady(r, d.fireRate, delta)) spawnOrbitPair(d, r.sprite);
            }
            case "Sweep" -> { // SweepFiring: ping-pongs start->end->start, one shot per fireRate
                float sweepDuration = d.sweepDuration > 0 ? d.sweepDuration : 2f;
                r.sweepT += r.sweepDirection * delta / sweepDuration;
                if (r.sweepT >= 1f) { r.sweepT = 1f; r.sweepDirection = -1f; }
                else if (r.sweepT <= 0f) { r.sweepT = 0f; r.sweepDirection = 1f; }
                if (fireReady(r, d.fireRate, delta)) spawnSweepShot(d, r.sprite, r.sweepT);
            }
            case "Laser" -> {
                float rate = d.fireRate > 0 ? d.fireRate : (d.duration > 0 ? d.duration + 0.5f : 2f);
                if (fireReady(r, rate, delta)) spawnLaser(d);
            }
            case "RadialNearMiss" -> { // RadialNearMissFiring: volleyCount volleys, each after a full fireRate
                int volleyCount = d.volleyCount >= 0 ? d.volleyCount : 3;
                if (r.volleysFired >= volleyCount) return;
                if (fireReady(r, d.fireRate > 0 ? d.fireRate : 1.5f, delta)) {
                    r.volleysFired++;
                    spawnRadialVolley(d, r.sprite);
                }
            }
            case "SineWave" -> {
                if (fireReady(r, d.fireRate, delta)) spawnSwayShot(d, r.sprite, Kind.SINE, 1.0f, 4.0f, 5f, 0.2f);
            }
            case "Feather" -> {
                if (fireReady(r, d.fireRate, delta)) spawnSwayShot(d, r.sprite, Kind.FEATHER, 1.4f, 1.1f, 1.5f, 0.2f);
            }
            case "Shape" -> {
                if (fireReady(r, shapeFireRate(d), delta)) spawnShapeVolley(d, r.sprite);
            }
            case "SelfDestruct" -> {
                // The real one needs the player within 3 units, which never happens here; fire once.
                if (r.volleysFired > 0) return;
                r.timer += delta;
                if (r.timer < 0.5f) return;
                r.volleysFired++;
                for (int i = 0; i < 8; i++) spawnStraight(d, i * 45f, speedOf(d, 4f), sizeOf(d, 0.25f), r.sprite);
            }
            case "ExplodingAimed" -> {
                if (fireReady(r, d.fireRate, delta)) spawnExplodingRing(d, r.sprite);
            }
            case "BurstAimed" -> stepBurstAimed(r, d, delta);
            default -> { // Aimed, AimedAtPoint, QuarterCircle
                if (fireReady(r, d.fireRate, delta)) spawnAimedFamily(d, type, r.sprite);
            }
        }
    }

    /** The standard shootTimer/fireRate gate. */
    private static boolean fireReady(RunningPattern r, float fireRate, float delta) {
        r.timer += delta;
        if (r.timer < fireRate) return false;
        r.timer = 0f;
        return true;
    }

    // BurstAimedFiring's state machine: BURST_COUNT aimed shots burstInterval apart, then fireRate.
    private static final int BURST_AIMED_BURST_COUNT = 5;
    private static final float BURST_AIMED_DEFAULT_INTERVAL = 0.15f;

    private void stepBurstAimed(RunningPattern r, FiringPatternDef d, float delta) {
        float fireRate = d.fireRate;
        float burstInterval = d.burstInterval > 0 ? d.burstInterval : BURST_AIMED_DEFAULT_INTERVAL;

        if (!r.bursting) {
            r.timer += delta;
            if (r.timer >= fireRate) {
                r.bursting = true;
                r.timer = 0f;
                r.currentBurstShot = 0;
                r.burstTimer = burstInterval; // Fire first shot immediately - matches BurstAimedFiring.update().
            }
        }

        if (r.bursting) {
            r.burstTimer += delta;
            if (r.burstTimer >= burstInterval) {
                r.burstTimer = 0f;
                // BurstAimed ignores targetOffsetX/Y, so aim directly rather than via aimAngle().
                float angle = (float) Math.toDegrees(Math.atan2(playerY - emitterY(d), playerX - emitterX(d)));
                spawnStraight(d, angle, speedOf(d, 5f), sizeOf(d, 0.25f), r.sprite);
                r.currentBurstShot++;
                if (r.currentBurstShot >= BURST_AIMED_BURST_COUNT) r.bursting = false;
            }
        }
    }

    // --- Spawners (each mirrors one FiringPattern class) ---

    /** Aimed / AimedAtPoint / QuarterCircle / SelfDestruct: straight shots on a bearing fixed at
     *  spawn (QuarterCircle fires a fan). */
    private void spawnAimedFamily(FiringPatternDef d, String type, BulletSprite sprite) {
        float speed = speedOf(d, 5f);
        float size = sizeOf(d, 0.25f);
        if ("QuarterCircle".equals(type)) {
            float spread = d.spreadDegrees > 0 ? d.spreadDegrees : 90f;
            int count = Math.max(d.numBullets > 0 ? d.numBullets : 9, 1);
            float base = !Float.isNaN(d.quarterCircleFixedAngle) ? d.quarterCircleFixedAngle : aimAngle(d);
            for (int i = 0; i < count; i++) {
                float t = count == 1 ? 0.5f : (float) i / (count - 1);
                spawnStraight(d, base - spread / 2f + spread * t, speed, size, sprite);
            }
            return;
        }
        float angle = "AimedAtPoint".equals(type)
            ? (float) Math.toDegrees(Math.atan2((Float.isNaN(d.targetY) ? 0 : d.targetY) - emitterY(d),
                                                 (Float.isNaN(d.targetX) ? 0 : d.targetX) - emitterX(d)))
            : aimAngle(d);
        spawnStraight(d, angle, speed, size, sprite);
    }

    /** SweepFiring: one shot at the current sweep angle (default 225 -> 315 over 2s). */
    private void spawnSweepShot(FiringPatternDef d, BulletSprite sprite, float sweepT) {
        float start = !Float.isNaN(d.sweepStartAngle) ? d.sweepStartAngle : 225f;
        float end = !Float.isNaN(d.sweepEndAngle) ? d.sweepEndAngle : 315f;
        spawnStraight(d, start + sweepT * (end - start), speedOf(d, 5f), sizeOf(d, 0.25f), sprite);
    }

    /** SineWaveFiring / FeatherFiring: one bullet falling and swaying (frequency in rad/s). */
    private void spawnSwayShot(FiringPatternDef d, BulletSprite sprite, Kind kind, float defaultAmplitude, float defaultFrequency, float defaultSpeed, float defaultSize) {
        PreviewBullet b = new PreviewBullet();
        b.kind = kind;
        b.sprite = sprite;
        b.x = b.originX = emitterX(d);
        b.y = b.originY = emitterY(d);
        b.angleDeg = b.facingDeg = -90f;
        b.speed = speedOf(d, defaultSpeed);
        applySpeedProfile(b, d);
        b.size = sizeOf(d, defaultSize);
        b.amplitude = d.amplitude > 0 ? d.amplitude : defaultAmplitude;
        b.frequency = d.frequency > 0 ? d.frequency : defaultFrequency;
        bullets.add(b);
    }

    /** OrbitingFiring: two bullets half an orbit apart around a center drifting at the player. */
    private void spawnOrbitPair(FiringPatternDef d, BulletSprite sprite) {
        float radius = d.orbitRadius > 0 ? d.orbitRadius : 0.4f;
        float orbitSpeed = d.orbitSpeed > 0 ? d.orbitSpeed : 5f;
        float speed = speedOf(d, 4f);
        double aim = Math.atan2(playerY - emitterY(d), playerX - emitterX(d));
        for (int i = 0; i < 2; i++) {
            PreviewBullet b = new PreviewBullet();
            b.kind = Kind.ORBIT;
            b.sprite = sprite;
            b.originX = emitterX(d);
            b.originY = emitterY(d);
            b.velocityX = (float) Math.cos(aim) * speed;
            b.velocityY = (float) Math.sin(aim) * speed;
            b.amplitude = radius;
            b.frequency = orbitSpeed;
            b.phase = i == 0 ? 0f : (float) Math.PI;
            b.rampable = false; // OrbitingFiring never threads a SpeedProfile through its bullets - see PreviewBullet.rampable's own doc
            b.size = sizeOf(d, 0.5f);
            b.x = b.originX + radius * (float) Math.cos(b.phase);
            b.y = b.originY + radius * (float) Math.sin(b.phase);
            bullets.add(b);
        }
    }

    /** WallFiring / PolkaDotFiring: one falling bullet per included lane across the field. */
    private void spawnLaneRow(FiringPatternDef d, BulletSprite sprite, java.util.function.IntPredicate includeLane) {
        float margin = d.wallMarginX > 0 ? d.wallMarginX : 0.25f;
        float spacing = d.wallSpacing > 0 ? d.wallSpacing : 0.4f;
        float speed = speedOf(d, 5f);
        float size = sizeOf(d, 0.25f);
        int laneCount = (int) ((WORLD_WIDTH - margin * 2f) / spacing + 0.0001f) + 1;
        for (int lane = 0; lane < laneCount; lane++) {
            if (!includeLane.test(lane)) continue;
            PreviewBullet b = new PreviewBullet();
            b.kind = Kind.STRAIGHT;
            b.sprite = sprite;
            b.x = margin + lane * spacing;
            b.y = enemyY;
            b.angleDeg = -90f;
            b.speed = speed;
            applySpeedProfile(b, d);
            b.size = size;
            bullets.add(b);
        }
    }

    /** RadialNearMissFiring: a ring of bullets spawned out on the play area's edges around the
     *  player, each flying past the player at nearMissDistance on the same tangential side. */
    private void spawnRadialVolley(FiringPatternDef d, BulletSprite sprite) {
        int count = d.numBullets > 0 ? d.numBullets : 16;
        float missDistance = d.nearMissDistance > 0 ? d.nearMissDistance : 0.35f;
        float speed = speedOf(d, 4f);
        float size = sizeOf(d, 0.3f);
        float edge = 0.3f;
        float xMin = edge, xMax = (float) WORLD_WIDTH - edge, yMin = -edge, yMax = (float) WORLD_HEIGHT + edge;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(360.0 * i / count);
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            float t = Float.MAX_VALUE;
            if (dx > 0.0001f) t = Math.min(t, (xMax - playerX) / dx);
            else if (dx < -0.0001f) t = Math.min(t, (xMin - playerX) / dx);
            if (dy > 0.0001f) t = Math.min(t, (yMax - playerY) / dy);
            else if (dy < -0.0001f) t = Math.min(t, (yMin - playerY) / dy);
            if (t <= 0f || t == Float.MAX_VALUE) continue;
            float spawnX = playerX + dx * t, spawnY = playerY + dy * t;
            double perp = angle + Math.PI / 2;
            float targetX = playerX + missDistance * (float) Math.cos(perp);
            float targetY = playerY + missDistance * (float) Math.sin(perp);
            PreviewBullet b = new PreviewBullet();
            b.kind = Kind.STRAIGHT;
            b.sprite = sprite;
            b.x = spawnX;
            b.y = spawnY;
            b.angleDeg = (float) Math.toDegrees(Math.atan2(targetY - spawnY, targetX - spawnX));
            b.speed = speed;
            applySpeedProfile(b, d);
            b.size = size;
            bullets.add(b);
        }
    }

    /** ExplodingAimedFiring: a ring of 8 that bursts outward, then after 0.4s each bullet turns
     *  toward the player's position at that moment and speeds up by 20% - see ExplodingAimedBullet. */
    private void spawnExplodingRing(FiringPatternDef d, BulletSprite sprite) {
        for (int i = 0; i < 8; i++) {
            spawnStraight(d, i * 45f, speedOf(d, 6f), sizeOf(d, 0.25f), sprite);
            bullets.get(bullets.size() - 1).kind = Kind.EXPLODING;
        }
    }

    /** ShapeFiring: shapeCount pictures fanned over spreadDegrees around the aim. */
    private void spawnShapeVolley(FiringPatternDef d, BulletSprite sprite) {
        float[] points = d.shapePoints != null && d.shapePoints.length >= 2 ? d.shapePoints : new float[]{0f, 0f};
        int count = Math.max(1, d.numBullets > 0 ? d.numBullets : 1);
        float spread = Math.max(0f, d.spreadDegrees);
        float aim = !Float.isNaN(d.fireAngle) ? d.fireAngle : aimAngle(d);
        float first = count > 1 ? aim - spread / 2f : aim;
        float step = count > 1 ? spread / (count - 1) : 0f;
        float speed = speedOf(d, 3f);
        float size = sizeOf(d, 0.25f);
        for (int s = 0; s < count; s++) {
            float angle = first + s * step;
            double turn = d.shapeRotateWithDirection ? Math.toRadians(angle - 270f) : 0.0;
            float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
            for (int i = 0; i + 1 < points.length; i += 2) {
                float px = d.shapeFlipX ? -points[i] : points[i];
                float py = points[i + 1];
                PreviewBullet b = new PreviewBullet();
                b.kind = Kind.SHAPE;
                b.sprite = sprite;
                b.originX = emitterX(d);
                b.originY = emitterY(d);
                b.angleDeg = angle;
                b.facingDeg = 90f; // ShapeBullet dots are drawn upright, never rotated
                b.offsetX = px * cos - py * sin;
                b.offsetY = px * sin + py * cos;
                b.formScale = d.shapeScale > 0 ? d.shapeScale : 1f;
                b.formTime = d.shapeFormTime;
                if (b.formTime > 0f) {
                    float r = Math.max(0f, Math.min(2f, d.shapeDriftRatio));
                    b.spreadSpeed = (2f - r) * b.formScale / b.formTime;
                    b.spreadAcceleration = 2f * (r - 1f) * b.formScale / (b.formTime * b.formTime);
                    b.driftSpeed = r * b.formScale / b.formTime;
                }
                b.speed = speed;
                applySpeedProfile(b, d);
                b.size = size;
                b.x = b.originX + b.offsetX * shapeSpread(b);
                b.y = b.originY + b.offsetY * shapeSpread(b);
                bullets.add(b);
            }
        }
    }

    /** LaserFiring: aims at the player when no fixed fireAngle is set. */
    private void spawnLaser(FiringPatternDef d) {
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.LASER;
        b.x = emitterX(d);
        b.y = emitterY(d);
        b.angleDeg = !Float.isNaN(d.fireAngle) ? d.fireAngle
            : (float) Math.toDegrees(Math.atan2(playerY - b.y, playerX - b.x));
        b.angularSpeed = d.angularSpeed;
        b.length = d.length > 0 ? d.length : 8f;
        b.size = sizeOf(d, 0.3f);
        b.remaining = d.duration > 0 ? d.duration : 1.5f;
        bullets.add(b);
    }

    private void spawnStraight(FiringPatternDef d, float angleDeg, float speed, float size, BulletSprite sprite) {
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.STRAIGHT;
        b.sprite = sprite;
        b.x = emitterX(d);
        b.y = emitterY(d);
        b.angleDeg = b.facingDeg = angleDeg;
        b.speed = speed;
        applySpeedProfile(b, d);
        b.size = size;
        bullets.add(b);
    }

    /** As PatternFactory.speedProfile(): the pattern's phases, else the BulletDef's, else one phase
     *  of bulletAcceleration. minSpeed defaults to 0; maxSpeed is unbounded unless set. */
    private void applySpeedProfile(PreviewBullet b, FiringPatternDef d) {
        BulletDef bd = bulletDefOf(d);
        b.minSpeed = d.bulletMinSpeed > 0 ? d.bulletMinSpeed : (bd != null && bd.bulletMinSpeed > 0 ? bd.bulletMinSpeed : 0f);
        b.maxSpeed = d.bulletMaxSpeed > 0 ? d.bulletMaxSpeed : (bd != null && bd.bulletMaxSpeed > 0 ? bd.bulletMaxSpeed : Float.MAX_VALUE);
        com.badlogic.gdx.utils.Array<BulletSpeedPhase> phases = null;
        boolean loop = false;
        if (d.bulletSpeedPhases != null && d.bulletSpeedPhases.size > 0) {
            phases = d.bulletSpeedPhases;
            loop = d.bulletSpeedPhasesLoop;
        } else if (bd != null && bd.bulletSpeedPhases != null && bd.bulletSpeedPhases.size > 0) {
            phases = bd.bulletSpeedPhases;
            loop = bd.bulletSpeedPhasesLoop;
        }
        if (phases != null) {
            int n = phases.size;
            b.accelerations = new float[n];
            b.durations = new float[n];
            for (int i = 0; i < n; i++) {
                BulletSpeedPhase phase = phases.get(i);
                b.accelerations[i] = phase.acceleration;
                b.durations[i] = phase.duration;
            }
            b.loop = loop;
        } else {
            float accel = d.bulletAcceleration != 0f ? d.bulletAcceleration : (bd != null ? bd.bulletAcceleration : 0f);
            b.accelerations = new float[]{ accel };
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
            case EXPLODING -> {
                // ExplodingAimedBullet: after its 0.4s burst, re-aims once at the player, 20% faster.
                if (!b.aimed && b.age >= 0.4f) {
                    b.aimed = true;
                    b.angleDeg = (float) Math.toDegrees(Math.atan2(playerY - b.y, playerX - b.x));
                    b.speed *= 1.2f;
                }
                b.speed = applyRamp(b, delta);
                double rad = Math.toRadians(b.angleDeg);
                b.x += (float) Math.cos(rad) * b.speed * delta;
                b.y += (float) Math.sin(rad) * b.speed * delta;
                b.facingDeg = b.angleDeg;
            }
            case SINE -> {
                // SineBullet: falls with x = origin + amplitude * sin(frequency * t).
                b.speed = applyRamp(b, delta);
                b.traveled += b.speed * delta;
                float t = b.age;
                b.x = b.originX + b.amplitude * (float) Math.sin(b.frequency * t);
                b.y = b.originY - b.traveled;
                float vx = b.amplitude * b.frequency * (float) Math.cos(b.frequency * t);
                b.facingDeg = (float) Math.toDegrees(Math.atan2(-b.speed, vx));
            }
            case FEATHER -> {
                // FeatherBullet: adds a smaller, faster sway; the sprite rocks +-28 degrees.
                b.speed = applyRamp(b, delta);
                b.traveled += b.speed * delta;
                float t = b.age;
                float sway = b.amplitude * (float) Math.sin(b.frequency * t)
                    + b.amplitude * 0.3f * (float) Math.sin(b.frequency * 2.3f * t + 1.7f);
                b.x = b.originX + sway;
                b.y = b.originY - b.traveled;
                b.facingDeg = 90f + 28f * (float) Math.sin(b.frequency * 1.5f * t);
            }
            case ORBIT -> {
                // OrbitingBullet: circles a center drifting at a constant velocity.
                float t = b.age;
                float angle = b.frequency * t + b.phase;
                float cx = b.originX + b.velocityX * t;
                float cy = b.originY + b.velocityY * t;
                b.x = cx + b.amplitude * (float) Math.cos(angle);
                b.y = cy + b.amplitude * (float) Math.sin(angle);
                float vx = b.velocityX - b.amplitude * b.frequency * (float) Math.sin(angle);
                float vy = b.velocityY + b.amplitude * b.frequency * (float) Math.cos(angle);
                b.facingDeg = (float) Math.toDegrees(Math.atan2(vy, vx));
            }
            case SHAPE -> {
                // ShapeBullet: position = travel + offset * spread.
                b.speed = applyRamp(b, delta);
                b.traveled += b.speed * delta;
                double rad = Math.toRadians(b.angleDeg);
                b.x = b.originX + (float) Math.cos(rad) * b.traveled + b.offsetX * shapeSpread(b);
                b.y = b.originY + (float) Math.sin(rad) * b.traveled + b.offsetY * shapeSpread(b);
            }
            case LASER -> {
                b.remaining -= delta;
                b.angleDeg += b.angularSpeed * delta;
            }
        }
    }

    /** ShapeBullet.spread(). */
    private static float shapeSpread(PreviewBullet b) {
        if (b.formTime <= 0f) return b.formScale;
        if (b.age < b.formTime) return b.spreadSpeed * b.age + 0.5f * b.spreadAcceleration * b.age * b.age;
        return b.formScale + b.driftSpeed * (b.age - b.formTime);
    }

    /** SpeedRamp.apply(); returns the speed unchanged for non-rampable bullets. */
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

    // --- Drawing ---

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
            } else if (b.sprite != null) {
                drawBulletSprite(g, b, bx, by);
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

    /** Draws the current frame at bulletSize wide (height from the frame aspect), rotated to its
     *  facing (canvas Y is flipped). */
    private void drawBulletSprite(GraphicsContext g, PreviewBullet b, double bx, double by) {
        BulletSprite sprite = b.sprite;
        double width = Math.max(b.size * SCALE, 2);
        double height = width * sprite.aspect();

        int frameIndex = 0;
        if (sprite.frameCount > 1 && sprite.frameDuration > 0f) {
            frameIndex = (int) ((b.age / sprite.frameDuration) % sprite.frameCount);
            if (frameIndex < 0) frameIndex += sprite.frameCount;
        }
        double sx = (frameIndex % sprite.columns) * sprite.frameW;
        double sy = (frameIndex / sprite.columns) * sprite.frameH;

        g.save();
        g.translate(bx, by);
        g.rotate(90 - b.facingDeg);
        g.drawImage(sprite.sheet, sx, sy, sprite.frameW, sprite.frameH, -width / 2, -height / 2, width, height);
        g.restore();
    }

    private double worldToCanvasX(double worldX) { return worldX * SCALE; }
    private double worldToCanvasY(double worldY) { return getHeight() - worldY * SCALE; }
}
