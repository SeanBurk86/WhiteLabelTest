package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.HitboxSpec;
import whitelabeltest.enemy.SpeedProfile;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

/** Every fireRate seconds, fires a burst of 5 shots burstInterval apart, each aimed at the player. */
public class BurstAimedFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;

    private static final float DEFAULT_BURST_INTERVAL = 0.15f;

    private final int burstCount = 5;
    private final float burstInterval;
    private int currentBurstShot = 0;
    private float burstTimer = 0;
    private boolean isBursting = false;

    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    // Initial shootTimer value (see the phaseOffset constructor).
    private final float phaseOffset;
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
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, 1);
    }

    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile,hitboxSpec see SpeedProfile and HitboxSpec */
    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec, 0f);
    }

    /** @param phaseOffset initial shootTimer, shortening the wait before the first burst (>= fireRate
     *  = burst immediately). Two emitters alternate if both use fireRate = 2 * pause + burst length,
     *  one with phaseOffset = fireRate and the other with phaseOffset = pause. */
    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec, float phaseOffset) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec, phaseOffset, DEFAULT_BURST_INTERVAL);
    }

    /** @param burstInterval seconds between shots within a burst; shorter keeps the re-aimed shots
     *  closer together, leaving smaller gaps. */
    public BurstAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec, float phaseOffset, float burstInterval) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.burstInterval = burstInterval;
        this.phaseOffset = phaseOffset;
        this.shootTimer = phaseOffset;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
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
                fireAimedShot(self, sprite, enemyBullets, spriteOverride != null ? spriteOverride : bulletAnimation, playerHitbox);
                currentBurstShot++;

                if (currentBurstShot >= burstCount) {
                    isBursting = false;
                }
            }
        }
    }

    private void fireAimedShot(Enemy self, Sprite sprite, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        float targetX = playerHitbox.x;
        float targetY = playerHitbox.y;

        AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
        b.init(bulletAnimation, centerX, centerY, targetX, targetY, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
        enemyBullets.add(b);
    }

    @Override
    public void reset() {
        shootTimer = phaseOffset;
        isBursting = false;
        currentBurstShot = 0;
        burstTimer = 0;
    }
}
