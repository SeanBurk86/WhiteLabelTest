package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;

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

    @Override
    default void reset() {}
}
