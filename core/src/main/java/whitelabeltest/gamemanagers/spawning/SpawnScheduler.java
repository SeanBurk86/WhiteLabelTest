package whitelabeltest.gamemanagers.spawning;
import whitelabeltest.gamemanagers.background.Stage2KaleidoscopeShader;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.gamemanagers.TextCue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.enemy.EnemyDefinition;

import java.util.Comparator;
import java.util.Objects;

/** Legacy time-based stage script (a *_schedule.json). Only used by stages without a trigger file;
 *  see TriggerManager for the current distance-based system. */
public class SpawnScheduler {
    public static class SpawnEvent {
        public float time;
        public String type;
        public float x = Float.NaN;
        public float y = Float.NaN;
        // Guaranteed weapon-powerup tier (1-3) dropped on death; null = none.
        public Integer powerup;
        public boolean inverseMovement = false;
        public boolean spawned = false;

        // Squad formation slot (see PatternFactory.createMovement). NaN = not a formation member.
        public float offsetX = Float.NaN;
        public float offsetY = Float.NaN;

        // Override the enemy definition's movement/firing pattern for this spawn.
        public String movementPattern;
        public String firingPattern;

        // Instead of spawning: stop every active enemy of definition `type` from firing.
        public boolean silence = false;

        // Instead of spawning: place a stationary PointGem at (x, y), for "fly through these points"
        // drills scored by a gemsCollected gate.
        public boolean waypointGem = false;

        // Instead of spawning: remove every active enemy of definition `type` (no death, score or drops).
        public boolean despawn = false;

        // Instead of spawning: put weapon swapWeaponId in slot weaponSlot (see Player.setSlotWeapon()).
        public String swapWeaponId = null;
        public int weaponSlot = 0;

        public SpawnEvent() {}
    }

    // One-off sound effect; `sound` is a path relative to assets/, loaded on first play.
    public static class SoundCue {
        public float time;
        public String sound;
        public boolean triggered = false;

        public SoundCue() {}
    }

    // One-off sprite-sheet animation at a fixed position; frameCount 1 shows a static image for
    // frameDuration seconds.
    public static class SpriteCue {
        public float time;
        public String texture;
        public float x;
        public float y;
        public float size = 1f;
        public int columns = 1;
        public int rows = 1;
        public int frameCount = 1;
        public float frameDuration = 1f;
        public boolean triggered = false;

        public SpriteCue() {}
    }

    // When the schedule clock reaches `time`, it freezes there until `condition` is satisfied. Live
    // gameplay keeps running; only the schedule is paused.
    public static class GateCue {
        public float time;
        // Same condition names as Condition.type, minus enemyTypeDestroyed/spawnDestroyed.
        // Input conditions are edge-triggered (held input doesn't count); weaponSwitch is a level
        // check on the equipped weapon. Unknown/null counts as satisfied.
        public String condition;
        // enemiesDestroyed/gemsCollected/grazed: how many since the gate engaged.
        public int count = 1;
        // weaponSwitch: the weapon id that must be equipped.
        public String weaponId;
        public boolean triggered = false;

        public GateCue() {}
    }

    // The JSON shape of a schedule file (also loaded/saved whole by SpawnScheduleEditor).
    public static class ScheduleFile {
        public Array<TextCue> textCues;
        public Array<SpawnEvent> events;
        public Array<SoundCue> soundCues;
        public Array<SpriteCue> spriteCues;
        public Array<GateCue> gates;
        // Optional time to start the boss video.
        public Float backgroundVideoTime;
        // Optional time to fade out the stage music (independent of backgroundVideoTime).
        public Float musicFadeOutTime;
        // Optional time marking the schedule complete, for stages with no boss (e.g. the tutorial).
        public Float scheduleEndTime;
        // Optional overrides; null = Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME /
        // ScrollingBackground.DEFAULT_SCROLL_SPEED.
        public Float kaleidoscopeTransitionTime;
        public Float groundScrollSpeed;
        // [start, end) time windows; see the matching isXxx() methods.
        public Array<PracticeCheckpoint> practiceCheckpoints;
        public Array<InvincibilityWindow> invincibilityWindows;
        public Array<WeaponsDisabledWindow> weaponsDisabledWindows;
        public Array<WeaponsDisabledWindow> hyperAttackDisabledWindows;
        public Array<WeaponsDisabledWindow> bombDisabledWindows;

