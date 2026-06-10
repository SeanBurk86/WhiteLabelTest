package whitelabeltest.player.powerups;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.player.Player;

public interface Powerup extends Pool.Poolable {
    void init(Texture texture, float x, float y, float worldWidth, float worldHeight);
    void update(float delta);
    void draw(SpriteBatch batch);
    boolean isOffScreen();
    Rectangle getRectangle();
    void apply(Player player);

    @Override
    default void reset() {}
}
