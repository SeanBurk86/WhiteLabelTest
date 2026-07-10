package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class BurstAimedFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;

    private final int burstCount = 5;
    private final float burstInterval = 0.15f;
    private int currentBurstShot = 0;
    private float burstTimer = 0;
    private boolean isBursting = false;

    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private static final float DEFAULT_SPEED = 5f;

    public BurstAimedFiring(float fireRate) {
        this(fireRate, 0.25f, DEFAULT_SPEED, null);
    }

    public BurstAimedFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
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
                fireAimedShot(sprite, enemyBullets, spriteOverride != null ? spriteOverride : bulletAnimation, playerHitbox);
                currentBurstShot++;

                if (currentBurstShot >= burstCount) {
                    isBursting = false;
                }
            }
        }
    }

    private void fireAimedShot(Sprite sprite, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        float targetX = playerHitbox.x;
        float targetY = playerHitbox.y;

        AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
        b.init(bulletAnimation, centerX, centerY, targetX, targetY, bulletSize, bulletSpeed);
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
