package whitelabeltest.gamemanagers.trigger;

import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.ScoreManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.gamemanagers.spawning.EnemySpawnOps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.player.Player;

import java.util.Comparator;
import java.util.Objects;

/** Camera-position-driven counterpart to SpawnScheduler - see that class's doc for the wall-clock
 *  cues (text/gates/practice windows/etc.) this deliberately leaves alone. A stage that sets
 *  StageDefinition.triggerFile gets one of these alongside its SpawnScheduler; the two run in
 *  parallel, each firing whatever content was authored onto it.
 *
 * Owns a LevelCamera and fires each Trigger exactly once, the moment the camera's swept collision
 * box (see LevelCamera.getCollisionBox()) reaches that trigger's distance - i.e. the camera
 * "collides" with it, same framing as every other hitbox check in this game, just against a
 * 1-dimensional position instead of a 2D one. */
public class TriggerManager {
    /** Public so a future debug/editor tool can load/edit/save the whole file directly, same
     *  reasoning as SpawnScheduler.ScheduleFile. */
    public static class TriggerFile {
        public float cameraSpeed = 1f;
        public Array<Trigger> triggers;

        public TriggerFile() {}
    }

    private final float worldWidth;
    private final float worldHeight;
    private final AssetManager assets;
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions;
    private final LevelCamera camera;
    private Array<Trigger> triggers;

    public TriggerManager(float worldWidth, float worldHeight, AssetManager assets, String triggerFilePath,
                           ObjectMap<String, EnemyDefinition> enemyDefinitions) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = assets;
        this.enemyDefinitions = enemyDefinitions;

