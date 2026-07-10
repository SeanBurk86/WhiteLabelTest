package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class SeekingMovement implements MovementPattern {
    public static final float DEFAULT_STOP_DISTANCE = 3.0f;

    private final float speed;
    private final float stopDistance;
    private final float angleOffsetDeg;
    private boolean finished = false;
    private final Vector2 tempDir = new Vector2();

    public SeekingMovement(float speed, float stopDistance) {
        this(speed, stopDistance, DEFAULT_ANGLE_DEG);
    }
    public SeekingMovement(float speed, float stopDistance, float angleDeg) {
        this.speed = speed;
        this.stopDistance = stopDistance;
        this.angleOffsetDeg = angleDeg - DEFAULT_ANGLE_DEG;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        Vector2 targetPos = new Vector2(playerHitbox.x, playerHitbox.y);
        Vector2 currentPos = new Vector2(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);

        float dist = currentPos.dst(targetPos);

        if (dist <= stopDistance) {
            finished = true;
        } else {
            tempDir.set(targetPos).sub(currentPos).nor();
            tempDir.rotateDeg(angleOffsetDeg);
            if (inverseMovement) {
                tempDir.scl(-1); // Move away from player
            }
            sprite.translate(tempDir.x * speed * delta, tempDir.y * speed * delta);
            sprite.setRotation(tempDir.angleDeg() + 90f);
            rectangle.setPosition(sprite.getX(), sprite.getY());
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void reset() {
        finished = false;
    }
}