        // True: every text cue freezes the schedule until the player confirms, instead of hiding
        // after its duration.
        public boolean textCuesRequireConfirm = false;

        public ScheduleFile() {}
    }

    public static class PracticeCheckpoint {
        public float start;
        public float end;

        public PracticeCheckpoint() {}
    }

    public static class InvincibilityWindow {
        public float start;
        public float end;

        public InvincibilityWindow() {}
    }

    public static class WeaponsDisabledWindow {
        public float start;
        public float end;

        public WeaponsDisabledWindow() {}
    }

    // Schedule clock; freezes at gates and confirm-gated text cues.
    private float totalTime;
    // Never-frozen clock used for text cue display timing.
    private float realTime;
    private final float worldWidth;
    private final float worldHeight;
    private Array<SpawnEvent> schedule;
    private Array<TextCue> textCues = new Array<>();
    private Array<SoundCue> soundCues = new Array<>();
    private Array<SpriteCue> spriteCues = new Array<>();
    private Array<GateCue> gates = new Array<>();
    // The gate currently freezing the schedule (gates clear in order), or null.
    private GateCue activeGate;
    // Counters when activeGate engaged, so gate counts measure progress since then.
    private int enemiesDestroyedAtGateStart;
    private int gemsCollectedAtGateStart;
    private float grazePointsAtGateStart;
    // Gem count taken when the latest waypoint gem spawned (-1 = none). Used as the next gate's
    // baseline so a gem grabbed before the gate engages still counts.
    private int gemsAtLastWaypointSpawn = -1;
    private Float backgroundVideoTime;
    private boolean backgroundVideoTriggered;
    private Float musicFadeOutTime;
    private boolean musicFadeOutTriggered;
    private Float scheduleEndTime;
    private boolean scheduleEndTriggered;
    private float kaleidoscopeTransitionTime = Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME;
    private float groundScrollSpeed = ScrollingBackground.DEFAULT_SCROLL_SPEED;
    private Array<PracticeCheckpoint> practiceCheckpoints = new Array<>();
    private Array<InvincibilityWindow> invincibilityWindows = new Array<>();
    private Array<WeaponsDisabledWindow> weaponsDisabledWindows = new Array<>();
    private Array<WeaponsDisabledWindow> hyperAttackDisabledWindows = new Array<>();
    private Array<WeaponsDisabledWindow> bombDisabledWindows = new Array<>();
    private boolean textCuesRequireConfirm = false;
    // The text cue freezing the schedule while awaiting confirm (at most one at a time).
    private TextCue awaitingConfirmCue;
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions;
    private final AssetManager assets;
    private final String scheduleFilePath;

