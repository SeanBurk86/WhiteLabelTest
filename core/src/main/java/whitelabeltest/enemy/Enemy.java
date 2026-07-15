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
    default void drawShadow(SpriteBatch batch) {}
    boolean isOffScreen();
    Rectangle getRectangle();
    boolean takeDamage(int amount);
    default boolean isBoss() { return false; }
    default boolean isGround() { return false; }


    default int getHealth() { return 0; }
    default int getMaxHealth() { return 0; }


    default boolean isActive() { return true; }
    default boolean isDying() { return false; }


    void setGuaranteedPowerup(String powerupType);
    String getGuaranteedPowerup();


    void setInvertMovement(boolean invert);

    Enemy create(Texture texture, float worldWidth, float worldHeight);
    float getSpawnRate();

    @Override
    default void reset() {}
}
