package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.LaserBullet;
import whitelabeltest.gamemanagers.ObjectPools;

/** Fires a persistent beam (see LaserBullet) anchored at the enemy's emission point instead of a
 *  bullet that travels away. The beam can sweep by giving it a nonzero angularSpeed. */
public class LaserFiring implements FiringPattern {
    public static final float DEFAULT_LENGTH = 12f;
    public static final float DEFAULT_DURATION = 2.0f;

    private final float fireRate;
    private final float thickness;
    private final float length;
    private final float angularSpeed;
    private final float fireAngle; // NaN means "aim at the player at the moment of firing"
    private final float duration;
    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private float shootTimer;

    public LaserFiring(float fireRate) {
        this(fireRate, 0.3f, DEFAULT_LENGTH, 0f, Float.NaN, DEFAULT_DURATION, null, 0f, 0f);
    }

    public LaserFiring(float fireRate, float thickness) {
        this(fireRate, thickness, DEFAULT_LENGTH, 0f, Float.NaN, DEFAULT_DURATION, null, 0f, 0f);
    }

    public LaserFiring(float fireRate, float thickness, float length) {
        this(fireRate, thickness, length, 0f, Float.NaN, DEFAULT_DURATION, null, 0f, 0f);
    }

    public LaserFiring(float fireRate, float thickness, float length, float angularSpeed, float fireAngle, float duration, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY) {
        this(fireRate, thickness, length, angularSpeed, fireAngle, duration, spriteOverride, offsetX, offsetY, 1);
    }

    public LaserFiring(float fireRate, float thickness, float length, float angularSpeed, float fireAngle, float duration, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage) {
        this.fireRate = fireRate;
        this.thickness = thickness;
        this.length = length;
        this.angularSpeed = angularSpeed;
        this.fireAngle = fireAngle;
        this.duration = duration;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float originX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float originY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        float startAngle = Float.isNaN(fireAngle)
            ? new Vector2(playerHitbox.x - originX, playerHitbox.y - originY).angleDeg()
            : fireAngle;

        LaserBullet b = ObjectPools.laserBulletPool.obtain();
        b.init(animation, originX, originY, startAngle, angularSpeed, length, thickness, duration, bulletDamage, self);
        enemyBullets.add(b);
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
