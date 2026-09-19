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

    /** The decoded sprite sheet a leaf pattern's bullets actually use in-game, resolved once per
     *  rebuild() (see resolveBulletSprite()) rather than per-shot - mirrors PatternFactory.
     *  buildBulletAnimation()'s own pattern-then-bulletDef texture/layout fallback (there's no
     *  enemy-level fallback here though, since this preview has no enemy context - a pattern with
     *  no texture anywhere in its own chain just falls back to the plain-dot drawing redraw() has
     *  always used). Loaded via EnemySpriteImages.loadImage(), the same plain-JavaFX Image decoder
     *  TriggerNode/PlayerPreviewView already use - unlike a real LibGDX Sprite/Texture (see this
     *  class's own top-of-file doc on why THAT approach is a dead end here), a JavaFX Image needs no
     *  GL context at all, so the real bullet art can be shown without needing a running backend. */
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
        // Null falls back to the plain colored dot redraw() has always drawn - see BulletSprite's
        // own doc for when that happens.
        BulletSprite sprite;
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
     *  current stage/a leaf's shootTimer wouldn't mean anything against a differently-shaped tree.
     *  `bursting`/`burstTimer`/`currentBurstShot` are BurstAimed-only - see stepBurstAimed()'s own
     *  doc, which mirrors BurstAimedFiring's own identically-named fields exactly. */
    private static final class RunningPattern {
        FiringPatternDef def;
        float timer;
        float stageTime;
        int stageIndex;
        List<RunningPattern> children;
        boolean bursting;
        float burstTimer;
        int currentBurstShot;
        // This leaf's own resolved bullet art (null for Sequence/Combined, or a leaf with no
        // texture anywhere in its chain) - see BulletSprite's own doc.
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
        // Re-read fresh on every rebuild (every field edit, not just a pattern switch) rather than
        // once per canvas lifetime - cheap (data/bullets.json is small) and keeps a bulletId's
        // resolved art in sync with edits made to that referenced BulletDef elsewhere in the editor
        // during this same session.
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
        // Mirrors BurstAimedFiring's own constructor seeding shootTimer = phaseOffset - see
        // stepBurstAimed()'s own doc on what that's for.
        if ("BurstAimed".equals(def.type)) r.timer = def.phaseOffset;
        return r;
    }

    // --- Bullet sprite resolution - see BulletSprite's own doc -----------------------------------

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

    /** Mirrors PatternFactory.buildBulletAnimation()'s texture/frame-layout fallback chain (pattern
     *  field wins, then its referenced BulletDef's, then FiringPatternDef.DEFAULT_BULLET_*) minus
     *  the enemy-level fallback, which this per-pattern preview has no enemy to resolve. Returns
     *  null wherever that real code would - no texture anywhere in the chain, or the referenced file
     *  doesn't exist on disk yet - so callers fall back to the plain colored dot. */
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

    /** Reads data/bullets.json exactly like PatternRegistry.load() does in the real game (same
     *  "one flat array, each entry its own BulletDef" shape), just via a plain java.io read + Json.
     *  fromJson(Class, Class, String) instead of Gdx.files.internal(...) - that overload needs a
     *  running LibGDX backend to resolve a FileHandle from (see this class's own top-of-file doc),
     *  which this plain JavaFX process never has; a raw file read and the (Class,Class,String)
     *  overload sidestep that entirely, the same trick PatternIds' id-listing methods already use. */
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
                if (r.timer >= rate) { r.timer -= rate; spawnWallRow(d, r.sprite); }
            }
            case "Orbiting" -> {
                float rate = d.fireRate;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnOrbitBullet(d, r.sprite); }
            }
            case "Sweep" -> {
                float rate = d.fireRate;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnSweepShot(d, r.sprite); }
            }
            case "Laser" -> {
                float rate = d.fireRate > 0 ? d.fireRate : (d.duration > 0 ? d.duration + 0.5f : 2f);
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnLaser(d); }
            }
            case "RadialNearMiss" -> {
                float rate = d.fireRate > 0 ? d.fireRate : 1.5f;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnRadialVolley(d, r.sprite); }
            }
            case "SineWave" -> {
                float rate = d.fireRate;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnSineShot(d, r.sprite, 0.6f, 2f); }
            }
            case "Feather" -> {
                float rate = d.fireRate;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnSineShot(d, r.sprite, 1.4f, 1.1f, 1.5f); }
            }
            case "BurstAimed" -> stepBurstAimed(r, d, delta);
            default -> { // Aimed, AimedAtPoint, QuarterCircle, SelfDestruct, ExplodingAimed
                float rate = d.fireRate;
                r.timer += delta;
                if (r.timer >= rate) { r.timer -= rate; spawnAimedFamily(d, type, r.sprite); }
            }
        }
    }

    // Mirrors BurstAimedFiring's own state machine exactly (see that class's own doc/fields:
    // shootTimer -> r.timer, isBursting -> r.bursting, burstTimer/currentBurstShot identical) -
    // unlike every other leaf type above, BurstAimed needed its OWN case rather than falling into
    // spawnAimedFamily()'s generic "one shot every fireRate seconds" handling, since its entire
    // authored purpose is firing BURST_COUNT aimed shots burstInterval seconds apart, then waiting
    // fireRate seconds before the next burst - collapsing that to one shot per fireRate (this
    // preview's previous behavior) silently dropped the burst - and with it def.burstInterval/
    // def.phaseOffset, both authored/editable in FiringPatternFieldsEditor but never read anywhere
    // in this file - entirely, making it look and behave just like a plain Aimed pattern.
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
                // BurstAimedFiring.fireAimedShot() aims straight at the player hitbox with no
                // targetOffsetX/Y support at all (unlike AimedFiring) - aimAngle(d) would silently
                // apply an offset the real pattern never reads, so this aims raw instead.
                float angle = (float) Math.toDegrees(Math.atan2(playerY - emitterY(d), playerX - emitterX(d)));
                spawnStraight(d, angle, speedOf(d, 5f), sizeOf(d, 0.25f), r.sprite);
                r.currentBurstShot++;
                if (r.currentBurstShot >= BURST_AIMED_BURST_COUNT) r.bursting = false;
            }
        }
    }

    // --- Spawners - each mirrors one FiringPattern subclass's own shape/timing (see
    // PatternFactory.createFiring()'s dispatch for the real formula each is modeled on) ----------

    /** Aimed/AimedAtPoint/QuarterCircle/SelfDestruct/ExplodingAimed all reduce to "one or more
     *  straight shots on a fixed bearing, chosen once at spawn" for preview purposes -
     *  QuarterCircleFiring's own spread fan (see PatternFactory's "QuarterCircle" case) is the
     *  only one of these that's actually a multi-bullet volley. BurstAimed is handled separately -
     *  see stepBurstAimed()'s own doc for why it needed its own case instead. */
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

    private void spawnSweepShot(FiringPatternDef d, BulletSprite sprite) {
        float start = !Float.isNaN(d.sweepStartAngle) ? d.sweepStartAngle : 45f;
        float end = !Float.isNaN(d.sweepEndAngle) ? d.sweepEndAngle : 135f;
        float duration = d.sweepDuration > 0 ? d.sweepDuration : 3f;
        float t = (elapsed % duration) / duration;
        float angle = start + (end - start) * t;
        spawnStraight(d, angle, speedOf(d, 5f), sizeOf(d, 0.25f), sprite);
    }

    private void spawnSineShot(FiringPatternDef d, BulletSprite sprite, float defaultAmplitude, float defaultFrequency) {
        spawnSineShot(d, sprite, defaultAmplitude, defaultFrequency, 5f);
    }

    private void spawnSineShot(FiringPatternDef d, BulletSprite sprite, float defaultAmplitude, float defaultFrequency, float defaultSpeed) {
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.SINE;
        b.sprite = sprite;
        b.x = b.originX = emitterX(d);
        b.y = b.originY = emitterY(d);
        b.angleDeg = aimAngle(d);
        b.speed = speedOf(d, defaultSpeed);
        applySpeedProfile(b, d);
        b.size = sizeOf(d, 0.2f);
        b.amplitude = d.amplitude > 0 ? d.amplitude : defaultAmplitude;
        b.frequency = d.frequency > 0 ? d.frequency : defaultFrequency;
        bullets.add(b);
    }

    private void spawnOrbitBullet(FiringPatternDef d, BulletSprite sprite) {
        float radius = d.orbitRadius > 0 ? d.orbitRadius : 1.5f;
        float orbitSpeed = d.orbitSpeed != 0 ? d.orbitSpeed : 2f;
        PreviewBullet b = new PreviewBullet();
        b.kind = Kind.SINE; // reuses the "origin + phase" evaluation; amplitude/frequency repurposed below
        b.sprite = sprite;
        b.originX = emitterX(d);
        b.originY = emitterY(d) - radius; // drifts downward, orbiting as it goes - visually distinct from a straight sine shot
        b.angleDeg = -90f; // downward
        b.speed = speedOf(d, 4f);
        b.rampable = false; // OrbitingFiring never threads a SpeedProfile through its bullets - see PreviewBullet.rampable's own doc
        b.size = sizeOf(d, 0.5f);
        b.amplitude = radius;
        b.frequency = orbitSpeed / (float) (Math.PI * 2);
        b.x = b.originX;
        b.y = b.originY;
        bullets.add(b);
    }

    private void spawnWallRow(FiringPatternDef d, BulletSprite sprite) {
        float margin = d.wallMarginX > 0 ? d.wallMarginX : 0.25f;
        float spacing = d.wallSpacing > 0 ? d.wallSpacing : 0.4f;
        int gapStart = Math.max(d.gapLaneStart, 0);
        int gapCount = d.gapLaneCount >= 0 ? d.gapLaneCount : 1;
        float speed = speedOf(d, 5f);
        float size = sizeOf(d, 0.25f);
        int lane = 0;
        for (float x = margin; x < WORLD_WIDTH - margin; x += spacing, lane++) {
            if (lane >= gapStart && lane < gapStart + gapCount) continue;
            PreviewBullet b = new PreviewBullet();
            b.kind = Kind.STRAIGHT;
            b.sprite = sprite;
            b.x = x;
            b.y = emitterY(d);
            b.angleDeg = -90f;
            b.speed = speed;
            applySpeedProfile(b, d);
            b.size = size;
            bullets.add(b);
        }
    }

    private void spawnRadialVolley(FiringPatternDef d, BulletSprite sprite) {
        int count = d.numBullets > 0 ? d.numBullets : 16;
        float speed = speedOf(d, 4f);
        float size = sizeOf(d, 0.3f);
        for (int i = 0; i < count; i++) {
            float angle = 360f * i / count;
            spawnStraight(d, angle, speed, size, sprite);
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
        b.angleDeg = angleDeg;
        b.speed = speed;
        applySpeedProfile(b, d);
        b.size = size;
        bullets.add(b);
    }

    /** Builds `b`'s own ramp state from whichever speed-change spec PatternFactory.speedProfile()
     *  would have picked for `d` - its authored bulletSpeedPhases sequence if it set one (wholesale,
     *  never merged with the flat fields below - see FiringPatternDef.bulletSpeedPhases's own doc),
     *  else its referenced BulletDef's phases, otherwise a single phase held forever from the flat
     *  bulletAcceleration (pattern's own, else the BulletDef's). minSpeed/maxSpeed mirror
     *  PatternFactory.bulletMinSpeed()/bulletMaxSpeed() (0 = "can decelerate to a stop but not
     *  reverse", unbounded above unless set). */
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

    /** Draws `b`'s real sprite - cropped to its current animation frame and sized to its actual
     *  bulletSize world-unit width (matching EnemyBullet.init()'s own "width = size, height = size *
     *  frameAspect" sizing exactly, see e.g. SineBullet.init()) - instead of the plain colored dot,
     *  rotated to face its travel bearing the same way the real bullet classes orient themselves
     *  (velocity.angleDeg() - 90, see e.g. SineBullet.update()) modulo the world/canvas Y-flip every
     *  other draw call here already accounts for (see worldToCanvasY) - derivable as
     *  90 - b.angleDeg, but not pixel-verified against the real renderer since this preview draws
     *  everything from scratch on a plain Canvas rather than reusing LibGDX's own Sprite pipeline
     *  (see this class's own top-of-file doc on why). Good enough for a preview; not claimed exact. */
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
        g.rotate(90 - b.angleDeg);
        g.drawImage(sprite.sheet, sx, sy, sprite.frameW, sprite.frameH, -width / 2, -height / 2, width, height);
        g.restore();
    }

    private double worldToCanvasX(double worldX) { return worldX * SCALE; }
    private double worldToCanvasY(double worldY) { return getHeight() - worldY * SCALE; }
}
