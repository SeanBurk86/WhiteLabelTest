package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class ExplodingAimedFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;
    private final int numRadialBullets = 8;
    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private static final float DEFAULT_SPEED = 6f;

    public ExplodingAimedFiring(float fireRate) {
        this(fireRate, 0.25f, DEFAULT_SPEED, null);
    }

    public ExplodingAimedFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public ExplodingAimedFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public ExplodingAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public ExplodingAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer >= fireRate) {
            shootTimer = 0;
            float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
            float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
            Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

            for (int i = 0; i < numRadialBullets; i++) {
                float angle = i * (360f / numRadialBullets);
                ExplodingAimedBullet b = ObjectPools.explodingAimedBulletPool.obtain();
                b.init(animation, centerX, centerY, angle, playerHitbox, bulletSize, bulletSpeed);
                enemyBullets.add(b);
            }
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
