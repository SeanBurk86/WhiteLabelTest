package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Moves straight toward a fixed point in the gameplay area (world units, same space as
 *  worldWidth/worldHeight) and reports isFinished() once within stopDistance of it. */
public class MoveToPointMovement implements MovementPattern {
    public static final float DEFAULT_STOP_DISTANCE = 0.1f;

    private final float speed;
    private final float targetX;
    private final float targetY;
    private final float stopDistance;
    private boolean finished = false;
    private final Vector2 tempDir = new Vector2();
    private final Vector2 tempPos = new Vector2();

    public MoveToPointMovement(float speed, float targetX, float targetY) {
        this(speed, targetX, targetY, DEFAULT_STOP_DISTANCE);
    }

    public MoveToPointMovement(float speed, float targetX, float targetY, float stopDistance) {
        this.speed = speed;
        this.targetX = targetX;
        this.targetY = targetY;
        this.stopDistance = stopDistance;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        if (finished) return;

        tempPos.set(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);
        float dist = tempPos.dst(targetX, targetY);

        if (dist <= stopDistance) {
            finished = true;
            return;
        }

        tempDir.set(targetX, targetY).sub(tempPos).nor();
        float step = speed * delta;
        if (step > dist) step = dist;
        sprite.translate(tempDir.x * step, tempDir.y * step);
        sprite.setRotation(tempDir.angleDeg() + 90f);
        rectangle.setPosition(sprite.getX(), sprite.getY());
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