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

/** Fires one or more full-width horizontal curtains of bullets straight down, laid out in
 *  world-space X (from marginX to worldWidth - marginX, every spacing units) rather than relative
 *  to the firing enemy's own position - the only other pattern here that does that is Orbiting, and
 *  even that still centers on the enemy. This is a scripted "wall with a hole" drill: the enemy
 *  driving it can sit anywhere safe (away from BaseEnemy's ceasefire zone, which silences the
 *  *enemy*, never an individual already-spawned bullet) while the curtain itself still spans the
 *  whole play area.
 *
 *  The hole is carved by lane INDEX (gapLaneStart/gapLaneCount), not by a raw X-distance window -
 *  laying bullets out with a float accumulator and then excluding "anything within gapWidth of
 *  gapCenterX" only produces exactly one skipped lane when gapCenterX happens to land exactly on a
 *  lane; nudge it off-grid (as a designer hand-tuning the gap position easily can) and the window
 *  can straddle zero or two lanes instead of one, quietly leaving the wall solid. Indexing directly
 *  makes "skip exactly gapLaneCount lanes" true for any gapLaneStart.
 *
 *  gapLaneSequence (optional) fires one volley per array entry, fireRate seconds apart, each with
 *  that entry as its gapLaneStart - since every volley falls at the same bulletSpeed from the same
 *  Y, closely-spaced volleys stack into one continuous, vertically dense column with the gap
 *  sliding smoothly between rows (e.g. left, left, center, right, right, center, left... traces a
 *  weave the player has to physically follow to stay in the safe lane, rather than a single flat
 *  wall). Without it, fires a single volley from gapLaneStart, immediately - the original
 *  single-shot behavior, unchanged for every pattern authored before gapLaneSequence existed.
 *
 *  reset() re-arms the whole sequence for pooled reuse. */
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

    /** @param gapLaneSequence null/empty for the original single-volley behavior (uses
     *  gapLaneStart); otherwise one volley per entry, fireRate seconds apart, each entry becoming
     *  that volley's gapLaneStart (gapLaneCount stays the same width throughout). */
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

        // First volley fires immediately (matches the original single-shot pattern's timing); only
        // volleys after that wait fireRate apart.
        if (volleysFired > 0) {
            timer += delta;
            if (timer < fireRate) return;
            timer = 0f;
        }

        int currentGapStart = totalVolleys > 1 ? gapLaneSequence[volleysFired] : gapLaneStart;
        volleysFired++;

        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;
        float originY = sprite.getY() + sprite.getHeight() / 2f;

        // +1 so a curtain that divides evenly still includes its final lane (e.g. margin 0.25,
        // spacing 0.4, span 8.5 -> 21.25 steps -> 22 lanes, indices 0..21).
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
