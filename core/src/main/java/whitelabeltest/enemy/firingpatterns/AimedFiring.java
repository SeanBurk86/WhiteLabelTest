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

/** Fires single bullets at the player (plus targetOffset) every fireRate seconds. */
public class AimedFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final float targetOffsetX;
    private final float targetOffsetY;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    private float shootTimer;

    private static final float DEFAULT_SPEED = 5f;

    public AimedFiring(float fireRate) {
        this(fireRate, 0.25f, DEFAULT_SPEED, null);
    }

    public AimedFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public AimedFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public AimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public AimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, 1);
    }

    /** @param bulletDamage damage to the player (and to the source enemy if reflected) */
    public AimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, 0f, 0f);
    }

    /** @param targetOffsetX,targetOffsetY aim point offset from the player, in world units */
    public AimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, float targetOffsetX, float targetOffsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, targetOffsetX, targetOffsetY, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile,hitboxSpec see SpeedProfile and HitboxSpec */
    public AimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, float targetOffsetX, float targetOffsetY, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.targetOffsetX = targetOffsetX;
        this.targetOffsetY = targetOffsetY;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer >= fireRate) {
            shootTimer = 0;
            float targetX = playerHitbox.x + targetOffsetX;
            float targetY = playerHitbox.y + targetOffsetY;
            Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, sprite.getX() + sprite.getWidth()/2 + offsetX, sprite.getY() + sprite.getHeight()/2 + offsetY, targetX, targetY, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
