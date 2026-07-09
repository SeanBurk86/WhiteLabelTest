package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.OrbitingBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class OrbitingFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private float shootTimer;

    private static final float DEFAULT_CENTER_SPEED = 4.0f;
    private static final float ORBIT_RADIUS = 0.4f;
    private static final float ORBIT_SPEED = 5.0f; // radians per second

    private final Animation<TextureRegion> spriteOverride;

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
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.shootTimer = fireRate; // fire immediately on first update
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;
        float playerX = playerHitbox.x;
        float playerY = playerHitbox.y;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        Vector2 vel = new Vector2(playerX - centerX, playerY - centerY).nor().scl(bulletSpeed);

        OrbitingBullet b1 = ObjectPools.orbitingBulletPool.obtain();
        b1.init(animation, centerX, centerY, vel.x, vel.y, ORBIT_RADIUS, ORBIT_SPEED, 0, bulletSize);
        enemyBullets.add(b1);

        OrbitingBullet b2 = ObjectPools.orbitingBulletPool.obtain();
        b2.init(animation, centerX, centerY, vel.x, vel.y, ORBIT_RADIUS, ORBIT_SPEED, MathUtils.PI, bulletSize);
        enemyBullets.add(b2);
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}