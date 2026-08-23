package whitelabeltest.gamemanagers.spawning;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.input.InputManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import whitelabeltest.enemy.BulletDef;
import whitelabeltest.enemy.BulletSpeedPhase;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.FiringPatternDef;
import whitelabeltest.enemy.GenericEnemy;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.LaserFiring;
import whitelabeltest.enemy.firingpatterns.OrbitingFiring;
import whitelabeltest.enemy.firingpatterns.SineWaveFiring;
import whitelabeltest.enemy.firingpatterns.SweepFiring;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.MoveToPointMovement;
import whitelabeltest.enemy.movementpatterns.SeekingMovement;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Debug-only tool: pick any enemy from the JSON-loaded registry (or create a brand-new one),
 *  edit its stats and its movement/firing pattern trees - including switching a pattern's type
 *  and adding/removing nested Sequence/Combined/Squadron sub-patterns - and watch a dedicated
 *  preview enemy replay the result live. Every edit installs the working copy into
 *  PatternRegistry/AssetManager (see applyChange) and fully respawns the preview enemy, so the
 *  change is visible immediately. Nothing is written to the JSON files until the "Save All To
 *  Disk" row is confirmed. */
public class PatternPreviewer {
    private static final String[] MOVEMENT_TYPES = {"None", "Straight", "ZigZag", "Seeking", "MoveToPoint", "Spline", "Sequence", "Squadron"};
    private static final String[] FIRING_TYPES = {"None", "SelfDestruct", "ExplodingAimed", "BurstAimed", "Sweep", "SineWave", "Orbiting", "Wall", "PolkaDot", "RadialNearMiss", "SpawnEnemy", "Aimed", "QuarterCircle", "AimedAtPoint", "Laser", "Sequence", "Combined"};
    private static final String NONE_LABEL = "(none)";
    // See HitboxSpec.Shape - NONE_LABEL here means "null", i.e. let the bullet class's own default
    // shape stand (see each bullet class's getHitRadius()) rather than forcing one.
    private static final Array<String> HITBOX_SHAPE_OPTIONS = Array.with(NONE_LABEL, "Circle", "Rectangle");

    private interface FloatGetter { float get(); }
    private interface FloatSetter { void set(float value); }
    private interface BoolGetter { boolean get(); }
    private interface BoolSetter { void set(boolean value); }

    private static final class Row {
        final int indent;
        final Supplier<String> label;
        final Runnable onLeft;
        final Runnable onRight;
        final Runnable onConfirm;
        final Runnable onDelete;

        Row(int indent, Supplier<String> label, Runnable onLeft, Runnable onRight, Runnable onConfirm, Runnable onDelete) {
            this.indent = indent;
            this.label = label;
            this.onLeft = onLeft;
            this.onRight = onRight;
            this.onConfirm = onConfirm;
            this.onDelete = onDelete;
        }
    }

    public static final class DisplayRow {
        public final int indent;
        public final String label;

        DisplayRow(int indent, String label) {
            this.indent = indent;
            this.label = label;
        }
    }

    private boolean active;
    private AssetManager assets;
    private EntityManager entities;
    private float worldWidth, worldHeight;
    private float spawnX, spawnY;
    private String enemyId;
    private EnemyDefinition workingEnemy;
    private MovementPatternDef workingMovement;
    private FiringPatternDef workingFiring;
    // Independent of the selected enemy/movement/firing trio above - bullets aren't referenced by
    // id from EnemyDefinition, only from a FiringPatternDef's own bulletId, so this section is
    // browsed on its own instead of following enemy selection.
    private BulletDef workingBullet;
    private GenericEnemy previewEnemy;
    private final Array<Row> rows = new Array<>();
    private int selectedRow;
    private boolean dirty;
    private String statusMessage;
    private float statusMessageTimer;
    private Array<String> textureFilesCache;

    // ---- In-menu text entry (new enemy/movement/firing id prompts) ----------------------------
    // Gdx.input.getTextInput is a no-op on the lwjgl3 desktop backend (it just calls canceled()
    // immediately, no dialog is shown), so id entry is done with a small text field built into
    // this screen instead: a temporary InputProcessor captures keystrokes while active, and the
    // normal row navigation in handleInput() is suppressed until it's confirmed or cancelled.
    private boolean textEntryActive;
    private String textEntryTitle;
    private StringBuilder textEntryBuffer;
    private Consumer<String> textEntryCallback;
    private InputProcessor previousInputProcessor;
    private boolean suppressNextMenuInput;

    public boolean isActive() { return active; }
    public int getSelectedRow() { return selectedRow; }
    public boolean isTextEntryActive() { return textEntryActive; }
    public String getTextEntryTitle() { return textEntryTitle; }
    public String getTextEntryText() { return textEntryBuffer != null ? textEntryBuffer.toString() : ""; }

    public Array<DisplayRow> getDisplayRows() {
        Array<DisplayRow> out = new Array<>(rows.size);
        for (Row r : rows) out.add(new DisplayRow(r.indent, r.label.get()));
        return out;
    }

    public void open(EntityManager entities, AssetManager assets, float worldWidth, float worldHeight) {
        active = true;
        this.assets = assets;
        this.entities = entities;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.spawnX = worldWidth / 2f;
        this.spawnY = worldHeight / 2f;
        this.dirty = false;
        this.statusMessage = null;
        this.statusMessageTimer = 0f;
        this.selectedRow = 0;
        this.textureFilesCache = null;

        Array<String> ids = assets.getEnemyIds();
        selectEnemy(ids.size > 0 ? ids.first() : null);

        Array<String> bulletIds = PatternRegistry.getBulletIds();
        selectBulletId(bulletIds.size > 0 ? bulletIds.first() : null);
    }

    public void close(EntityManager entities) {
        if (!active) return;
        if (textEntryActive) finishTextEntry(false);
        active = false;
        if (previewEnemy != null) {
            entities.getEnemies().removeValue(previewEnemy, true);
            ObjectPools.freeEnemy(previewEnemy);
            previewEnemy = null;
        }
    }

    /** @return true if the delete key was consumed by the selected row (e.g. removing a
     *  sub-pattern) rather than falling through to closing the whole screen. */
    public boolean handleInput(InputManager input) {
        if (textEntryActive) return false;
        if (suppressNextMenuInput) {
            // The same physical Enter press that just confirmed/cancelled the text field would
            // otherwise also register as a menu confirm this frame (both the InputProcessor
            // callback and InputManager's polling see the same keypress) and immediately re-fire
            // whichever row opened the prompt.
            suppressNextMenuInput = false;
            return false;
        }
        if (rows.size == 0) return false;

        if (input.isDebugMenuUpJustPressed()) selectedRow = (selectedRow - 1 + rows.size) % rows.size;
        if (input.isDebugMenuDownJustPressed()) selectedRow = (selectedRow + 1) % rows.size;

        Row row = rows.get(selectedRow);
        if (input.isDebugMenuLeftJustPressed() && row.onLeft != null) row.onLeft.run();
        if (input.isDebugMenuRightJustPressed() && row.onRight != null) row.onRight.run();
        if (input.isDebugMenuConfirmJustPressed() && row.onConfirm != null) row.onConfirm.run();
        if (input.isDebugMenuDeleteJustPressed() && row.onDelete != null) {
            row.onDelete.run();
            return true;
        }
        return false;
    }

    /** Steps the preview enemy (and any bullets its firing pattern spawns) each frame, since the
     *  rest of the game is frozen while the debug menu is open. Respawns from a clean starting
     *  point once its pattern carries it off-screen, so the demo keeps replaying. */
    public void tick(float delta, EntityManager entities) {
        if (!active) return;

        if (statusMessageTimer > 0f) {
            statusMessageTimer -= delta;
            if (statusMessageTimer <= 0f) {
                statusMessage = null;
                rebuildRows();
            }
        }

        if (previewEnemy == null) return;

        previewEnemy.update(delta, entities.getEnemyBullets(), entities.getPlayer().getHitbox(), entities.getPlayer().getGrazeHitbox(), false);

        Array<EnemyBullet> enemyBullets = entities.getEnemyBullets();
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            b.update(delta);
            if (b.isOffScreen()) {
                enemyBullets.removeIndex(i);
                ObjectPools.freeEnemyBullet(b);
            }
        }

