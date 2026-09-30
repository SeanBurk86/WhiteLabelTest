package whitelabeltest.gamemanagers.trigger;

import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.ScoreManager;
import whitelabeltest.gamemanagers.TextCue;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.gamemanagers.spawning.EnemySpawnOps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.HealthPhase;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.player.Player;

import java.util.Comparator;
import java.util.Objects;

/** Runs a stage's trigger file: advances a LevelCamera and arms/fires each Trigger as the camera's
 *  swept distance reaches it. Also answers the stage's distance windows (practice checkpoints,
 *  invincibility, weapons/Hyper-Attack/bomb disabled). The distance-based successor to
 *  SpawnScheduler; see the README's "Trigger system" section. */
public class TriggerManager {
    /** The JSON shape of a *_triggers.json file (also loaded/saved directly by the editor). */
    public static class TriggerFile {
        public float cameraSpeed = 1f;
        public Array<Trigger> triggers;

        // [start, end) distance windows; see the matching isXxx() query methods.
        public Array<DistanceWindow> practiceCheckpoints;
        public Array<DistanceWindow> invincibilityWindows;
        public Array<DistanceWindow> weaponsDisabledWindows;
        public Array<DistanceWindow> hyperAttackDisabledWindows;
        public Array<DistanceWindow> bombDisabledWindows;

        public TriggerFile() {}
    }

    public static class DistanceWindow {
        public float start;
        public float end;

        public DistanceWindow() {}
    }

    private final float worldWidth;
    private final float worldHeight;
    private final AssetManager assets;
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions;
    private final ScrollingBackground background;
    private final LevelCamera camera;
    // The authored cameraSpeed: the "100%" reference getSpeedScale() divides by.
    private final float baseSpeed;
    private Array<Trigger> triggers;
    // Trigger.id -> trigger, for "spawnDestroyed" lookups.
    private final ObjectMap<String, Trigger> triggersById = new ObjectMap<>();
    // Trigger.id -> total enemies that spawn produces (1, or the wave size), recorded when it fires.
    // A fired spawn trigger with no entry was skipped by seekTo(), which "spawnDestroyed" treats as done.
    private final ObjectMap<String, Integer> spawnGroupExpected = new ObjectMap<>();
    // Text cues shown by triggers; drawn by UIManager.
    private final Array<TextCue> liveTextCues = new Array<>();
    // The gate currently freezing the camera, or null.
    private Trigger activeGate;
    // Never-frozen clock for text reveal timing and wave spawn staggering. Keeps running while a gate
    // freezes the camera.
    private float realTime = 0f;
    private Array<DistanceWindow> practiceCheckpoints = new Array<>();
    // The checkpoint being retried after a failed attempt, or null on a first attempt.
    private DistanceWindow retryCheckpoint;
    private Array<DistanceWindow> invincibilityWindows = new Array<>();
    private Array<DistanceWindow> weaponsDisabledWindows = new Array<>();
    private Array<DistanceWindow> hyperAttackDisabledWindows = new Array<>();
    private Array<DistanceWindow> bombDisabledWindows = new Array<>();
    // Gem count taken right after the latest waypoint gem spawned, used as the next gemsCollected
    // gate's baseline. A fast player can grab the gem before that gate arms, and a live snapshot
    // would then demand one extra gem. -1 = none pending (use the live count).
    private int gemsAtLastWaypointSpawn = -1;
    // Same idea for enemiesDestroyed gates: kill count taken right after the latest enemy spawned.
    private int enemiesDestroyedAtLastSpawn = -1;

    /** A wave member waiting for its staggered spawn time (see fireWave()). */
    private static final class PendingWaveSpawn {
        final Trigger source; // type/firingPattern/inverseMovement/powerup come from here
        final float dueRealTime;
        final float x, y, offsetX, offsetY;
        final String movementPatternId; // already registered in PatternRegistry
        final Trigger entranceView; // per-member stand-in for EnemyEntranceMovement
        final Array<HealthPhase> healthPhases; // this member's (possibly formation-shifted) phases

        PendingWaveSpawn(Trigger source, float dueRealTime, float x, float y, float offsetX, float offsetY,
                          String movementPatternId, Trigger entranceView, Array<HealthPhase> healthPhases) {
            this.healthPhases = healthPhases;
            this.source = source;
            this.dueRealTime = dueRealTime;
            this.x = x;
            this.y = y;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.movementPatternId = movementPatternId;
            this.entranceView = entranceView;
        }
    }

