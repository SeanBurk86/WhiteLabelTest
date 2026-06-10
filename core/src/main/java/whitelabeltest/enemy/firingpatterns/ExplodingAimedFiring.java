package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class ExplodingAimedFiring implements FiringPattern {
    private final float fireRate;
    private float shootTimer;
    private final int numRadialBullets = 8;
    private final float radialSpeed = 4f;

    public ExplodingAimedFiring(float fireRate) {
        this.fireRate = fireRate;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        shootTimer += delta;
        if (shootTimer >= fireRate) {
            shootTimer = 0;
            float centerX = sprite.getX() + sprite.getWidth() / 2;
            float centerY = sprite.getY() + sprite.getHeight() / 2;

            for (int i = 0; i < numRadialBullets; i++) {
                float angle = i * (360f / numRadialBullets);
                ExplodingAimedBullet b = ObjectPools.explodingAimedBulletPool.obtain();
                b.init(bulletTexture, centerX, centerY, angle, playerHitbox);
                enemyBullets.add(b);
            }
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
