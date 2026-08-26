package whitelabeltest.gamemanagers.spawning;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.effects.PointGem;
import whitelabeltest.gamemanagers.background.Stage2KaleidoscopeShader;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.gamemanagers.TextCue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.GenericEnemy;

import java.util.Comparator;
import java.util.Objects;

public class SpawnScheduler {
    public static class SpawnEvent {
        public float time;
        public String type;
        public float x = Float.NaN;
        public float y = Float.NaN;
        // Guaranteed weapon-powerup tier (1-3) this spawn drops on death - see
        // Enemy.setGuaranteedPowerup()/GameController.spawnPowerup(). Null means no guarantee.
        public Integer powerup;
        public boolean inverseMovement = false;
        public boolean spawned = false;

        // This spawn's slot in a squad formation - see PatternFactory.createMovement's javadoc.
        // NaN (the default) means "not a formation member", so a Squadron-type movement pattern
        // falls back to whatever offsetX/offsetY it has baked in.
        public float offsetX = Float.NaN;
        public float offsetY = Float.NaN;

        // Overrides the enemy definition's own movementPattern when set - lets several spawn
        // events share one enemy definition while steering each toward a different movement
        // pattern (e.g. two waves of the same squad with different rally points/exits).
        public String movementPattern;

        // Same idea as movementPattern above, but for firingPattern - e.g. several WallFiring
        // spawns sharing one enemy definition while each cuts its hole at a different gapCenterX.
        public String firingPattern;

        // When true, this "event" doesn't spawn anything - at its scheduled time it instead calls
        // Enemy.silenceFiring() on every currently active enemy whose EnemyDefinition id matches
        // `type`. Lets a scripted enemy whose firing pattern has no fixed duration of its own (e.g.
        // TutorialStreamShot, which must fire continuously for as long as its drill takes - see
        // SpawnScheduler's gate-freeze doc on GateCue) still be told to stop the instant the drill
        // actually finishes, by scheduling this a hair after the gate that completion clears -
        // since the schedule clock stays frozen at that gate's time for however long the drill
        // really takes, this event only fires once time resumes past it, however long that is.
        public boolean silence = false;

        // When true, this "event" spawns a stationary PointGem at (x, y) instead of an enemy - see
        // spawnWaypointGem(). Reuses PointGem's existing graze-hitbox collection and the
        // "gemsCollected" gate condition to let a drill be scored as "fly through this series of
        // points" (e.g. the bullet-restreaming drill) without a bespoke waypoint/scoring system.
        public boolean waypointGem = false;

        // When true, this "event" doesn't spawn anything - at its scheduled time it instead
        // silently removes (no death animation, no score, no drops) every currently active enemy
        // whose EnemyDefinition id matches `type` - see despawnMatching(). Lets a scripted demo
        // target (e.g. the weapons section's practice dummy) be cleaned up on a schedule instead of
        // either lingering on-screen for the rest of the stage or requiring the player to actually
        // kill it - the latter is its own race condition (see gemsAtLastWaypointSpawn's doc for the
        // same class of bug): if the player deals enough damage during earlier, ungated practice
        // fire that the dummy dies before an "enemiesDestroyed" gate even engages, that gate's
        // baseline snapshot already includes the kill and can never see the "+1 more" it's waiting
        // on.
        public boolean despawn = false;

        // When set, this "event" doesn't spawn anything - at its scheduled time it instead calls
        // Player.setSlotWeapon(weaponSlot, swapWeaponId), swapping that slot to a different weapon
        // (ids match Player.weaponById(): "BasicWeapon", "WaveBlastWeapon", "OrbitWeapon",
        // "Thunderbolt"). Lets a scripted stage (e.g. the tutorial) hand the player a new weapon
        // mid-run without them picking up a powerup for it - if the replaced slot happens to be the
        // active one, the new weapon becomes active in its place immediately (see setSlotWeapon()),
        // otherwise it just waits in that slot until they switch to it. Null (the default) means
        // this isn't a weapon-swap event.
        public String swapWeaponId = null;
        public int weaponSlot = 0;

        public SpawnEvent() {}
    }

