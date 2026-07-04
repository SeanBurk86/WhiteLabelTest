package whitelabeltest.gamemanagers;

import com.badlogic.gdx.math.Intersector;
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
            if (Intersector.overlaps(player.getHitbox(), bullet.getRectangle())) {
                collisionHighlight.set(bullet.getRectangle());
                return true;
            }
        }
        return false;
    }

    public boolean checkGrazeCollisions(Player player, Array<EnemyBullet> enemyBullets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (Intersector.overlaps(player.getGrazeHitbox(), bullet.getRectangle())) {
                return true;
            }
        }
        return false;
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
