package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.Weapon;
import whitelabeltest.player.powerups.WeaponPowerup;

public class CollisionManager {
    private final Rectangle collisionHighlight;

    public CollisionManager() {
        this.collisionHighlight = new Rectangle();
    }

    public boolean checkPlayerEnemyCollisions(Player player, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (player.getHitbox().overlaps(enemy.getRectangle())) {
                collisionHighlight.set(enemy.getRectangle());
                return true;
            }
        }
        return false;
    }

    public boolean checkPlayerBulletCollisions(Player player, Array<EnemyBullet> enemyBullets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (player.getHitbox().overlaps(bullet.getRectangle())) {
                collisionHighlight.set(bullet.getRectangle());
                return true;
            }
        }
        return false;
    }

    public void checkPlayerPowerupCollisions(Player player, Array<Powerup> powerups) {
        for (int i = powerups.size - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            if (player.getHitbox().overlaps(p.getRectangle())) {
                p.apply(player);
                powerups.removeIndex(i);
                ObjectPools.freePowerup(p);
            }
        }
    }

    public int checkBulletEnemyCollisions(Array<Weapon> bullets, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight) {
        int scoreGained = 0;
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            for (int j = bullets.size - 1; j >= 0; j--) {
                Weapon bullet = bullets.get(j);
                if (enemy.getRectangle().overlaps(bullet.getRectangle())) {
                    if (enemy.takeDamage(bullet.getDamage())) {
                        enemies.removeIndex(i);

                        float centerX = enemy.getRectangle().x + enemy.getRectangle().width / 2;
                        float centerY = enemy.getRectangle().y + enemy.getRectangle().height / 2;

                        ExplosionEffect explosion = ObjectPools.explosionPool.obtain();
                        explosion.init(assets.explosionTextures, centerX, centerY, enemy.getRectangle().width);
                        entityManager.getExplosions().add(explosion);

                        String guaranteed = enemy.getGuaranteedPowerup();
                        if (guaranteed != null || com.badlogic.gdx.math.MathUtils.random() < 0.20f) {
                            spawnPowerup(entityManager.getPowerups(), assets, enemy.getRectangle().x, enemy.getRectangle().y, worldWidth, worldHeight, guaranteed);
                        }

                        ObjectPools.freeEnemy(enemy);
                        audio.playDrop();
                        scoreGained += 10;
                    }
                    if (bullet.shouldDestroyOnCollision()) {
                        bullets.removeIndex(j);
                        ObjectPools.freeWeapon(bullet);
                    }
                    break;
                }
            }
        }
        return scoreGained;
    }

    private void spawnPowerup(Array<Powerup> powerups, AssetManager assets, float x, float y, float worldWidth, float worldHeight, String forcedType) {
        WeaponPowerup wp = ObjectPools.weaponPowerupPool.obtain();
        Texture tex;
        String weaponId; // Changed to String
        int choice;
        if (forcedType != null) {
            if (forcedType.equals("BasicWeapon")) choice = 0;
            else if (forcedType.equals("WaveBlastWeapon")) choice = 1;
            else if (forcedType.equals("ThunderWhipWeapon")) choice = 2;
            else choice = 3;
        } else {
            choice = com.badlogic.gdx.math.MathUtils.random(0, 3);
        }
        if (choice == 0) { tex = assets.powerup1; weaponId = "BasicWeapon"; }
        else if (choice == 1) { tex = assets.powerup2; weaponId = "WaveBlastWeapon"; }
        else if (choice == 2) { tex = assets.powerup3; weaponId = "ThunderWhipWeapon"; }
        else { tex = assets.powerup4; weaponId = "OrbitWeapon"; }
        wp.initWithType(tex, weaponId, x, y, worldWidth, worldHeight); // Pass weaponId (String)
        powerups.add(wp);
    }

    public Rectangle getCollisionHighlight() {
        return collisionHighlight;
    }

    public void reset() {
        collisionHighlight.set(0, 0, 0, 0);
    }
}