    // A scripted one-off sound effect - lets a level trigger SFX (alarms, environmental stingers,
    // dialogue blips, etc.) purely from spawn_schedule.json, the same way SpawnEvent triggers
    // enemies. "sound" is an asset path relative to assets/ (e.g. "audio/sfx/alarm.mp3"), lazily
    // loaded and cached the first time it's played - see AudioManager.playCueSound().
    public static class SoundCue {
        public float time;
        public String sound;
        public boolean triggered = false;

        public SoundCue() {}
    }

    // A scripted one-off sprite/animation played at a fixed world position - see
    // ScheduledSpriteEffect. frameCount/columns/rows/frameDuration describe the sprite sheet the
    // same way EnemyDefinition's animations do; frameCount == 1 (the default) plays a single
    // static image for frameDuration seconds instead of animating.
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

    // A condition-gated checkpoint - see SpawnScheduler.update()/isGateSatisfied(). Once the
    // schedule clock reaches this gate's time, it freezes there (nothing else in the schedule -
    // spawns, text/sound/sprite cues, the background-video/music-fade cues - advances or fires)
    // until condition is satisfied, then resumes counting from exactly gate.time. Lets a tutorial
    // stage pace itself on "do X to continue" instead of a fixed clock. Live gameplay (movement,
    // collisions, entities already on screen) is NOT paused by this - only the schedule is.
    public static class GateCue {
        public float time;
        // "shoot" (shoot input freshly pressed), "bomb" (bomb just used), "weaponSwitch" (the
        // correct weapon - see weaponId below - is the one currently equipped), "moved" (movement
        // freshly started), "movedLeft"/"movedRight" (movement freshly started specifically in that
        // X direction - e.g. a left/right/left micro-dodging drill chains three of these), or
        // "enemiesDestroyed" (see count) - see isGateSatisfied(). Every condition but weaponSwitch is
        // edge-triggered (not "currently held") so it can't be trivially satisfied by input the
        // player was already holding from before the gate engaged - see InputManager's
        // ...JustPressed()/isMoveJustStarted(). weaponSwitch is deliberately a level check instead
        // (see weaponId) - it cares whether the right weapon ends up equipped, not whether a switch
        // input happened to fire, so it clears immediately if that weapon was already equipped
        // (nothing to switch away from and back to) and stays satisfied if the player keeps
        // switching around after clearing it. An unrecognized/null condition is treated as already
        // satisfied, so a typo here can't soft-lock the stage.
        public String condition;
        // Only used when condition == "enemiesDestroyed": how many enemies the player must destroy
        // after this gate engages (not a running stage total) to clear it.
        public int count = 1;
        // Only used when condition == "weaponSwitch": which weapon (id string - see
        // Player.getCurrentWeaponId()/setSlotWeapon(), e.g. "BasicWeapon", "Thunderbolt",
        // "OrbitWeapon", "WaveBlastWeapon") must be the currently equipped one to clear this gate.
        public String weaponId;
        public boolean triggered = false;

        public GateCue() {}
    }

