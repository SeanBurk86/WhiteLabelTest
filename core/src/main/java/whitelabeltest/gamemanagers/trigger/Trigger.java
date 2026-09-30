package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.HealthPhase;
import whitelabeltest.gamemanagers.TextCue;

/** One entry in a stage's trigger file. It arms when the LevelCamera reaches `distance`, fires once
 *  its `conditions` are met, and performs whichever action its fields describe (action fields are
 *  meant to be mutually exclusive; see TriggerManager.fire() for precedence). See the README's
 *  "Trigger system" section for the full lifecycle. */
public class Trigger {
    public float distance;

    // Optional unique name. On a spawn trigger it tags every enemy spawned, so a "spawnDestroyed"
    // condition can wait on them.
    public String id;

    // Extra conditions checked after arming; null/empty fires immediately.
    public Array<Condition> conditions;
    // "ALL" (default) or "ANY".
    public String conditionMode = "ALL";
    // Runtime-only: reached `distance` and condition baselines were taken.
    public boolean armed = false;

    // True: once armed, freezes the camera until this trigger resolves (conditions met and, if
    // requireConfirm, confirmed).
    public boolean gate = false;
    // True: after firing, waits for a SHOOT/RESTART press before counting as resolved. Firing itself
    // is never delayed, so a text cue shows immediately and stays up until confirmed.
    public boolean requireConfirm = false;
    // Runtime-only: requireConfirm's press has been received.
    public boolean confirmed = false;
    // Runtime-only: the action has run (distinct from `fired`, which also requires confirmation), so
    // the action isn't repeated while waiting for confirm.
    public boolean actionFired = false;
    // Runtime-only: the TextCue this trigger showed, if any, so confirm can finish its typewriter
    // reveal before dismissing it.
    public TextCue liveTextCue;
    // True: marks the stage complete when this fires (for stages with no boss, e.g. the tutorial).
    public boolean scheduleEnd = false;

    // Practice-checkpoint retry variants: after a hit rewinds the player to a checkpoint's start,
    // firstAttemptOnly triggers inside that checkpoint are skipped and retryOnly ones play instead.
    // retryOnly triggers are skipped on the first pass. See TriggerManager.seekToPracticeRetry().
    public boolean firstAttemptOnly = false;
    public boolean retryOnly = false;

    // --- Enemy spawn / despawn / silence / waypoint gem / weapon swap ---
    // x/y also position a sprite cue.
    public String type;
    public float x = Float.NaN;
    public float y = Float.NaN;
    public Integer powerup;
    public boolean inverseMovement = false;
    public float offsetX = Float.NaN;
    public float offsetY = Float.NaN;
    public String movementPattern;
    public String firingPattern;
    // Distance units EARLY to arm, so the trigger can be placed where it should engage while the
    // enemy is already present (or has already arrived) by then. 0 = arm exactly at `distance`.
    public float spawnLead = 0f;
    // True: enter from above the screen and arrive at (x, y) as the camera reaches `distance`.
    // Needs a nonzero spawnLead to have travel time. See EnemyEntranceMovement.
    public boolean enterFromAbove = false;
    // Optional health-triggered movement/firing swaps; see HealthPhase and BaseEnemy.takeDamage().
    public Array<HealthPhase> healthPhases;
    public boolean silence = false;
    public boolean despawn = false;
    public boolean waypointGem = false;
    public String swapWeaponId = null;
    public int weaponSlot = 0;

    // --- Wave: spawns a whole formation of `type` instead of one enemy (see WaveSpawnPlanner and
    // TriggerManager.fireWave()). null = no wave. While set, x/y is the formation's anchor and
    // offsetX/offsetY are ignored. An authored movementPattern is reused by every member; without
    // one, members fly straight at waveSpeed in their waveOrientation direction.
    public String waveShape; // "point", "circle", "plane", "triangle"
    // "in front", "to the center", "to the player", "to the exterior"
    public String waveOrientation = "in front";
    // point, circle
    public int waveNumberOfSpawns = 4;
    // circle, plane, triangle (world units; the play area is ~9x12)
    public float waveWidth = 3f;
    public float waveHeight = 3f;
    // circle
    public float waveStartAngle = 0f;
    public float waveEndAngle = 360f;
    public float waveCircleOffset = 0f;
    // plane: waveLines across the width, waveColumns rows down the height.
    // triangle: waveColumns is the back row's count.
    public int waveLines = 1;
    public int waveColumns = 1;
    // Real seconds (not distance) after firing before the first / between each member spawn.
    public float waveStartDelay = 0f;
    public float waveSpawnInterval = 0f;
    // True: members hold the formation's rigid shape while moving instead of flying independently.
    public boolean waveKeepFormation = false;
    // Straight-line speed (world units/sec) for members without an authored movementPattern.
    public float waveSpeed = 3f;
    // Degrees (0 = right, 90 = up): rotates the whole formation around the anchor.
    public float waveRotation = 0f;

    // Runtime-only: an additive Y lift shared by every member of a wave's entrance, so all members
    // clear their targets by the same margin while keeping the formation's vertical shape. NaN for
    // non-wave triggers. See EnemyEntranceMovement.spawnY().
    public float waveSpawnLift = Float.NaN;

    // --- Other actions ---

    // Plays a one-off sound (path relative to assets/).
    public String sound;

    // Plays a sprite animation at (x, y); see SpawnScheduler.SpriteCue.
    public String spriteTexture;
    public float size = 1f;
    public int columns = 1;
    public int rows = 1;
    public int frameCount = 1;
    public float frameDuration = 1f;

    // Sets the camera speed (world units/sec) instantly.
    public Float setSpeed;

    // Shows an on-screen text cue built from these fields (see TextCue for what each does).
    public String text;
    public String textEffect = "static";
    public float textDuration = Float.MAX_VALUE;
    public float textX = 0f;
    public float textY = 0f;
    public boolean textCentered = false;
    public float textFontSize = 1f;
    public float textCharsPerSecond = 30f;
    public float textBlinksPerSecond = 4f;

    // Starts the background's boss-intro video.
    public boolean triggerBossVideo = false;
    // Fades out the stage music.
    public boolean fadeOutMusic = false;
    // Switches the stage music to this looping track (path relative to assets/). Seeks restore the
    // right track via TriggerManager.musicAt().
    public String music;

    // Runtime-only: fully resolved (action run and, if required, confirmed); skipped from now on.
    public boolean fired = false;

    public Trigger() {}
}
