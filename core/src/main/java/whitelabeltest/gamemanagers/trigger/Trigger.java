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

    // --- Wave: expands this ONE enemy-spawn trigger (see `type` above) into a whole formation of
    // that same enemy type at fire time, instead of just the one spawn - see WaveSpawnPlanner (the
    // shape/orientation math) and TriggerManager.fireWave() (the actual staggered spawning). null
    // (the default) means "no wave" - just the ordinary single spawn `type`/x/y/movementPattern/etc.
    // already describe - every other wave* field below is meaningless without it. While a wave IS
    // set, x/y become the wave's own ANCHOR/center point rather than a literal spawn position, and
    // offsetX/offsetY are ignored (each member's own formation offset is computed from the shape
    // instead). movementPattern is NOT ignored - if it's set (still authored/edited completely
    // normally via PropertiesPanel's "Movement Path" section, waypoints included), every member
    // reuses it as-is; only an UNSET movementPattern falls back to a straight line synthesized from
    // waveSpeed/waveOrientation - see TriggerManager.fireWave()'s own doc for exactly how.
    public String waveShape; // "point", "circle", "plane", "triangle"
    // "in front", "to the center", "to the player", "to the exterior" - see
    // WaveSpawnPlanner.computeAngle().
    public String waveOrientation = "in front";
    // point, circle
    public int waveNumberOfSpawns = 4;
    // circle, plane, triangle - world units (this stage's whole play area is ~9x12).
    public float waveWidth = 3f;
    public float waveHeight = 3f;
    // circle
    public float waveStartAngle = 0f;
    public float waveEndAngle = 360f;
    public float waveCircleOffset = 0f;
    // plane: waveLines is the count ACROSS THE WIDTH, waveColumns the count DOWN THE HEIGHT - see
    // WaveSpawnPlanner.planeMembers()'s own doc on why those aren't named the other way round.
    // triangle: waveColumns is the wide (back) row's count; total members =
    // waveColumns*(waveColumns+1)/2 - see WaveSpawnPlanner.triangleMembers().
    public int waveLines = 1;
    public int waveColumns = 1;
    // Real SECONDS after this trigger fires (NOT `distance` units - once a wave trigger fires, its
    // members stagger out over real time regardless of whether the camera itself is even still
    // moving) before the first/each subsequent member spawns - see TriggerManager's own pending-wave
    // queue.
    public float waveStartDelay = 0f;
    public float waveSpawnInterval = 0f;
    // True: every member holds the group's own rigid shape while moving - either an authored
    // movementPattern's targets shifted per member, or (with none set) every member sharing the
    // exact same synthesized direction - instead of each flying independently (an authored pattern
    // reused verbatim, or each member's own per-slot angle) - see TriggerManager.fireWave()'s own
    // doc for exactly how either case is built. "to the center"/"to the exterior" degenerate to
    // "in front" for the synthesized-direction case specifically (a rigid body can't fly toward its
    // own center) - see WaveSpawnPlanner.plan()'s own doc.
    public boolean waveKeepFormation = false;
    // Straight-line speed (world units/sec) for the movement this wave generates for each member -
    // required for waveOrientation to actually produce visible motion.
    public float waveSpeed = 3f;
    // Degrees, standard math convention (matches MovementPatternDef.movementAngle - 0 = +X/right,
    // 90 = +Y/up) - rotates the WHOLE shape's member positions around the anchor (trigger.x/y)
    // before waveOrientation's own per-member facing is computed against them, so e.g. a "plane"
    // that's normally a horizontal row can be turned into a vertical column, or any angle between,
    // without reauthoring width/lines/columns - see WaveSpawnPlanner.plan(). 0 (the default) leaves
    // every shape exactly as its own doc already describes it.
    public float waveRotation = 0f;

    // Runtime-only, never authored in JSON: for a wave member's synthetic entranceView (see
    // TriggerManager.fireWave()), a single ADDITIVE lift - the SAME delta for every member of one
    // wave - added to THIS member's own natural slot.y (never an absolute value replacing it). See
    // EnemyEntranceMovement.spawnY()'s own doc for why this has to be a per-member-relative shift, not
    // a shared absolute floor: this trigger's own base movementPattern shifts EVERY member's real
    // waypoint target by that exact same member's own (slot.y - trigger.y), so target.y - slot.y is
    // the SAME constant for every member regardless of the formation's own rotated shape - which means
    // ONE shared additive lift, applied to each member's own already-different slot.y, simultaneously
    // (a) clears every member's own target by the identical margin and (b) keeps every member's
    // spawn-to-arrival Y difference from its squadmates EXACTLY what the formation's own shape says it
    // should be, at every point along the flight, not just at the two endpoints - the earlier "one
    // shared absolute ceiling" design this replaced instead collapsed every member's spawn Y to the
    // SAME height, discarding the formation's own vertical shape at spawn entirely (holding it only in
    // X) and letting it visibly shear open during the flight instead of staying rigid. NaN (the
    // default, and always for a non-wave trigger) means "nothing to lift - fall back to the plain
    // per-trigger computation alone", exactly the single-enemy behavior this field never touches.
    public float waveSpawnLift = Float.NaN;

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
