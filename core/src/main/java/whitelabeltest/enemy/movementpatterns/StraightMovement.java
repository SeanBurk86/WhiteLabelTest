package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;

public class StraightMovement implements MovementPattern {
    private final float speed;

    public StraightMovement(float speed) {
        this.speed = speed;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Rectangle playerHitbox, boolean inverseMovement) {
        float currentSpeed = speed;
        if (inverseMovement) {
            currentSpeed = -speed; // Reverse direction
        }
        sprite.translateY(-currentSpeed * delta); // Negative for downward, so positive for upward if inverted
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public boolean isFinished() { return false; }
    @Override
    public void reset() {}
}
