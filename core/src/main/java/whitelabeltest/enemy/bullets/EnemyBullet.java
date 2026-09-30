package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.Enemy;

/** A pooled enemy bullet. Its hitbox is getRectangle() (or a circle of getHitRadius()), scaled by
 *  getHitboxScale() around its center, shifted by the offset, and rotated by getRotation(). */
public interface EnemyBullet extends Pool.Poolable {
    void update(float delta);
    void draw(SpriteBatch batch);
    boolean isOffScreen();
    Rectangle getRectangle();
    int getDamage();

    /** Hitbox rotation in degrees about the pivot; 0 = plain AABB test. Bullets that turn to face
     *  their travel direction return their sprite rotation. */
    default float getRotation() { return 0f; }

    /** Rotation pivot. Defaults to the rect's bottom-center (a laser's emission point); bullets that
     *  rotate about their sprite center override it. */
    default float getRotationPivotX() { Rectangle r = getRectangle(); return r.x + r.width / 2f; }
    default float getRotationPivotY() { return getRectangle().y; }

    /** Radius of a circular hitbox at the rect's center, or -1 to use the rectangle. */
    default float getHitRadius() { return -1f; }

    /** Hitbox size multiplier around its center (1 = fits the sprite). */
    default float getHitboxScale() { return 1f; }

    /** Hitbox center offset in the bullet's unrotated frame (rotated with the bullet). */
    default float getHitboxOffsetX() { return 0f; }
    default float getHitboxOffsetY() { return 0f; }

    /** The enemy that fired it (for shield reflections). May be stale after pooling, so check
     *  isActive() before homing on it. */
    default Enemy getSourceEnemy() { return null; }

    /** The current sprite, so a reflected bolt can copy its look; null if unsupported. */
    default Sprite getSprite() { return null; }

    @Override
    default void reset() {}
}
