package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;

public class ZigZagMovement implements MovementPattern {
    private final float speedX;
    private final float speedY;
    private boolean movingRight;

    public ZigZagMovement(float speedX, float speedY) {
        this.speedX = speedX;
        this.speedY = speedY;
        this.movingRight = true;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        if (movingRight) {
            sprite.translateX(speedX * delta);
            if (sprite.getX() > worldWidth - sprite.getWidth()) {
                movingRight = false;
            }
        } else {
            sprite.translateX(-speedX * delta);
            if (sprite.getX() < 0) {
                movingRight = true;
            }
        }

        float currentSpeedY = speedY;
        if (inverseMovement) {
            currentSpeedY = -speedY;
        }
        sprite.translateY(-currentSpeedY * delta);

        sprite.setFlip(!movingRight, false);

        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public boolean isFinished() { return false; }

    @Override
    public void reset() {
        movingRight = MathUtils.randomBoolean();
    }
}
