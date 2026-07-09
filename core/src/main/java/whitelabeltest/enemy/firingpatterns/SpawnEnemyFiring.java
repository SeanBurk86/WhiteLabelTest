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
    private float spawnTimer;

    public SpawnEnemyFiring(String enemyType, float spawnInterval) {
        this.enemyType = enemyType;
        this.spawnInterval = spawnInterval;
        this.spawnTimer = 0f;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        spawnTimer += delta;
        if (spawnTimer >= spawnInterval) {
            spawnTimer -= spawnInterval;
            float spawnX = sprite.getX() + sprite.getWidth() / 2f;
            float spawnY = sprite.getY();
            EnemySpawnRegistry.spawn(enemyType, spawnX, spawnY);
        }
    }

    @Override
    public void reset() {
        spawnTimer = 0f;
    }
}