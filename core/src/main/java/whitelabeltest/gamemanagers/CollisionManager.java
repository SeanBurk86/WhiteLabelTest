package whitelabeltest.gamemanagers;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.Weapon;

public class CollisionManager {
    private final Rectangle collisionHighlight;

    public CollisionManager() {
        this.collisionHighlight = new Rectangle();
    }

    public boolean checkPlayerEnemyCollisions(Player player, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue; // entering/dying enemies don't deal contact damage
            if (Intersector.overlaps(player.getHitbox(), enemy.getRectangle())) {
                collisionHighlight.set(enemy.getRectangle());
                return true;
            }
        }
        return false;
    }

    public boolean checkPlayerBulletCollisions(Player player, Array<EnemyBullet> enemyBullets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (overlaps(player.getHitbox(), bullet)) {
                collisionHighlight.set(bullet.getRectangle());
                return true;
            }
        }
        return false;
    }

    public boolean checkGrazeCollisions(Player player, Array<EnemyBullet> enemyBullets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (overlaps(player.getGrazeHitbox(), bullet)) {
                return true;
            }
        }
        return false;
    }

    /** Most bullets report getRotation() == 0, so this is a plain Circle-vs-AABB check; bullets
     *  like LaserBullet whose hitbox is rotated (but not resized) get an exact rotated-rect check
     *  instead, by testing the circle against the rectangle in the rectangle's own local frame. */
    private boolean overlaps(Circle circle, EnemyBullet bullet) {
        Rectangle rect = bullet.getRectangle();
        float rotation = bullet.getRotation();
        if (rotation == 0f) return Intersector.overlaps(circle, rect);

        float pivotX = rect.x + rect.width / 2f;
        float pivotY = rect.y;
        float dx = circle.x - pivotX;
        float dy = circle.y - pivotY;

        // Undo the rectangle's rotation to bring the circle's center into the rectangle's own
        // un-rotated local frame, where it's a plain axis-aligned box again.
        float cos = MathUtils.cosDeg(-rotation);
        float sin = MathUtils.sinDeg(-rotation);
        float localX = dx * cos - dy * sin;
        float localY = dx * sin + dy * cos;

        float halfWidth = rect.width / 2f;
        float closestX = MathUtils.clamp(localX, -halfWidth, halfWidth);
        float closestY = MathUtils.clamp(localY, 0f, rect.height);

        float distX = localX - closestX;
        float distY = localY - closestY;
        return distX * distX + distY * distY <= circle.radius * circle.radius;
    }

    public void checkPlayerPowerupCollisions(Player player, Array<Powerup> powerups, AudioManager audio) {
        for (int i = powerups.size - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            if (Intersector.overlaps(player.getHitbox(), p.getRectangle())) {
                p.apply(player);
                audio.playPowerup();
                powerups.removeIndex(i);
                ObjectPools.freePowerup(p);
            }
        }
    }

    public void checkBulletEnemyCollisions(Array<Weapon> bullets, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue; // bullets pass through entering/dying enemies
            for (int j = bullets.size - 1; j >= 0; j--) {
                Weapon bullet = bullets.get(j);
                if (enemy.getRectangle().overlaps(bullet.getRectangle())) {
                    if (enemy.takeDamage(bullet.getDamage())) {
                        scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy), bullet.getChainWindow());
                    }
                    if (bullet.shouldDestroyOnCollision()) {
                        bullets.removeIndex(j);
                        ObjectPools.freeWeapon(bullet);
                    }
                    break;
                }
            }
        }
    }

    public Rectangle getCollisionHighlight() {
        return collisionHighlight;
    }

    public void reset() {
        collisionHighlight.set(0, 0, 0, 0);
    }
}
