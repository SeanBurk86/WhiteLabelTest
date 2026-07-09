package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class ZigZagMovement implements MovementPattern {
    private final float speedX;
    private final float speedY;
    private final float angleDeg;
    private boolean movingRight;

    // Tracks the sideways oscillation on its own axis, independent of the sprite's actual
    // (rotated) screen position, so the bounce works the same regardless of angleDeg.
    private float perpOffset;

    private final Vector2 tempDelta = new Vector2();

    public ZigZagMovement(float speedX, float speedY) {
        this(speedX, speedY, DEFAULT_ANGLE_DEG);
    }

    public ZigZagMovement(float speedX, float speedY, float angleDeg) {
        this.speedX = speedX;
        this.speedY = speedY;
        this.angleDeg = angleDeg;
        this.movingRight = true;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        if (movingRight) {
            perpOffset += speedX * delta;
            if (perpOffset > worldWidth - sprite.getWidth()) {
                movingRight = false;
            }
        } else {
            perpOffset -= speedX * delta;
            if (perpOffset < 0) {
                movingRight = true;
            }
        }

        float currentSpeedY = speedY;
        if (inverseMovement) {
            currentSpeedY = -speedY;
        }

        // Compose the frame's movement in ZigZag's default (straight-down) frame, then rotate the
        // whole pattern so it points along angleDeg.
        tempDelta.set((movingRight ? 1 : -1) * speedX, -currentSpeedY).scl(delta);
        tempDelta.rotateDeg(angleDeg - DEFAULT_ANGLE_DEG);

        sprite.translate(tempDelta.x, tempDelta.y);
        sprite.setRotation(tempDelta.angleDeg() + 90f);

        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public boolean isFinished() { return false; }

    @Override
    public void reset() {
        movingRight = MathUtils.randomBoolean();
        perpOffset = 0f;
    }
}