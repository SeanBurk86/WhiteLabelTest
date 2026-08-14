package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.HitboxSpec;
import whitelabeltest.enemy.SpeedProfile;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

/** Fires numBullets-per-volley converging on the player's CURRENT position (playerHitbox, snapshot
 *  fresh each volley) from every angle around a full circle, each one deliberately aimed missDistance
 *  off-center rather than straight at it - so a player holding still sees a "wall closing in from
 *  everywhere" that never actually touches their hitbox, only their much larger sprite. Moving
 *  breaks the guarantee (a later volley re-aims at wherever they've moved to, but a bullet already
 *  in flight from an earlier volley keeps its original aim), which is the point - this is the
 *  "trust your tiny hitbox and don't panic-dodge" drill, not a movement test.
 *
 *  Each bullet's spawn point is the player's position projected outward along that bullet's angle
 *  until it hits the play area's edge (see update()'s ray/box projection), NOT a fixed distance out
 *  - AimedEnemyBullet.isOffScreen() uses hard-coded world bounds and culls a bullet the very first
 *  frame it exists past them, so a fixed spawnRadius large enough to clear the field from a
 *  center-ish position (e.g. 9+) puts most of a full-circle spread's spawn points outside those
 *  bounds - especially "downward" from a player resting near the bottom, where there's only ~1
 *  world unit of slack below y=0 before the cull triggers. Projecting onto the edge instead
 *  guarantees every spawn point is valid regardless of where the player is standing or which of the
 *  numBullets directions a given bullet comes from.
 *
 *  Every bullet in a volley shares the same missDistance and a consistent tangential offset
 *  direction (see update()'s perpAngle), so the whole ring reads as one coherent formation curving
 *  past the player rather than a scatter of independent near-misses.
 *
 *  Fires up to volleyCount volleys, fireRate seconds apart, then goes idle - reset() re-arms it for
 *  pooled reuse. */
public class RadialNearMissFiring implements FiringPattern {
    // Inset from AimedEnemyBullet's actual cull bounds (x in [0,9], y in [-1,13] for this game's
    // play area) so a spawned bullet's sprite - not just its center point - stays fully inside them.
    private static final float EDGE_MARGIN = 0.3f;

    private final float bulletSize;
    private final float bulletSpeed;
    private final int bulletDamage;
    private final Animation<TextureRegion> spriteOverride;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    private final float worldWidth;
    private final float worldHeight;
    private final int numBullets;
    private final float missDistance;
    private final float fireRate;
    private final int volleyCount;

    private float timer;
    private int volleysFired;

    public RadialNearMissFiring(float bulletSize, float bulletSpeed, int bulletDamage, Animation<TextureRegion> spriteOverride,
                                 SpeedProfile speedProfile, HitboxSpec hitboxSpec, float worldWidth, float worldHeight,
                                 int numBullets, float missDistance, float fireRate, int volleyCount) {
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.bulletDamage = bulletDamage;
        this.spriteOverride = spriteOverride;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.numBullets = numBullets;
        this.missDistance = missDistance;
        this.fireRate = fireRate;
        this.volleyCount = volleyCount;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        if (volleysFired >= volleyCount) return;

        timer += delta;
        if (timer < fireRate) return;
        timer = 0f;
        volleysFired++;

        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;
        float px = playerHitbox.x;
        float py = playerHitbox.y;
        float angleStep = 360f / numBullets;

        float xMin = EDGE_MARGIN;
        float xMax = worldWidth - EDGE_MARGIN;
        float yMin = -EDGE_MARGIN;
        float yMax = worldHeight + EDGE_MARGIN;

        for (int i = 0; i < numBullets; i++) {
            float angle = i * angleStep;
            float dx = MathUtils.cosDeg(angle);
            float dy = MathUtils.sinDeg(angle);

            // Ray from (px,py) in (dx,dy): smallest positive t that reaches any box edge, so the
            // spawn point lands exactly on whichever edge this direction points toward.
            float t = Float.MAX_VALUE;
            if (dx > 0.0001f) t = Math.min(t, (xMax - px) / dx);
            else if (dx < -0.0001f) t = Math.min(t, (xMin - px) / dx);
            if (dy > 0.0001f) t = Math.min(t, (yMax - py) / dy);
            else if (dy < -0.0001f) t = Math.min(t, (yMin - py) / dy);
            if (t <= 0f || t == Float.MAX_VALUE) continue; // player already sitting on this edge

            float spawnX = px + dx * t;
            float spawnY = py + dy * t;

            // Tangential (angle + 90) offset, not radial - keeps every bullet's straight-line path
            // the same missDistance from px/py regardless of which of the numBullets directions it
            // came from, and the shared +90 sense makes the whole ring curve past in one direction.
            float perpAngle = angle + 90f;
            float targetX = px + missDistance * MathUtils.cosDeg(perpAngle);
            float targetY = py + missDistance * MathUtils.sinDeg(perpAngle);

            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(animation, spawnX, spawnY, targetX, targetY, bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        timer = 0f;
        volleysFired = 0;
    }
}
