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
    private final Vector2 tempPos = new Vector2();

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
        tempPos.set(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);

        float dist = tempPos.dst(playerHitbox.x, playerHitbox.y);

        // Even once within stopDistance, keep advancing until the whole sprite has entered the
        // play area - otherwise an enemy that spawns off-screen close to the player (or with a
        // generous stopDistance) could stop while still partially off-screen.
        if (dist <= stopDistance && isFullyOnScreen(rectangle, worldWidth, worldHeight)) {
            finished = true;
        } else {
            tempDir.set(playerHitbox.x, playerHitbox.y).sub(tempPos).nor();
            tempDir.rotateDeg(angleOffsetDeg);
            if (inverseMovement) {
                tempDir.scl(-1); // Move away from player
            }
            sprite.translate(tempDir.x * speed * delta, tempDir.y * speed * delta);
            sprite.setRotation(tempDir.angleDeg() + 90f);
            rectangle.setPosition(sprite.getX(), sprite.getY());
        }
    }

    private static boolean isFullyOnScreen(Rectangle rectangle, float worldWidth, float worldHeight) {
        return rectangle.x >= 0f && rectangle.x + rectangle.width <= worldWidth
            && rectangle.y >= 0f && rectangle.y + rectangle.height <= worldHeight;
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
