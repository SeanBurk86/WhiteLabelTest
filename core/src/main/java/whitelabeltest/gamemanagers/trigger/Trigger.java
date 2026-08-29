package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.utils.Array;

/** One entry in a stage's trigger file - fires once the LevelCamera's collision box reaches
 *  `distance` (see TriggerManager.update()), instead of SpawnScheduler.SpawnEvent's wall-clock
 *  `time`. Mirrors SpawnEvent's own "several optional fields, whichever is set decides what fires"
 *  dispatch style rather than a class hierarchy, plus the new action kinds this pass adds (sound,
 *  sprite, camera speed) - see TriggerManager.update()'s dispatch order for exactly which field
 *  wins when more than one happens to be set (they're meant to be mutually exclusive per entry).
 *
 * Reaching `distance` only ARMS a trigger (see TriggerManager.update()/armConditions()) - if
 * `conditions` is empty (the default), an armed trigger fires that same frame, same as before
 * conditions existed. Otherwise it waits, checking every frame, until its conditions are satisfied
 * (see `conditionMode`) - the camera keeps advancing and other triggers keep arming/firing normally
 * in the meantime, unlike SpawnScheduler's GateCue, which freezes its whole schedule's clock. */
public class Trigger {
    public float distance;

    // Zero or more gameplay conditions (see Condition's own doc) that must additionally be
    // satisfied, on top of reaching `distance`, before this trigger fires. Null/empty (the default)
    // means no extra gating - fires the instant the camera reaches `distance`.
    public Array<Condition> conditions;
    // "ALL" (the default) requires every condition satisfied; "ANY" requires just one.
    public String conditionMode = "ALL";
    // Runtime-only: true once the camera has reached `distance` and this trigger's conditions have
    // been snapshotted (see TriggerManager.armConditions()) - not meant to be authored in JSON.
    public boolean armed = false;

    // --- Enemy spawn/despawn/silence/waypoint-gem/weapon-swap - identical meaning to the same-named
    // fields on SpawnScheduler.SpawnEvent; see that class's docs for each. Also doubles as the sprite
    // cue's draw position (x/y) when spriteTexture is set below, same "reuse the generic x/y" economy
    // SpawnEvent itself already relies on.
    public String type;
    public float x = Float.NaN;
    public float y = Float.NaN;
    public Integer powerup;
    public boolean inverseMovement = false;
    public float offsetX = Float.NaN;
    public float offsetY = Float.NaN;
    public String movementPattern;
    public String firingPattern;
    public boolean silence = false;
    public boolean despawn = false;
    public boolean waypointGem = false;
    public String swapWeaponId = null;
    public int weaponSlot = 0;

    // --- New action kinds this pass adds ---

    // Non-null: play this one-off sound (asset path relative to assets/) - see
    // SpawnScheduler.SoundCue.
    public String sound;

    // Non-null: play this scripted sprite/animation at (x, y) - see SpawnScheduler.SpriteCue. Same
    // field meanings as that class.
    public String spriteTexture;
    public float size = 1f;
    public int columns = 1;
    public int rows = 1;
    public int frameCount = 1;
    public float frameDuration = 1f;

    // Non-null: sets the LevelCamera's speed (world units/sec) from this point on, applied
    // instantly - no ramp in this pass.
    public Float setSpeed;

    public boolean fired = false;

    public Trigger() {}
}