    private final Array<PendingWaveSpawn> pendingWaveSpawns = new Array<>();
    // Counter for unique synthetic movement pattern ids registered by waves.
    private int nextSyntheticPatternId = 0;

    public TriggerManager(float worldWidth, float worldHeight, AssetManager assets, String triggerFilePath,
                           ObjectMap<String, EnemyDefinition> enemyDefinitions, ScrollingBackground background) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = assets;
        this.enemyDefinitions = enemyDefinitions;
        this.background = background;

        Json json = new Json();
        float initialSpeed = 1f;
        try {
            TriggerFile file = json.fromJson(TriggerFile.class, Gdx.files.internal(triggerFilePath));
            this.triggers = (file != null && file.triggers != null) ? file.triggers : new Array<>();
            if (file != null) initialSpeed = file.cameraSpeed;
            if (file != null && file.practiceCheckpoints != null) this.practiceCheckpoints = file.practiceCheckpoints;
            if (file != null && file.invincibilityWindows != null) this.invincibilityWindows = file.invincibilityWindows;
            if (file != null && file.weaponsDisabledWindows != null) this.weaponsDisabledWindows = file.weaponsDisabledWindows;
            if (file != null && file.hyperAttackDisabledWindows != null) this.hyperAttackDisabledWindows = file.hyperAttackDisabledWindows;
            if (file != null && file.bombDisabledWindows != null) this.bombDisabledWindows = file.bombDisabledWindows;
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
        for (Trigger trigger : triggers) {
            if (trigger.id == null || trigger.id.isBlank()) continue;
            if (triggersById.containsKey(trigger.id)) {
                Gdx.app.error("TriggerManager", "Duplicate trigger id '" + trigger.id + "' in " + triggerFilePath + " - conditions referring to it will use the last one");
            }
            triggersById.put(trigger.id, trigger);
        }
        camera = new LevelCamera(worldWidth, initialSpeed);
        baseSpeed = initialSpeed;
    }

    public LevelCamera getCamera() { return camera; }
    public Array<TextCue> getTextCues() { return liveTextCues; }
    public float getRealTime() { return realTime; }

    /** Current camera speed relative to the authored speed (1 = normal, 0 = stopped by setSpeed).
     *  GameController scales background and ground-enemy scrolling by this. Falls back to the raw
     *  speed if the authored speed is <= 0. */
    public float getSpeedScale() { return baseSpeed > 0f ? camera.getSpeed() / baseSpeed : camera.getSpeed(); }

