package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.SineBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SineWaveFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;

    private static final float AMPLITUDE = 1.0f;
    private static final float FREQUENCY = 4.0f;
    private static final float DEFAULT_SPEED = 5.0f;

    private final Animation<TextureRegion> spriteOverride;

    public SineWaveFiring(float fireRate) {
        this(fireRate, 0.2f, DEFAULT_SPEED, null);
    }

    public SineWaveFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public SineWaveFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public SineWaveFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.shootTimer = fireRate;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        SineBullet b1 = ObjectPools.sineBulletPool.obtain();
        b1.init(animation, centerX + .35f, centerY, AMPLITUDE, FREQUENCY, 0, bulletSpeed, bulletSize);
        enemyBullets.add(b1);

        SineBullet b2 = ObjectPools.sineBulletPool.obtain();
        b2.init(animation, centerX - .35f, centerY, AMPLITUDE, FREQUENCY, 0, bulletSpeed, bulletSize);
        enemyBullets.add(b2);
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}