        if (previewEnemy.isOffScreen()) respawnPreview();
    }

    // ---- Selection / creation ----------------------------------------------------------------

    private void selectEnemy(String id) {
        this.enemyId = id;
        EnemyDefinition src = id != null ? assets.getEnemyDefinition(id) : null;
        workingEnemy = cloneEnemy(src, id);
        loadMovementDef(workingEnemy.movementPattern);
        loadFiringDef(workingEnemy.firingPattern);
        applyChange();
    }

    private void selectMovementId(String id) {
        loadMovementDef(id);
        applyChange();
    }

    private void selectFiringId(String id) {
        loadFiringDef(id);
        applyChange();
    }

    private void selectBulletId(String id) {
        loadBulletDef(id);
        applyChange();
    }

    private void loadMovementDef(String id) {
        workingMovement = cloneMovement(PatternRegistry.getMovement(id), id);
        resolveMovementSentinels(workingMovement);
    }

    private void loadFiringDef(String id) {
        workingFiring = cloneFiring(PatternRegistry.getFiring(id), id);
        resolveFiringSentinels(workingFiring);
    }

    private void loadBulletDef(String id) {
        workingBullet = cloneBulletDef(PatternRegistry.getBullet(id), id);
        resolveBulletSentinels(workingBullet);
    }

    private void createNewEnemy(String id) {
        if (id == null || id.isBlank()) return;
        assets.putEnemyDefinition(cloneEnemy(null, id));
        selectEnemy(id);
    }

    private void createNewMovement(String id) {
        if (id == null || id.isBlank()) return;
        MovementPatternDef fresh = new MovementPatternDef();
        fresh.id = id;
        fresh.type = "Straight";
        PatternRegistry.putMovement(id, fresh);
        selectMovementId(id);
    }

    private void createNewFiring(String id) {
        if (id == null || id.isBlank()) return;
        FiringPatternDef fresh = new FiringPatternDef();
        fresh.id = id;
        fresh.type = "None";
        PatternRegistry.putFiring(id, fresh);
        selectFiringId(id);
    }

    private void createNewBulletDef(String id) {
        if (id == null || id.isBlank()) return;
        BulletDef fresh = new BulletDef();
        fresh.id = id;
        PatternRegistry.putBullet(id, fresh);
        selectBulletId(id);
    }

    private void promptNewId(String title, Consumer<String> onEntered) {
        promptTextEntry(title, c -> Character.isLetterOrDigit(c) || c == '_' || c == '-', onEntered);
    }

    /** Same text-field prompt as promptNewId, but the character filter only lets through digits,
     *  '.' and '-' (a leading minus, or a decimal point for non-integer fields) - used by numberRow
     *  so stat values can be typed exactly instead of only nudged with left/right. */
    private void promptNumber(String name, float current, boolean isInt, FloatSetter setter) {
        String title = "Enter value for " + name + " (current: " + (isInt ? String.valueOf(Math.round(current)) : formatFloat(current)) + ")";
        Predicate<Character> filter = isInt
            ? c -> Character.isDigit(c) || c == '-'
            : c -> Character.isDigit(c) || c == '-' || c == '.';
        promptTextEntry(title, filter, text -> {
            try {
                float parsed = Float.parseFloat(text);
                setter.set(isInt ? Math.round(parsed) : snap(parsed));
                applyChange();
            } catch (NumberFormatException ignored) {
                // Leave the field unchanged on unparsable input (e.g. a bare "-" or ".").
            }
        });
    }

    private void promptTextEntry(String title, Predicate<Character> charFilter, Consumer<String> onEntered) {
        textEntryActive = true;
        textEntryTitle = title;
        textEntryBuffer = new StringBuilder();
        textEntryCallback = onEntered;
        previousInputProcessor = Gdx.input.getInputProcessor();
        Gdx.input.setInputProcessor(new InputAdapter() {
            // A single input drain can deliver multiple queued events against this same
            // processor instance (e.g. Enter fires both KEY_DOWN and KEY_TYPED('\r')), so every
            // callback must no-op once finishTextEntry() has already run and cleared the buffer.
            @Override
            public boolean keyTyped(char character) {
                if (!textEntryActive) return false;
                if (textEntryBuffer.length() < 40 && charFilter.test(character)) {
                    textEntryBuffer.append(character);
                }
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (!textEntryActive) return false;
                if (keycode == Input.Keys.BACKSPACE) {
                    if (textEntryBuffer.length() > 0) textEntryBuffer.setLength(textEntryBuffer.length() - 1);
                    return true;
                }
                if (keycode == Input.Keys.ENTER) {
                    finishTextEntry(true);
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    finishTextEntry(false);
                    return true;
                }
                return false;
            }
        });
    }

    private void finishTextEntry(boolean confirmed) {
        Gdx.input.setInputProcessor(previousInputProcessor);
        textEntryActive = false;
        suppressNextMenuInput = true;
        String id = confirmed && textEntryBuffer != null ? textEntryBuffer.toString().trim() : null;
        Consumer<String> callback = textEntryCallback;
        textEntryCallback = null;
        textEntryBuffer = null;
        if (id != null && !id.isEmpty() && callback != null) callback.accept(id);
    }

    // ---- Apply / respawn / save ---------------------------------------------------------------

    /** Commits the working copies into the live registries, rebuilds the editable row list (its
     *  structure can change - a type switch or add/remove sub-pattern changes which rows exist)
     *  and fully respawns the preview enemy from the edited definition. Called after every single
     *  edit; this is a debug tool, not a hot path, so simplicity wins over incremental updates. */
    private void applyChange() {
        dirty = true;
        workingEnemy.movementPattern = workingMovement.id;
        workingEnemy.firingPattern = workingFiring.id;
        PatternRegistry.putMovement(workingMovement.id, workingMovement);
        PatternRegistry.putFiring(workingFiring.id, workingFiring);
        // workingBullet is set up independently of the enemy/movement/firing trio above (see
        // open()) but shares this same applyChange() - every row-building helper (numberRow,
        // toggleRow, idPickRow, promptNumber's typed-entry path) already hardcodes a call to this
        // method, so folding the bullet section in here is what actually lets those rows persist
        // its edits, rather than needing a parallel applyBulletChange() the row helpers don't know
        // to call.
        if (workingBullet != null) PatternRegistry.putBullet(workingBullet.id, workingBullet);
        assets.putEnemyDefinition(workingEnemy);
        rebuildRows();
        respawnPreview();
    }

    // Left/right-nudge or typed-entry target for the "-- Preview Position --" rows - clamped to
    // stay on the visible play field, since respawnPreview() always spawns here next.
    private void setSpawnX(float v) { spawnX = MathUtils.clamp(v, 0f, worldWidth); }
    private void setSpawnY(float v) { spawnY = MathUtils.clamp(v, 0f, worldHeight); }

    private void respawnPreview() {
        if (entities == null) return;
        if (previewEnemy != null) {
            entities.getEnemies().removeValue(previewEnemy, true);
            ObjectPools.freeEnemy(previewEnemy);
            previewEnemy = null;
        }
        if (workingEnemy.id == null) return;

        assets.ensureTexture(workingEnemy.texture);
        assets.ensureTexture(workingEnemy.bulletTexture);
        assets.ensureTexture(workingEnemy.spawnTexture);
        assets.ensureTexture(workingEnemy.deathTexture);

        previewEnemy = EnemySpawnRegistry.spawn(workingEnemy.id, spawnX, spawnY);
    }

    /** Writes every registered movement pattern, firing pattern, enemy definition and bullet
     *  definition (including whatever's been live-edited this session) back to the real assets/
     *  JSON files. Only
     *  resolves to the true source files when launched via `gradlew run`/`:lwjgl3:run`, which
     *  pins the working directory to assets/ (see lwjgl3/build.gradle) - the same mechanism
     *  DebugSaveStateManager already relies on for debug_savestates.json. */
    private void saveAll() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        // One file per movement pattern (filename == id) instead of one shared array - see
        // PatternRegistry.load()'s matching read side for why.
        FileHandle movementDir = Gdx.files.local("data/movement_patterns");
        movementDir.mkdirs();
        for (String id : PatternRegistry.getMovementIds()) {
            MovementPatternDef def = PatternRegistry.getMovement(id);
            movementDir.child(id + ".json").writeString(json.prettyPrint(json.toJson(def, MovementPatternDef.class)), false);
        }
        // Same per-file split for firing patterns.
        FileHandle firingDir = Gdx.files.local("data/firing_patterns");
        firingDir.mkdirs();
        for (String id : PatternRegistry.getFiringIds()) {
            FiringPatternDef def = PatternRegistry.getFiring(id);
            firingDir.child(id + ".json").writeString(json.prettyPrint(json.toJson(def, FiringPatternDef.class)), false);
        }
        Gdx.files.local("data/enemies.json").writeString(
            json.prettyPrint(json.toJson(assets.getAllEnemyDefinitionsSorted(), Array.class, EnemyDefinition.class)), false);

        Array<BulletDef> bulletDefs = new Array<>();
        for (String id : PatternRegistry.getBulletIds()) bulletDefs.add(PatternRegistry.getBullet(id));
        Gdx.files.local("data/bullets.json").writeString(
            json.prettyPrint(json.toJson(bulletDefs, Array.class, BulletDef.class)), false);

        dirty = false;
        statusMessage = "Saved to disk.";
        statusMessageTimer = 3f;
        rebuildRows();
    }

    // ---- Cloning / defaults --------------------------------------------------------------------

    private EnemyDefinition cloneEnemy(EnemyDefinition src, String fallbackId) {
        EnemyDefinition d = new EnemyDefinition();
        if (src != null) {
            d.id = src.id;
            d.texture = src.texture;
            d.bulletTexture = src.bulletTexture;
            d.frameCount = src.frameCount;
            d.columns = src.columns;
            d.rows = src.rows;
            d.frameDuration = src.frameDuration;
            d.size = src.size;
            d.health = src.health;
            d.movementPattern = src.movementPattern;
            d.inverseMovement = src.inverseMovement;
            d.rotateWithMovement = src.rotateWithMovement;
            d.isBoss = src.isBoss;
            d.isGround = src.isGround;
            d.sealable = src.sealable;
            d.defiant = src.defiant;
            d.damageableByEnemyBullets = src.damageableByEnemyBullets;
            d.showHealthBar = src.showHealthBar;
            d.targetableByHoming = src.targetableByHoming;
            d.pairId = src.pairId;
            d.healthRegenPerSecond = src.healthRegenPerSecond;
            d.bulletCancel = src.bulletCancel;
            d.score = src.score;
            d.firingPattern = src.firingPattern;
            d.explosionPattern = src.explosionPattern;
            d.spawnTexture = src.spawnTexture;
            d.spawnFrameCount = src.spawnFrameCount;
            d.spawnColumns = src.spawnColumns;
            d.spawnRows = src.spawnRows;
            d.spawnDuration = src.spawnDuration;
            d.deathTexture = src.deathTexture;
            d.deathFrameCount = src.deathFrameCount;
            d.deathColumns = src.deathColumns;
            d.deathRows = src.deathRows;
            d.deathDuration = src.deathDuration;
        } else {
            d.id = fallbackId;
            Array<String> textures = listTextureFiles();
            d.texture = textures.size > 0 ? textures.first() : null;
            d.frameCount = 1;
            d.columns = 0;
            d.rows = 1;
            d.frameDuration = 0.1f;
            d.size = 1f;
            d.health = 10;
            d.movementPattern = "None";
            d.firingPattern = "NoFiring";
            d.explosionPattern = PatternRegistry.getExplosionIds().size > 0 ? PatternRegistry.getExplosionIds().first() : null;
            d.score = 10;
        }
        return d;
    }

    private static MovementPatternDef cloneMovement(MovementPatternDef src, String fallbackId) {
        MovementPatternDef d = new MovementPatternDef();
        if (src != null) {
            d.id = src.id;
            d.type = src.type;
            d.speed = src.speed;
            d.movementAngle = src.movementAngle;
            d.stopDistance = src.stopDistance;
            d.targetX = src.targetX;
            d.targetY = src.targetY;
            d.duration = src.duration;
            d.patterns = src.patterns;
            d.pattern = src.pattern;
            d.offsetX = src.offsetX;
            d.offsetY = src.offsetY;
        } else {
            d.id = fallbackId != null ? fallbackId : "NewMovement";
            d.type = "Straight";
        }
        return d;
    }

    private static FiringPatternDef cloneFiring(FiringPatternDef src, String fallbackId) {
        FiringPatternDef d = new FiringPatternDef();
        if (src != null) {
            d.id = src.id;
            d.type = src.type;
            d.fireRate = src.fireRate;
            d.duration = src.duration;
            d.spawnType = src.spawnType;
            d.bulletId = src.bulletId;
            d.bulletSize = src.bulletSize;
            d.bulletSpeed = src.bulletSpeed;
            d.bulletAcceleration = src.bulletAcceleration;
            d.bulletMinSpeed = src.bulletMinSpeed;
            d.bulletMaxSpeed = src.bulletMaxSpeed;
            d.bulletSpeedPhases = src.bulletSpeedPhases;
            d.bulletSpeedPhasesLoop = src.bulletSpeedPhasesLoop;
            d.bulletDamage = src.bulletDamage;
            d.bulletTexture = src.bulletTexture;
            d.bulletFrameCount = src.bulletFrameCount;
            d.bulletColumns = src.bulletColumns;
            d.bulletRows = src.bulletRows;
            d.bulletFrameDuration = src.bulletFrameDuration;
            d.hitboxShape = src.hitboxShape;
            d.hitboxScale = src.hitboxScale;
            d.hitboxOffsetX = src.hitboxOffsetX;
            d.hitboxOffsetY = src.hitboxOffsetY;
            d.spreadDegrees = src.spreadDegrees;
            d.numBullets = src.numBullets;
            d.offsetX = src.offsetX;
            d.offsetY = src.offsetY;
            d.length = src.length;
            d.angularSpeed = src.angularSpeed;
            d.fireAngle = src.fireAngle;
            d.quarterCircleFixedAngle = src.quarterCircleFixedAngle;
            d.targetX = src.targetX;
            d.targetY = src.targetY;
            d.targetOffsetX = src.targetOffsetX;
            d.targetOffsetY = src.targetOffsetY;
            d.sweepDuration = src.sweepDuration;
            d.sweepStartAngle = src.sweepStartAngle;
            d.sweepEndAngle = src.sweepEndAngle;
            d.amplitude = src.amplitude;
            d.frequency = src.frequency;
            d.orbitRadius = src.orbitRadius;
            d.orbitSpeed = src.orbitSpeed;
            d.wallMarginX = src.wallMarginX;
            d.wallSpacing = src.wallSpacing;
            d.gapLaneStart = src.gapLaneStart;
            d.gapLaneCount = src.gapLaneCount;
            d.gapLaneSequence = src.gapLaneSequence;
            d.nearMissDistance = src.nearMissDistance;
            d.volleyCount = src.volleyCount;
            d.phaseOffset = src.phaseOffset;
            d.burstInterval = src.burstInterval;
            d.patterns = src.patterns;
        } else {
            d.id = fallbackId != null ? fallbackId : "NewFiring";
            d.type = "None";
        }
        return d;
    }

    /** Materializes "use the default" sentinels (-1, NaN) into concrete numbers so the field
     *  steppers below have something real to adjust from, and seeds an empty Sequence/Squadron
     *  with a first sub-pattern so switching to that type immediately has something editable. */
    private void resolveMovementSentinels(MovementPatternDef d) {
        if (d.speed <= 0) d.speed = 1.5f;
        if (Float.isNaN(d.movementAngle)) d.movementAngle = MovementPattern.DEFAULT_ANGLE_DEG;
        if (d.stopDistance <= 0) {
            d.stopDistance = "MoveToPoint".equals(d.type) ? MoveToPointMovement.DEFAULT_STOP_DISTANCE : SeekingMovement.DEFAULT_STOP_DISTANCE;
        }
        if (Float.isNaN(d.targetX)) d.targetX = spawnX;
        if (Float.isNaN(d.targetY)) d.targetY = 0f;
        if ("Sequence".equals(d.type)) {
            if (d.patterns == null || d.patterns.size == 0) {
                d.patterns = new Array<>();
                MovementPatternDef first = new MovementPatternDef();
                first.type = "Straight";
                d.patterns.add(first);
            }
        } else {
            // Clears sub-patterns left over from switching away from Sequence/Squadron - otherwise
            // a cloned/leftover patterns or pattern array dangles on the def and gets serialized
            // into a leaf node that never reads it (see saveAll).
            d.patterns = null;
        }
        if ("Squadron".equals(d.type)) {
            if (d.pattern == null) {
                d.pattern = new MovementPatternDef();
                d.pattern.type = "Straight";
            }
        } else {
            d.pattern = null;
        }
    }

    private void resolveFiringSentinels(FiringPatternDef d) {
        if (d.bulletSize <= 0) d.bulletSize = "Orbiting".equals(d.type) ? 0.5f : 0.25f;
        if (d.bulletSpeed <= 0) d.bulletSpeed = "Orbiting".equals(d.type) ? 4f : 5f;
        if (d.bulletDamage <= 0) d.bulletDamage = BulletDef.DEFAULT_DAMAGE;
        if (d.spreadDegrees <= 0) d.spreadDegrees = 90f;
        if (d.numBullets <= 0) d.numBullets = 9;
        if (d.length <= 0) d.length = LaserFiring.DEFAULT_LENGTH;
        if (Float.isNaN(d.fireAngle)) d.fireAngle = 0f;
        if (Float.isNaN(d.targetX)) d.targetX = 0f;
        if (Float.isNaN(d.targetY)) d.targetY = 0f;
        if (d.sweepDuration <= 0) d.sweepDuration = SweepFiring.DEFAULT_SWEEP_DURATION;
        if (Float.isNaN(d.sweepStartAngle)) d.sweepStartAngle = SweepFiring.DEFAULT_START_ANGLE;
        if (Float.isNaN(d.sweepEndAngle)) d.sweepEndAngle = SweepFiring.DEFAULT_END_ANGLE;
        if (d.amplitude <= 0) d.amplitude = SineWaveFiring.DEFAULT_AMPLITUDE;
        if (d.frequency <= 0) d.frequency = SineWaveFiring.DEFAULT_FREQUENCY;
        if (d.orbitRadius <= 0) d.orbitRadius = OrbitingFiring.DEFAULT_ORBIT_RADIUS;
        if (d.orbitSpeed <= 0) d.orbitSpeed = OrbitingFiring.DEFAULT_ORBIT_SPEED;
        if (d.wallMarginX <= 0) d.wallMarginX = 0.25f;
        if (d.wallSpacing <= 0) d.wallSpacing = 0.4f;
        if (d.gapLaneStart < 0) d.gapLaneStart = 0;
        if (d.gapLaneCount < 0) d.gapLaneCount = 1;
        if (d.nearMissDistance <= 0) d.nearMissDistance = 0.35f;
        if (d.volleyCount < 0) d.volleyCount = 3;
        if (d.bulletId == null) {
            Array<String> bulletIds = PatternRegistry.getBulletIds();
            if (bulletIds.size > 0) d.bulletId = bulletIds.first();
        }
        if ("Sequence".equals(d.type) || "Combined".equals(d.type)) {
            if (d.patterns == null || d.patterns.size == 0) {
                d.patterns = new Array<>();
                FiringPatternDef first = new FiringPatternDef();
                first.type = "None";
                d.patterns.add(first);
            }
        } else {
            // Clears sub-patterns left over from switching away from Sequence/Combined - see the
            // matching comment in resolveMovementSentinels.
            d.patterns = null;
        }
    }

    private static BulletDef cloneBulletDef(BulletDef src, String fallbackId) {
        BulletDef d = new BulletDef();
        if (src != null) {
            d.id = src.id;
            d.bulletSize = src.bulletSize;
            d.bulletSpeed = src.bulletSpeed;
            d.bulletAcceleration = src.bulletAcceleration;
            d.bulletMinSpeed = src.bulletMinSpeed;
            d.bulletMaxSpeed = src.bulletMaxSpeed;
            d.bulletSpeedPhases = src.bulletSpeedPhases;
            d.bulletSpeedPhasesLoop = src.bulletSpeedPhasesLoop;
            d.hitboxShape = src.hitboxShape;
            d.hitboxScale = src.hitboxScale;
            d.hitboxOffsetX = src.hitboxOffsetX;
            d.hitboxOffsetY = src.hitboxOffsetY;
            d.bulletTexture = src.bulletTexture;
            d.bulletFrameCount = src.bulletFrameCount;
            d.bulletColumns = src.bulletColumns;
            d.bulletRows = src.bulletRows;
            d.bulletFrameDuration = src.bulletFrameDuration;
            d.damage = src.damage;
        } else {
            d.id = fallbackId != null ? fallbackId : "NewBullet";
        }
        return d;
    }

    private void resolveBulletSentinels(BulletDef d) {
        if (d.bulletSize <= 0) d.bulletSize = 0.25f;
        if (d.bulletSpeed <= 0) d.bulletSpeed = 5f;
        if (d.hitboxScale <= 0) d.hitboxScale = 1f;
        if (d.bulletFrameCount <= 0) d.bulletFrameCount = FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT;
        if (d.bulletColumns < 0) d.bulletColumns = FiringPatternDef.DEFAULT_BULLET_COLUMNS;
        if (d.bulletRows <= 0) d.bulletRows = FiringPatternDef.DEFAULT_BULLET_ROWS;
        if (d.bulletFrameDuration <= 0) d.bulletFrameDuration = FiringPatternDef.DEFAULT_BULLET_FRAME_DURATION;
        if (d.damage <= 0) d.damage = BulletDef.DEFAULT_DAMAGE;
    }

    private Array<String> listTextureFiles() {
        if (textureFilesCache == null) {
            textureFilesCache = new Array<>();
            collectPngFiles(Gdx.files.local("."), "", textureFilesCache);
            textureFilesCache.sort();
        }
        return textureFilesCache;
    }

    /** Recurses through assets/ collecting every .png as a path relative to assets/ root (e.g.
     *  "images/enemies/ICE000.png") - the same relative-path format assets.ensureTexture()/
     *  Gdx.files.internal() expect elsewhere, since assets/ was reorganized into subfolders
     *  instead of sitting flat. Skips assets/unused/, which holds art nothing in the game
     *  references. */
    private void collectPngFiles(FileHandle dir, String prefix, Array<String> out) {
        if (!dir.exists() || !dir.isDirectory()) return;
        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) {
                if (f.name().equals("unused")) continue;
                collectPngFiles(f, prefix + f.name() + "/", out);
            } else if ("png".equalsIgnoreCase(f.extension())) {
                out.add(prefix + f.name());
            }
        }
    }

    // ---- Row tree building ----------------------------------------------------------------------

    private void rebuildRows() {
        int prevSelected = selectedRow;
        rows.clear();

        rows.add(idPickRow(0, "Enemy", () -> enemyId, assets.getEnemyIds(), this::selectEnemy));
        rows.add(actionRow(1, () -> "[+ New Enemy]", () -> promptNewId("New enemy id", this::createNewEnemy)));
        rows.add(actionRow(0, () -> dirty ? "[ SAVE ALL TO DISK ]  *unsaved*" : "[ SAVE ALL TO DISK ]", this::saveAll));
        if (statusMessage != null) rows.add(headerRow(0, statusMessage));
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Preview Position --"));
        rows.add(numberRow(1, "Spawn X", () -> spawnX, this::setSpawnX, 0.25f, false));
        rows.add(numberRow(1, "Spawn Y", () -> spawnY, this::setSpawnY, 0.25f, false));
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Enemy Definition --"));
        appendEnemyFieldRows();
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Movement Pattern --"));
        rows.add(idPickRow(0, "Movement Id", () -> workingMovement.id, PatternRegistry.getMovementIds(), this::selectMovementId));
        rows.add(actionRow(1, () -> "[+ New Movement Pattern]", () -> promptNewId("New movement pattern id", this::createNewMovement)));
        appendMovementRows(workingMovement, 1, null, false);
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Firing Pattern --"));
        rows.add(idPickRow(0, "Firing Id", () -> workingFiring.id, PatternRegistry.getFiringIds(), this::selectFiringId));
        rows.add(actionRow(1, () -> "[+ New Firing Pattern]", () -> promptNewId("New firing pattern id", this::createNewFiring)));
        appendFiringRows(workingFiring, 1, null);
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Bullet Definition --"));
        rows.add(idPickRow(0, "Bullet Id", () -> workingBullet.id, PatternRegistry.getBulletIds(), this::selectBulletId));
        rows.add(actionRow(1, () -> "[+ New Bullet]", () -> promptNewId("New bullet id", this::createNewBulletDef)));
        appendBulletFieldRows();

        selectedRow = rows.size == 0 ? 0 : MathUtils.clamp(prevSelected, 0, rows.size - 1);
    }

    private void appendEnemyFieldRows() {
        EnemyDefinition d = workingEnemy;
        rows.add(texturePickRow(1, "Texture", () -> d.texture, v -> d.texture = v, false));
        rows.add(texturePickRow(1, "Bullet Texture", () -> d.bulletTexture, v -> d.bulletTexture = v, true));
        rows.add(numberRow(1, "Frame Count", () -> (float) d.frameCount, v -> d.frameCount = Math.round(v), 1f, true));
        rows.add(numberRow(1, "Columns", () -> (float) d.columns, v -> d.columns = Math.round(v), 1f, true));
        rows.add(numberRow(1, "Rows", () -> (float) d.rows, v -> d.rows = Math.round(v), 1f, true));
        rows.add(numberRow(1, "Frame Duration", () -> d.frameDuration, v -> d.frameDuration = v, 0.01f, false));
        rows.add(numberRow(1, "Size", () -> d.size, v -> d.size = v, 0.1f, false));
        rows.add(numberRow(1, "Health", () -> (float) d.health, v -> d.health = Math.round(v), 5f, true));
        rows.add(numberRow(1, "Score", () -> (float) d.score, v -> d.score = Math.round(v), 10f, true));
        rows.add(toggleRow(1, "Inverse Movement", () -> d.inverseMovement, v -> d.inverseMovement = v));
        rows.add(toggleRow(1, "Rotate With Movement", () -> d.rotateWithMovement, v -> d.rotateWithMovement = v));
        rows.add(toggleRow(1, "Is Boss", () -> d.isBoss, v -> d.isBoss = v));
        rows.add(toggleRow(1, "Is Ground", () -> d.isGround, v -> d.isGround = v));
        rows.add(toggleRow(1, "Sealable", () -> d.sealable, v -> d.sealable = v));
        rows.add(toggleRow(1, "Defiant", () -> d.defiant, v -> d.defiant = v));
        rows.add(toggleRow(1, "Damageable By Enemy Bullets", () -> d.damageableByEnemyBullets, v -> d.damageableByEnemyBullets = v));
        rows.add(toggleRow(1, "Show Health Bar", () -> d.showHealthBar, v -> d.showHealthBar = v));
        rows.add(toggleRow(1, "Targetable By Homing", () -> d.targetableByHoming, v -> d.targetableByHoming = v));
        rows.add(numberRow(1, "Health Regen/Sec", () -> d.healthRegenPerSecond, v -> d.healthRegenPerSecond = v, 0.5f, false));
        rows.add(toggleRow(1, "Bullet Cancel", () -> d.bulletCancel, v -> d.bulletCancel = v));
        rows.add(idPickRow(1, "Explosion Pattern", () -> d.explosionPattern, PatternRegistry.getExplosionIds(), v -> { d.explosionPattern = v; applyChange(); }));

        rows.add(headerRow(1, "Spawn animation:"));
        rows.add(texturePickRow(2, "Spawn Texture", () -> d.spawnTexture, v -> d.spawnTexture = v, true));
        rows.add(numberRow(2, "Spawn Frame Count", () -> (float) d.spawnFrameCount, v -> d.spawnFrameCount = Math.round(v), 1f, true));
        rows.add(numberRow(2, "Spawn Columns", () -> (float) d.spawnColumns, v -> d.spawnColumns = Math.round(v), 1f, true));
        rows.add(numberRow(2, "Spawn Rows", () -> (float) d.spawnRows, v -> d.spawnRows = Math.round(v), 1f, true));
        rows.add(numberRow(2, "Spawn Duration", () -> d.spawnDuration, v -> d.spawnDuration = v, 0.05f, false));

        rows.add(headerRow(1, "Death animation:"));
        rows.add(texturePickRow(2, "Death Texture", () -> d.deathTexture, v -> d.deathTexture = v, true));
        rows.add(numberRow(2, "Death Frame Count", () -> (float) d.deathFrameCount, v -> d.deathFrameCount = Math.round(v), 1f, true));
        rows.add(numberRow(2, "Death Columns", () -> (float) d.deathColumns, v -> d.deathColumns = Math.round(v), 1f, true));
        rows.add(numberRow(2, "Death Rows", () -> (float) d.deathRows, v -> d.deathRows = Math.round(v), 1f, true));
        rows.add(numberRow(2, "Death Duration", () -> d.deathDuration, v -> d.deathDuration = v, 0.05f, false));
    }

    private void appendBulletFieldRows() {
        BulletDef d = workingBullet;
        rows.add(texturePickRow(1, "Bullet Texture", () -> d.bulletTexture, v -> d.bulletTexture = v, true));
        rows.add(numberRow(1, "Frame Count", () -> (float) d.bulletFrameCount, v -> d.bulletFrameCount = Math.round(v), 1f, true));
        rows.add(numberRow(1, "Columns", () -> (float) d.bulletColumns, v -> d.bulletColumns = Math.round(v), 1f, true));
        rows.add(numberRow(1, "Rows", () -> (float) d.bulletRows, v -> d.bulletRows = Math.round(v), 1f, true));
        rows.add(numberRow(1, "Frame Duration", () -> d.bulletFrameDuration, v -> d.bulletFrameDuration = v, 0.01f, false));
        rows.add(numberRow(1, "Bullet Size", () -> d.bulletSize, v -> d.bulletSize = v, 0.05f, false));
        rows.add(numberRow(1, "Bullet Speed", () -> d.bulletSpeed, v -> d.bulletSpeed = v, 0.25f, false));
        rows.add(numberRow(1, "Damage", () -> (float) d.damage, v -> d.damage = Math.round(v), 1f, true));
        appendBulletSpeedPhaseRows(d, 1);

        rows.add(headerRow(1, "Hitbox (independent of the sprite - see HitboxSpec):"));
        rows.add(idPickRow(2, "Shape", () -> d.hitboxShape != null ? d.hitboxShape : NONE_LABEL, HITBOX_SHAPE_OPTIONS,
            v -> { d.hitboxShape = NONE_LABEL.equals(v) ? null : v; applyChange(); }));
        rows.add(numberRow(2, "Scale", () -> d.hitboxScale, v -> d.hitboxScale = v, 0.05f, false));
        rows.add(numberRow(2, "Offset X", () -> d.hitboxOffsetX, v -> d.hitboxOffsetX = v, 0.05f, false));
        rows.add(numberRow(2, "Offset Y", () -> d.hitboxOffsetY, v -> d.hitboxOffsetY = v, 0.05f, false));
    }

    /** Mirrors appendSpeedPhaseRows below, but for a BulletDef's own bulletAcceleration/
     *  bulletMinSpeed/bulletMaxSpeed/bulletSpeedPhases/bulletSpeedPhasesLoop - the same fields,
     *  same fallback role, just on the class a firing pattern's own fields fall back to (see
     *  PatternFactory.speedProfile). Kept as a separate method rather than a shared generic one:
     *  BulletDef and FiringPatternDef aren't related types, so there's no common supertype to
     *  write one method against without adding an abstraction neither class otherwise needs. */
    private void appendBulletSpeedPhaseRows(BulletDef d, int indent) {
        rows.add(numberRow(indent, "Bullet Accel", () -> d.bulletAcceleration, v -> d.bulletAcceleration = v, 0.1f, false));
        rows.add(numberRow(indent, "Bullet Min Speed", () -> d.bulletMinSpeed, v -> d.bulletMinSpeed = v, 0.25f, false));
        rows.add(numberRow(indent, "Bullet Max Speed", () -> d.bulletMaxSpeed, v -> d.bulletMaxSpeed = v, 0.25f, false));

        int count = d.bulletSpeedPhases != null ? d.bulletSpeedPhases.size : 0;
        rows.add(headerRow(indent, "Speed Phases (" + count + "):"));
        rows.add(toggleRow(indent, "Phases Loop", () -> d.bulletSpeedPhasesLoop, v -> d.bulletSpeedPhasesLoop = v));
        if (d.bulletSpeedPhases != null) {
            for (int i = 0; i < d.bulletSpeedPhases.size; i++) {
                int idx = i;
                BulletSpeedPhase phase = d.bulletSpeedPhases.get(i);
                rows.add(headerRow(indent + 1, "==== Phase " + (i + 1) + " of " + count + " ===="));
                rows.add(numberRow(indent + 1, "Acceleration", () -> phase.acceleration, v -> phase.acceleration = v, 0.25f, false));
                rows.add(numberRow(indent + 1, "Duration", () -> phase.duration, v -> phase.duration = v, 0.25f, false));
                rows.add(actionRow(indent + 1, () -> "[Remove Phase " + (idx + 1) + "]", () -> {
                    d.bulletSpeedPhases.removeIndex(idx);
                    applyChange();
                }));
                rows.add(headerRow(indent + 1, ""));
            }
        }
        rows.add(actionRow(indent + 1, () -> "[+ Add speed phase]", () -> {
            if (d.bulletSpeedPhases == null) d.bulletSpeedPhases = new Array<>();
            d.bulletSpeedPhases.add(new BulletSpeedPhase());
            applyChange();
        }));
    }

    /** @param showDuration whether to show the "Duration" field - only meaningful for a Sequence's
     *  own array elements (SequenceMovement advances through them on a timer); Squadron's single
     *  wrapped pattern runs continuously and never reads its own duration, so that call site passes
     *  false to avoid showing a field that would silently do nothing. */
    private void appendMovementRows(MovementPatternDef node, int indent, Runnable removeSelf, boolean showDuration) {
        rows.add(typeRow(indent, () -> node.type, MOVEMENT_TYPES,
            t -> { node.type = t; resolveMovementSentinels(node); applyChange(); },
            removeSelf == null ? null : () -> { removeSelf.run(); applyChange(); }));
        if (showDuration) {
            rows.add(numberRow(indent, "Duration", () -> node.duration, v -> node.duration = v, 0.25f, false));
        }

        switch (node.type) {
            case "Straight":
            case "ZigZag":
                rows.add(numberRow(indent, "Speed", () -> node.speed, v -> node.speed = v, 0.25f, false));
                rows.add(numberRow(indent, "Angle", () -> node.movementAngle, v -> node.movementAngle = v, 5f, false));
                break;
            case "Seeking":
                rows.add(numberRow(indent, "Speed", () -> node.speed, v -> node.speed = v, 0.25f, false));
                rows.add(numberRow(indent, "Angle", () -> node.movementAngle, v -> node.movementAngle = v, 5f, false));
                rows.add(numberRow(indent, "Stop Dist", () -> node.stopDistance, v -> node.stopDistance = v, 0.25f, false));
                break;
            case "MoveToPoint":
                rows.add(numberRow(indent, "Speed", () -> node.speed, v -> node.speed = v, 0.25f, false));
                rows.add(numberRow(indent, "Target X", () -> node.targetX, v -> node.targetX = v, 0.25f, false));
                rows.add(numberRow(indent, "Target Y", () -> node.targetY, v -> node.targetY = v, 0.25f, false));
                rows.add(numberRow(indent, "Stop Dist", () -> node.stopDistance, v -> node.stopDistance = v, 0.25f, false));
                break;
            case "Spline":
                rows.add(numberRow(indent, "Angle", () -> node.movementAngle, v -> node.movementAngle = v, 5f, false));
                break;
            case "Sequence": {
                int count = node.patterns != null ? node.patterns.size : 0;
                rows.add(headerRow(indent, "Sub-patterns (" + count + "):"));
                if (node.patterns != null) {
                    for (int i = 0; i < node.patterns.size; i++) {
                        int idx = i;
                        rows.add(headerRow(indent + 1, "==== Sub-pattern " + (i + 1) + " of " + count + " ===="));
                        appendMovementRows(node.patterns.get(i), indent + 1, () -> node.patterns.removeIndex(idx), true);
                        rows.add(headerRow(indent + 1, ""));
                    }
                }
                rows.add(actionRow(indent + 1, () -> "[+ Add sub-pattern]", () -> {
                    if (node.patterns == null) node.patterns = new Array<>();
                    MovementPatternDef fresh = new MovementPatternDef();
                    fresh.type = "Straight";
                    node.patterns.add(fresh);
                    applyChange();
                }));
                break;
            }
            case "Squadron":
                rows.add(numberRow(indent, "Offset X", () -> node.offsetX, v -> node.offsetX = v, 0.1f, false));
                rows.add(numberRow(indent, "Offset Y", () -> node.offsetY, v -> node.offsetY = v, 0.1f, false));
                if (node.pattern == null) {
                    node.pattern = new MovementPatternDef();
                    node.pattern.type = "Straight";
                }
                rows.add(headerRow(indent, "==== Wrapped pattern ===="));
                // Del on the wrapped pattern's Type row clears it back to null - the null-check just
                // above recreates a fresh default "Straight" the next time rows rebuild, so this is
                // Squadron's version of "remove sub-pattern" (it always needs exactly one, so a full
                // detach isn't meaningful - reset-to-default is the equivalent operation).
                appendMovementRows(node.pattern, indent + 1, () -> node.pattern = null, false);
                rows.add(headerRow(indent, ""));
                break;
            default:
                break;
        }
    }

    private void appendFiringRows(FiringPatternDef node, int indent, Runnable removeSelf) {
        rows.add(typeRow(indent, () -> node.type, FIRING_TYPES,
            t -> { node.type = t; resolveFiringSentinels(node); applyChange(); },
            removeSelf == null ? null : () -> { removeSelf.run(); applyChange(); }));
        if (removeSelf != null) {
            rows.add(numberRow(indent, "Duration", () -> node.duration, v -> node.duration = v, 0.25f, false));
        }

        switch (node.type) {
            case "SpawnEnemy":
                rows.add(numberRow(indent, "Fire Rate", () -> node.fireRate, v -> node.fireRate = v, 0.1f, false));
                rows.add(idPickRow(indent, "Spawn Type", () -> node.spawnType, assets.getEnemyIds(), v -> { node.spawnType = v; applyChange(); }));
                rows.add(numberRow(indent, "Offset X", () -> node.offsetX, v -> node.offsetX = v, 0.1f, false));
                rows.add(numberRow(indent, "Offset Y", () -> node.offsetY, v -> node.offsetY = v, 0.1f, false));
                break;
            case "Aimed":
                appendCommonBulletRows(node, indent);
                rows.add(numberRow(indent, "Target Off X", () -> node.targetOffsetX, v -> node.targetOffsetX = v, 0.1f, false));
                rows.add(numberRow(indent, "Target Off Y", () -> node.targetOffsetY, v -> node.targetOffsetY = v, 0.1f, false));
                break;
            case "QuarterCircle":
                appendCommonBulletRows(node, indent);
                rows.add(numberRow(indent, "Spread Deg", () -> node.spreadDegrees, v -> node.spreadDegrees = v, 5f, false));
                rows.add(numberRow(indent, "Num Bullets", () -> (float) node.numBullets, v -> node.numBullets = Math.round(v), 1f, true));
                rows.add(numberRow(indent, "Target Off X", () -> node.targetOffsetX, v -> node.targetOffsetX = v, 0.1f, false));
                rows.add(numberRow(indent, "Target Off Y", () -> node.targetOffsetY, v -> node.targetOffsetY = v, 0.1f, false));
                break;
            case "AimedAtPoint":
                appendCommonBulletRows(node, indent);
                rows.add(numberRow(indent, "Target X", () -> node.targetX, v -> node.targetX = v, 0.25f, false));
                rows.add(numberRow(indent, "Target Y", () -> node.targetY, v -> node.targetY = v, 0.25f, false));
                break;
            case "Laser":
                rows.add(numberRow(indent, "Fire Rate", () -> node.fireRate, v -> node.fireRate = v, 0.1f, false));
                rows.add(idPickRow(indent, "Bullet", () -> node.bulletId, PatternRegistry.getBulletIds(), v -> { node.bulletId = v; applyChange(); }));
                rows.add(numberRow(indent, "Thickness", () -> node.bulletSize, v -> node.bulletSize = v, 0.05f, false));
                rows.add(numberRow(indent, "Length", () -> node.length, v -> node.length = v, 0.5f, false));
                rows.add(numberRow(indent, "Angular Spd", () -> node.angularSpeed, v -> node.angularSpeed = v, 5f, false));
                rows.add(numberRow(indent, "Fire Angle", () -> node.fireAngle, v -> node.fireAngle = v, 5f, false));
                rows.add(numberRow(indent, "Offset X", () -> node.offsetX, v -> node.offsetX = v, 0.1f, false));
                rows.add(numberRow(indent, "Offset Y", () -> node.offsetY, v -> node.offsetY = v, 0.1f, false));
                break;
            case "Sweep":
                appendCommonBulletRows(node, indent);
                rows.add(numberRow(indent, "Sweep Dur", () -> node.sweepDuration, v -> node.sweepDuration = v, 0.25f, false));
                rows.add(numberRow(indent, "Start Angle", () -> node.sweepStartAngle, v -> node.sweepStartAngle = v, 5f, false));
                rows.add(numberRow(indent, "End Angle", () -> node.sweepEndAngle, v -> node.sweepEndAngle = v, 5f, false));
                break;
            case "SelfDestruct":
            case "ExplodingAimed":
            case "BurstAimed":
                appendCommonBulletRows(node, indent);
                break;
            case "SineWave":
                appendCommonBulletRows(node, indent);
                rows.add(numberRow(indent, "Amplitude", () -> node.amplitude, v -> node.amplitude = v, 0.1f, false));
                rows.add(numberRow(indent, "Frequency", () -> node.frequency, v -> node.frequency = v, 0.25f, false));
                break;
            case "Orbiting":
                appendCommonBulletRows(node, indent);
                rows.add(numberRow(indent, "Orbit Radius", () -> node.orbitRadius, v -> node.orbitRadius = v, 0.05f, false));
                rows.add(numberRow(indent, "Orbit Speed", () -> node.orbitSpeed, v -> node.orbitSpeed = v, 0.25f, false));
                break;
            case "Wall":
                appendCoreBulletRows(node, indent);
                rows.add(numberRow(indent, "Wall Margin X", () -> node.wallMarginX, v -> node.wallMarginX = v, 0.05f, false));
                rows.add(numberRow(indent, "Wall Spacing", () -> node.wallSpacing, v -> node.wallSpacing = v, 0.05f, false));
                rows.add(numberRow(indent, "Gap Lane Start", () -> (float) node.gapLaneStart, v -> node.gapLaneStart = Math.round(v), 1f, true));
                rows.add(numberRow(indent, "Gap Lane Count", () -> (float) node.gapLaneCount, v -> node.gapLaneCount = Math.round(v), 1f, true));
                break;
            case "PolkaDot":
                appendCoreBulletRows(node, indent);
                rows.add(numberRow(indent, "Wall Margin X", () -> node.wallMarginX, v -> node.wallMarginX = v, 0.05f, false));
                rows.add(numberRow(indent, "Wall Spacing", () -> node.wallSpacing, v -> node.wallSpacing = v, 0.05f, false));
                break;
            case "RadialNearMiss":
                appendCoreBulletRows(node, indent);
                rows.add(numberRow(indent, "Num Bullets", () -> (float) node.numBullets, v -> node.numBullets = Math.round(v), 1f, true));
                rows.add(numberRow(indent, "Near Miss Dist", () -> node.nearMissDistance, v -> node.nearMissDistance = v, 0.05f, false));
                rows.add(numberRow(indent, "Volley Count", () -> (float) node.volleyCount, v -> node.volleyCount = Math.round(v), 1f, true));
                break;
            case "Sequence":
            case "Combined": {
                int count = node.patterns != null ? node.patterns.size : 0;
                rows.add(headerRow(indent, "Sub-patterns (" + count + "):"));
                if (node.patterns != null) {
                    for (int i = 0; i < node.patterns.size; i++) {
                        int idx = i;
                        rows.add(headerRow(indent + 1, "==== Sub-pattern " + (i + 1) + " of " + count + " ===="));
                        appendFiringRows(node.patterns.get(i), indent + 1, () -> node.patterns.removeIndex(idx));
                        rows.add(headerRow(indent + 1, ""));
                    }
                }
                rows.add(actionRow(indent + 1, () -> "[+ Add sub-pattern]", () -> {
                    if (node.patterns == null) node.patterns = new Array<>();
                    FiringPatternDef fresh = new FiringPatternDef();
                    fresh.type = "None";
                    node.patterns.add(fresh);
                    applyChange();
                }));
                break;
            }
            default:
                break;
        }
    }

    // Shared by every bullet-firing type that offsets its emission point from the enemy's own
    // position (most of them). Wall/PolkaDot/RadialNearMiss don't - see PatternFactory's "Wall"/
    // "PolkaDot"/"RadialNearMiss" cases, none of which pass offsetX/offsetY to their constructors -
    // so those three call appendCoreBulletRows directly instead, skipping the offset rows.
    private void appendCommonBulletRows(FiringPatternDef node, int indent) {
        appendCoreBulletRows(node, indent);
        rows.add(numberRow(indent, "Offset X", () -> node.offsetX, v -> node.offsetX = v, 0.1f, false));
        rows.add(numberRow(indent, "Offset Y", () -> node.offsetY, v -> node.offsetY = v, 0.1f, false));
    }

    private void appendCoreBulletRows(FiringPatternDef node, int indent) {
        rows.add(numberRow(indent, "Fire Rate", () -> node.fireRate, v -> node.fireRate = v, 0.1f, false));
        rows.add(idPickRow(indent, "Bullet", () -> node.bulletId, PatternRegistry.getBulletIds(), v -> { node.bulletId = v; applyChange(); }));
        rows.add(numberRow(indent, "Bullet Size", () -> node.bulletSize, v -> node.bulletSize = v, 0.05f, false));
        rows.add(numberRow(indent, "Bullet Speed", () -> node.bulletSpeed, v -> node.bulletSpeed = v, 0.25f, false));
        appendSpeedPhaseRows(node, indent);
    }

    /** See FiringPatternDef.bulletAcceleration/bulletMinSpeed/bulletMaxSpeed/bulletSpeedPhases -
     *  a constant ramp (bulletAcceleration, clamped by bulletMinSpeed/bulletMaxSpeed) or, if any
     *  phases are added below, a scripted accelerate/decelerate sequence that wholly overrides the
     *  constant ramp. -1 on Min/Max Speed means "unclamped", matching the sentinel the JSON schema
     *  already uses elsewhere in this def. */
    private void appendSpeedPhaseRows(FiringPatternDef node, int indent) {
        rows.add(numberRow(indent, "Bullet Accel", () -> node.bulletAcceleration, v -> node.bulletAcceleration = v, 0.1f, false));
        rows.add(numberRow(indent, "Bullet Min Speed", () -> node.bulletMinSpeed, v -> node.bulletMinSpeed = v, 0.25f, false));
        rows.add(numberRow(indent, "Bullet Max Speed", () -> node.bulletMaxSpeed, v -> node.bulletMaxSpeed = v, 0.25f, false));

        int count = node.bulletSpeedPhases != null ? node.bulletSpeedPhases.size : 0;
        rows.add(headerRow(indent, "Speed Phases (" + count + "):"));
        rows.add(toggleRow(indent, "Phases Loop", () -> node.bulletSpeedPhasesLoop, v -> node.bulletSpeedPhasesLoop = v));
        if (node.bulletSpeedPhases != null) {
            for (int i = 0; i < node.bulletSpeedPhases.size; i++) {
                int idx = i;
                BulletSpeedPhase phase = node.bulletSpeedPhases.get(i);
                rows.add(headerRow(indent + 1, "==== Phase " + (i + 1) + " of " + count + " ===="));
                rows.add(numberRow(indent + 1, "Acceleration", () -> phase.acceleration, v -> phase.acceleration = v, 0.25f, false));
                rows.add(numberRow(indent + 1, "Duration", () -> phase.duration, v -> phase.duration = v, 0.25f, false));
                rows.add(actionRow(indent + 1, () -> "[Remove Phase " + (idx + 1) + "]", () -> {
                    node.bulletSpeedPhases.removeIndex(idx);
                    applyChange();
                }));
                rows.add(headerRow(indent + 1, ""));
            }
        }
        rows.add(actionRow(indent + 1, () -> "[+ Add speed phase]", () -> {
            if (node.bulletSpeedPhases == null) node.bulletSpeedPhases = new Array<>();
            node.bulletSpeedPhases.add(new BulletSpeedPhase());
            applyChange();
        }));
    }

    // ---- Row factories ---------------------------------------------------------------------------

    private Row headerRow(int indent, String text) {
        return new Row(indent, () -> text, null, null, null, null);
    }

    private Row actionRow(int indent, Supplier<String> label, Runnable onConfirm) {
        return new Row(indent, label, null, null, onConfirm, null);
    }

    private Row numberRow(int indent, String name, FloatGetter getter, FloatSetter setter, float step, boolean isInt) {
        Supplier<String> label = () -> name + ": " + (isInt ? String.valueOf(Math.round(getter.get())) : formatFloat(getter.get()));
        Runnable dec = () -> { setter.set(isInt ? getter.get() - step : snap(getter.get() - step)); applyChange(); };
        Runnable inc = () -> { setter.set(isInt ? getter.get() + step : snap(getter.get() + step)); applyChange(); };
        Runnable typeIn = () -> promptNumber(name, getter.get(), isInt, setter);
        return new Row(indent, label, dec, inc, typeIn, null);
    }

    // Repeated stepper +=/-= on a float accumulates binary floating-point error over many presses
    // (e.g. nudging by 0.1 sixteen times lands on 1.5999999 instead of 1.6 - this is exactly where
    // the "1.1500001"/"0.70000005"/"1.4901161E-8"-style noise already sitting in the pattern JSON
    // came from). Snapping to the nearest 0.01 on every edit - stepper and typed entry alike - keeps
    // that from accumulating in the first place; 2 decimals is already finer than any existing field
    // needs. Row labels also show full (unrounded) precision now, so any noise a value already has
    // stays visible instead of being silently hidden by a "%.2f" display.
    private static float snap(float value) {
        return Math.round(value * 100f) / 100f;
    }

    private static String formatFloat(float value) {
        if (value == Math.round(value)) return String.valueOf(Math.round(value));
        return String.valueOf(value);
    }

    private Row toggleRow(int indent, String name, BoolGetter getter, BoolSetter setter) {
        Supplier<String> label = () -> name + ": " + (getter.get() ? "true" : "false");
        Runnable flip = () -> { setter.set(!getter.get()); applyChange(); };
        return new Row(indent, label, flip, flip, flip, null);
    }

    private Row idPickRow(int indent, String name, Supplier<String> current, Array<String> ids, Consumer<String> onSelect) {
        Supplier<String> label = () -> name + ": " + (current.get() != null ? current.get() : "-");
        Runnable prev = () -> cycleId(ids, current.get(), -1, onSelect);
        Runnable next = () -> cycleId(ids, current.get(), 1, onSelect);
        return new Row(indent, label, prev, next, null, null);
    }

    private Row texturePickRow(int indent, String name, Supplier<String> current, Consumer<String> setter, boolean includeNone) {
        Array<String> options = new Array<>();
        if (includeNone) options.add(NONE_LABEL);
        options.addAll(listTextureFiles());
        Supplier<String> label = () -> name + ": " + (current.get() != null ? current.get() : NONE_LABEL);
        Consumer<String> onSelect = v -> { setter.accept(NONE_LABEL.equals(v) ? null : v); applyChange(); };
        Runnable prev = () -> cycleId(options, current.get() != null ? current.get() : NONE_LABEL, -1, onSelect);
        Runnable next = () -> cycleId(options, current.get() != null ? current.get() : NONE_LABEL, 1, onSelect);
        return new Row(indent, label, prev, next, null, null);
    }

    private Row typeRow(int indent, Supplier<String> currentType, String[] allTypes, Consumer<String> onTypeChange, Runnable onRemove) {
        Supplier<String> label = () -> "Type: " + currentType.get();
        Runnable prev = () -> cycleType(allTypes, currentType.get(), -1, onTypeChange);
        Runnable next = () -> cycleType(allTypes, currentType.get(), 1, onTypeChange);
        return new Row(indent, label, prev, next, null, onRemove);
    }

    private static void cycleId(Array<String> ids, String currentId, int dir, Consumer<String> onSelect) {
        if (ids.size == 0) return;
        int idx = ids.indexOf(currentId, false);
        if (idx < 0) idx = 0;
        int next = (idx + dir + ids.size) % ids.size;
        onSelect.accept(ids.get(next));
    }

    private static void cycleType(String[] types, String current, int dir, Consumer<String> onChange) {
        int idx = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(current)) { idx = i; break; }
        }
        int next = (idx + dir + types.length) % types.length;
        onChange.accept(types[next]);
    }
}
