package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

/** Like AimedFiring, but the target is a fixed world-space point set at construction time rather
 *  than the player's current position — useful for scripted shots at a known location. */
public class PointAimedFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private final Animation<TextureRegion> spriteOverride;
    private final float targetX;
    private final float targetY;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private float shootTimer;

    public PointAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float targetX, float targetY, float offsetX, float offsetY) {
        this(fireRate, bulletSize, bulletSpeed, spriteOverride, targetX, targetY, offsetX, offsetY, 1);
    }

    public PointAimedFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float targetX, float targetY, float offsetX, float offsetY, int bulletDamage) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.targetX = targetX;
        this.targetY = targetY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer >= fireRate) {
            shootTimer = 0;
            Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, sprite.getX() + sprite.getWidth()/2 + offsetX, sprite.getY() + sprite.getHeight()/2 + offsetY, targetX, targetY, bulletSize, bulletSpeed, bulletDamage, self);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}