    private static class ScheduleFile {
        public Array<TextCue> textCues;
        public Array<SpawnEvent> events;
        public Array<SoundCue> soundCues;
        public Array<SpriteCue> spriteCues;
        public Array<GateCue> gates;
        // Optional cue time (seconds) for handing the scrolling background off to the boss video -
        // see ScrollingBackground.triggerBossVideo(). Null means no schedule-driven trigger.
        public Float backgroundVideoTime;
        // Optional cue time (seconds) for fading out the stage music - see
        // AudioManager.fadeOutStageMusic(). Null means no schedule-driven trigger. Independent of
        // backgroundVideoTime so the two can be timed apart (e.g. music fading ahead of/behind the
        // video hand-off).
        public Float musicFadeOutTime;
        // Optional cue time (seconds) marking the whole schedule as finished - see
        // isScheduleEndTriggered(). Null means no schedule-driven end (the normal case: an arcade
        // stage's completion is instead driven by GameController's boss-kill levelComplete flow).
        // Exists for a schedule that has no boss to kill at all (e.g. the tutorial) but still needs
        // a defined ending - GameController surfaces this so its caller can decide what "done" means
        // (the tutorial's case: hand control back to the start screen).
        public Float scheduleEndTime;
        // Optional seconds of u_time this stage's Stage2KaleidoscopeShader background plays the
        // phosphene kaleidoscope effect before switching to the tentacles tunnel - see
        // ScrollingBackground.setKaleidoscopeTransitionTime()/getKaleidoscopeTransitionTime()
        // below. Null (the default, and the only sensible value for a stage that isn't using that
        // shader background) falls back to Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME.
        public Float kaleidoscopeTransitionTime;
        // Optional world-units/sec the background scrolls (negative = downward, matching the
        // direction enemies move toward the player) - see ScrollingBackground's own per-layer
        // scrollSpeed for the parallax visuals, and getGroundScrollSpeed()/EnemyDefinition.isGround
        // for what this drives: a ground enemy is shifted by this same amount every frame on top of
        // its own movement pattern, so it stays visually planted on the terrain instead of sliding
        // relative to it as the world scrolls past. Null (the default) falls back to
        // ScrollingBackground.DEFAULT_SCROLL_SPEED, matching that class's own default layer speed.
        public Float groundScrollSpeed;
        // Zero or more [start, end) schedule-time windows - see isInPracticeSection(). A schedule
        // can have several independent drills (e.g. a movement dodge, then later a stand-still
        // dodge), each with its own restart-on-hit range.
        public Array<PracticeCheckpoint> practiceCheckpoints;
        // Zero or more [start, end) schedule-time windows - see isPlayerInvincible(). Deliberately
        // separate from Player.isInvincible() (the post-hit i-frame flag): that flag also tells
        // EntityManager to pause every enemy's firing (see its firingPaused computation), which
        // would silence the very enemy fire a scripted "safe to stand in bullets" drill needs kept
        // alive. This only suppresses the hit's consequence in GameController, nothing else.
        public Array<InvincibilityWindow> invincibilityWindows;

        // Zero or more [start, end) schedule-time windows - see isWeaponsDisabled(). Lets a stage
        // withhold both firing and bombing (e.g. the tutorial, before it's actually taught the
        // player how to shoot) without touching input handling itself - see GameController's use
        // of it, which only gates whether a press takes effect, not whether it's detected.
        public Array<WeaponsDisabledWindow> weaponsDisabledWindows;

        // Same [start, end) shape and gating style as weaponsDisabledWindows, but independent of it
        // - a tutorial teaches normal fire, Hyper Attack, and bombing at three different points, so
        // each capability needs its own "not taught yet" window rather than sharing one flag that
        // would either withhold fire too long or let Hyper Attack/bomb through too early. See
        // isHyperAttackDisabled()/isBombDisabled().
        public Array<WeaponsDisabledWindow> hyperAttackDisabledWindows;
        public Array<WeaponsDisabledWindow> bombDisabledWindows;

        // When true, every text cue in this schedule freezes the schedule clock the instant it
        // triggers - same "nothing else advances or fires" freeze as an active GateCue - until the
        // player presses confirm (see update()'s cue-await-confirm block), instead of auto-hiding
        // after its own `duration`. Lets a schedule with lots of reading (e.g. the tutorial)
        // guarantee every message actually gets read instead of racing a fixed timer against
        // whatever's simultaneously happening on screen. False (the default) keeps every existing
        // schedule's original fixed-duration cue behavior unchanged.
        public boolean textCuesRequireConfirm = false;

        public ScheduleFile() {}
    }

    // A [start, end) schedule-time window during which a hit restarts the drill instead of costing
    // a life - see isInPracticeSection()/GameController.restartPracticeSection().
    public static class PracticeCheckpoint {
        public float start;
        public float end;

        public PracticeCheckpoint() {}
    }

    // A [start, end) schedule-time window during which the player takes no damage at all - see
    // isPlayerInvincible().
    public static class InvincibilityWindow {
        public float start;
        public float end;

        public InvincibilityWindow() {}
    }

    // A [start, end) schedule-time window during which the player can't fire their weapon or use a
    // bomb - see isWeaponsDisabled().
    public static class WeaponsDisabledWindow {
        public float start;
        public float end;

        public WeaponsDisabledWindow() {}
    }

