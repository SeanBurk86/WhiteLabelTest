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
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.spreadDegrees = spreadDegrees;
        this.numBullets = numBullets;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2 + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2 + offsetY;
        float playerX = playerHitbox.x;
        float playerY = playerHitbox.y;
        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

        float aimAngle = new Vector2(playerX - centerX, playerY - centerY).angleDeg();
        float startAngle = numBullets > 1 ? aimAngle - spreadDegrees / 2 : aimAngle;
        float step = numBullets > 1 ? spreadDegrees / (numBullets - 1) : 0f;

        for (int i = 0; i < numBullets; i++) {
            float angle = startAngle + i * step;
            Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, centerX, centerY, centerX + dir.x, centerY + dir.y, bulletSize, bulletSpeed);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}
