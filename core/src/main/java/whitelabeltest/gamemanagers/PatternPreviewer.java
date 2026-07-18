package whitelabeltest.gamemanagers;

import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.BulletDef;
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

/** Debug-only tool: pick a movement/firing pattern id from the JSON-loaded registry, tweak its
 *  numeric fields, and watch a dedicated preview enemy react live. Every edit installs the
 *  working copy into PatternRegistry (see putMovement/putFiring) and rebuilds the preview
 *  enemy's running pattern instances via GenericEnemy.refreshPreviewPatterns, so the change is
 *  visible immediately without touching the JSON-loaded set or restarting the game.
 *
 *  Scoped to editing an existing pattern's numeric fields, not its type or nested
 *  sub-patterns (Sequence/Squadron/Combined) - those still require editing the JSON. */
public class PatternPreviewer {
    private static final String PREVIEW_ENEMY_TYPE = "BasicEnemy";

    public static final int ROW_MOVEMENT_ID = 0;
    public static final int ROW_FIRING_ID = 1;
    public static final int FIELD_ROWS_START = 2;

    private interface FloatGetter { float get(); }
    private interface FloatSetter { void set(float value); }

    private static final class Field {
        final String name;
        final FloatGetter getter;
        final FloatSetter setter;
        final float step;
        final boolean isInt;

        Field(String name, FloatGetter getter, FloatSetter setter, float step, boolean isInt) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.step = step;
            this.isInt = isInt;
        }