        Json json = new Json();
        float initialSpeed = 1f;
        try {
            TriggerFile file = json.fromJson(TriggerFile.class, Gdx.files.internal(triggerFilePath));
            this.triggers = (file != null && file.triggers != null) ? file.triggers : new Array<>();
            if (file != null) initialSpeed = file.cameraSpeed;
        } catch (SerializationException e) {
            Gdx.app.error("TriggerManager", "Error parsing " + triggerFilePath, e);
            this.triggers = new Array<>();
        }
        triggers.sort(new Comparator<Trigger>() {
            @Override
            public int compare(Trigger t1, Trigger t2) {
                return Float.compare(t1.distance, t2.distance);
            }
        });
        camera = new LevelCamera(worldWidth, initialSpeed);
    }

    public LevelCamera getCamera() { return camera; }

    public void update(float delta, EntityManager entityManager, AudioManager audio, InputManager input, ScoreManager scoreManager) {
        camera.update(delta);
        Rectangle box = camera.getCollisionBox();
        float minY = box.y;
        float maxY = box.y + box.height;
        Player player = entityManager.getPlayer();

        for (Trigger trigger : triggers) {
            if (trigger.fired) continue;
            if (!trigger.armed) {
                if (trigger.distance < minY || trigger.distance >= maxY) continue;
                trigger.armed = true;
                armConditions(trigger, scoreManager, player);
            }
            if (!conditionsSatisfied(trigger, input, scoreManager, player)) continue;

            fire(trigger, entityManager, audio);
            trigger.fired = true;
        }
    }

    /** Snapshots each of trigger's conditions' baseline the instant it arms - count-based
     *  conditions (enemiesDestroyed/enemyTypeDestroyed/gemsCollected/grazed) compare against how
     *  much has happened SINCE arming, not the run's running total, same reasoning as
     *  SpawnScheduler's own *AtGateStart fields (see Condition's doc for why this can't just be a
     *  handful of instance fields here the way it is there). */
    private void armConditions(Trigger trigger, ScoreManager scoreManager, Player player) {
        if (trigger.conditions == null) return;
        for (Condition condition : trigger.conditions) {
            if (condition.type == null) continue;
            condition.baseline = switch (condition.type) {
                case "enemiesDestroyed" -> scoreManager.getEnemiesDestroyed();
                case "enemyTypeDestroyed" -> scoreManager.getEnemiesDestroyedByType(condition.enemyType);
                case "gemsCollected" -> scoreManager.getGemsCollected();
                case "grazed" -> player.getGrazePoints();
                default -> 0f;
            };
        }
    }

    /** True once trigger's conditions (see conditionMode) are met - vacuously true for a trigger
     *  with no conditions at all, so an armed trigger with nothing to wait on fires the same frame
     *  it arms, exactly like before conditions existed. */
    private boolean conditionsSatisfied(Trigger trigger, InputManager input, ScoreManager scoreManager, Player player) {
        if (trigger.conditions == null || trigger.conditions.size == 0) return true;
        boolean any = "ANY".equalsIgnoreCase(trigger.conditionMode);
        for (Condition condition : trigger.conditions) {
            boolean satisfied = isConditionSatisfied(condition, input, scoreManager, player);
            if (any) {
                if (satisfied) return true;
            } else if (!satisfied) {
                return false;
            }
        }
        return !any; // ALL: nothing failed -> true. ANY: nothing matched -> false.
    }

    private boolean isConditionSatisfied(Condition condition, InputManager input, ScoreManager scoreManager, Player player) {
        if (condition.type == null) return true;
        return switch (condition.type) {
            case "shoot" -> input.isShootJustPressed();
            case "bomb" -> input.isBombJustPressed();
            case "weaponSwitch" -> Objects.equals(condition.weaponId, player.getCurrentWeaponId());
            case "moved" -> input.isMoveJustStarted();
            case "movedLeft" -> input.isMoveLeftJustStarted();
            case "movedRight" -> input.isMoveRightJustStarted();
            case "hyperAttack" -> input.isHyperAttackJustPressed();
            case "hyperAttackReleased" -> input.isHyperAttackJustReleased();
            case "enemiesDestroyed" -> scoreManager.getEnemiesDestroyed() - condition.baseline >= condition.count;
            case "enemyTypeDestroyed" -> scoreManager.getEnemiesDestroyedByType(condition.enemyType) - condition.baseline >= condition.count;
            case "gemsCollected" -> scoreManager.getGemsCollected() - condition.baseline >= condition.count;
            case "grazed" -> player.getGrazePoints() - condition.baseline >= condition.count;
            default -> true; // unrecognized condition string - don't soft-lock content over a typo
        };
    }

    private void fire(Trigger trigger, EntityManager entityManager, AudioManager audio) {
        if (trigger.sound != null) {
            audio.playCueSound(trigger.sound);
        } else if (trigger.spriteTexture != null) {
            EnemySpawnOps.spawnSpriteCue(entityManager, assets, trigger.spriteTexture, trigger.x, trigger.y, trigger.size,
                trigger.columns, trigger.rows, trigger.frameCount, trigger.frameDuration);
        } else if (trigger.setSpeed != null) {
            camera.setSpeed(trigger.setSpeed);
        } else if (trigger.silence) {
            EnemySpawnOps.silenceMatching(entityManager, trigger.type);
        } else if (trigger.despawn) {
            EnemySpawnOps.despawnMatching(entityManager, trigger.type);
        } else if (trigger.waypointGem) {
            EnemySpawnOps.spawnWaypointGem(entityManager, assets, worldWidth, worldHeight, trigger.x, trigger.y);
        } else if (trigger.swapWeaponId != null) {
            entityManager.getPlayer().setSlotWeapon(trigger.weaponSlot, trigger.swapWeaponId);
        } else {
            EnemySpawnOps.spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight,
                trigger.type, trigger.x, trigger.y, trigger.offsetX, trigger.offsetY, trigger.movementPattern, trigger.firingPattern,
                trigger.inverseMovement, trigger.powerup);
        }
    }

    /** True for a Trigger that actually spawns an enemy (the default action, same as an ordinary
     *  SpawnScheduler.SpawnEvent) rather than one of the other action kinds - see
     *  getEnemySpawnCount(). */
    private static boolean isEnemySpawn(Trigger trigger) {
        return trigger.sound == null && trigger.spriteTexture == null && trigger.setSpeed == null
            && !trigger.silence && !trigger.despawn && !trigger.waypointGem && trigger.swapWeaponId == null;
    }

    /** Number of triggers that actually spawn an enemy - added into GameController's
     *  totalEnemiesAcrossRun alongside SpawnScheduler.getSchedule().size(). */
    public int getEnemySpawnCount() {
        int count = 0;
        for (Trigger trigger : triggers) if (isEnemySpawn(trigger)) count++;
        return count;
    }

    /** Mirrors SpawnScheduler.getBossSpawnTime() - the distance of the first enemy-spawning trigger
     *  whose EnemyDefinition sets isBoss, or -1 if this stage's triggers spawn no boss (either it
     *  has none, or - as today - its boss still spawns via the old SpawnScheduler). */
    public float getBossSpawnDistance() {
        for (Trigger trigger : triggers) {
            if (!isEnemySpawn(trigger)) continue;
            EnemyDefinition def = enemyDefinitions.get(trigger.type);
            if (def != null && def.isBoss) return trigger.distance;
        }
        return -1f;
    }

    public void reset() {
        camera.reset();
        for (Trigger trigger : triggers) {
            trigger.fired = false;
            trigger.armed = false;
        }
    }

    /** Debug/practice-rewind parity with SpawnScheduler.seekTo() - same "skip rather than replay"
     *  compromise that method's own doc describes for a gate it jumps past: a trigger whose distance
     *  falls behind targetDistance is marked armed-and-fired without its conditions (if any) ever
     *  actually being checked, rather than replayed. One ahead of it resets to fully unarmed so it
     *  behaves normally once the camera reaches it again. */
    public void seekTo(float targetDistance) {
        camera.seekTo(targetDistance);
        for (Trigger trigger : triggers) {
            boolean past = trigger.distance <= targetDistance;
            trigger.armed = past;
            trigger.fired = past;
        }
    }
}
