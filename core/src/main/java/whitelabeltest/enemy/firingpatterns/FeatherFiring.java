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
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.FeatherBullet;
import whitelabeltest.gamemanagers.ObjectPools;

/** Fires bullets that waft down the screen like a feather - a slow, gently-swaying descent instead
 *  of a straight or aggressively wavy shot. See FeatherBullet for the actual motion; this class
 *  just spawns one on each fireRate tick, mirroring SineWaveFiring's own shape exactly (same
 *  constructor chain and defaults resolution) so the two patterns are interchangeable in the editor
 *  and reuse the same amplitude/frequency fields on FiringPatternDef. */
public class FeatherFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;

    // A feather's own defaults: wider, lazier sway than SineWave's (SineWaveFiring.
    // DEFAULT_AMPLITUDE/DEFAULT_FREQUENCY) and a much slower fall - see DEFAULT_SPEED below.
    public static final float DEFAULT_AMPLITUDE = 1.4f;
    public static final float DEFAULT_FREQUENCY = 1.1f;
    private static final float DEFAULT_SPEED = 1.5f;

    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    private final float amplitude;
    private final float frequency;

    public FeatherFiring(float fireRate) {
        this(fireRate, 0.2f, DEFAULT_SPEED, null);
    }

    public FeatherFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public FeatherFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public FeatherFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public FeatherFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, 1);
    }

    public FeatherFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile how bulletSpeed changes over each bullet's flight - see SpeedProfile
     *  @param hitboxSpec each bullet's collision hitbox, independent of its visual size - see
     *  HitboxSpec */
    public FeatherFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, speedProfile, hitboxSpec, DEFAULT_AMPLITUDE, DEFAULT_FREQUENCY);
    }

    /** @param amplitude, frequency the sway's shape - see FiringPatternDef.amplitude/frequency */
    public FeatherFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, SpeedProfile speedProfile, HitboxSpec hitboxSpec, float amplitude, float frequency) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.shootTimer = fireRate;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        FeatherBullet b1 = ObjectPools.featherBulletPool.obtain();
        b1.init(animation, centerX, centerY, amplitude, frequency, 0, bulletSpeed, bulletSize, bulletDamage, self, speedProfile, hitboxSpec);
        enemyBullets.add(b1);
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}
