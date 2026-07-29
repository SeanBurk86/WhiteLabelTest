package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.HitboxSpec;
import whitelabeltest.enemy.SpeedProfile;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SweepFiring implements FiringPattern {
    private final float bulletInterval;
    private final float bulletSize;
    private final float bulletSpeed;
    public static final float DEFAULT_SWEEP_DURATION = 2.0f;
    public static final float DEFAULT_START_ANGLE = 225f; // down-left
    public static final float DEFAULT_END_ANGLE = 315f;   // down-right
    private static final float DEFAULT_SPEED = 5f;

    private final float sweepDuration;
    private final float startAngle;
    private final float endAngle;

    private float bulletTimer;
    private float sweepT;         // 0 = left edge, 1 = right edge
    private float sweepDirection; // +1 = left→right, -1 = right→left

    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;

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
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, DEFAULT_SWEEP_DURATION, DEFAULT_START_ANGLE, DEFAULT_END_ANGLE);
    }

    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, DEFAULT_SWEEP_DURATION, DEFAULT_START_ANGLE, DEFAULT_END_ANGLE, bulletDamage);
    }

    /** @param speedProfile how bulletSpeed changes over each bullet's flight - see SpeedProfile
     *  @param hitboxSpec each bullet's collision hitbox, independent of its visual size - see
     *  HitboxSpec */
    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, DEFAULT_SWEEP_DURATION, DEFAULT_START_ANGLE, DEFAULT_END_ANGLE, bulletDamage, speedProfile, hitboxSpec);
    }

    /** @param sweepDuration seconds for one full pass from startAngle to endAngle (and back)
     *  @param startAngle, endAngle sweep bounds in degrees, standard math convention (0 = right, 90 = up) */
    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, float sweepDuration, float startAngle, float endAngle) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, sweepDuration, startAngle, endAngle, 1);
    }

    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, float sweepDuration, float startAngle, float endAngle, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, sweepDuration, startAngle, endAngle, bulletDamage, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile how bulletSpeed changes over each bullet's flight - see SpeedProfile
     *  @param hitboxSpec each bullet's collision hitbox, independent of its visual size - see
     *  HitboxSpec */
    public SweepFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, float sweepDuration, float startAngle, float endAngle, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this.bulletInterval = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.sweepDuration = sweepDuration;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
        this.bulletDamage = bulletDamage;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.bulletTimer = bulletInterval; // fire immediately on first update
        this.sweepT = 0f;
        this.sweepDirection = 1f;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        sweepT += sweepDirection * delta / sweepDuration;
        if (sweepT >= 1f) { sweepT = 1f; sweepDirection = -1f; }
        else if (sweepT <= 0f) { sweepT = 0f; sweepDirection = 1f; }

        bulletTimer += delta;
        if (bulletTimer < bulletInterval) return;
        bulletTimer = 0;

        float angle = startAngle + sweepT * (endAngle - startAngle);
        Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
        b.init(animation, centerX, centerY, centerX + dir.x, centerY + dir.y, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
        enemyBullets.add(b);
    }

    @Override
    public void reset() {
        bulletTimer = bulletInterval;
        sweepT = 0f;
        sweepDirection = 1f;
    }
}