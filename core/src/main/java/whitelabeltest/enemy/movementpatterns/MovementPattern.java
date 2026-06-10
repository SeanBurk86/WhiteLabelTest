package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;

public interface MovementPattern {
    void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Rectangle playerHitbox, boolean inverseMovement);
    boolean isFinished();
    void reset();
}
