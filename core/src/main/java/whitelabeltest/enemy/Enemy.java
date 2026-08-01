package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.bullets.EnemyBullet;

public interface Enemy extends Pool.Poolable {
    void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY);
    void update(float delta, Array<EnemyBullet> enemyBullets, Circle playerHitbox, Circle grazeHitbox, boolean firingPaused);
    void draw(SpriteBatch batch);
    default void drawShadow(SpriteBatch batch) {}
    boolean isOffScreen();
    Rectangle getRectangle();
    boolean takeDamage(int amount);
    default boolean isBoss() { return false; }
    default boolean isGround() { return false; }
    // Sealable enemies hold their fire while the player's graze halo overlaps their hitbox - see
    // BaseEnemy.update's firing gate.
    default boolean isSealable() { return false; }
    // Defiant enemies take no damage until they've fired at least once - see
    // BaseEnemy.takeDamage()/hasFiredOnce.
    default boolean isDefiant() { return false; }
    // See EnemyDefinition.bulletCancel - GameController.destroyEnemy checks this to decide
    // whether to also clear out this enemy's in-flight bullets when it dies.
    default boolean cancelsBulletsOnDeath() { return false; }
    default int getScore() { return 10; }
    default String getExplosionPattern() { return null; }


    default int getHealth() { return 0; }
    default int getMaxHealth() { return 0; }


    default boolean isActive() { return true; }
    default boolean isDying() { return false; }


    // The guaranteed weapon-powerup tier (1-3, see GameController.spawnPowerup()) this enemy drops
    // on death, or null for no guarantee.
    void setGuaranteedPowerup(Integer powerupTier);
    Integer getGuaranteedPowerup();


    void setInvertMovement(boolean invert);

    Enemy create(Texture texture, float worldWidth, float worldHeight);
    float getSpawnRate();

    @Override
    default void reset() {}
}
