package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.GenericEnemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.spawning.EnemySpawnRegistry;

/** Periodically spawns another enemy (by type id) at this enemy's position, instead of firing bullets. */
public class SpawnEnemyFiring implements FiringPattern {
    private final String enemyType;
    private final float spawnInterval;
    private final float offsetX;
    private final float offsetY;
    // The movement pattern each spawn is given (null = it doesn't move) - see
    // FiringPatternDef.spawnMovementPattern.
    private final String movementPattern;
    private float spawnTimer;

    public SpawnEnemyFiring(String enemyType, float spawnInterval) {
        this(enemyType, spawnInterval, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center/bottom, in world units */
    public SpawnEnemyFiring(String enemyType, float spawnInterval, float offsetX, float offsetY) {
        this(enemyType, spawnInterval, offsetX, offsetY, null);
    }

    /** @param movementPattern the movement pattern id every spawn gets, or null for none */
    public SpawnEnemyFiring(String enemyType, float spawnInterval, float offsetX, float offsetY, String movementPattern) {
        this.enemyType = enemyType;
        this.spawnInterval = spawnInterval;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.movementPattern = movementPattern;
        this.spawnTimer = 0f;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        spawnTimer += delta;
        if (spawnTimer >= spawnInterval) {
            spawnTimer -= spawnInterval;
            float spawnX = sprite.getX() + sprite.getWidth() / 2f + offsetX;
            float spawnY = sprite.getY() + offsetY;
            GenericEnemy spawned = EnemySpawnRegistry.spawn(enemyType, spawnX, spawnY, movementPattern);
            // spawn() places the new sprite's bottom-left corner at (x, y); centre it on the emission
            // point instead, so a spawn comes out exactly where offsetX/offsetY say.
            if (spawned != null) spawned.centerOn(spawnX, spawnY);
        }
    }

    @Override
    public void reset() {
        spawnTimer = 0f;
    }
}