    private float totalTime;
    // Real elapsed time - unlike totalTime, never freezes at a gate. Exists purely to time text
    // cues' own display duration against (see TextCue.triggeredAtRealTime) so a gate stalling
    // totalTime doesn't also stall a cue's typewriter reveal partway through.
    private float realTime;
    private final float worldWidth;
    private final float worldHeight;
    private Array<SpawnEvent> schedule;
    private Array<TextCue> textCues = new Array<>();
    private Array<SoundCue> soundCues = new Array<>();
    private Array<SpriteCue> spriteCues = new Array<>();
    private Array<GateCue> gates = new Array<>();
    // The one gate currently blocking the schedule clock, or null if none is - see update()/
    // isGateSatisfied(). Only ever the earliest untriggered entry in gates (they clear in order).
    private GateCue activeGate;
    // ScoreManager.getEnemiesDestroyed()/getGemsCollected()/Player.getGrazePoints() snapshotted at
    // the moment activeGate engaged - see isGateSatisfied(), which counts each since then, not the
    // stage's running total.
    private int enemiesDestroyedAtGateStart;
    private int gemsCollectedAtGateStart;
    private float grazePointsAtGateStart;
    // gemsCollected as of the most recent waypointGem SpawnEvent (see spawnWaypointGem()), or -1 if
    // none has fired since the last gate consumed it - see update()'s gate-engagement snapshot,
    // which prefers this over the live gemsCollected value when set. A "gemsCollected count: 1"
    // gate immediately following a waypointGem spawn (the tutorial's bullet-restreaming drill
    // chains several of these) would otherwise snapshot its baseline at ENGAGEMENT time - if the
    // player grabs the just-spawned gem in the gap between it spawning and the gate reaching that
    // point in the schedule (trivial for a stationary pickup the player might already be standing
    // on), the baseline captured at engagement already includes that collection, so the "+1 more"
    // requirement can never be satisfied and the chain softlocks. Snapshotting instead at spawn
    // time - necessarily before the gem could possibly be collected - closes that race regardless
    // of how fast the player grabs it.
    private int gemsAtLastWaypointSpawn = -1;
    private Float backgroundVideoTime;
    private boolean backgroundVideoTriggered;
    private Float musicFadeOutTime;
    private boolean musicFadeOutTriggered;
    private Float scheduleEndTime;
    private boolean scheduleEndTriggered;
    // See ScheduleFile.kaleidoscopeTransitionTime.
    private float kaleidoscopeTransitionTime = Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME;
    // See ScheduleFile.groundScrollSpeed.
    private float groundScrollSpeed = ScrollingBackground.DEFAULT_SCROLL_SPEED;
    // See isInPracticeSection() - lets a scripted section (e.g. a tutorial dodge drill) tell
    // GameController "a death in here doesn't cost a life, just rewind to the start of this
    // window" instead of the normal hit-handling.
    private Array<PracticeCheckpoint> practiceCheckpoints = new Array<>();
    // See isPlayerInvincible() - lets a scripted section (e.g. "safely stand in this stream of
    // enemy fire") tell GameController to skip the normal hit-consequence entirely, without
    // touching Player.isInvincible()/EntityManager's firingPaused (see InvincibilityWindow's doc).
    private Array<InvincibilityWindow> invincibilityWindows = new Array<>();
    // See isWeaponsDisabled().
    private Array<WeaponsDisabledWindow> weaponsDisabledWindows = new Array<>();
    // See isHyperAttackDisabled()/isBombDisabled().
    private Array<WeaponsDisabledWindow> hyperAttackDisabledWindows = new Array<>();
    private Array<WeaponsDisabledWindow> bombDisabledWindows = new Array<>();
    // See ScheduleFile.textCuesRequireConfirm.
    private boolean textCuesRequireConfirm = false;
    // The cue currently freezing the schedule clock while textCuesRequireConfirm is on, waiting on
    // a confirm press - see update(). Only one at a time: totalTime can't reach a second cue's
    // trigger time while frozen at the first's.
    private TextCue awaitingConfirmCue;
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions;
    private final AssetManager assets;

