package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

public class EnemyDefinition {
    public String id;
    public String texture;
    public String bulletTexture;
    public int frameCount;
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.1f;
    public float size;
    // The enemy's collision shapes - any number of rectangles/circles placed over the sprite (see HitboxDef and the
    // editor's hitbox editor), which is what bullets, the player's ship, the halo and blasts actually hit. null or
    // empty (the default) keeps the classic single box exactly the size of the sprite, so an enemy that never
    // gets one behaves exactly as before.
    public Array<HitboxDef> hitboxes;
    public int health;
    // Movement is deliberately NOT part of this type definition - see Trigger.movementPattern's own
    // doc. Every placed spawn gets its own movement (ideally a WaypointPath, authored via the
    // editor's Movement Path panel) assigned individually; there is no type-level default to fall
    // back to (GenericEnemy.initWithDefinition() resolves purely from the spawning trigger/event's
    // own movementPatternId - null there just means this particular spawn doesn't move).
    public boolean inverseMovement = false;
    public boolean rotateWithMovement = true;
    // When true, this enemy's sprite continuously rotates to face the player's current position
    // every frame, overriding both its movement pattern's own rotation and rotateWithMovement -
    // see BaseEnemy.applyFacePlayer(). Independent of firing pattern; use it for an enemy whose
    // art should visibly track/aim at the player even if its firing pattern isn't itself aimed
    // (or to sell an aimed firing pattern with matching visuals).
    public boolean facePlayer = false;
    public boolean isBoss = false;
    public boolean isGround = false;
    // Index (declaration order in the stage's backgroundLayers - see StageDefinition.
    // BackgroundLayerDef/ScrollingBackground's own "far-to-near" doc) of the background layer this
    // enemy is drawn against, or -1 (the default) for "not attached to any layer" - the ordinary
    // behavior of drawing in front of the WHOLE background stack, same as before this field
    // existed. When set, GameController.draw() sandwiches this enemy's shadow+sprite between that
    // layer and the next one instead, so e.g. a ground enemy can sit visually behind a closer
    // foreground parallax layer instead of always drawing on top of it - see
    // EntityManager.drawEnemiesAttachedToLayer(). If isGround is also set, this enemy's implicit
    // scroll (see BaseEnemy.applyGroundScroll) is driven by THIS layer's own scrollSpeed instead of
    // the schedule-wide SpawnScheduler.groundScrollSpeed - see EntityManager's ground-scroll
    // resolution - so it stays planted on the specific layer it's attached to, even if that layer
    // scrolls at a different speed than the rest of the background. Out-of-range for a given
    // stage's actual layer count falls back to "unattached" (z-order) / the schedule-wide speed
    // (scroll), rather than throwing, so one enemy definition can be safely reused across stages
    // with different numbers of background layers.
    public int backgroundLayer = -1;
    public boolean sealable = false;
    // When true, this enemy keeps firing even while its hitbox sits in the play area's bottom/
    // left/right ceasefire zone, which normally holds an enemy's fire so it can't shoot into or
    // past the boundary - see BaseEnemy's CEASEFIRE_ZONE_Y/CEASEFIRE_ZONE_X firing gate and
    // Enemy.ignoresCeasefireZone(). For an enemy that's MEANT to live at an edge and shoot from
    // there (e.g. a ground turret parked at the bottom of the screen, or a scripted emitter hugging
    // a side wall), which would otherwise be silent for its whole life.
    public boolean ignoreCeasefireZone = false;
    public boolean defiant = false;
    // See Enemy.isDamageableByEnemyBullets() - opts this enemy into being damaged by other
    // enemies' bullets (e.g. PowerCarrier as a bullet-streaming drill's target).
    public boolean damageableByEnemyBullets = false;
    // See Enemy.showsHealthBar() - opts this enemy into a player-facing health meter drawn above it.
    public boolean showHealthBar = false;
    // See Enemy.isTargetableByHoming() - false opts a purely decorative/utility enemy (e.g. a
    // scripted bullet emitter) out of any screen-wide homing target scan.
    public boolean targetableByHoming = true;
    // See Enemy.getPairId()/CollisionManager.resolvePairedEnemyDeaths() - null (the default) means
    // normal independent death. Every active enemy sharing this same non-null string is expected
    // to die within CollisionManager.PAIR_GRACE_WINDOW of this one, or none of them do (see
    // reviveFully()). Definition-level rather than per-instance, so this is meant for a bespoke
    // scripted pair (e.g. a tutorial drill), not a generally-reusable enemy type spawned in
    // varying group sizes.
    public String pairId = null;
    // Health regenerated per second while active (0 = no regen, the default) - see
    // BaseEnemy.update()'s regen step. Caps at the enemy's starting health. Lets an enemy demand
    // sustained damage rather than dying to a handful of scattered hits - e.g. the tutorial's
    // bullet-streaming target, which should only fall to a continuous stream, not a stray graze.
    public float healthRegenPerSecond = 0f;
    // When true, destroying this enemy also destroys every bullet it has in flight - see
    // GameController.destroyEnemy.
    public boolean bulletCancel = false;
    public int score = 10;
    public String firingPattern;
    public String explosionPattern;
    // Named alternates to firingPattern this enemy can be switched to mid-flight - name -> a
    // firing-pattern id (same ids PatternRegistry.getFiring() resolves data/firing_patterns/ from).
    // Null/empty means "no alternates" (the default, unchanged behavior). Consumed by a
    // WaypointPath movement's "change weapon set" waypoints (see MovementPatternDef.weaponSet /
    // BaseEnemy's WaypointCue handling) - the waypoint stores a KEY into this map, not a firing-
    // pattern id directly, so the same authored path can be reused by a different enemy definition
    // whose own weaponSets map that same key to a different pattern.
    public ObjectMap<String, String> weaponSets;

    // Named alternate animations (name -> sheet) a HealthPhase can switch this enemy to via its own
    // `animation` key - see EnemyAnimationDef/BaseEnemy.enterHealthPhase(). Null/empty (the default)
    // means the enemy only ever plays its base texture animation above.
    public ObjectMap<String, EnemyAnimationDef> animations;

    // True: the sprite mirrors horizontally to face whichever way it's currently moving - see
    // BaseEnemy.updateFacing(). The art is assumed to be drawn facing LEFT, so the sprite flips while
    // moving right. A HealthPhase can turn this on/off mid-fight via its own flipWithDirection.
    public boolean flipWithDirection = false;

    // True: every animation this enemy plays (base, spawn, and each of `animations`) is drawn at the
    // SAME world-units-per-pixel scale as the base animation's first frame, with the sprite resized
    // around its own center whenever a frame's pixel size differs - so sheets cut at different frame
    // sizes keep the character the same size on screen instead of each being squashed into the base
    // animation's box. False (the default) keeps the classic behavior of drawing every frame into
    // the one sprite box sized from the base animation.
    public boolean uniformPixelScale = false;

    public String spawnTexture;
    public int spawnFrameCount;
    public int spawnColumns = 0;
    public int spawnRows = 1;
    public float spawnDuration = 0.4f;
    // Seconds per frame of the spawn animation (it plays once, holding its last frame if
    // spawnDuration outlasts it).
    public float spawnFrameDuration = 0.05f;


    public String deathTexture;
    public int deathFrameCount;
    public int deathColumns = 0;
    public int deathRows = 1;
    public float deathDuration = 0.4f;

    public EnemyDefinition() {}
}