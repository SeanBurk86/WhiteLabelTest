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

/** Fires full-width rows straight down (laid out in world X like WallFiring), each row filling
 *  alternate lanes (even, then odd), so the screen fills with a checkerboard of dots the player must
 *  weave between. The first row fires immediately. */
public class PolkaDotFiring implements FiringPattern {
    private final float bulletSize;
    private final float bulletSpeed;
    private final int bulletDamage;
    private final Animation<TextureRegion> spriteOverride;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    private final float worldWidth;
    private final float marginX;
    private final float spacing;
    private final float fireRate;

    private float timer;
    private int rowIndex;

    public PolkaDotFiring(float bulletSize, float bulletSpeed, int bulletDamage, Animation<TextureRegion> spriteOverride,
                           SpeedProfile speedProfile, HitboxSpec hitboxSpec, float worldWidth, float marginX,
                           float spacing, float fireRate) {
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.bulletDamage = bulletDamage;
        this.spriteOverride = spriteOverride;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.worldWidth = worldWidth;
        this.marginX = marginX;
        this.spacing = spacing;
        this.fireRate = fireRate;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        // First row fires immediately, then every fireRate seconds.
        if (rowIndex > 0) {
            timer += delta;
            if (timer < fireRate) return;
            timer = 0f;
        }

        boolean evenRow = rowIndex % 2 == 0;
        rowIndex++;

        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;
        float originY = sprite.getY() + sprite.getHeight() / 2f;

        // +1 so an evenly dividing field still includes its last lane.
        int laneCount = (int) ((worldWidth - marginX * 2f) / spacing + 0.0001f) + 1;
        for (int i = 0; i < laneCount; i++) {
            if ((i % 2 == 0) != evenRow) continue;

            float x = marginX + i * spacing;
            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, x, originY, x, originY - 1f, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        timer = 0f;
        rowIndex = 0;
    }
}