    /** Debug overlay text describing the gate currently freezing the camera, or null. */
    public String describeActiveGate() {
        if (activeGate == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("gate@").append(activeGate.distance);
        if (!activeGate.actionFired) {
            sb.append(" [awaiting conditions]");
        } else if (activeGate.requireConfirm && !activeGate.confirmed) {
            sb.append(" [fired, awaiting confirm]");
        }
        if (activeGate.conditions != null) {
            for (Condition c : activeGate.conditions) {
                if (c.type == null) continue;
                sb.append(' ').append(c.type);
                if ("weaponSwitch".equals(c.type)) {
                    sb.append('=').append(c.weaponId);
                } else if ("enemiesDestroyed".equals(c.type) || "enemyTypeDestroyed".equals(c.type)
                    || "gemsCollected".equals(c.type) || "grazed".equals(c.type)) {
                    sb.append('(').append(c.count).append(')');
                }
                if ("spawnDestroyed".equals(c.type)) sb.append('=').append(c.triggerId);
            }
        }
        return sb.toString();
    }

    /** realTime and pending wave spawns always advance. While a gate is active, only that gate is
     *  re-checked and the camera stays frozen; gameplay itself (entities, bullets, player) runs
     *  outside this class and is unaffected. */
    public void update(float delta, EntityManager entityManager, AudioManager audio, InputManager input, ScoreManager scoreManager) {
        Player player = entityManager.getPlayer();
        realTime += delta;
        dispatchPendingWaveSpawns(entityManager);

        if (activeGate != null) {
            if (!tryResolve(activeGate, entityManager, audio, input, scoreManager, player, realTime)) {
                updateTextCueTyping(audio, realTime);
                return;
            }
            activeGate.fired = true;
            activeGate = null;
        }

        camera.update(delta);
        Rectangle box = camera.getCollisionBox();
        float minY = box.y;
        float maxY = box.y + box.height;

        for (Trigger trigger : triggers) {
            if (trigger.fired) continue;
            // spawnLead arms early. Clamped to 0, since the camera starts at 0 and a negative arm
            // distance would never be reached; such triggers arm on the first frame instead.
            float armDistance = Math.max(0f, trigger.distance - trigger.spawnLead);
            if (!trigger.armed) {
                if (armDistance < minY || armDistance >= maxY) continue;
                if (isSkippedThisAttempt(trigger)) {
                    trigger.armed = true;
                    trigger.actionFired = true;
                    trigger.fired = true;
                    continue;
                }
                trigger.armed = true;
                armConditions(trigger, scoreManager, player);
            }
            if (!tryResolve(trigger, entityManager, audio, input, scoreManager, player, realTime)) {
                if (trigger.gate) {
                    activeGate = trigger;
                    // See LevelCamera.clampTo(): keeps later triggers at this distance armable.
                    camera.clampTo(armDistance);
                    break; // nothing further arms while the gate holds
                }
                continue;
            }
            trigger.fired = true;
        }

        updateTextCueTyping(audio, realTime);
    }

    /** Fires the action as soon as the conditions are met (once), then, if requireConfirm, waits for
     *  the confirm press. Firing must come first: confirm means "seen and pressed on", so it can't be
     *  checked before the thing to see has appeared. Returns true when fully resolved. */
    private boolean tryResolve(Trigger trigger, EntityManager entityManager, AudioManager audio,
                                InputManager input, ScoreManager scoreManager, Player player, float realTime) {
        if (!trigger.actionFired) {
            if (!conditionsSatisfied(trigger, input, scoreManager, player)) return false;
            fire(trigger, entityManager, audio, realTime);
            trigger.actionFired = true;
            if (trigger.waypointGem) {
                gemsAtLastWaypointSpawn = scoreManager.getGemsCollected();
            } else if (isEnemySpawn(trigger)) {
                enemiesDestroyedAtLastSpawn = scoreManager.getEnemiesDestroyed();
                registerSpawnGroup(trigger, scoreManager);
            }
        }
        if (trigger.requireConfirm && !trigger.confirmed) {
            checkConfirm(trigger, audio, input, realTime);
            return trigger.confirmed;
        }
        return true;
    }

    /** Handles a SHOOT/RESTART press for a requireConfirm trigger. If its typewriter text is still
     *  revealing, the first press completes the reveal (by backdating triggeredAtRealTime) and a
     *  second press confirms. Confirming dismisses the text cue so it doesn't linger under the next. */
    private void checkConfirm(Trigger trigger, AudioManager audio, InputManager input, float realTime) {
        if (!trigger.requireConfirm || trigger.confirmed) return;
        if (!(input.isRestartJustPressed() || input.isShootJustPressed())) return;

        TextCue cue = trigger.liveTextCue;
        if (cue != null && "typewriter".equals(cue.effect)) {
            float revealDuration = cue.charsPerSecond > 0f ? cue.text.length() / cue.charsPerSecond : 0f;
            float cueElapsedTime = realTime - cue.triggeredAtRealTime;
            if (cueElapsedTime < revealDuration) {
                cue.triggeredAtRealTime = realTime - revealDuration;
                if (cue.typingSoundActive) {
                    audio.stopTextCueLoop();
                    cue.typingSoundActive = false;
                }
                return; // this press completed the reveal; not confirmed yet
            }
        }
        trigger.confirmed = true;
        if (cue != null) cue.dismissed = true;
    }

    /** Stops each typewriter cue's typing sound once its reveal finishes. Runs even while a gate is
     *  active, since the reveal keeps playing in real time. */
    private void updateTextCueTyping(AudioManager audio, float realTime) {
        for (TextCue cue : liveTextCues) {
            if (!cue.typingSoundActive) continue;
            float cueElapsedTime = realTime - cue.triggeredAtRealTime;
            float revealDuration = cue.charsPerSecond > 0f ? cue.text.length() / cue.charsPerSecond : 0f;
            // A confirm-gated cue stays up past its duration, so its typing sound runs until the
            // text is fully shown.
            if (cueElapsedTime >= revealDuration || (!cue.requireConfirm && cueElapsedTime >= cue.duration)) {
                audio.stopTextCueLoop();
                cue.typingSoundActive = false;
            }
        }
    }

    /** Snapshots each count-based condition's baseline so it measures progress since arming. Only a
     *  trigger with real conditions consumes the pending post-spawn snapshots; a text cue arming in
     *  between must not wipe them before the gate that needs them arms. */
    private void armConditions(Trigger trigger, ScoreManager scoreManager, Player player) {
        if (trigger.conditions == null || trigger.conditions.size == 0) return;
        for (Condition condition : trigger.conditions) {
            if (condition.type == null) continue;
            condition.baseline = switch (condition.type) {
                case "enemiesDestroyed" -> enemiesDestroyedAtLastSpawn >= 0 ? enemiesDestroyedAtLastSpawn : scoreManager.getEnemiesDestroyed();
                case "enemyTypeDestroyed" -> scoreManager.getEnemiesDestroyedByType(condition.enemyType);
                case "gemsCollected" -> gemsAtLastWaypointSpawn >= 0 ? gemsAtLastWaypointSpawn : scoreManager.getGemsCollected();
                case "grazed" -> player.getGrazePoints();
                default -> 0f;
            };
        }
        gemsAtLastWaypointSpawn = -1;
        enemiesDestroyedAtLastSpawn = -1;
    }

    /** True once the conditions are met per conditionMode; always true with no conditions. */
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

    /** "spawnDestroyed": every enemy from spawn trigger `id` (including wave members not yet spawned)
     *  has been killed. True for an unknown id or a spawn skipped by seekTo(). */
    private boolean isSpawnDestroyed(String id, ScoreManager scoreManager) {
        Trigger source = id == null ? null : triggersById.get(id);
        if (source == null) return true;
        if (!source.actionFired) return false;
        Integer expected = spawnGroupExpected.get(id);
        if (expected == null) return true;
        return scoreManager.getGroupDestroyed(id) >= expected;
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
            case "spawnDestroyed" -> isSpawnDestroyed(condition.triggerId, scoreManager);
            default -> true; // unknown type: don't soft-lock over a typo
        };
    }

