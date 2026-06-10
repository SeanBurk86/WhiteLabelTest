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

    @Override
    default void reset() {}
}
