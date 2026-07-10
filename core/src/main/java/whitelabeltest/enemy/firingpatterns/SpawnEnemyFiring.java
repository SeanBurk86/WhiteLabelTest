package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.EnemySpawnRegistry;

/** Periodically spawns another enemy (by type id) at this enemy's position, instead of firing bullets. */
public class SpawnEnemyFiring implements FiringPattern {
    private final String enemyType;
    private final float spawnInterval;
    private final float offsetX;
    private final float offsetY;
    private float spawnTimer;

    public SpawnEnemyFiring(String enemyType, float spawnInterval) {
        this(enemyType, spawnInterval, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center/bottom, in world units */
    public SpawnEnemyFiring(String enemyType, float spawnInterval, float offsetX, float offsetY) {
        this.enemyType = enemyType;
        this.spawnInterval = spawnInterval;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.spawnTimer = 0f;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        spawnTimer += delta;
        if (spawnTimer >= spawnInterval) {
            spawnTimer -= spawnInterval;
            float spawnX = sprite.getX() + sprite.getWidth() / 2f + offsetX;
            float spawnY = sprite.getY() + offsetY;
            EnemySpawnRegistry.spawn(enemyType, spawnX, spawnY);
        }
    }

    @Override
    public void reset() {
        spawnTimer = 0f;
    }
}