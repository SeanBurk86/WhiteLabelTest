package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class StraightMovement implements MovementPattern {
    private final float speed;
    private final Vector2 direction;
    private final Vector2 facing = new Vector2();

    public StraightMovement(float speed) {
        this(speed, DEFAULT_ANGLE_DEG);
    }

    public StraightMovement(float speed, float angleDeg) {
        this.speed = speed;
        this.direction = new Vector2(1, 0).setAngleDeg(angleDeg);
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        float dirSign = inverseMovement ? -1f : 1f; // Reverse direction
        sprite.translate(direction.x * speed * dirSign * delta, direction.y * speed * dirSign * delta);
        facing.set(direction).scl(dirSign);
        sprite.setRotation(facing.angleDeg() + 90f);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public boolean isFinished() { return false; }
    @Override
    public void reset() {}
}