    private void fire(Trigger trigger, EntityManager entityManager, AudioManager audio, float realTime) {
        if (trigger.sound != null) {
            audio.playCueSound(trigger.sound);
        } else if (trigger.spriteTexture != null) {
            EnemySpawnOps.spawnSpriteCue(entityManager, assets, trigger.spriteTexture, trigger.x, trigger.y, trigger.size,
                trigger.columns, trigger.rows, trigger.frameCount, trigger.frameDuration);
        } else if (trigger.setSpeed != null) {
            camera.setSpeed(trigger.setSpeed);
        } else if (trigger.text != null) {
            fireTextCue(trigger, audio, realTime);
        } else if (trigger.triggerBossVideo) {
            if (background != null) background.triggerBossVideo();
        } else if (trigger.fadeOutMusic) {
            audio.fadeOutStageMusic();
        } else if (trigger.music != null) {
            audio.switchStageMusic(trigger.music);
        } else if (trigger.silence) {
            EnemySpawnOps.silenceMatching(entityManager, trigger.type);
        } else if (trigger.despawn) {
            EnemySpawnOps.despawnMatching(entityManager, trigger.type);
        } else if (trigger.waypointGem) {
            EnemySpawnOps.spawnWaypointGem(entityManager, assets, worldWidth, worldHeight, trigger.x, trigger.y);
        } else if (trigger.swapWeaponId != null) {
            entityManager.getPlayer().setSlotWeapon(trigger.weaponSlot, trigger.swapWeaponId);
        } else if (trigger.type != null) {
            // The null check matters: a blank trigger (e.g. a bare gate) must not reach spawnEnemy(),
            // because ObjectMap.get(null) throws in this libGDX version. The entrance spawn height
            // is resolved later in GenericEnemy once the real sprite size is known.
            if (trigger.waveShape != null) {
                fireWave(trigger, realTime);
            } else {
                int enemiesBefore = entityManager.getEnemies().size;
                EnemySpawnOps.spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight,
                    trigger.type, trigger.x, trigger.y, trigger.offsetX, trigger.offsetY, trigger.movementPattern, trigger.firingPattern,
                    trigger.inverseMovement, trigger.powerup, trigger, camera.getSpeed(), trigger.healthPhases);
                tagSpawnGroup(entityManager, enemiesBefore, trigger);
            }
        }
    }

    /** Queues every member of a wave as a PendingWaveSpawn due at waveStartDelay + i * waveSpawnInterval
     *  (with zero delays, members still spawn this frame via dispatchPendingWaveSpawns()).
     *
     * Each member is an ordinary self-contained spawn at its own slot. With waveKeepFormation and an
     * authored movementPattern, each member gets its own clone of the pattern with every absolute
     * target shifted by its slot offset (registerShiftedClone()), which keeps the shape rigid. Without
     * an authored pattern, all members share one straight-line direction. waveRotation only moves
     * spawn positions; it never rotates the direction members fly. */
    private void fireWave(Trigger trigger, float fireRealTime) {
        Array<WaveSpawnPlanner.Slot> slots = WaveSpawnPlanner.plan(trigger, worldWidth / 2f, 1f);
        if (slots.size == 0) return;

        boolean hasOwnMovement = trigger.movementPattern != null && !trigger.movementPattern.isBlank()
            && PatternRegistry.getMovement(trigger.movementPattern) != null;

        // Keep-formation without an authored pattern: one shared angle, so identical velocities keep
        // the shape rigid (per-member angles would fan the group out).
        String sharedFallbackAngleId = null;
        if (trigger.waveKeepFormation && !hasOwnMovement) {
            float sharedAngle = WaveSpawnPlanner.plan(singleAnchorProbe(trigger), worldWidth / 2f, 1f).first().angleDeg;
            sharedFallbackAngleId = registerStraight(trigger.waveSpeed, sharedAngle, null);
        }

        float waveSpawnLift = trigger.waveKeepFormation ? computeWaveSpawnLift(trigger, slots, hasOwnMovement) : Float.NaN;

        for (int i = 0; i < slots.size; i++) {
            WaveSpawnPlanner.Slot slot = slots.get(i);
            float dueRealTime = fireRealTime + trigger.waveStartDelay + i * trigger.waveSpawnInterval;

            String movementPatternId;
            if (trigger.waveKeepFormation) {
                movementPatternId = hasOwnMovement
                    ? registerShiftedClone(trigger.movementPattern, slot.x - trigger.x, slot.y - trigger.y)
                    : sharedFallbackAngleId;
            } else {
                movementPatternId = hasOwnMovement ? trigger.movementPattern : registerStraight(trigger.waveSpeed, slot.angleDeg, null);
            }

            // Per-member stand-in for EnemyEntranceMovement: this member's true slot position plus the
            // shared additive lift (see Trigger.waveSpawnLift), so the formation keeps its shape from
            // spawn through arrival.
            Trigger entranceView = new Trigger();
            entranceView.x = slot.x;
            entranceView.y = slot.y;
            entranceView.enterFromAbove = trigger.enterFromAbove;
            entranceView.spawnLead = trigger.spawnLead;
            entranceView.distance = trigger.distance;
            entranceView.waveSpawnLift = waveSpawnLift;

            // Shift health-phase movement patterns per member too, or the formation would collapse
            // onto the same absolute waypoints when a phase starts.
            Array<HealthPhase> memberPhases = trigger.healthPhases;
            if (trigger.waveKeepFormation && memberPhases != null && memberPhases.size > 0) {
                memberPhases = new Array<>();
                for (HealthPhase phase : trigger.healthPhases) {
                    String phaseMovement = phase.movementPattern;
                    if (phaseMovement != null && PatternRegistry.getMovement(phaseMovement) != null) {
                        phaseMovement = registerShiftedClone(phaseMovement, slot.x - trigger.x, slot.y - trigger.y);
                    }
                    memberPhases.add(phase.withMovementPattern(phaseMovement));
                }
            }

            pendingWaveSpawns.add(new PendingWaveSpawn(trigger, dueRealTime, slot.x, slot.y, trigger.offsetX, trigger.offsetY, movementPatternId, entranceView, memberPhases));
        }
    }

    /** The shared additive entrance lift for a keep-formation wave, or NaN with no entrance. It must
     *  (a) lift the lowest member above worldHeight and (b) lift every member above its own shifted
     *  target. Because targets are shifted by the same offset as slots, (b) is one constant,
     *  baseTarget.y - trigger.y, for all members. Only an authored WaypointPath has a target to clear. */
    private float computeWaveSpawnLift(Trigger trigger, Array<WaveSpawnPlanner.Slot> slots, boolean hasOwnMovement) {
        if (!trigger.enterFromAbove) return Float.NaN;
        float minSlotY = Float.POSITIVE_INFINITY;
        for (WaveSpawnPlanner.Slot slot : slots) minSlotY = Math.min(minSlotY, slot.y);
        float lift = worldHeight - minSlotY;

        if (hasOwnMovement) {
            MovementPatternDef source = PatternRegistry.getMovement(trigger.movementPattern);
            if (source != null && "WaypointPath".equals(source.type) && source.patterns != null && source.patterns.size > 0) {
                float baseTargetY = source.patterns.first().targetY;
                if (!Float.isNaN(baseTargetY)) lift = Math.max(lift, baseTargetY - trigger.y);
            }
        }
        return lift;
    }

    /** Deep-clones a movement pattern, translates every absolute target in it (recursively through
     *  waypoint legs and sub-patterns) by (dx, dy), and registers it under a synthetic id (never saved
     *  to disk). A plain translate: members keep the authored flight direction even when waveRotation
     *  is set. NaN (unset) targets are relative already and stay untouched. */
    private String registerShiftedClone(String sourceId, float dx, float dy) {
        MovementPatternDef source = PatternRegistry.getMovement(sourceId);
        Json json = new Json();
        MovementPatternDef clone = json.fromJson(MovementPatternDef.class, json.toJson(source, MovementPatternDef.class));
        shiftTargets(clone, dx, dy);
        String id = "__wave" + (nextSyntheticPatternId++);
        clone.id = id;
        PatternRegistry.putMovement(id, clone);
        return id;
    }

    private static void shiftTargets(MovementPatternDef def, float dx, float dy) {
        if (def == null) return;
        if (!Float.isNaN(def.targetX)) def.targetX += dx;
        if (!Float.isNaN(def.targetY)) def.targetY += dy;
        if (def.patterns != null) for (MovementPatternDef sub : def.patterns) shiftTargets(sub, dx, dy);
        shiftTargets(def.pattern, dx, dy);
    }

    /** A one-member "point" wave at `trigger`'s anchor, used to ask WaveSpawnPlanner for the anchor's
     *  orientation angle. */
    private static Trigger singleAnchorProbe(Trigger trigger) {
        Trigger probe = new Trigger();
        probe.x = trigger.x;
        probe.y = trigger.y;
        probe.waveShape = "point";
        probe.waveOrientation = trigger.waveOrientation;
        probe.waveNumberOfSpawns = 1;
        return probe;
    }

    /** Registers a "Straight" movement pattern under `explicitId` or a fresh synthetic id, and
     *  returns the id. */
    private String registerStraight(float speed, float angleDeg, String explicitId) {
        MovementPatternDef def = new MovementPatternDef();
        String id = explicitId != null ? explicitId : ("__wave" + (nextSyntheticPatternId++));
        def.id = id;
        def.type = "Straight";
        def.speed = speed;
        def.movementAngle = angleDeg;
        PatternRegistry.putMovement(id, def);
        return id;
    }

    /** Spawns every wave member whose due time has arrived. Runs before the gate check so waves keep
     *  spawning while the camera is frozen. */
    private void dispatchPendingWaveSpawns(EntityManager entityManager) {
        for (int i = pendingWaveSpawns.size - 1; i >= 0; i--) {
            PendingWaveSpawn pending = pendingWaveSpawns.get(i);
            if (pending.dueRealTime > realTime) continue;
            int enemiesBefore = entityManager.getEnemies().size;
            EnemySpawnOps.spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight,
                pending.source.type, pending.x, pending.y, pending.offsetX, pending.offsetY,
                pending.movementPatternId, pending.source.firingPattern, pending.source.inverseMovement,
                pending.source.powerup, pending.entranceView, camera.getSpeed(), pending.healthPhases);
            tagSpawnGroup(entityManager, enemiesBefore, pending.source);
            pendingWaveSpawns.removeIndex(i);
        }
    }

    /** Builds and shows a TextCue from the trigger's text fields: a looping typing sound for a
     *  typewriter cue, otherwise a single blip. */
    private void fireTextCue(Trigger trigger, AudioManager audio, float realTime) {
        TextCue cue = new TextCue();
        cue.text = trigger.text;
        cue.effect = trigger.textEffect;
        cue.duration = trigger.textDuration;
        cue.requireConfirm = trigger.requireConfirm;
        cue.x = trigger.textX;
        cue.y = trigger.textY;
        cue.centered = trigger.textCentered;
        cue.fontSize = trigger.textFontSize;
        cue.charsPerSecond = trigger.textCharsPerSecond;
        cue.blinksPerSecond = trigger.textBlinksPerSecond;
        cue.triggeredAtRealTime = realTime;
        if ("typewriter".equals(cue.effect)) {
            audio.loopTextCue();
            cue.typingSoundActive = true;
        } else {
            audio.playTextCue();
        }
        liveTextCues.add(cue);
        trigger.liveTextCue = cue;
    }

    /** Tags the enemy a spawn call just added with the trigger's id, for "spawnDestroyed".
     *  `enemiesBefore` guards against a spawn that added nothing (unknown enemy type). */
    private static void tagSpawnGroup(EntityManager entityManager, int enemiesBefore, Trigger trigger) {
        if (trigger.id == null || trigger.id.isBlank()) return;
        Array<Enemy> enemies = entityManager.getEnemies();
        if (enemies.size > enemiesBefore) enemies.peek().setSpawnGroup(trigger.id);
    }

    /** Records how many enemies an id'd spawn trigger will produce and resets its kill tally. */
    private void registerSpawnGroup(Trigger trigger, ScoreManager scoreManager) {
        if (trigger.id == null || trigger.id.isBlank() || trigger.type == null) return;
        int members = trigger.waveShape != null ? WaveSpawnPlanner.plan(trigger, 0f, 0f).size : 1;
        spawnGroupExpected.put(trigger.id, members);
        scoreManager.clearGroupDestroyed(trigger.id);
    }

    /** True for a trigger whose action is spawning an enemy (no other action field set). */
    private static boolean isEnemySpawn(Trigger trigger) {
        return trigger.sound == null && trigger.spriteTexture == null && trigger.setSpeed == null
            && trigger.text == null && !trigger.triggerBossVideo && !trigger.fadeOutMusic && trigger.music == null
            && !trigger.silence && !trigger.despawn && !trigger.waypointGem && trigger.swapWeaponId == null
            && !trigger.gate && !trigger.scheduleEnd;
    }

    /** Total enemies this stage's triggers spawn, counting every wave member. Feeds the end-of-stage
     *  total and computeRank()'s kill fraction. */
    public int getEnemySpawnCount() {
        int count = 0;
        for (Trigger trigger : triggers) {
            if (!isEnemySpawn(trigger)) continue;
            count += trigger.waveShape != null ? WaveSpawnPlanner.plan(trigger, 0f, 0f).size : 1;
        }
        return count;
    }

    /** Distance of the first trigger spawning an isBoss enemy, or -1. */
    public float getBossSpawnDistance() {
        for (Trigger trigger : triggers) {
            if (!isEnemySpawn(trigger)) continue;
            EnemyDefinition def = enemyDefinitions.get(trigger.type);
            if (def != null && def.isBoss) return trigger.distance;
        }
        return -1f;
    }

    /** The track of the latest music trigger at or before `distance`, or null if none (the stage's
     *  own music applies). Used to restore the right track after a seek. */
    public String musicAt(float distance) {
        String track = null;
        float trackDistance = -Float.MAX_VALUE;
        for (Trigger trigger : triggers) {
            if (trigger.music != null && trigger.distance <= distance && trigger.distance >= trackDistance) {
                track = trigger.music;
                trackDistance = trigger.distance;
            }
        }
        return track;
    }

    /** Every sound the triggers can play, so they can be preloaded at stage load. */
    public Array<String> getCueSoundPaths() {
        Array<String> paths = new Array<>();
        for (Trigger trigger : triggers) {
            if (trigger.sound != null && !paths.contains(trigger.sound, false)) paths.add(trigger.sound);
        }
        return paths;
    }

    /** Distance of the boss-video trigger, or -1. GameController also uses it as the hue-cycle
     *  background's period. */
    public float getBossVideoDistance() {
        for (Trigger trigger : triggers) {
            if (trigger.triggerBossVideo) return trigger.distance;
        }
        return -1f;
    }

    /** True once a scheduleEnd trigger has fired (stage complete without a boss). */
    public boolean isScheduleEndTriggered() {
        for (Trigger trigger : triggers) {
            if (trigger.scheduleEnd && trigger.fired) return true;
        }
        return false;
    }

    /** True while `distance` is inside a practice checkpoint, where a hit rewinds instead of
     *  costing a life. */
    public boolean isInPracticeSection(float distance) {
        return findCheckpoint(distance) != null;
    }

    /** Start of the checkpoint containing `distance`, or `distance` itself if none. */
    public float getPracticeCheckpointStart(float distance) {
        DistanceWindow checkpoint = findCheckpoint(distance);
        return checkpoint != null ? checkpoint.start : distance;
    }

    /** True if `trigger` should be skipped on this pass (see Trigger.firstAttemptOnly/retryOnly). */
    private boolean isSkippedThisAttempt(Trigger trigger) {
        if (!trigger.firstAttemptOnly && !trigger.retryOnly) return false;
        boolean retrying = retryCheckpoint != null
            && trigger.distance >= retryCheckpoint.start && trigger.distance < retryCheckpoint.end;
        return trigger.retryOnly ? !retrying : retrying;
    }

    /** Rewinds to the start of the checkpoint containing `distance` after a failed attempt and marks
     *  that checkpoint as being retried. Returns the distance rewound to. */
    public float seekToPracticeRetry(float distance) {
        DistanceWindow checkpoint = findCheckpoint(distance);
        float target = checkpoint != null ? checkpoint.start : distance;
        seekTo(target);
        retryCheckpoint = checkpoint;
        return target;
    }

    private DistanceWindow findCheckpoint(float distance) {
        for (DistanceWindow checkpoint : practiceCheckpoints) {
            if (distance >= checkpoint.start && distance < checkpoint.end) return checkpoint;
        }
        return null;
    }

    public boolean isPlayerInvincible(float distance) {
        return inAnyWindow(invincibilityWindows, distance);
    }

    public boolean isWeaponsDisabled(float distance) {
        return inAnyWindow(weaponsDisabledWindows, distance);
    }

    public boolean isHyperAttackDisabled(float distance) {
        return inAnyWindow(hyperAttackDisabledWindows, distance);
    }

    public boolean isBombDisabled(float distance) {
        return inAnyWindow(bombDisabledWindows, distance);
    }

    private static boolean inAnyWindow(Array<DistanceWindow> windows, float distance) {
        for (DistanceWindow window : windows) {
            if (distance >= window.start && distance < window.end) return true;
        }
        return false;
    }

    public void reset() {
        camera.reset();
        realTime = 0f;
        liveTextCues.clear();
        activeGate = null;
        retryCheckpoint = null;
        gemsAtLastWaypointSpawn = -1;
        enemiesDestroyedAtLastSpawn = -1;
        for (Trigger trigger : triggers) {
            trigger.fired = false;
            trigger.armed = false;
            trigger.confirmed = false;
            trigger.actionFired = false;
        }
    }

    /** Jumps to targetDistance. Triggers behind it are marked fired without running (skipped, not
     *  replayed); triggers ahead are reset. Live text cues and any active gate are dropped. */
    public void seekTo(float targetDistance) {
        camera.seekTo(targetDistance);
        liveTextCues.clear();
        activeGate = null;
        retryCheckpoint = null; // a plain seek is a fresh pass
        spawnGroupExpected.clear();
        gemsAtLastWaypointSpawn = -1;
        enemiesDestroyedAtLastSpawn = -1;
        for (Trigger trigger : triggers) {
            // Same clamped arm distance as update().
            boolean past = Math.max(0f, trigger.distance - trigger.spawnLead) <= targetDistance;
            trigger.armed = past;
            trigger.fired = past;
            trigger.actionFired = past;
            trigger.confirmed = false;
        }
    }
}
