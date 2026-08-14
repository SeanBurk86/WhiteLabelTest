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

public class QuarterCircleFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private final float spreadDegrees;
    private final int numBullets;
    private float shootTimer;
    private static final int DEFAULT_NUM_BULLETS = 9;
    private static final float DEFAULT_SPREAD_DEGREES = 90f;
    private static final float DEFAULT_SPEED = 5f;
    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final float targetOffsetX;
    private final float targetOffsetY;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    // Overrides the fan's aim direction to a fixed world-space angle (degrees, standard atan2
    // convention) instead of tracking the player - NaN (the default) keeps the original live
    // tracking behavior. Lets a scripted volley blanket one side of the arena regardless of where
    // the player happens to be standing, e.g. alternating fixed left/right angles across
    // successive volleys to force the player to relocate to whichever side is clear next, rather
    // than always converging back on wherever they're already standing.
    private final float fixedAimAngleDeg;

    public QuarterCircleFiring(float fireRate) {
        this(fireRate, 0.25f, DEFAULT_SPEED, null);
    }

    public QuarterCircleFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_SPEED, null);
    }

    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, DEFAULT_SPREAD_DEGREES, DEFAULT_NUM_BULLETS);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation
     *  @param spreadDegrees total angular width of the fan, centered on the aim direction
     *  @param numBullets how many bullets make up the fan (must be >= 2) */
    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, spreadDegrees, numBullets, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, spreadDegrees, numBullets, offsetX, offsetY, 1);
    }

    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, spreadDegrees, numBullets, offsetX, offsetY, bulletDamage, 0f, 0f);
    }

    /** @param targetOffsetX, targetOffsetY offset from the player's position that the fan is
     *  centered on, in world units - lets the spread lead/trail the player or center on a point
     *  near them instead of dead-on */
    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY, int bulletDamage, float targetOffsetX, float targetOffsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, spreadDegrees, numBullets, offsetX, offsetY, bulletDamage, targetOffsetX, targetOffsetY, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile how bulletSpeed changes over each bullet's flight - see SpeedProfile
     *  @param hitboxSpec each bullet's collision hitbox, independent of its visual size - see
     *  HitboxSpec */
    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY, int bulletDamage, float targetOffsetX, float targetOffsetY, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, spreadDegrees, numBullets, offsetX, offsetY, bulletDamage, targetOffsetX, targetOffsetY, speedProfile, hitboxSpec, Float.NaN);
    }

    /** @param fixedAimAngleDeg see the field doc - Float.NaN keeps tracking the player live */
    public QuarterCircleFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float spreadDegrees, int numBullets, float offsetX, float offsetY, int bulletDamage, float targetOffsetX, float targetOffsetY, SpeedProfile speedProfile, HitboxSpec hitboxSpec, float fixedAimAngleDeg) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.spreadDegrees = spreadDegrees;
        this.numBullets = numBullets;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.targetOffsetX = targetOffsetX;
        this.targetOffsetY = targetOffsetY;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.fixedAimAngleDeg = fixedAimAngleDeg;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        float aimAngle;
        if (!Float.isNaN(fixedAimAngleDeg)) {
            aimAngle = fixedAimAngleDeg;
        } else {
            float playerX = playerHitbox.x + targetOffsetX;
            float playerY = playerHitbox.y + targetOffsetY;
            aimAngle = new Vector2(playerX - centerX, playerY - centerY).angleDeg();
        }
        float startAngle = numBullets > 1 ? aimAngle - spreadDegrees / 2 : aimAngle;
        float step = numBullets > 1 ? spreadDegrees / (numBullets - 1) : 0f;

        for (int i = 0; i < numBullets; i++) {
            float angle = startAngle + i * step;
            Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, centerX, centerY, centerX + dir.x, centerY + dir.y, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
