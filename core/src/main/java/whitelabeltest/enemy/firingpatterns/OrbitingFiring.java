package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.OrbitingBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class OrbitingFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;

    private static final float DEFAULT_CENTER_SPEED = 4.0f;
    public static final float DEFAULT_ORBIT_RADIUS = 0.4f;
    public static final float DEFAULT_ORBIT_SPEED = 5.0f; // radians per second

    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final float orbitRadius;
    private final float orbitSpeed;

    public OrbitingFiring(float fireRate) {
        this(fireRate, 0.5f, DEFAULT_CENTER_SPEED, null);
    }

    public OrbitingFiring(float fireRate, float bulletSize) {
        this(fireRate, bulletSize, DEFAULT_CENTER_SPEED, null);
    }

    public OrbitingFiring(float fireRate, float bulletSize, float bulletSpeed) {
        this(fireRate, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public OrbitingFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, 0f, 0f);
    }

    /** @param offsetX, offsetY emission point offset from the sprite's center, in world units */
    public OrbitingFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, 1);
    }

    public OrbitingFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, offsetX, offsetY, bulletDamage, DEFAULT_ORBIT_RADIUS, DEFAULT_ORBIT_SPEED);
    }

    /** @param orbitRadius, orbitSpeed each bullet's spin around its own drifting center - see
     *  FiringPatternDef.orbitRadius/orbitSpeed */
    public OrbitingFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage, float orbitRadius, float orbitSpeed) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.shootTimer = fireRate; // fire immediately on first update
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        float playerX = playerHitbox.x;
        float playerY = playerHitbox.y;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        Vector2 vel = new Vector2(playerX - centerX, playerY - centerY).nor().scl(bulletSpeed);

        OrbitingBullet b1 = ObjectPools.orbitingBulletPool.obtain();
        b1.init(animation, centerX, centerY, vel.x, vel.y, orbitRadius, orbitSpeed, 0, bulletSize, bulletDamage, self);
        enemyBullets.add(b1);

        OrbitingBullet b2 = ObjectPools.orbitingBulletPool.obtain();
        b2.init(animation, centerX, centerY, vel.x, vel.y, orbitRadius, orbitSpeed, MathUtils.PI, bulletSize, bulletDamage, self);
        enemyBullets.add(b2);
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}