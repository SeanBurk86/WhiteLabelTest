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

    /** Degrees getRectangle()'s box is rotated (same convention as Sprite.setRotation) around the
     *  bottom-center of that box, i.e. (x + width/2, y). 0 for axis-aligned bullets — collision
     *  code should treat that as a plain AABB check and only do rotated-rect math otherwise. */
    default float getRotation() { return 0f; }

    /** Radius of a circular hitbox centered on getRectangle()'s center, for a bullet round enough
     *  that a circle fits it better than its bounding box. -1 (the default) means "not circular" -
     *  collision code falls back to getRectangle() (and getRotation(), if set) instead. A beam
     *  like LaserBullet stays rectangular since a circle can't represent its shape. */
    default float getHitRadius() { return -1f; }

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
