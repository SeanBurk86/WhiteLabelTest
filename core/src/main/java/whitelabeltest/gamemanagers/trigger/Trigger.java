package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.TextCue;

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
 * in the meantime, unless `gate` is also set (see that field's own doc), which is the distance-based
 * equivalent of SpawnScheduler's GateCue: freezing the whole camera, not just this one trigger. */
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

    // True: once armed, this trigger also freezes the CAMERA (see TriggerManager.update()'s
    // activeGate handling) until `conditions` (and requireConfirm, if set) are satisfied - the
    // distance-based equivalent of SpawnScheduler.GateCue, migrated off that wall-clock mechanism so
    // a stage's scripted pacing (e.g. a tutorial waiting on "player moved") can hold up everything
    // further into the stage, not just itself. A gate with no conditions and requireConfirm=false
    // clears (and unfreezes) the instant it arms, same as an ordinary condition-less trigger - it
    // just also momentarily holds the camera exactly at its own distance that one frame.
    public boolean gate = false;
    // True: this trigger additionally waits for the player to press SHOOT or RESTART, ONCE ALREADY
    // FIRED (see TriggerManager.tryResolve()), before counting as fully resolved - the trigger-
    // based, per-trigger equivalent of SpawnScheduler.ScheduleFile.textCuesRequireConfirm (a whole-
    // schedule flag there; here a stage can mix confirm-gated and auto-advancing triggers freely).
    // Firing itself is NEVER gated by this - a text cue with requireConfirm still shows the instant
    // its `conditions` clear, exactly like one without; requireConfirm only delays this trigger (and,
    // combined with `gate`, the whole camera) from being treated as DONE until the player has
    // actually seen it and pressed on, matching how the tutorial's own confirm-gated dialogue always
    // showed immediately and only paused the REST of the schedule while awaiting confirm.
    public boolean requireConfirm = false;
    // Runtime-only: true once requireConfirm's wait has been satisfied - not meant to be authored in
    // JSON.
    public boolean confirmed = false;
    // Runtime-only: true once fire() has actually run for this trigger - distinct from `fired`
    // (which now means fully resolved: fired AND, if requireConfirm is set, confirmed too) so a
    // requireConfirm trigger's post-fire confirm wait doesn't re-fire the action every frame it's
    // re-checked. Not meant to be authored in JSON.
    public boolean actionFired = false;
    // Runtime-only: the live TextCue this trigger fired (see TriggerManager.fireTextCue()), if it
    // was a text-cue trigger - null for every other kind. Lets TriggerManager's confirm handling
    // force-complete an in-progress typewriter reveal on the first press instead of cutting the
    // message off mid-type, exactly like SpawnScheduler.update()'s own awaitingConfirmCue handling -
    // see TriggerManager.checkConfirm(). Not meant to be authored in JSON.
    public TextCue liveTextCue;
    // True: marks the stage complete the instant this fires - the distance-based equivalent of
    // SpawnScheduler.ScheduleFile.scheduleEndTime, for a stage with no boss to kill (e.g. the
    // tutorial) - see TriggerManager.isScheduleEndTriggered()/Main.java's own level-complete check.
    public boolean scheduleEnd = false;

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
    // How many distance-units EARLY this trigger actually arms/fires, relative to its own authored
    // `distance` - see TriggerManager.update()'s own doc on how this is applied (armDistance =
    // distance - spawnLead). Lets a trigger be PLACED (and displayed/dragged in the editor) at the
    // distance where it should matter/engage - lined up with a gate, text cue, or just where you want
    // it visually - while actually spawning that many distance-units earlier, so a stationary enemy
    // is already sitting there (not popping in) and a moving one has had time to already arrive, by
    // the time the camera reaches this trigger's own authored distance. 0 (the default) means "fires
    // exactly at distance", unchanged from before this field existed.
    public float spawnLead = 0f;
    // True: this spawn enters from off-screen above the play area and travels straight down to its
    // own authored (x, y), timed via spawnLead so it's fully arrived by the time the camera reaches
    // this trigger's own distance - see EnemyEntranceMovement's own doc for the actual mechanism.
    // Meaningless (a no-op) without a nonzero spawnLead - with no travel-time budget there's nowhere
    // for the entrance movement to come from, so it just spawns normally at (x, y) instead.
    public boolean enterFromAbove = false;
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

    // Non-null: shows this on-screen text cue - migrated off SpawnScheduler's wall-clock
    // ScheduleFile.textCues (see that class's TextCue doc for what effect/duration/fontSize/
    // charsPerSecond/blinksPerSecond each do). TriggerManager builds a live
    // whitelabeltest.gamemanagers.TextCue from these authored fields the moment it fires (see
    // TriggerManager.fire()) rather than embedding a whole TextCue instance here, so this stays a
    // flat, easily-hand-authored action like every other trigger kind instead of round-tripping
    // TextCue's own runtime-only playback state (triggeredAtRealTime/typingSoundActive/dismissed)
    // through the trigger file.
    public String text;
    public String textEffect = "static";
    public float textDuration = Float.MAX_VALUE;
    public float textX = 0f;
    public float textY = 0f;
    public boolean textCentered = false;
    public float textFontSize = 1f;
    public float textCharsPerSecond = 30f;
    public float textBlinksPerSecond = 4f;

    // True: fires ScrollingBackground.triggerBossVideo() once - migrated off SpawnScheduler.
    // ScheduleFile's old backgroundVideoTime (a wall-clock latch; this fires exactly once, the
    // normal way every other trigger action does, since TriggerManager.fire() is itself already an
    // edge-triggered "this just happened" callback - no separate latch needed).
    public boolean triggerBossVideo = false;
    // True: fires AudioManager.fadeOutStageMusic() once - migrated off SpawnScheduler.ScheduleFile's
    // old musicFadeOutTime, same reasoning as triggerBossVideo above.
    public boolean fadeOutMusic = false;

    // Runtime-only: true once this trigger is FULLY resolved (see TriggerManager.tryResolve()) -
    // its action has run AND, if requireConfirm is set, the player has confirmed - at which point
    // the main update loop skips it for good. See actionFired above for the intermediate "action
    // ran, still awaiting confirm" state this is deliberately distinct from.
    public boolean fired = false;

    public Trigger() {}
}