        String label() {
            return isInt ? name + ": " + Math.round(getter.get()) : name + ": " + String.format("%.2f", getter.get());
        }
    }

    private boolean active;
    private int movementIndex;
    private int firingIndex;
    private int selectedRow;
    private float spawnX, spawnY;
    private MovementPatternDef movementDef;
    private FiringPatternDef firingDef;
    private final Array<Field> movementFields = new Array<>();
    private final Array<Field> firingFields = new Array<>();
    private GenericEnemy previewEnemy;

    public boolean isActive() { return active; }

    public void open(EntityManager entities, float worldWidth, float worldHeight) {
        active = true;
        movementIndex = 0;
        firingIndex = 0;
        selectedRow = ROW_MOVEMENT_ID;
        spawnX = worldWidth / 2f;
        spawnY = worldHeight / 2f;

        previewEnemy = EnemySpawnRegistry.spawn(PREVIEW_ENEMY_TYPE, spawnX, spawnY);

        loadMovementDef();
        loadFiringDef();
        applyToPreview();
    }

    public void close(EntityManager entities) {
        if (!active) return;
        active = false;
        if (previewEnemy != null) {
            entities.getEnemies().removeValue(previewEnemy, true);
            ObjectPools.freeEnemy(previewEnemy);
            previewEnemy = null;
        }
    }

    public void handleInput(InputManager input) {
        int totalRows = FIELD_ROWS_START + movementFields.size + firingFields.size;
        if (input.isDebugMenuUpJustPressed()) selectedRow = (selectedRow - 1 + totalRows) % totalRows;
        if (input.isDebugMenuDownJustPressed()) selectedRow = (selectedRow + 1) % totalRows;

        if (selectedRow == ROW_MOVEMENT_ID) {
            Array<String> ids = PatternRegistry.getMovementIds();
            if (ids.size == 0) return;
            if (input.isDebugMenuLeftJustPressed()) { movementIndex = (movementIndex - 1 + ids.size) % ids.size; loadMovementDef(); applyToPreview(); }
            if (input.isDebugMenuRightJustPressed()) { movementIndex = (movementIndex + 1) % ids.size; loadMovementDef(); applyToPreview(); }
        } else if (selectedRow == ROW_FIRING_ID) {
            Array<String> ids = PatternRegistry.getFiringIds();
            if (ids.size == 0) return;
            if (input.isDebugMenuLeftJustPressed()) { firingIndex = (firingIndex - 1 + ids.size) % ids.size; loadFiringDef(); applyToPreview(); }
            if (input.isDebugMenuRightJustPressed()) { firingIndex = (firingIndex + 1) % ids.size; loadFiringDef(); applyToPreview(); }
        } else {
            int fieldRow = selectedRow - FIELD_ROWS_START;
            Field field = fieldRow < movementFields.size ? movementFields.get(fieldRow) : firingFields.get(fieldRow - movementFields.size);
            if (input.isDebugMenuLeftJustPressed()) { field.setter.set(field.getter.get() - field.step); applyToPreview(); }
            if (input.isDebugMenuRightJustPressed()) { field.setter.set(field.getter.get() + field.step); applyToPreview(); }
        }
    }

    /** Steps the preview enemy (and any bullets its firing pattern spawns) each frame, since the
     *  rest of the game is frozen while the debug menu is open. Loops the enemy back to the
     *  spawn point once its pattern carries it off-screen, so the demo keeps replaying. */
    public void tick(float delta, EntityManager entities) {
        if (!active || previewEnemy == null) return;

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

        if (previewEnemy.isOffScreen()) {
            applyToPreview();
        }
    }

    private void applyToPreview() {
        PatternRegistry.putMovement(movementDef.id, movementDef);
        PatternRegistry.putFiring(firingDef.id, firingDef);
        if (previewEnemy != null) previewEnemy.refreshPreviewPatterns(movementDef, firingDef, spawnX, spawnY);
    }

    private void loadMovementDef() {
        Array<String> ids = PatternRegistry.getMovementIds();
        String id = ids.get(movementIndex);
        movementDef = cloneMovement(PatternRegistry.getMovement(id));
        resolveMovementSentinels(movementDef);
        buildMovementFields();
    }

    private void loadFiringDef() {
        Array<String> ids = PatternRegistry.getFiringIds();
        String id = ids.get(firingIndex);
        firingDef = cloneFiring(PatternRegistry.getFiring(id));
        resolveFiringSentinels(firingDef);
        buildFiringFields();
    }

    private static MovementPatternDef cloneMovement(MovementPatternDef src) {
        MovementPatternDef d = new MovementPatternDef();
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
        return d;
    }

    private static FiringPatternDef cloneFiring(FiringPatternDef src) {
        FiringPatternDef d = new FiringPatternDef();
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
        return d;
    }

    /** Materializes "use the default" sentinels (-1, NaN) into concrete numbers so the field
     *  steppers below have something real to adjust from. */
    private void resolveMovementSentinels(MovementPatternDef d) {
        if (d.speed <= 0) d.speed = 1.5f;
        if (Float.isNaN(d.movementAngle)) d.movementAngle = MovementPattern.DEFAULT_ANGLE_DEG;
        if (d.stopDistance <= 0) {
            d.stopDistance = "MoveToPoint".equals(d.type) ? MoveToPointMovement.DEFAULT_STOP_DISTANCE : SeekingMovement.DEFAULT_STOP_DISTANCE;
        }
        if (Float.isNaN(d.targetX)) d.targetX = spawnX;
        if (Float.isNaN(d.targetY)) d.targetY = 0f;
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
    }

    private void buildMovementFields() {
        movementFields.clear();
        MovementPatternDef d = movementDef;
        switch (d.type) {
            case "Straight":
            case "ZigZag":
                movementFields.add(field("Speed", () -> d.speed, v -> d.speed = v, 0.25f, false));
                movementFields.add(field("Angle", () -> d.movementAngle, v -> d.movementAngle = v, 5f, false));
                break;
            case "Seeking":
                movementFields.add(field("Speed", () -> d.speed, v -> d.speed = v, 0.25f, false));
                movementFields.add(field("Angle", () -> d.movementAngle, v -> d.movementAngle = v, 5f, false));
                movementFields.add(field("Stop Dist", () -> d.stopDistance, v -> d.stopDistance = v, 0.25f, false));
                break;
            case "MoveToPoint":
                movementFields.add(field("Speed", () -> d.speed, v -> d.speed = v, 0.25f, false));
                movementFields.add(field("Target X", () -> d.targetX, v -> d.targetX = v, 0.25f, false));
                movementFields.add(field("Target Y", () -> d.targetY, v -> d.targetY = v, 0.25f, false));
                movementFields.add(field("Stop Dist", () -> d.stopDistance, v -> d.stopDistance = v, 0.25f, false));
                break;
            case "Spline":
                movementFields.add(field("Angle", () -> d.movementAngle, v -> d.movementAngle = v, 5f, false));
                break;
            default:
                break;
        }
    }

    private void buildFiringFields() {
        firingFields.clear();
        FiringPatternDef d = firingDef;
        switch (d.type) {
            case "SpawnEnemy":
                firingFields.add(field("Fire Rate", () -> d.fireRate, v -> d.fireRate = v, 0.1f, false));
                break;
            case "Aimed":
                addCommonBulletFields(d);
                firingFields.add(field("Target Off X", () -> d.targetOffsetX, v -> d.targetOffsetX = v, 0.1f, false));
                firingFields.add(field("Target Off Y", () -> d.targetOffsetY, v -> d.targetOffsetY = v, 0.1f, false));
                break;
            case "QuarterCircle":
                addCommonBulletFields(d);
                firingFields.add(field("Spread Deg", () -> d.spreadDegrees, v -> d.spreadDegrees = v, 5f, false));
                firingFields.add(field("Num Bullets", () -> (float) d.numBullets, v -> d.numBullets = Math.round(v), 1f, true));
                firingFields.add(field("Target Off X", () -> d.targetOffsetX, v -> d.targetOffsetX = v, 0.1f, false));
                firingFields.add(field("Target Off Y", () -> d.targetOffsetY, v -> d.targetOffsetY = v, 0.1f, false));
                break;
            case "AimedAtPoint":
                addCommonBulletFields(d);
                firingFields.add(field("Target X", () -> d.targetX, v -> d.targetX = v, 0.25f, false));
                firingFields.add(field("Target Y", () -> d.targetY, v -> d.targetY = v, 0.25f, false));
                break;
            case "Laser":
                firingFields.add(field("Fire Rate", () -> d.fireRate, v -> d.fireRate = v, 0.1f, false));
                firingFields.add(field("Thickness", () -> d.bulletSize, v -> d.bulletSize = v, 0.05f, false));
                firingFields.add(field("Length", () -> d.length, v -> d.length = v, 0.5f, false));
                firingFields.add(field("Angular Spd", () -> d.angularSpeed, v -> d.angularSpeed = v, 5f, false));
                firingFields.add(field("Fire Angle", () -> d.fireAngle, v -> d.fireAngle = v, 5f, false));
                firingFields.add(field("Duration", () -> d.duration, v -> d.duration = v, 0.25f, false));
                break;
            case "Sweep":
                addCommonBulletFields(d);
                firingFields.add(field("Sweep Dur", () -> d.sweepDuration, v -> d.sweepDuration = v, 0.25f, false));
                firingFields.add(field("Start Angle", () -> d.sweepStartAngle, v -> d.sweepStartAngle = v, 5f, false));
                firingFields.add(field("End Angle", () -> d.sweepEndAngle, v -> d.sweepEndAngle = v, 5f, false));
                break;
            case "SelfDestruct":
            case "ExplodingAimed":
            case "BurstAimed":
            case "SineWave":
            case "Orbiting":
                addCommonBulletFields(d);
                break;
            default:
                break;
        }
    }

    private void addCommonBulletFields(FiringPatternDef d) {
        firingFields.add(field("Fire Rate", () -> d.fireRate, v -> d.fireRate = v, 0.1f, false));
        firingFields.add(field("Bullet Size", () -> d.bulletSize, v -> d.bulletSize = v, 0.05f, false));
        firingFields.add(field("Bullet Speed", () -> d.bulletSpeed, v -> d.bulletSpeed = v, 0.25f, false));
        firingFields.add(field("Offset X", () -> d.offsetX, v -> d.offsetX = v, 0.1f, false));
        firingFields.add(field("Offset Y", () -> d.offsetY, v -> d.offsetY = v, 0.1f, false));
    }

    private static Field field(String name, FloatGetter getter, FloatSetter setter, float step, boolean isInt) {
        return new Field(name, getter, setter, step, isInt);
    }

    public String getMovementId() { return movementDef != null ? movementDef.id : "-"; }
    public String getFiringId() { return firingDef != null ? firingDef.id : "-"; }
    public int getSelectedRow() { return selectedRow; }

    public Array<String> getMovementFieldLabels() {
        Array<String> labels = new Array<>();
        for (Field f : movementFields) labels.add(f.label());
        return labels;
    }

    public Array<String> getFiringFieldLabels() {
        Array<String> labels = new Array<>();
        for (Field f : firingFields) labels.add(f.label());
        return labels;
    }
}
