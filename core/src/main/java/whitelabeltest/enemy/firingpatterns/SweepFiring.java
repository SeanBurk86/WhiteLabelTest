package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SweepFiring implements FiringPattern {
    private final float bulletInterval;
    private final float bulletSize;
    private final float bulletSpeed;
    private static final float SWEEP_DURATION = 2.0f;
    private static final float SWEEP_START_ANGLE = 225f; // down-left
    private static final float SWEEP_END_ANGLE = 315f;   // down-right
    private static final float DEFAULT_SPEED = 5f;

    private float bulletTimer;
    private float sweepT;         // 0 = left edge, 1 = right edge
    private float sweepDirection; // +1 = left→right, -1 = right→left

    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;

    public SweepFiring(float fireRate) {
        this(fireRate, 0.25f, DEFAULT_SPEED, null);
    }

    public SweepFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
        this.bulletInterval = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletTimer = bulletInterval; // fire immediately on first update
        this.sweepT = 0f;
        this.sweepDirection = 1f;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        sweepT += sweepDirection * delta / SWEEP_DURATION;
        if (sweepT >= 1f) { sweepT = 1f; sweepDirection = -1f; }
        else if (sweepT <= 0f) { sweepT = 0f; sweepDirection = 1f; }

        bulletTimer += delta;
        if (bulletTimer < bulletInterval) return;
        bulletTimer = 0;

        float angle = SWEEP_START_ANGLE + sweepT * (SWEEP_END_ANGLE - SWEEP_START_ANGLE);
        Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
        b.init(animation, centerX, centerY, centerX + dir.x, centerY + dir.y, bulletSize, bulletSpeed);
        enemyBullets.add(b);
    }

    @Override
    public void reset() {
        bulletTimer = bulletInterval;
        sweepT = 0f;
        sweepDirection = 1f;
    }
}