package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/** An enemy type from data/enemies.json. Movement is not part of the type: each spawn assigns its
 *  own movement pattern (null = doesn't move). See the README's "Enemies" section. */
public class EnemyDefinition {
    public String id;
    public String texture;
    public String bulletTexture;
    public int frameCount;
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.1f;
    public float size;
    // Collision shapes (rectangles/circles) over the sprite; null/empty = one box the size of the sprite.
    public Array<HitboxDef> hitboxes;
    public int health;
    public boolean inverseMovement = false;
    public boolean rotateWithMovement = true;
    // Continuously rotate to face the player (overrides movement rotation).
    public boolean facePlayer = false;
    public boolean isBoss = false;
    // Ground enemies scroll with the background and never collide with the player.
    public boolean isGround = false;
    // Background layer index to draw this enemy on (between that layer and the next), or -1 to draw
    // in front of the whole background. A ground enemy also scrolls at that layer's speed. Out-of-range
    // indexes fall back to unattached.
    public int backgroundLayer = -1;
    public boolean sealable = false;
    // Keep firing inside the edge ceasefire zones (for enemies meant to sit at an edge).
    public boolean ignoreCeasefireZone = false;
    public boolean defiant = false;
    // Can be damaged by other enemies' bullets.
    public boolean damageableByEnemyBullets = false;
    // Show a player-facing health bar above it.
    public boolean showHealthBar = false;
    // false = ignored by homing target scans (decorative/utility enemies).
    public boolean targetableByHoming = true;
    // Enemies sharing a pairId must die within CollisionManager.PAIR_GRACE_WINDOW of each other or
    // all revive. For bespoke scripted pairs.
    public String pairId = null;
    // Health regenerated per second, capped at starting health (forces sustained damage).
    public float healthRegenPerSecond = 0f;
    // Destroying this enemy also destroys all its bullets in flight.
    public boolean bulletCancel = false;
    public int score = 10;
    public String firingPattern;
    public String explosionPattern;
    // Named alternate firing patterns (key -> firing pattern id), switched to by WaypointPath
    // "change weapon set" waypoints. The waypoint stores the key, so one path works across enemies.
    public ObjectMap<String, String> weaponSets;

    // Named alternate animations a HealthPhase can switch to.
    public ObjectMap<String, EnemyAnimationDef> animations;

    // Mirror the sprite to face its movement direction (art is drawn facing left).
    public boolean flipWithDirection = false;

    // Draw every animation at the base animation's world-units-per-pixel scale (resizing around the
    // center), so sheets with different frame sizes keep the character the same size.
    public boolean uniformPixelScale = false;

    public String spawnTexture;
    public int spawnFrameCount;
    public int spawnColumns = 0;
    public int spawnRows = 1;
    public float spawnDuration = 0.4f;
    // Seconds per spawn animation frame (plays once, holding the last frame).
    public float spawnFrameDuration = 0.05f;


    public String deathTexture;
    public int deathFrameCount;
    public int deathColumns = 0;
    public int deathRows = 1;
    public float deathDuration = 0.4f;

    public EnemyDefinition() {}
}
