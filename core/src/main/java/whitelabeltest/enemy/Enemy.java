package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.audio.AudioManager;

/** A pooled enemy. Most flags mirror EnemyDefinition fields; see that class for their meaning. */
public interface Enemy extends Pool.Poolable {
    void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY);
    // groundScrollSpeed moves ground enemies with the terrain (ignored by others); audio plays
    // waypoint sound cues.
    void update(float delta, Array<EnemyBullet> enemyBullets, Circle playerHitbox, Circle grazeHitbox, boolean firingPaused, float groundScrollSpeed, AudioManager audio);
    void draw(SpriteBatch batch);
    default void drawShadow(SpriteBatch batch) {}
    boolean isOffScreen();
    // Unrotated sprite box.
    Rectangle getRectangle();
    // Sprite rotation in degrees about the box center; 0 lets collisions use a plain AABB test.
    default float getRotation() { return 0f; }
    // Custom collision shapes, or null for one box the size of getRectangle().
    default Array<HitboxDef> getHitboxDefs() { return null; }
    // Rotation pivot: always the box center (enemy sprites use setOriginCenter()).
    default float getRotationPivotX() { Rectangle r = getRectangle(); return r.x + r.width / 2f; }
    default float getRotationPivotY() { Rectangle r = getRectangle(); return r.y + r.height / 2f; }
    boolean takeDamage(int amount);
    default boolean isBoss() { return false; }
    default boolean isGround() { return false; }
    // Background layer to draw on / scroll with, or -1 for none.
    default int getBackgroundLayer() { return -1; }
    // Holds fire while the player's graze halo overlaps it.
    default boolean isSealable() { return false; }
    // Keeps firing inside the edge ceasefire zones.
    default boolean ignoresCeasefireZone() { return false; }
    // Takes no damage until it has fired once.
    default boolean isDefiant() { return false; }
    // Can be damaged (and consume) other enemies' bullets.
    default boolean isDamageableByEnemyBullets() { return false; }
    // Shows a player-facing health bar.
    default boolean showsHealthBar() { return false; }
    // Can be picked by homing target scans.
    default boolean isTargetableByHoming() { return true; }
    // Paired-death group, or null (see CollisionManager.resolvePairedEnemyDeaths()).
    default String getPairId() { return null; }
    // Undoes a paired enemy's death (full health, active again) when its partner didn't follow.
    default void reviveFully() {}
    // Set once a pair's death is finalized, so it isn't reprocessed during the death animation.
    default boolean isPairResolved() { return false; }
    default void markPairResolved() {}
    // Seconds left waiting for the partner to die; negative when not waiting.
    default float getPairGraceTimer() { return -1f; }
    default void setPairGraceTimer(float secondsRemaining) {}
    // Destroys its bullets in flight on death.
    default boolean cancelsBulletsOnDeath() { return false; }
    default int getScore() { return 10; }
    default String getExplosionPattern() { return null; }

    // Advances the firing pattern to its next stage (called on bosses whenever any enemy dies).
    default void advanceFiringPattern() {}

    // Permanently stops firing (a silence trigger).
    default void silenceFiring() {}
    // EnemyDefinition id, or null.
    default String getDefinitionId() { return null; }


    /** The Trigger.id of the spawn trigger that created this enemy, or null ("spawnDestroyed"). */
    default String getSpawnGroup() { return null; }
    default void setSpawnGroup(String group) {}
    default int getHealth() { return 0; }
    default int getMaxHealth() { return 0; }


    default boolean isActive() { return true; }
    default boolean isDying() { return false; }


    // Guaranteed powerup tier (1-3) dropped on death, or null.
    void setGuaranteedPowerup(Integer powerupTier);
    Integer getGuaranteedPowerup();


    // Health thresholds at which it switches movement/firing (see HealthPhase).
    default void setHealthPhases(Array<HealthPhase> phases) {}

    void setInvertMovement(boolean invert);

    Enemy create(Texture texture, float worldWidth, float worldHeight);
    float getSpawnRate();

    @Override
    default void reset() {}
}