    public SpawnScheduler(float worldWidth, float worldHeight, AssetManager assets, String scheduleFilePath) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = assets;
        this.totalTime = 0;
        this.enemyDefinitions = new ObjectMap<>();
        this.scheduleFilePath = scheduleFilePath;
        loadDefinitions();
        loadSchedule(scheduleFilePath);
    }

    /** Asset-relative path this schedule was loaded from (used by SpawnScheduleEditor). */
    public String getScheduleFilePath() { return scheduleFilePath; }

    /** Enemy definitions parsed from enemies.json, shared with TriggerManager. */
    public ObjectMap<String, EnemyDefinition> getEnemyDefinitions() { return enemyDefinitions; }

    private void loadDefinitions() {
        enemyDefinitions.putAll(whitelabeltest.enemy.EnemyDefinitionLoader.load());
    }

    private void loadSchedule(String scheduleFilePath) {
        Json json = new Json();
        try {
            ScheduleFile file = json.fromJson(ScheduleFile.class, Gdx.files.internal(scheduleFilePath));
            this.schedule = (file != null && file.events != null) ? file.events : new Array<>();
            if (file != null && file.textCues != null) this.textCues = file.textCues;
            if (file != null && file.soundCues != null) this.soundCues = file.soundCues;
            if (file != null && file.spriteCues != null) this.spriteCues = file.spriteCues;
            if (file != null && file.gates != null) this.gates = file.gates;
            if (file != null) this.backgroundVideoTime = file.backgroundVideoTime;
            if (file != null) this.musicFadeOutTime = file.musicFadeOutTime;
            if (file != null) this.scheduleEndTime = file.scheduleEndTime;
            if (file != null && file.kaleidoscopeTransitionTime != null) this.kaleidoscopeTransitionTime = file.kaleidoscopeTransitionTime;
            if (file != null && file.groundScrollSpeed != null) this.groundScrollSpeed = file.groundScrollSpeed;
            if (file != null && file.practiceCheckpoints != null) this.practiceCheckpoints = file.practiceCheckpoints;
            if (file != null && file.invincibilityWindows != null) this.invincibilityWindows = file.invincibilityWindows;
            if (file != null && file.weaponsDisabledWindows != null) this.weaponsDisabledWindows = file.weaponsDisabledWindows;
            if (file != null && file.hyperAttackDisabledWindows != null) this.hyperAttackDisabledWindows = file.hyperAttackDisabledWindows;
            if (file != null && file.bombDisabledWindows != null) this.bombDisabledWindows = file.bombDisabledWindows;
            if (file != null) this.textCuesRequireConfirm = file.textCuesRequireConfirm;
            schedule.sort(new Comparator<SpawnEvent>() {
                @Override
                public int compare(SpawnEvent e1, SpawnEvent e2) {
                    return Float.compare(e1.time, e2.time);
                }
            });
            soundCues.sort(new Comparator<SoundCue>() {
                @Override
                public int compare(SoundCue c1, SoundCue c2) {
                    return Float.compare(c1.time, c2.time);
                }
            });
            spriteCues.sort(new Comparator<SpriteCue>() {
                @Override
                public int compare(SpriteCue c1, SpriteCue c2) {
                    return Float.compare(c1.time, c2.time);
                }
            });
            gates.sort(new Comparator<GateCue>() {
                @Override
                public int compare(GateCue g1, GateCue g2) {
                    return Float.compare(g1.time, g2.time);
                }
            });
        } catch (SerializationException e) {
            Gdx.app.error("SpawnScheduler", "Error parsing " + scheduleFilePath, e);
            this.schedule = new Array<>();
        }
    }

    public Array<TextCue> getTextCues() { return textCues; }
    public float getRealTime() { return realTime; }
    public boolean isTextCuesRequireConfirm() { return textCuesRequireConfirm; }

    public float getTotalTime() { return totalTime; }

    /** The gate currently freezing the schedule, or null (for the debug overlay). */
    public GateCue getActiveGate() { return activeGate; }

    public Array<SpawnEvent> getSchedule() { return schedule; }

    /** Time of the first boss spawn, or -1. The boss time bonus measures the fight from here. */
    public float getBossSpawnTime() {
        for (SpawnEvent event : schedule) {
            EnemyDefinition def = enemyDefinitions.get(event.type);
            if (def != null && def.isBoss) return event.time;
        }
        return -1f;
    }

    /** Latched once the clock passes backgroundVideoTime; GameController edge-detects it. */
    public boolean isBackgroundVideoTriggered() { return backgroundVideoTriggered; }

    // Hue-cycle period when a schedule has no backgroundVideoTime (0 would flash every frame).
    private static final float DEFAULT_BACKGROUND_VIDEO_TIME = 60f;

    /** backgroundVideoTime, or a default. Used as the hue-cycle background's period so one full
     *  cycle ends as the boss video starts. */
    public float getBackgroundVideoTime() {
        return backgroundVideoTime != null ? backgroundVideoTime : DEFAULT_BACKGROUND_VIDEO_TIME;
    }

    /** Latched once the clock passes musicFadeOutTime. */
    public boolean isMusicFadeOutTriggered() { return musicFadeOutTriggered; }

    /** Latched once the clock passes scheduleEndTime (stage complete without a boss). */
    public boolean isScheduleEndTriggered() { return scheduleEndTriggered; }

    public float getKaleidoscopeTransitionTime() { return kaleidoscopeTransitionTime; }

    public float getGroundScrollSpeed() { return groundScrollSpeed; }

    /** True inside a practice checkpoint, where a hit rewinds to the checkpoint start instead of
     *  costing a life. */
    public boolean isInPracticeSection(float time) {
        return findCheckpoint(time) != null;
    }

    /** Start of the checkpoint containing `time`, or `time` itself if none. */
    public float getPracticeCheckpointStart(float time) {
        PracticeCheckpoint checkpoint = findCheckpoint(time);
        return checkpoint != null ? checkpoint.start : time;
    }

    private PracticeCheckpoint findCheckpoint(float time) {
        for (PracticeCheckpoint checkpoint : practiceCheckpoints) {
            if (time >= checkpoint.start && time < checkpoint.end) return checkpoint;
        }
        return null;
    }

    /** True inside an invincibility window: hits have no consequence, but unlike
     *  Player.isInvincible() enemies keep firing. */
    public boolean isPlayerInvincible(float time) {
        for (InvincibilityWindow window : invincibilityWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** True inside a weapons-disabled window (no firing or bombs). Only the press's effect is
     *  blocked; input is still detected for everything else (gates, focus slowdown, replays). */
    public boolean isWeaponsDisabled(float time) {
        for (WeaponsDisabledWindow window : weaponsDisabledWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** Like isWeaponsDisabled(), for Hyper Attack only. */
    public boolean isHyperAttackDisabled(float time) {
        for (WeaponsDisabledWindow window : hyperAttackDisabledWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** Like isWeaponsDisabled(), for bombs only. */
    public boolean isBombDisabled(float time) {
        for (WeaponsDisabledWindow window : bombDisabledWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** Jumps the clock to targetTime. Everything before it counts as already happened without
     *  running (skipped, not replayed), including gates and the video/music/end latches;
     *  everything after it resets. */
    public void seekTo(float targetTime, AudioManager audio) {
        totalTime = Math.max(0f, targetTime);
        if (schedule != null) {
            for (SpawnEvent event : schedule) event.spawned = event.time <= totalTime;
        }
        for (SoundCue cue : soundCues) cue.triggered = cue.time <= totalTime;
        for (SpriteCue cue : spriteCues) cue.triggered = cue.time <= totalTime;
        // Rebuild each text cue's real-time stamp for the new position: finished if its window is
        // behind the target, resumed mid-window if it straddles it, untriggered if ahead. (Stamping
        // them all "now" would pile every past cue on screen at once.)
        audio.stopTextCueLoop();
        awaitingConfirmCue = null;
        for (TextCue cue : textCues) {
            cue.typingSoundActive = false;
            if (textCuesRequireConfirm) {
                // In confirm mode the clock never sits mid-cue, so a passed cue is simply dismissed.
                cue.dismissed = cue.time <= totalTime;
                cue.triggeredAtRealTime = cue.dismissed ? realTime : -1f;
            } else if (cue.time > totalTime) {
                cue.triggeredAtRealTime = -1f;
            } else if (cue.time + cue.duration <= totalTime) {
                cue.triggeredAtRealTime = realTime - cue.duration;
            } else {
                cue.triggeredAtRealTime = realTime - (totalTime - cue.time);
                if ("typewriter".equals(cue.effect)) {
                    float revealDuration = cue.charsPerSecond > 0f ? cue.text.length() / cue.charsPerSecond : 0f;
                    if (totalTime - cue.time < revealDuration) {
                        audio.loopTextCue();
                        cue.typingSoundActive = true;
                    }
                }
            }
        }
        for (GateCue gate : gates) gate.triggered = gate.time <= totalTime;
        activeGate = null;
        gemsAtLastWaypointSpawn = -1;
        backgroundVideoTriggered = backgroundVideoTime != null && totalTime >= backgroundVideoTime;
        musicFadeOutTriggered = musicFadeOutTime != null && totalTime >= musicFadeOutTime;
        scheduleEndTriggered = scheduleEndTime != null && totalTime >= scheduleEndTime;
    }

    public void update(float delta, EntityManager entityManager, AudioManager audio, InputManager input,
                        int enemiesDestroyed, int gemsCollected, float grazePoints) {
        realTime += delta;
        for (TextCue cue : textCues) {
            if (cue.triggeredAtRealTime < 0f) {
                if (totalTime < cue.time) continue;
                cue.triggeredAtRealTime = realTime;
                if (textCuesRequireConfirm) awaitingConfirmCue = cue;
                if ("typewriter".equals(cue.effect)) {
                    audio.loopTextCue();
                    cue.typingSoundActive = true;
                } else {
                    audio.playTextCue();
                }
            } else if (cue.typingSoundActive) {
                float cueElapsedTime = realTime - cue.triggeredAtRealTime;
                float revealDuration = cue.charsPerSecond > 0f ? cue.text.length() / cue.charsPerSecond : 0f;
                if (cueElapsedTime >= revealDuration || cueElapsedTime >= cue.duration) {
                    audio.stopTextCueLoop();
                    cue.typingSoundActive = false;
                }
            }
        }

        // Confirm-gated text cue: frozen until SHOOT/RESTART. The first press during a typewriter
        // reveal completes it; the next press dismisses.
        if (awaitingConfirmCue != null) {
            if (input.isRestartJustPressed() || input.isShootJustPressed()) {
                float revealDuration = "typewriter".equals(awaitingConfirmCue.effect) && awaitingConfirmCue.charsPerSecond > 0f
                    ? awaitingConfirmCue.text.length() / awaitingConfirmCue.charsPerSecond : 0f;
                float cueElapsedTime = realTime - awaitingConfirmCue.triggeredAtRealTime;
                if (cueElapsedTime < revealDuration) {
                    awaitingConfirmCue.triggeredAtRealTime = realTime - revealDuration;
                    if (awaitingConfirmCue.typingSoundActive) {
                        audio.stopTextCueLoop();
                        awaitingConfirmCue.typingSoundActive = false;
                    }
                } else {
                    awaitingConfirmCue.dismissed = true;
                    awaitingConfirmCue = null;
                }
            } else {
                return; // frozen
            }
        }

        if (activeGate == null) {
            totalTime += delta;
            GateCue nextGate = nextUntriggeredGate();
            if (nextGate != null && totalTime >= nextGate.time) {
                // Clamp exactly at the gate (keeps replays deterministic).
                totalTime = nextGate.time;
                activeGate = nextGate;
                enemiesDestroyedAtGateStart = enemiesDestroyed;
                gemsCollectedAtGateStart = gemsAtLastWaypointSpawn >= 0 ? gemsAtLastWaypointSpawn : gemsCollected;
                gemsAtLastWaypointSpawn = -1;
                grazePointsAtGateStart = grazePoints;
            }
        }
        if (activeGate != null) {
            if (isGateSatisfied(activeGate, input, enemiesDestroyed, gemsCollected, grazePoints, entityManager.getPlayer().getCurrentWeaponId())) {
                activeGate.triggered = true;
                activeGate = null;
            } else {
                return; // frozen
            }
        }

        for (SpawnEvent event : schedule) {
            if (!event.spawned && totalTime >= event.time) {
                if (event.silence) {
                    silenceMatching(entityManager, event.type);
                } else if (event.despawn) {
                    despawnMatching(entityManager, event.type);
                } else if (event.waypointGem) {
                    spawnWaypointGem(entityManager, event);
                    gemsAtLastWaypointSpawn = gemsCollected;
                } else if (event.swapWeaponId != null) {
                    entityManager.getPlayer().setSlotWeapon(event.weaponSlot, event.swapWeaponId);
                } else {
                    spawnEnemy(entityManager, event);
                }
                event.spawned = true;
            }
        }
        for (SoundCue cue : soundCues) {
            if (!cue.triggered && totalTime >= cue.time) {
                audio.playCueSound(cue.sound);
                cue.triggered = true;
            }
        }
        for (SpriteCue cue : spriteCues) {
            if (!cue.triggered && totalTime >= cue.time) {
                spawnSpriteCue(entityManager, cue);
                cue.triggered = true;
            }
        }
        if (!backgroundVideoTriggered && backgroundVideoTime != null && totalTime >= backgroundVideoTime) {
            backgroundVideoTriggered = true;
        }
        if (!musicFadeOutTriggered && musicFadeOutTime != null && totalTime >= musicFadeOutTime) {
            musicFadeOutTriggered = true;
        }
        if (!scheduleEndTriggered && scheduleEndTime != null && totalTime >= scheduleEndTime) {
            scheduleEndTriggered = true;
        }
    }

    private GateCue nextUntriggeredGate() {
        for (GateCue gate : gates) {
            if (!gate.triggered) return gate;
        }
        return null;
    }

    private boolean isGateSatisfied(GateCue gate, InputManager input, int enemiesDestroyed, int gemsCollected, float grazePoints, String currentWeaponId) {
        if (gate.condition == null) return true;
        return switch (gate.condition) {
            case "shoot" -> input.isShootJustPressed();
            case "bomb" -> input.isBombJustPressed();
            case "weaponSwitch" -> Objects.equals(gate.weaponId, currentWeaponId);
            case "moved" -> input.isMoveJustStarted();
            case "movedLeft" -> input.isMoveLeftJustStarted();
            case "movedRight" -> input.isMoveRightJustStarted();
            case "hyperAttack" -> input.isHyperAttackJustPressed();
            case "hyperAttackReleased" -> input.isHyperAttackJustReleased();
            case "enemiesDestroyed" -> enemiesDestroyed - enemiesDestroyedAtGateStart >= gate.count;
            case "gemsCollected" -> gemsCollected - gemsCollectedAtGateStart >= gate.count;
            // Graze points (0.5 per graze), not graze events.
            case "grazed" -> grazePoints - grazePointsAtGateStart >= gate.count;
            default -> true; // unknown condition: don't soft-lock over a typo
        };
    }

    private void spawnEnemy(EntityManager entityManager, SpawnEvent event) {
        EnemySpawnOps.spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight,
            event.type, event.x, event.y, event.offsetX, event.offsetY, event.movementPattern, event.firingPattern,
            event.inverseMovement, event.powerup);
    }

    private void silenceMatching(EntityManager entityManager, String defId) {
        EnemySpawnOps.silenceMatching(entityManager, defId);
    }

    private void despawnMatching(EntityManager entityManager, String defId) {
        EnemySpawnOps.despawnMatching(entityManager, defId);
    }

    private void spawnWaypointGem(EntityManager entityManager, SpawnEvent event) {
        EnemySpawnOps.spawnWaypointGem(entityManager, assets, worldWidth, worldHeight, event.x, event.y);
    }

    private void spawnSpriteCue(EntityManager entityManager, SpriteCue cue) {
        EnemySpawnOps.spawnSpriteCue(entityManager, assets, cue.texture, cue.x, cue.y, cue.size,
            cue.columns, cue.rows, cue.frameCount, cue.frameDuration);
    }

    public void reset() {
        totalTime = 0;
        realTime = 0;
        if (schedule != null) {
            for (SpawnEvent event : schedule) event.spawned = false;
        }
        for (TextCue cue : textCues) {
            cue.triggeredAtRealTime = -1f;
            cue.typingSoundActive = false;
            cue.dismissed = false;
        }
        for (SoundCue cue : soundCues) cue.triggered = false;
        for (SpriteCue cue : spriteCues) cue.triggered = false;
        for (GateCue gate : gates) gate.triggered = false;
        activeGate = null;
        awaitingConfirmCue = null;
        gemsAtLastWaypointSpawn = -1;
        backgroundVideoTriggered = false;
        musicFadeOutTriggered = false;
        scheduleEndTriggered = false;
    }
}
