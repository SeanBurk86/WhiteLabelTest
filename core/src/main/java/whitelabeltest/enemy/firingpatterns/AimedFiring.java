package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class AimedFiring implements FiringPattern {
    private final float fireRate;
    private float shootTimer;

    public AimedFiring(float fireRate) {
        this.fireRate = fireRate;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        shootTimer += delta;
        if (shootTimer >= fireRate) {
            shootTimer = 0;
            float targetX = playerHitbox.x + playerHitbox.width / 2;
            float targetY = playerHitbox.y + playerHitbox.height / 2;

            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(bulletTexture, sprite.getX() + sprite.getWidth()/2, sprite.getY() + sprite.getHeight()/2, targetX, targetY);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
