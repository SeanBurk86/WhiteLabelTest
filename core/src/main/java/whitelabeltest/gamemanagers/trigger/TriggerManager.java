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
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.player.Player;

import java.util.Comparator;
import java.util.Objects;

/** Camera-position-driven counterpart to SpawnScheduler. Every SpawnScheduler feature now has a
 *  distance-based equivalent here: enemy/sound/sprite/text cues, camera-speed changes, boss-video/
 *  music-fade triggers (see Trigger's own action fields), gates (see Trigger.gate/requireConfirm -
 *  freezes the whole camera, not just one trigger, the same way GateCue freezes the whole schedule),
 *  a schedule-end marker for a boss-less stage (Trigger.scheduleEnd), and the five wall-clock
 *  [start, end) windows (practice checkpoints, invincibility, weapons/Hyper-Attack/bomb-disabled -
 *  see TriggerFile's own fields and the matching isXxx(distance) query methods below). A stage can
 *  still mix both sources - a SpawnScheduler with nothing left in it at all, like stage1's and the
 *  tutorial's once fully migrated, alongside a fully triggers-driven stage - since a stage that sets
 *  StageDefinition.triggerFile gets one of these alongside its SpawnScheduler either way; the two
 *  run in parallel, each firing whatever content was actually authored onto it.
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

        // Zero or more [start, end) distance windows - one class shared by all five lists below
        // (SpawnScheduler kept these as distinctly-named classes; here the list's own JSON key
        // already says what it's for, so one shape is enough). See the matching isXxx() query
        // method on TriggerManager for what each list actually gates.
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
    private Array<Trigger> triggers;
    // Live text cues fired via a Trigger.text action - see fire()/getTextCues(). GameController
    // merges this alongside SpawnScheduler's own (wall-clock) textCues into one combined list for
    // UIManager to draw, exactly as if they'd come from a single source.
    private final Array<TextCue> liveTextCues = new Array<>();
    // The one gate trigger (see Trigger.gate) currently freezing the camera, or null if none is -
    // see update()'s own doc on why this needs to be checked before camera.update() runs at all,
    // mirroring SpawnScheduler's own activeGate field exactly, just against distance instead of time.
    private Trigger activeGate;
    // This manager's own wall-clock accumulator, incremented by delta at the top of update() -
    // stamps/measures every text cue's reveal progress (fireTextCue()/checkConfirm()/
    // updateTextCueTyping(), all self-consistent against this SAME clock). Used to be supplied
    // externally (GameController.getSpawnScheduleRealTime(), i.e. SpawnScheduler's own clock) so a
    // stage running both systems drew every cue - whichever fired it - against one shared clock;
    // now that a triggerFile-driven stage no longer runs a SpawnScheduler at all (see
    // GameController.loadStage()), this manager owns its own instead. GameController's
    // getSpawnScheduleRealTime() falls back to THIS clock (getRealTime()) when there's no
    // SpawnScheduler running, so UIManager.drawTextCues() keeps working unmodified either way.
    private float realTime = 0f;
    private Array<DistanceWindow> practiceCheckpoints = new Array<>();
    private Array<DistanceWindow> invincibilityWindows = new Array<>();
    private Array<DistanceWindow> weaponsDisabledWindows = new Array<>();
    private Array<DistanceWindow> hyperAttackDisabledWindows = new Array<>();
    private Array<DistanceWindow> bombDisabledWindows = new Array<>();
    // Mirrors SpawnScheduler's own gemsAtLastWaypointSpawn exactly: gemsCollected as of the most
    // recent waypointGem trigger's own fire() call - necessarily taken before that gem could
    // possibly be collected - so a "gemsCollected" condition's armConditions() baseline can use THIS
    // instead of the live count, closing the race where a fast player grabs the gem before the gate
    // arms (almost always the very next trigger) and a live snapshot would already include it,
    // demanding one more gem than intended and cascading that off-by-one through however many more
    // gates follow (see the tutorial's own 21-gate waypoint-gem gauntlet). -1 means "nothing to use"
    // (falls back to the live count) - reset the moment ANY trigger arms (not just a gemsCollected
    // one), same as the original, so a stale snapshot from an unrelated earlier gem spawn can't leak
    // into a much later gate's baseline.
    private int gemsAtLastWaypointSpawn = -1;
    // Same reasoning as gemsAtLastWaypointSpawn, one leg over: enemiesDestroyed as of the most
    // recent enemy-spawning trigger's own fire() call - necessarily taken before that enemy could
    // possibly be destroyed. SpawnScheduler never protected "enemiesDestroyed" gates against this
    // race the way it did gems (no equivalent of this field exists there - see
    // enemiesDestroyedAtGateStart's own plain live-count snapshot), which mattered less there since
    // GateCue and TextCue never shared one arming path the way a Trigger's own gate+requireConfirm
    // can. Once LevelCamera.clampTo() started letting a gate co-located with its own text cue
    // reliably arm right after that cue (rather than being silently stranded - see that method's own
    // doc), this exact race became real: a fast player destroying the tutorial's third stream target
    // while its "Perfect!" text cue is still up gets that kill baked into the co-located
    // enemiesDestroyed gate's baseline the moment it finally arms, leaving it waiting on a 4th kill
    // that never comes. -1 means "nothing to use" (falls back to the live count); reset the moment
    // ANY trigger arms, same as gemsAtLastWaypointSpawn.
    private int enemiesDestroyedAtLastSpawn = -1;

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
        camera = new LevelCamera(worldWidth, initialSpeed);
    }

    public LevelCamera getCamera() { return camera; }
    public Array<TextCue> getTextCues() { return liveTextCues; }
    public float getRealTime() { return realTime; }

    /** Debug-only (see UIManager.drawDebugTriggerInfo()): a short human-readable description of
     *  whichever gate is currently freezing the camera, or null if none is - lets a stuck stage be
     *  diagnosed on screen (which gate, what it's waiting on) instead of guessing blind. */
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
            }
        }
        return sb.toString();
    }

    /** Advances this manager's own realTime clock (see that field's own doc) by delta FIRST, always
     *  - unlike distance (this.camera, frozen by an active gate below), realTime must never freeze,
     *  same "separate, ever-advancing clock purely for cue reveal timing" contract
     *  SpawnScheduler.getRealTime() (vs. its own gate-frozen getTotalTime()) already had: a
     *  confirm-gated typewriter cue still needs to finish revealing its text while the gate itself
     *  holds the camera/trigger timeline frozen waiting on that same confirm press.
     *
     * If activeGate is set (see Trigger.gate's own doc), this frame does nothing but re-check that
     * one gate's resolution state (see tryResolve()) - camera.update() is skipped entirely, freezing
     * distance exactly the way SpawnScheduler freezes totalTime at an unsatisfied GateCue, so nothing
     * further into the stage can arm/fire while a gate is blocking. Gameplay itself (entities,
     * bullets, player movement) is driven entirely outside this class and keeps running normally
     * throughout - only the camera/trigger timeline is what freezes, same distinction SpawnScheduler's
     * own gate freeze already makes. */
    public void update(float delta, EntityManager entityManager, AudioManager audio, InputManager input, ScoreManager scoreManager) {
        Player player = entityManager.getPlayer();
        realTime += delta;

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
            // See Trigger.spawnLead's own doc - a trigger with a lead set actually arms/fires this
            // many distance-units BEFORE its own authored `distance`, even though `distance` itself
            // (used for sorting/display/every other purpose) is untouched. Clamped to 0: the camera's
            // own collision box (see LevelCamera.getCollisionBox()) starts at minY=0 and only ever
            // advances forward from there, so a negative armDistance (a lead bigger than the
            // trigger's own distance - exactly what "already spawned before the camera starts
            // scrolling at all" needs) would fall inside NO box ever produced and could never arm -
            // clamping means it simply arms on the very first frame instead, which is the correct
            // "present from the start" behavior this was actually being used for.
            float armDistance = Math.max(0f, trigger.distance - trigger.spawnLead);
            if (!trigger.armed) {
                if (armDistance < minY || armDistance >= maxY) continue;
                trigger.armed = true;
                armConditions(trigger, scoreManager, player);
            }
            if (!tryResolve(trigger, entityManager, audio, input, scoreManager, player, realTime)) {
                if (trigger.gate) {
                    activeGate = trigger;
                    // See LevelCamera.clampTo()'s own doc - without this, any OTHER trigger sharing
                    // this exact distance (later in trigger order) would be permanently stranded once
                    // this gate clears. Clamped to armDistance (not the raw `distance`) so the camera
                    // freezes exactly where this gate actually armed, matching spawnLead's own effect.
                    camera.clampTo(armDistance);
                    break; // freeze here - nothing further into the stage arms/fires this frame
                }
                continue;
            }
            trigger.fired = true;
        }

        updateTextCueTyping(audio, realTime);
    }

    /** Advances trigger through its two independent phases for this frame: fires the moment
     *  `conditions` are satisfied - exactly once, guarded by actionFired, and NEVER delayed by
     *  requireConfirm, so a text cue shows the instant it's reached, same as one without confirm -
     *  then, only once already fired, waits for requireConfirm's press if it's set. Returns true once
     *  BOTH phases are done (or were never needed), meaning the camera/rest of the stage can treat
     *  this trigger as fully resolved.
     *
     * Firing before checking confirm (rather than gating firing on it, this method's original and
     * wrong shape) is the whole point: confirm means "the player has SEEN this and pressed on," which
     * is meaningless to check before the thing they need to see has actually appeared. Getting this
     * backwards froze every confirm trigger with nothing shown, so an unrelated shoot-press (which
     * confirm also treats as its input) satisfied confirm and firing in the same frame, letting the
     * camera race on to the next one - the same failure mode is why despawn/silence triggers further
     * into the stage no longer landed where they should either, once the pacing was off. */
    private boolean tryResolve(Trigger trigger, EntityManager entityManager, AudioManager audio,
                                InputManager input, ScoreManager scoreManager, Player player, float realTime) {
        if (!trigger.actionFired) {
            if (!conditionsSatisfied(trigger, input, scoreManager, player)) return false;
            fire(trigger, entityManager, audio, realTime);
            trigger.actionFired = true;
            if (trigger.waypointGem) {
                // See gemsAtLastWaypointSpawn's own doc - taken now, immediately after spawning,
                // necessarily before this gem could possibly be collected.
                gemsAtLastWaypointSpawn = scoreManager.getGemsCollected();
            } else if (isEnemySpawn(trigger)) {
                // See enemiesDestroyedAtLastSpawn's own doc - same reasoning as
                // gemsAtLastWaypointSpawn, just for a spawned enemy's own kill instead of a gem's
                // own collection.
                enemiesDestroyedAtLastSpawn = scoreManager.getEnemiesDestroyed();
            }
        }
        if (trigger.requireConfirm && !trigger.confirmed) {
            checkConfirm(trigger, audio, input, realTime);
            return trigger.confirmed;
        }
        return true;
    }

    /** Latches trigger.confirmed the first frame SHOOT or RESTART is pressed while it's waiting -
     *  see Trigger.requireConfirm's own doc. Mirrors SpawnScheduler.update()'s own awaitingConfirmCue
     *  handling exactly: if the press lands while a linked typewriter cue (see Trigger.liveTextCue)
     *  is STILL REVEALING, it force-completes the reveal instead of confirming - rewinding the cue's
     *  own triggeredAtRealTime by its reveal duration, the same trick that method uses, so every
     *  other bit of elapsed-time math (typing sound, display duration) keeps working unmodified -
     *  rather than cutting the message off before the player has actually read it. Only a SECOND
     *  press, once the reveal is genuinely done (naturally or just force-completed), actually
     *  confirms. A trigger with no linked text cue, or a non-typewriter one (nothing to reveal),
     *  confirms on the first press same as before this fix. A no-op once already confirmed or if
     *  this trigger doesn't use confirm at all. */
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
                return; // still frozen - this press force-completed the reveal, not confirmed yet
            }
        }
        trigger.confirmed = true;
        // UIManager.drawTextCues() only hides a cue once `dismissed` is set - otherwise it stays
        // drawn (at whatever screen position, usually shared by every cue) until its OWN duration
        // naturally runs out, regardless of this trigger having moved on - see TextCue.dismissed's
        // own doc. Without this, a cue confirmed well inside its own duration window (the normal
        // case - the player reads and confirms faster than the multi-second duration meant as a
        // "max time if never confirmed" ceiling) stays on screen overlapping whatever the NEXT
        // trigger fires right after, exactly the way SpawnScheduler.update() itself sets
        // awaitingConfirmCue.dismissed the instant it resolves.
        if (cue != null) cue.dismissed = true;
    }

    /** Stops a typewriter cue's looping blip sound once its reveal finishes - same condition
     *  SpawnScheduler.update() checks for its own text cues (see that method's own doc). Runs every
     *  frame regardless of an active gate, since an already-shown cue's reveal keeps playing out in
     *  real time even while the camera/trigger timeline itself is frozen waiting on that gate. */
    private void updateTextCueTyping(AudioManager audio, float realTime) {
        for (TextCue cue : liveTextCues) {
            if (!cue.typingSoundActive) continue;
            float cueElapsedTime = realTime - cue.triggeredAtRealTime;
            float revealDuration = cue.charsPerSecond > 0f ? cue.text.length() / cue.charsPerSecond : 0f;
            if (cueElapsedTime >= revealDuration || cueElapsedTime >= cue.duration) {
                audio.stopTextCueLoop();
                cue.typingSoundActive = false;
            }
        }
    }

    /** Snapshots each of trigger's conditions' baseline the instant it arms - count-based
     *  conditions (enemiesDestroyed/enemyTypeDestroyed/gemsCollected/grazed) compare against how
     *  much has happened SINCE arming, not the run's running total, same reasoning as
     *  SpawnScheduler's own *AtGateStart fields (see Condition's doc for why this can't just be a
     *  handful of instance fields here the way it is there).
     *
     * Also consumes+resets gemsAtLastWaypointSpawn/enemiesDestroyedAtLastSpawn (see either field's
     * own doc) - but ONLY for a trigger that actually HAS conditions of its own, i.e. one that's
     * functionally a GateCue-equivalent, not merely gate=true for its OWN confirm-freeze (see
     * Trigger.gate/requireConfirm). This distinction matters here in a way it never needed to in
     * SpawnScheduler: there, GateCue and TextCue were entirely separate mechanisms operating on
     * separate arrays, so a text cue "arming" (triggering) never touched gemsAtLastWaypointSpawn at
     * all. Here, a text-cue Trigger arms through this exact same method (it's a Trigger too) despite
     * having no conditions of its own - resetting unconditionally on EVERY arm (this method's
     * original, wrong shape) meant a text cue co-located with its own paired gate (the tutorial's
     * "Perfect!" cue right before the third stream-kill gate, same pattern as the gems gauntlet)
     * would wipe out the snapshot the SECOND it armed, before the real gate ever got a chance to
     * consume it - leaving that gate to fall back to the live count once it finally armed after the
     * cue's own confirm resolved, which by then usually already included the kill. Gating the reset
     * on "has real conditions" restores the original's actual behavior: only a genuine gate-with-
     * conditions consumes (and clears) the pending snapshot; anything else arming in between (a text
     * cue, a plain spawn, a sound cue) leaves it alone for whatever later trigger actually needs it. */
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
        } else if (trigger.silence) {
            EnemySpawnOps.silenceMatching(entityManager, trigger.type);
        } else if (trigger.despawn) {
            EnemySpawnOps.despawnMatching(entityManager, trigger.type);
        } else if (trigger.waypointGem) {
            EnemySpawnOps.spawnWaypointGem(entityManager, assets, worldWidth, worldHeight, trigger.x, trigger.y);
        } else if (trigger.swapWeaponId != null) {
            entityManager.getPlayer().setSlotWeapon(trigger.weaponSlot, trigger.swapWeaponId);
        } else if (trigger.type != null) {
            // Only reached with a real enemy id - a blank trigger (an ActionPalette "Trigger Event"
            // linked to nothing, or a bare gate/requireConfirm trigger with no action at all - see
            // Trigger.gate's own doc) has trigger.type == null here, and must NOT fall through to
            // EnemySpawnOps.spawnEnemy(): ObjectMap.get(null) throws IllegalArgumentException in
            // this libGDX version rather than returning null, so this guard is load-bearing, not
            // just a shortcut - a null type used to crash the whole game the instant such a trigger
            // fired.
            //
            // See EnemyEntranceMovement's own doc - trigger.enterFromAbove's actual off-screen spawnY
            // depends on the real spawn sprite's true height, which isn't known until GenericEnemy.
            // initWithDefinition() itself has built it, so this just passes trigger.y through as an
            // ordinary spawn position; that method overrides it (and builds the entrance MOVEMENT)
            // once the real size is known - this just passes `trigger` and the camera's CURRENT
            // speed through for it to use there.
            EnemySpawnOps.spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight,
                trigger.type, trigger.x, trigger.y, trigger.offsetX, trigger.offsetY, trigger.movementPattern, trigger.firingPattern,
                trigger.inverseMovement, trigger.powerup, trigger, camera.getSpeed());
        }
    }

    /** Builds a live TextCue from trigger's authored text fields and starts it playing - same
     *  trigger-time behavior SpawnScheduler.update() gives its own (wall-clock) text cues the
     *  instant they're reached: an immediate blip for a static/blinking cue, or a looping typing
     *  sound for a typewriter cue that update()'s own per-frame loop above stops once the reveal
     *  finishes. */
    private void fireTextCue(Trigger trigger, AudioManager audio, float realTime) {
        TextCue cue = new TextCue();
        cue.text = trigger.text;
        cue.effect = trigger.textEffect;
        cue.duration = trigger.textDuration;
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

    /** True for a Trigger that actually spawns an enemy (the default action, same as an ordinary
     *  SpawnScheduler.SpawnEvent) rather than one of the other action kinds - see
     *  getEnemySpawnCount(). */
    private static boolean isEnemySpawn(Trigger trigger) {
        return trigger.sound == null && trigger.spriteTexture == null && trigger.setSpeed == null
            && trigger.text == null && !trigger.triggerBossVideo && !trigger.fadeOutMusic
            && !trigger.silence && !trigger.despawn && !trigger.waypointGem && trigger.swapWeaponId == null
            && !trigger.gate && !trigger.scheduleEnd;
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

    /** Distance of this stage's triggerBossVideo trigger (see Trigger.triggerBossVideo), or -1 if
     *  this stage's triggers don't fire one - mirrors getBossSpawnDistance()'s own fallback
     *  pattern. Consumed by GameController's hue-cycle-background period (see
     *  ScrollingBackground.setHueCyclePeriod()), which reuses this same "how far into the stage
     *  things escalate" value as a ready-made period rather than needing its own separately
     *  authored one - the same accidental-but-kept coupling SpawnScheduler.getBackgroundVideoTime()
     *  served before this trigger kind existed. */
    public float getBossVideoDistance() {
        for (Trigger trigger : triggers) {
            if (trigger.triggerBossVideo) return trigger.distance;
        }
        return -1f;
    }

    /** True once a Trigger.scheduleEnd trigger has fired - the distance-based equivalent of
     *  SpawnScheduler.isScheduleEndTriggered(), for a stage with no boss to kill (e.g. the tutorial).
     *  A live lookup rather than a separately-tracked latch, since Trigger.fired already gets reset/
     *  reconstructed correctly by reset()/seekTo() with no extra bookkeeping needed here. */
    public boolean isScheduleEndTriggered() {
        for (Trigger trigger : triggers) {
            if (trigger.scheduleEnd && trigger.fired) return true;
        }
        return false;
    }

    /** True while `distance` sits inside any [start, end) practice checkpoint - see
     *  SpawnScheduler.isInPracticeSection()'s own doc for what this means for GameController's hit
     *  handling; identical meaning here, just keyed by distance instead of time. */
    public boolean isInPracticeSection(float distance) {
        return findCheckpoint(distance) != null;
    }

    /** Where a hit at `distance` (while isInPracticeSection(distance)) rewinds the camera back to -
     *  returns `distance` itself if no checkpoint currently contains it. */
    public float getPracticeCheckpointStart(float distance) {
        DistanceWindow checkpoint = findCheckpoint(distance);
        return checkpoint != null ? checkpoint.start : distance;
    }

    private DistanceWindow findCheckpoint(float distance) {
        for (DistanceWindow checkpoint : practiceCheckpoints) {
            if (distance >= checkpoint.start && distance < checkpoint.end) return checkpoint;
        }
        return null;
    }

    /** True while `distance` sits inside any [start, end) invincibility window - see
     *  SpawnScheduler.isPlayerInvincible()'s own doc. */
    public boolean isPlayerInvincible(float distance) {
        return inAnyWindow(invincibilityWindows, distance);
    }

    /** True while `distance` sits inside any [start, end) weapons-disabled window - see
     *  SpawnScheduler.isWeaponsDisabled()'s own doc. */
    public boolean isWeaponsDisabled(float distance) {
        return inAnyWindow(weaponsDisabledWindows, distance);
    }

    /** True while `distance` sits inside any [start, end) Hyper-Attack-disabled window - see
     *  SpawnScheduler.isHyperAttackDisabled()'s own doc. */
    public boolean isHyperAttackDisabled(float distance) {
        return inAnyWindow(hyperAttackDisabledWindows, distance);
    }

    /** True while `distance` sits inside any [start, end) bomb-disabled window - see
     *  SpawnScheduler.isBombDisabled()'s own doc. */
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
        gemsAtLastWaypointSpawn = -1;
        enemiesDestroyedAtLastSpawn = -1;
        for (Trigger trigger : triggers) {
            trigger.fired = false;
            trigger.armed = false;
            trigger.confirmed = false;
            trigger.actionFired = false;
        }
    }

    /** Debug/practice-rewind parity with SpawnScheduler.seekTo() - same "skip rather than replay"
     *  compromise that method's own doc describes for a gate it jumps past: a trigger whose distance
     *  falls behind targetDistance is marked armed-and-fired without its conditions (if any) ever
     *  actually being checked, rather than replayed. One ahead of it resets to fully unarmed so it
     *  behaves normally once the camera reaches it again. A text-cue trigger skipped this way simply
     *  never shows its cue (same skip, not replay) - liveTextCues is cleared unconditionally so a
     *  rewind can't leave a stale cue from beyond the new position still on screen. activeGate is
     *  likewise dropped unconditionally - whichever gate (if any) the new position actually falls on
     *  will simply arm again normally on a later update() once it's ahead of targetDistance, or was
     *  already marked fired/past if behind it. */
    public void seekTo(float targetDistance) {
        camera.seekTo(targetDistance);
        liveTextCues.clear();
        activeGate = null;
        gemsAtLastWaypointSpawn = -1;
        enemiesDestroyedAtLastSpawn = -1;
        for (Trigger trigger : triggers) {
            // See Trigger.spawnLead's own doc/update()'s matching (identically-clamped) armDistance -
            // a trigger due to arm early is "past" a seek target that's still short of its own
            // authored distance too.
            boolean past = Math.max(0f, trigger.distance - trigger.spawnLead) <= targetDistance;
            trigger.armed = past;
            trigger.fired = past;
            trigger.actionFired = past;
            trigger.confirmed = false;
        }
    }
}
