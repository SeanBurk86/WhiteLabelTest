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
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.ShapeBullet;
import whitelabeltest.gamemanagers.ObjectPools;

/** Fires whole bullet PICTURES instead of single shots - each volley is `shapeCount` pictures fanned
 *  across spreadDegrees around the aim angle, every picture being one bullet per point of
 *  `shapePoints`. All of a picture's bullets leave the emitter together and only resolve into the
 *  picture mid-flight, at formTime, before drifting apart again - see ShapeBullet.
 *
 *  shapePoints are authored as the shape should look while travelling straight DOWN the screen (its
 *  "top" pointing back at the emitter), in world units at scale 1, centered on the cluster's own
 *  origin. With rotateWithDirection each cluster is turned by (travel angle - 270) so it keeps facing
 *  back at the emitter whichever way it flies; flipX mirrors the shape left-to-right first (e.g. a
 *  left hand from a right hand). Fires a volley the moment it starts (or restarts inside a
 *  Sequence), then every fireRate seconds. */
public class ShapeFiring implements FiringPattern {
    private final float fireRate;
    private final float bulletSize;
    private final float bulletSpeed;
    private final Animation<TextureRegion> spriteOverride;
    private final float offsetX;
    private final float offsetY;
    private final int bulletDamage;
    private final SpeedProfile speedProfile;
    private final HitboxSpec hitboxSpec;
    private final float[] shapePoints;
    private final int shapeCount;
    private final float spreadDegrees;
    // NaN aims the volley's center at the player (plus targetOffset) instead.
    private final float fixedAngle;
    private final float targetOffsetX;
    private final float targetOffsetY;
    private final float formScale;
    private final float formTime;
    private final float driftRatio;
    private final boolean flipX;
    private final boolean rotateWithDirection;

    private float shootTimer;

    public ShapeFiring(float fireRate, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride, float offsetX, float offsetY, int bulletDamage,
                       SpeedProfile speedProfile, HitboxSpec hitboxSpec, float[] shapePoints, int shapeCount, float spreadDegrees, float fixedAngle,
                       float targetOffsetX, float targetOffsetY, float formScale, float formTime, float driftRatio,
                       boolean flipX, boolean rotateWithDirection) {
        this.fireRate = fireRate;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bulletDamage = bulletDamage;
        this.speedProfile = speedProfile;
        this.hitboxSpec = hitboxSpec;
        this.shapePoints = shapePoints != null ? shapePoints : new float[]{0f, 0f};
        this.shapeCount = Math.max(1, shapeCount);
        this.spreadDegrees = spreadDegrees;
        this.fixedAngle = fixedAngle;
        this.targetOffsetX = targetOffsetX;
        this.targetOffsetY = targetOffsetY;
        this.formScale = formScale;
        this.formTime = formTime;
        this.driftRatio = driftRatio;
        this.flipX = flipX;
        this.rotateWithDirection = rotateWithDirection;
        this.shootTimer = fireRate; // fire immediately on first update
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0f;

        Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;
        if (animation == null) return;
        float centerX = sprite.getX() + sprite.getWidth() / 2f + offsetX;
        float centerY = sprite.getY() + sprite.getHeight() / 2f + offsetY;

        float aimAngle = !Float.isNaN(fixedAngle) ? fixedAngle
            : MathUtils.atan2(playerHitbox.y + targetOffsetY - centerY, playerHitbox.x + targetOffsetX - centerX) * MathUtils.radiansToDegrees;
        float firstAngle = shapeCount > 1 ? aimAngle - spreadDegrees / 2f : aimAngle;
        float step = shapeCount > 1 ? spreadDegrees / (shapeCount - 1) : 0f;

        for (int s = 0; s < shapeCount; s++) {
            float angle = firstAngle + s * step;
            float turn = rotateWithDirection ? (angle - 270f) * MathUtils.degreesToRadians : 0f;
            float cos = MathUtils.cos(turn), sin = MathUtils.sin(turn);
            for (int i = 0; i + 1 < shapePoints.length; i += 2) {
                float px = flipX ? -shapePoints[i] : shapePoints[i];
                float py = shapePoints[i + 1];
                ShapeBullet b = ObjectPools.shapeBulletPool.obtain();
                b.init(animation, centerX, centerY, angle, px * cos - py * sin, px * sin + py * cos,
                    formScale, formTime, driftRatio,
                    bulletSize, bulletSpeed, bulletDamage, self, speedProfile, hitboxSpec);
                enemyBullets.add(b);
            }
        }
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}
