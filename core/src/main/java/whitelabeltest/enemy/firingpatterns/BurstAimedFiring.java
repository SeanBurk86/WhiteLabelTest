package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class BurstAimedFiring implements FiringPattern {
    private final float fireRate;
    private float shootTimer;

    private final int burstCount = 5;
    private final float burstInterval = 0.15f;
    private int currentBurstShot = 0;
    private float burstTimer = 0;
    private boolean isBursting = false;

    public BurstAimedFiring(float fireRate) {
        this.fireRate = fireRate;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        if (!isBursting) {
            shootTimer += delta;
            if (shootTimer >= fireRate) {
                isBursting = true;
                shootTimer = 0;
                currentBurstShot = 0;
                burstTimer = burstInterval; // Fire first shot immediately
            }
        }

        if (isBursting) {
            burstTimer += delta;
            if (burstTimer >= burstInterval) {
                burstTimer = 0;
                fireAimedShot(sprite, enemyBullets, bulletTexture, playerHitbox);
                currentBurstShot++;

                if (currentBurstShot >= burstCount) {
                    isBursting = false;
                }
            }
        }
    }

    private void fireAimedShot(Sprite sprite, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;
        float targetX = playerHitbox.x + playerHitbox.width / 2;
        float targetY = playerHitbox.y + playerHitbox.height / 2;

        AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
        b.init(bulletTexture, centerX, centerY, targetX, targetY);
        enemyBullets.add(b);
    }

    @Override
    public void reset() {
        shootTimer = 0;
        isBursting = false;
        currentBurstShot = 0;
        burstTimer = 0;
    }
}
