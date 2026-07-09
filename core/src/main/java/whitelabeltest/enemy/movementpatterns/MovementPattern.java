package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;

public interface MovementPattern {
    // Shared "no rotation" baseline (standard math convention: 0 = right, 90 = up; 270 = down,
    // the heading every pattern used before angles were configurable). Enemy definitions default
    // movementAngle to this value, and each pattern treats it as "use my original orientation".
    float DEFAULT_ANGLE_DEG = 270f;

    void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement);
    boolean isFinished();
    void reset();
}
