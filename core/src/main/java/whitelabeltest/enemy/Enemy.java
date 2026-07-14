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
    void update(float delta, Array<EnemyBullet> enemyBullets, Circle playerHitbox);
    void draw(SpriteBatch batch);
    boolean isOffScreen();
    Rectangle getRectangle();
    boolean takeDamage(int amount); // Returns true if destroyed
    default boolean isBoss() { return false; }

    // For debug display (e.g. an on-screen health meter); 0 means "not tracked".
    default int getHealth() { return 0; }
    default int getMaxHealth() { return 0; }

    // Lifecycle: false while playing an entrance or death animation. Used to keep enemies
    // invulnerable/non-colliding during those transitions.
    default boolean isActive() { return true; }
    default boolean isDying() { return false; }

    // Powerup drop logic
    void setGuaranteedPowerup(String powerupType);
    String getGuaranteedPowerup();

    // For movement pattern inversion
    void setInvertMovement(boolean invert);

    // Prototype methods
    Enemy create(Texture texture, float worldWidth, float worldHeight);
    float getSpawnRate();

    @Override
    default void reset() {}
}
