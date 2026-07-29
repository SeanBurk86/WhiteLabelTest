package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.Enemy;

public interface EnemyBullet extends Pool.Poolable {
    void update(float delta);
    void draw(SpriteBatch batch);
    boolean isOffScreen();
    Rectangle getRectangle();
    int getDamage();

    /** Degrees getRectangle()'s box is rotated (same convention as Sprite.setRotation) around
     *  (getRotationPivotX(), getRotationPivotY()). 0 for axis-aligned bullets — collision code
     *  should treat that as a plain AABB check and only do rotated-rect math otherwise. A bullet
     *  that visually turns to face its direction of travel (see AimedEnemyBullet/DrifterBullet/
     *  SineBullet/ExplodingAimedBullet) overrides this to match its sprite's current rotation, so
     *  a Rectangle-shaped hitbox (see getHitboxScale()) turns together with the sprite instead of
     *  staying axis-aligned. */
    default float getRotation() { return 0f; }

    /** World-space point getRotation() rotates the hitbox around. Defaults to getRectangle()'s
     *  bottom-center (x + width/2, y) — the historical convention, matching e.g. LaserBullet whose
     *  rect already positions that point at its emission point. A bullet that rotates visually
     *  around its sprite's own center (Sprite.setOriginCenter()) overrides this to return that
     *  center instead, so the hitbox rotates the same way the sprite does. */
    default float getRotationPivotX() { Rectangle r = getRectangle(); return r.x + r.width / 2f; }
    default float getRotationPivotY() { return getRectangle().y; }

    /** Radius of a circular hitbox centered on getRectangle()'s center (before getHitboxScale()/
     *  getHitboxOffsetX()/getHitboxOffsetY() are applied), for a bullet round enough that a circle
     *  fits it better than its bounding box. -1 (the default) means "not circular" - collision
     *  code falls back to getRectangle() (and getRotation(), if set) instead. A beam like
     *  LaserBullet stays rectangular since a circle can't represent its shape. */
    default float getHitRadius() { return -1f; }

    /** Multiplies the hitbox's auto-derived size - both getHitRadius() and getRectangle()'s
     *  dimensions - around its own center. 1 (the default) keeps the hitbox exactly matching the
     *  sprite/circle it's derived from. See BulletDef.hitboxScale for how a bullet definition
     *  authors this. */
    default float getHitboxScale() { return 1f; }

    /** World-unit offset of the hitbox's center from getRectangle()'s center, defined in the
     *  bullet's own unrotated frame and applied after getHitboxScale() - collision code rotates
     *  this vector by getRotation() before using it, so the offset stays attached to (and turns
     *  with) the sprite instead of always pointing the same screen direction. 0 (the default)
     *  keeps the hitbox centered on the sprite regardless of rotation. See
     *  BulletDef.hitboxOffsetX/hitboxOffsetY for how a bullet definition authors this. */
    default float getHitboxOffsetX() { return 0f; }
    default float getHitboxOffsetY() { return 0f; }

    /** The enemy that fired this bullet, if any - lets the player's reflect shield bounce a
     *  bullet back at its own source. May be stale (the enemy could since have died and its
     *  pooled instance been reused) - callers should check isActive() before homing on it. */
    default Enemy getSourceEnemy() { return null; }

    /** The bullet's own current visual, so something that copies its appearance (e.g. a
     *  reflected bolt) can match texture, region and size exactly. Null if unsupported. */
    default Sprite getSprite() { return null; }

    @Override
    default void reset() {}
}
