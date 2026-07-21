package whitelabeltest.gamemanagers;

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
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.FiringPatternDef;
import whitelabeltest.enemy.GenericEnemy;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.LaserFiring;
import whitelabeltest.enemy.firingpatterns.SweepFiring;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.MoveToPointMovement;
import whitelabeltest.enemy.movementpatterns.SeekingMovement;

import java.util.function.Consumer;
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
    private static final String[] FIRING_TYPES = {"None", "SelfDestruct", "ExplodingAimed", "BurstAimed", "Sweep", "SineWave", "Orbiting", "SpawnEnemy", "Aimed", "QuarterCircle", "AimedAtPoint", "Laser", "Sequence", "Combined"};
    private static final String NONE_LABEL = "(none)";

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

        previewEnemy.update(delta, entities.getEnemyBullets(), entities.getPlayer().getHitbox());

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

    private void loadMovementDef(String id) {
        workingMovement = cloneMovement(PatternRegistry.getMovement(id), id);
        resolveMovementSentinels(workingMovement);
    }

    private void loadFiringDef(String id) {
        workingFiring = cloneFiring(PatternRegistry.getFiring(id), id);
        resolveFiringSentinels(workingFiring);
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

    private void promptNewId(String title, Consumer<String> onEntered) {
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
                if (textEntryBuffer.length() < 40 && (Character.isLetterOrDigit(character) || character == '_' || character == '-')) {
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
        assets.putEnemyDefinition(workingEnemy);
        rebuildRows();
        respawnPreview();
    }

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

    /** Writes every registered movement pattern, firing pattern and enemy definition (including
     *  whatever's been live-edited this session) back to the real assets/ JSON files. Only
     *  resolves to the true source files when launched via `gradlew run`/`:lwjgl3:run`, which
     *  pins the working directory to assets/ (see lwjgl3/build.gradle) - the same mechanism
     *  DebugSaveStateManager already relies on for debug_savestates.json. */
    private void saveAll() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        Gdx.files.local("movement_patterns.json").writeString(
            json.prettyPrint(json.toJson(PatternRegistry.getAllMovementDefsSorted(), Array.class, MovementPatternDef.class)), false);
        Gdx.files.local("firing_patterns.json").writeString(
            json.prettyPrint(json.toJson(PatternRegistry.getAllFiringDefsSorted(), Array.class, FiringPatternDef.class)), false);
        Gdx.files.local("enemies.json").writeString(
            json.prettyPrint(json.toJson(assets.getAllEnemyDefinitionsSorted(), Array.class, EnemyDefinition.class)), false);

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
            d.bulletDamage = src.bulletDamage;
            d.bulletTexture = src.bulletTexture;
            d.bulletFrameCount = src.bulletFrameCount;
            d.bulletColumns = src.bulletColumns;
            d.bulletRows = src.bulletRows;
            d.bulletFrameDuration = src.bulletFrameDuration;
            d.spreadDegrees = src.spreadDegrees;
            d.numBullets = src.numBullets;
            d.offsetX = src.offsetX;
            d.offsetY = src.offsetY;
            d.length = src.length;
            d.angularSpeed = src.angularSpeed;
            d.fireAngle = src.fireAngle;
            d.targetX = src.targetX;
            d.targetY = src.targetY;
            d.targetOffsetX = src.targetOffsetX;
            d.targetOffsetY = src.targetOffsetY;
            d.sweepDuration = src.sweepDuration;
            d.sweepStartAngle = src.sweepStartAngle;
            d.sweepEndAngle = src.sweepEndAngle;
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

    private Array<String> listTextureFiles() {
        if (textureFilesCache == null) {
            textureFilesCache = new Array<>();
            FileHandle dir = Gdx.files.local(".");
            if (dir.exists() && dir.isDirectory()) {
                for (FileHandle f : dir.list(".png")) textureFilesCache.add(f.name());
            }
            textureFilesCache.sort();
        }
        return textureFilesCache;
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

        rows.add(headerRow(0, "-- Enemy Definition --"));
        appendEnemyFieldRows();
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Movement Pattern --"));
        rows.add(idPickRow(0, "Movement Id", () -> workingMovement.id, PatternRegistry.getMovementIds(), this::selectMovementId));
        rows.add(actionRow(1, () -> "[+ New Movement Pattern]", () -> promptNewId("New movement pattern id", this::createNewMovement)));
        appendMovementRows(workingMovement, 1, null);
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "-- Firing Pattern --"));
        rows.add(idPickRow(0, "Firing Id", () -> workingFiring.id, PatternRegistry.getFiringIds(), this::selectFiringId));
        rows.add(actionRow(1, () -> "[+ New Firing Pattern]", () -> promptNewId("New firing pattern id", this::createNewFiring)));
        appendFiringRows(workingFiring, 1, null);

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

    private void appendMovementRows(MovementPatternDef node, int indent, Runnable removeSelf) {
        rows.add(typeRow(indent, () -> node.type, MOVEMENT_TYPES,
            t -> { node.type = t; resolveMovementSentinels(node); applyChange(); },
            removeSelf == null ? null : () -> { removeSelf.run(); applyChange(); }));
        if (removeSelf != null) {
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
                        appendMovementRows(node.patterns.get(i), indent + 1, () -> node.patterns.removeIndex(idx));
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
                rows.add(headerRow(indent, "Pattern:"));
                appendMovementRows(node.pattern, indent + 1, null);
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
            case "SineWave":
            case "Orbiting":
                appendCommonBulletRows(node, indent);
                break;
            case "Sequence":
            case "Combined": {
                int count = node.patterns != null ? node.patterns.size : 0;
                rows.add(headerRow(indent, "Sub-patterns (" + count + "):"));
                if (node.patterns != null) {
                    for (int i = 0; i < node.patterns.size; i++) {
                        int idx = i;
                        appendFiringRows(node.patterns.get(i), indent + 1, () -> node.patterns.removeIndex(idx));
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

    private void appendCommonBulletRows(FiringPatternDef node, int indent) {
        rows.add(numberRow(indent, "Fire Rate", () -> node.fireRate, v -> node.fireRate = v, 0.1f, false));
        rows.add(idPickRow(indent, "Bullet", () -> node.bulletId, PatternRegistry.getBulletIds(), v -> { node.bulletId = v; applyChange(); }));
        rows.add(numberRow(indent, "Bullet Size", () -> node.bulletSize, v -> node.bulletSize = v, 0.05f, false));
        rows.add(numberRow(indent, "Bullet Speed", () -> node.bulletSpeed, v -> node.bulletSpeed = v, 0.25f, false));
        rows.add(numberRow(indent, "Offset X", () -> node.offsetX, v -> node.offsetX = v, 0.1f, false));
        rows.add(numberRow(indent, "Offset Y", () -> node.offsetY, v -> node.offsetY = v, 0.1f, false));
    }

    // ---- Row factories ---------------------------------------------------------------------------

    private Row headerRow(int indent, String text) {
        return new Row(indent, () -> text, null, null, null, null);
    }

    private Row actionRow(int indent, Supplier<String> label, Runnable onConfirm) {
        return new Row(indent, label, null, null, onConfirm, null);
    }

    private Row numberRow(int indent, String name, FloatGetter getter, FloatSetter setter, float step, boolean isInt) {
        Supplier<String> label = () -> name + ": " + (isInt ? String.valueOf(Math.round(getter.get())) : String.format("%.2f", getter.get()));
        Runnable dec = () -> { setter.set(getter.get() - step); applyChange(); };
        Runnable inc = () -> { setter.set(getter.get() + step); applyChange(); };
        return new Row(indent, label, dec, inc, null, null);
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