    public SpawnScheduler(float worldWidth, float worldHeight, AssetManager assets, String scheduleFilePath) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = assets;
        this.totalTime = 0;
        this.enemyDefinitions = new ObjectMap<>();
        loadDefinitions();
        loadSchedule(scheduleFilePath);
    }

    private void loadDefinitions() {
        Json json = new Json();
        @SuppressWarnings("unchecked")
        Array<EnemyDefinition> defs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("data/enemies.json"));
        for (EnemyDefinition def : defs) {
            enemyDefinitions.put(def.id, def);
        }
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

    /** The gate currently freezing the schedule, or null if none is - see update(). Exposed for UI
     *  (e.g. a debug overlay showing what the stage is waiting on). */
    public GateCue getActiveGate() { return activeGate; }

    public Array<SpawnEvent> getSchedule() { return schedule; }

    /** Scheduled spawn time of the stage's boss (the first SpawnEvent whose EnemyDefinition sets
     *  isBoss), or -1 if the schedule has no boss - see GameController's boss-takedown time bonus,
     *  which measures the fight against this rather than the whole stage's elapsed time. */
    public float getBossSpawnTime() {
        for (SpawnEvent event : schedule) {
            EnemyDefinition def = enemyDefinitions.get(event.type);
            if (def != null && def.isBoss) return event.time;
        }
        return -1f;
    }

    /** True once the schedule clock has crossed backgroundVideoTime - a permanent latch (only
     *  cleared by reset()/seekTo()) that GameController edge-detects to trigger the boss video
     *  hand-off exactly once - see GameController.update(). */
    public boolean isBackgroundVideoTriggered() { return backgroundVideoTriggered; }

    // Fallback for getBackgroundVideoTime() when a schedule has no backgroundVideoTime at all (most
    // don't, and hueCycleBackground - the only current consumer - is meaningless without a boss
    // video to sync against anyway) - an arbitrary but reasonable cycle length rather than 0, which
    // would make every frame flash back to the image's native colors.
    private static final float DEFAULT_BACKGROUND_VIDEO_TIME = 60f;

    /** Seconds until this schedule's boss video cue (see isBackgroundVideoTriggered()) fires, or
     *  DEFAULT_BACKGROUND_VIDEO_TIME if this schedule has none. Currently only consumed by
     *  ScrollingBackground.setHueCyclePeriod() - see StageDefinition.hueCycleBackground - so a hue
     *  cycle always completes its one full rotation exactly as the boss video cuts in, without
     *  needing its own separately-authored duration that could drift out of sync with the actual cue. */
    public float getBackgroundVideoTime() {
        return backgroundVideoTime != null ? backgroundVideoTime : DEFAULT_BACKGROUND_VIDEO_TIME;
    }

    /** True once the schedule clock has crossed musicFadeOutTime - same permanent-latch pattern as
     *  isBackgroundVideoTriggered(), edge-detected by GameController to fade out the stage music
     *  exactly once - see GameController.update(). */
    public boolean isMusicFadeOutTriggered() { return musicFadeOutTriggered; }

    /** True once the schedule clock has crossed scheduleEndTime - same permanent-latch pattern as
     *  isBackgroundVideoTriggered(), but for a schedule with no boss to drive GameController's usual
     *  levelComplete flow (e.g. the tutorial). Always false when the schedule doesn't set
     *  scheduleEndTime, so this is a no-op for every ordinary arcade stage. */
    public boolean isScheduleEndTriggered() { return scheduleEndTriggered; }

    /** See ScheduleFile.kaleidoscopeTransitionTime - Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME
     *  unless this stage's own schedule overrides it. Meaningless (and unread) for a stage whose
     *  shaderBackground isn't "kaleidoscope". */
    public float getKaleidoscopeTransitionTime() { return kaleidoscopeTransitionTime; }

    /** See ScheduleFile.groundScrollSpeed - ScrollingBackground.DEFAULT_SCROLL_SPEED unless this
     *  stage's own schedule overrides it. Meaningless (and unread) for a stage with no ground
     *  enemies (EnemyDefinition.isGround) in its schedule. */
    public float getGroundScrollSpeed() { return groundScrollSpeed; }

    /** True while the schedule clock sits inside any [start, end) practice checkpoint - see
     *  GameController.applyPlayerHit(), which checks this before applying the normal
     *  life-loss/game-over consequences of a hit: inside a window, a hit instead rewinds the
     *  schedule back to that window's start (via seekTo()) and costs nothing, so a scripted drill
     *  (e.g. "dodge these bullet walls") can be retried freely instead of eating into the player's
     *  real run. */
    public boolean isInPracticeSection(float time) {
        return findCheckpoint(time) != null;
    }

    /** Where a hit at time (while isInPracticeSection(time)) rewinds the schedule back to - returns
     *  time itself if no checkpoint currently contains it. */
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

    /** True while the schedule clock sits inside any [start, end) invincibility window - see
     *  GameController.update(), which skips a hit's normal consequence entirely while this is true
     *  (life loss, practice-section restart, everything) - unlike Player.isInvincible(), enemies
     *  keep firing normally throughout, so a scripted "safely stand in this stream of enemy fire"
     *  drill can keep the fire flowing while it teaches. */
    public boolean isPlayerInvincible(float time) {
        for (InvincibilityWindow window : invincibilityWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** True while the schedule clock sits inside any [start, end) weapons-disabled window - see
     *  GameController.update(), which uses this to withhold both firing and bomb use before the
     *  player's actually been taught to. Only gates whether a press takes effect, not whether it's
     *  detected - InputManager's isShooting()/isBombJustPressed() keep working normally for
     *  anything else that reads them (movement's focus-fire slowdown, the orbit ring, point-gem
     *  homing suppression, replay recording, this same schedule's own "shoot"/"bomb" gate
     *  conditions), so none of those are affected by this window. */
    public boolean isWeaponsDisabled(float time) {
        for (WeaponsDisabledWindow window : weaponsDisabledWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** True while the schedule clock sits inside any [start, end) Hyper-Attack-disabled window -
     *  see GameController.update()/Player.update(), which use this to withhold the Hyper Attack
     *  input before the player's actually been taught it, independent of isWeaponsDisabled() (a
     *  tutorial teaches normal fire well before Hyper Attack). Same "gates the press, not the
     *  detection" rule as isWeaponsDisabled(). */
    public boolean isHyperAttackDisabled(float time) {
        for (WeaponsDisabledWindow window : hyperAttackDisabledWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** True while the schedule clock sits inside any [start, end) bomb-disabled window - see
     *  GameController.update(), which uses this to withhold bomb use before the player's actually
     *  been taught it, independent of isWeaponsDisabled() (a tutorial teaches normal fire and Hyper
     *  Attack well before bombing). Same "gates the press, not the detection" rule as
     *  isWeaponsDisabled(). */
    public boolean isBombDisabled(float time) {
        for (WeaponsDisabledWindow window : bombDisabledWindows) {
            if (time >= window.start && time < window.end) return true;
        }
        return false;
    }

    /** Debug-only: jumps the schedule clock to targetTime, marking every event on the far side of
     *  it as (un)spawned so the normal update() loop picks back up correctly from there - forward
     *  seeks skip past events without spawning them, rewinds let already-passed events fire again.
     *  The background-video and music-fade cues follow the same rule: jumping past either marks it
     *  as already fired without actually triggering it, consistent with spawn events being skipped
     *  rather than replayed. */
    public void seekTo(float targetTime, AudioManager audio) {
        totalTime = Math.max(0f, targetTime);
        if (schedule != null) {
            for (SpawnEvent event : schedule) event.spawned = event.time <= totalTime;
        }
        for (SoundCue cue : soundCues) cue.triggered = cue.time <= totalTime;
        for (SpriteCue cue : spriteCues) cue.triggered = cue.time <= totalTime;
        // Reconstructs each cue's real-time trigger stamp for the new position, rather than just
        // stamping every past cue with "right now" - that would make every cue before the seek
        // target (there can be dozens, e.g. a practice checkpoint rewinding after a hit) all become
        // simultaneously "just triggered" and pile up on screen together. Three cases: a cue whose
        // window is entirely behind the target is marked already-finished (not just "triggered", or
        // it would render one more time); one whose window straddles the target resumes from the
        // same relative point within it; one still ahead of the target goes back to untriggered.
        // Stopped unconditionally first (at most one cue is ever actively looping at a time) so a
        // seek landing anywhere else doesn't leave it playing with nothing left tracking it; resumed
        // below for whichever cue's reveal the new position actually falls inside of.
        audio.stopTextCueLoop();
        awaitingConfirmCue = null;
        for (TextCue cue : textCues) {
            cue.typingSoundActive = false;
            if (textCuesRequireConfirm) {
                // A confirm-gated schedule can never have its clock sitting strictly between a
                // cue's trigger time and its resolution in real gameplay - it's frozen exactly at
                // the trigger until dismissed - so there's no real "mid-window" case to reconstruct
                // here the way the duration-timed branch below does. Simpler and, more importantly,
                // never leaves the schedule frozen after a debug/practice-rewind seek lands: past
                // the cue's time means it must already have been read and dismissed to get here.
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
        // Same "skip rather than replay" rule as every other cue type - a seek past a gate marks it
        // triggered without requiring its condition, and never leaves the schedule frozen.
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

        // Freezes the schedule clock exactly like an active GateCue below, until the player
        // confirms past the message that just triggered above - see ScheduleFile.textCuesRequireConfirm.
        // Checked ahead of the gate block since totalTime can never reach a gate's own trigger time
        // while stuck here, so the two can't contend over the same frame's input.
        if (awaitingConfirmCue != null) {
            if (input.isRestartJustPressed() || input.isShootJustPressed()) {
                float revealDuration = "typewriter".equals(awaitingConfirmCue.effect) && awaitingConfirmCue.charsPerSecond > 0f
                    ? awaitingConfirmCue.text.length() / awaitingConfirmCue.charsPerSecond : 0f;
                float cueElapsedTime = realTime - awaitingConfirmCue.triggeredAtRealTime;
                if (cueElapsedTime < revealDuration) {
                    // Still typing - this press force-completes the reveal instead of dismissing,
                    // so the player never has to wait out a slow typewriter once they've already
                    // asked to move on. Rewinding triggeredAtRealTime (rather than a separate
                    // "forced" flag) reuses the same elapsed-time math everywhere else already reads
                    // to decide the reveal is done. Stays frozen - the next press dismisses for real.
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
                return; // frozen - nothing below this point advances or fires this frame
            }
        }

        if (activeGate == null) {
            totalTime += delta;
            GateCue nextGate = nextUntriggeredGate();
            if (nextGate != null && totalTime >= nextGate.time) {
                // Clamp exactly at the gate rather than overshooting into whatever time delta
                // happened to land on - keeps "how far past the gate are we" well-defined once it
                // clears, and keeps this deterministic for replay purposes (see ReplayData).
                totalTime = nextGate.time;
                activeGate = nextGate;
                enemiesDestroyedAtGateStart = enemiesDestroyed;
                // Prefer the count from the most recent waypointGem spawn (necessarily taken before
                // that gem could be collected) over the live value here, which may already include
                // it if the player was fast enough to grab it before this gate got here - see
                // gemsAtLastWaypointSpawn's doc.
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
                return; // frozen - nothing below this point advances or fires this frame
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
                    // Taken now, before this gem exists to be collected - see
                    // gemsAtLastWaypointSpawn's doc for why this beats snapshotting at gate time.
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
            // A level check, not edge-triggered like the rest of these - see GateCue.weaponId's
            // doc for why. gate.weaponId == null (an unauthored gate) falls through to
            // currentWeaponId's own null-equality, which is only ever true if the player has no
            // weapon equipped at all (mid-death-wipe) - effectively never satisfied by accident.
            case "weaponSwitch" -> Objects.equals(gate.weaponId, currentWeaponId);
            case "moved" -> input.isMoveJustStarted();
            case "movedLeft" -> input.isMoveLeftJustStarted();
            case "movedRight" -> input.isMoveRightJustStarted();
            // Player.handleHyperAttack() fires on isHyperAttackJustPressed() regardless of which
            // weapon is equipped - Basic's Hyper Attack is instant (dash out/back), Thunderbolt's
            // starts a charge (see updateThunderboltCharge()) that "hyperAttackReleased" below
            // separately confirms was actually released/detonated.
            case "hyperAttack" -> input.isHyperAttackJustPressed();
            case "hyperAttackReleased" -> input.isHyperAttackJustReleased();
            case "enemiesDestroyed" -> enemiesDestroyed - enemiesDestroyedAtGateStart >= gate.count;
            case "gemsCollected" -> gemsCollected - gemsCollectedAtGateStart >= gate.count;
            // grazePoints increments by 0.5 per graze (see GameController.update()'s
            // checkGrazeCollisions branch), so gate.count here is a graze-POINT total, not a raw
            // graze-event count (count: 3 needs 6 individual grazes).
            case "grazed" -> grazePoints - grazePointsAtGateStart >= gate.count;
            default -> true; // unrecognized condition string - don't soft-lock the stage over a typo
        };
    }

    private void spawnEnemy(EntityManager entityManager, SpawnEvent event) {
        EnemyDefinition def = enemyDefinitions.get(event.type);
        if (def == null) return;

        Texture tex = assets.getTexture(def.texture);
        Texture bulletTex = assets.getTexture(def.bulletTexture);
        Texture spawnTex = def.spawnTexture != null ? assets.getTexture(def.spawnTexture) : null;
        Texture deathTex = def.deathTexture != null ? assets.getTexture(def.deathTexture) : null;

        GenericEnemy enemy = ObjectPools.genericEnemyPool.obtain();

        def.inverseMovement = event.inverseMovement;

        enemy.initWithDefinition(def, tex, bulletTex, spawnTex, deathTex, worldWidth, worldHeight, event.x, event.y, event.offsetX, event.offsetY, event.movementPattern, event.firingPattern);

        if (event.powerup != null) enemy.setGuaranteedPowerup(event.powerup);
        entityManager.getEnemies().add(enemy);
    }

    /** See SpawnEvent.silence - stops every currently active enemy whose EnemyDefinition id
     *  matches defId from firing any further, without otherwise touching it (still on-screen,
     *  still alive, just quiet). */
    private void silenceMatching(EntityManager entityManager, String defId) {
        for (Enemy enemy : entityManager.getEnemies()) {
            if (enemy.isActive() && defId.equals(enemy.getDefinitionId())) enemy.silenceFiring();
        }
    }

    /** See SpawnEvent.despawn - silently removes every currently active enemy whose
     *  EnemyDefinition id matches defId, with no death animation/score/drops (unlike actually
     *  killing it via Enemy.takeDamage(), which GameController.destroyEnemy would treat as a real
     *  kill for scoring/gem-drop/gate-counting purposes - this is scripted cleanup, not a kill). */
    private void despawnMatching(EntityManager entityManager, String defId) {
        Array<Enemy> enemies = entityManager.getEnemies();
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (enemy.isActive() && defId.equals(enemy.getDefinitionId())) {
                ObjectPools.freeEnemy(enemy);
                enemies.removeIndex(i);
            }
        }
    }

    /** See SpawnEvent.waypointGem - drops a stationary PointGem at (event.x, event.y), same
     *  animation as a gem an enemy would drop, but with none of that gem's pop/gravity/homing (see
     *  PointGem.init's stationary overload), so it just waits in place until the player flies into
     *  it. */
    private void spawnWaypointGem(EntityManager entityManager, SpawnEvent event) {
        Animation<TextureRegion> gemAnimation =
            AnimationCache.get(assets.pointGemTexture, 6, 4, 24, 0.05f, Animation.PlayMode.LOOP);
        PointGem gem = ObjectPools.pointGemPool.obtain();
        gem.init(gemAnimation, event.x, event.y, worldWidth, worldHeight, true);
        entityManager.getPointGems().add(gem);
    }

    private void spawnSpriteCue(EntityManager entityManager, SpriteCue cue) {
        Texture texture = assets.ensureTexture(cue.texture);
        if (texture == null) return;

        Animation<TextureRegion> animation =
            AnimationCache.get(texture, cue.columns, cue.rows, cue.frameCount, cue.frameDuration, Animation.PlayMode.NORMAL);

        // cue.size sets the draw height; width is derived from the sheet's per-frame aspect ratio
        // so non-square art (e.g. a wide banner like WarningSign.png) isn't squashed into a square.
        float frameAspect = (texture.getWidth() / (float) cue.columns) / (texture.getHeight() / (float) cue.rows);
        float height = cue.size;
        float width = height * frameAspect;
        entityManager.spawnScheduledSprite(animation, cue.x, cue.y, width, height);
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
