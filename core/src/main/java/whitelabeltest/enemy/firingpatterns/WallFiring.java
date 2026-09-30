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

/** Fires full-width curtains of bullets straight down, laid out in world X (marginX to
 *  worldWidth - marginX, every `spacing`), independent of the enemy's position. The gap is chosen by
 *  lane index (gapLaneStart, gapLaneCount lanes wide), so it's always exactly that many lanes.
 *
 *  With gapLaneSequence, fires one volley per entry, fireRate apart, each using that entry as its
 *  gap start; close volleys stack into a dense column with a weaving gap. Without it, fires a single
 *  volley. The first volley fires immediately. */
public class WallFiring implements FiringPattern {
    private final float bulletSize;
    private final float bulletSpeed;
    private final int bulletDamage;
    private final Animation<TextureRegion> spriteOverride;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    private final float worldWidth;
    private final float marginX;
    private final float spacing;
    private final int gapLaneStart;
    private final int gapLaneCount;
    private final int[] gapLaneSequence;
    private final float fireRate;

    private float timer;
    private int volleysFired;

    public WallFiring(float bulletSize, float bulletSpeed, int bulletDamage, Animation<TextureRegion> spriteOverride,
                       SpeedProfile speedProfile, HitboxSpec hitboxSpec, float worldWidth, float marginX,
                       float spacing, int gapLaneStart, int gapLaneCount) {
        this(bulletSize, bulletSpeed, bulletDamage, spriteOverride, speedProfile, hitboxSpec, worldWidth, marginX,
            spacing, gapLaneStart, gapLaneCount, null, 0f);
    }

    /** @param gapLaneSequence null/empty = one volley at gapLaneStart; else one volley per entry. */
    public WallFiring(float bulletSize, float bulletSpeed, int bulletDamage, Animation<TextureRegion> spriteOverride,
                       SpeedProfile speedProfile, HitboxSpec hitboxSpec, float worldWidth, float marginX,
                       float spacing, int gapLaneStart, int gapLaneCount, int[] gapLaneSequence, float fireRate) {
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.bulletDamage = bulletDamage;
        this.spriteOverride = spriteOverride;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.worldWidth = worldWidth;
        this.marginX = marginX;
        this.spacing = spacing;
        this.gapLaneStart = gapLaneStart;
        this.gapLaneCount = gapLaneCount;
        this.gapLaneSequence = gapLaneSequence;
        this.fireRate = fireRate;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        int totalVolleys = (gapLaneSequence != null && gapLaneSequence.length > 0) ? gapLaneSequence.length : 1;
        if (volleysFired >= totalVolleys) return;

        // First volley fires immediately, then every fireRate seconds.
        if (volleysFired > 0) {
            timer += delta;
            if (timer < fireRate) return;
            timer = 0f;
        }

        int currentGapStart = totalVolleys > 1 ? gapLaneSequence[volleysFired] : gapLaneStart;
        volleysFired++;

        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;
        float originY = sprite.getY() + sprite.getHeight() / 2f;

        // +1 so an evenly dividing curtain still includes its last lane (e.g. span 8.5 / 0.4 -> 22 lanes).
        int laneCount = (int) ((worldWidth - marginX * 2f) / spacing + 0.0001f) + 1;
        for (int i = 0; i < laneCount; i++) {
            if (i >= currentGapStart && i < currentGapStart + gapLaneCount) continue;

            float x = marginX + i * spacing;
            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, x, originY, x, originY - 1f, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        timer = 0f;
        volleysFired = 0;
    }
}
