package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.SineBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SineWaveFiring implements FiringPattern {
    private final float fireRate;
    private float shootTimer;

    private static final float AMPLITUDE = 1.0f;
    private static final float FREQUENCY = 4.0f;
    private static final float SPEED = 5.0f;

    public SineWaveFiring(float fireRate) {
        this.fireRate = fireRate;
        this.shootTimer = fireRate;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;

        SineBullet b1 = ObjectPools.sineBulletPool.obtain();
        b1.init(bulletTexture, centerX + .35f, centerY, AMPLITUDE, FREQUENCY, 0, SPEED);
        enemyBullets.add(b1);

        SineBullet b2 = ObjectPools.sineBulletPool.obtain();
        b2.init(bulletTexture, centerX - .35f, centerY, AMPLITUDE, FREQUENCY, 0, SPEED);
        enemyBullets.add(b2);
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}
