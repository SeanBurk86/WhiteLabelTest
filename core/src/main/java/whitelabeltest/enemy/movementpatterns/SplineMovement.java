package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class SplineMovement implements MovementPattern {
    private final CatmullRomSpline<Vector2> path;
    private float pathTime = 0;
    private final float pathDuration;
    private final Vector2 tempPos = new Vector2();
    private final Vector2 toPlayer = new Vector2();

    public SplineMovement(float worldHeight, float duration, float spawnCenterX) {
        this(worldHeight, duration, DEFAULT_ANGLE_DEG, spawnCenterX);
    }

    /** @param angleDeg rotates the whole path around its starting point;
     *  DEFAULT_ANGLE_DEG keeps the original top-to-bottom S-curve.
     *  @param spawnCenterX the lane this enemy spawns in (its spawn center-X), as dictated by
     *  the spawn schedule; the path's horizontal shape is anchored here instead of being random. */
    public SplineMovement(float worldHeight, float duration, float angleDeg, float spawnCenterX) {
        this.pathDuration = duration;

        float startY = worldHeight + 1;
        Vector2[] points = new Vector2[] {
            new Vector2(spawnCenterX, startY),
            new Vector2(spawnCenterX, worldHeight),
            new Vector2(spawnCenterX - 2f, worldHeight - 3f),
            new Vector2(spawnCenterX + 2f, worldHeight - 6f),
            new Vector2(spawnCenterX, -1f),
            new Vector2(spawnCenterX, -2f)
        };

        float rotationOffset = angleDeg - DEFAULT_ANGLE_DEG;
        if (rotationOffset != 0f) {
            for (Vector2 p : points) {
                p.sub(spawnCenterX, startY).rotateDeg(rotationOffset).add(spawnCenterX, startY);
            }
        }

        this.path = new CatmullRomSpline<>(points, false);
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        pathTime += delta;
        float t = pathTime / pathDuration;
        if (t > 1f) t = 1f;

        path.valueAt(tempPos, t);

        // Apply inverse movement by inverting Y position relative to world center
        float finalX = tempPos.x;
        float finalY = tempPos.y;
        if (inverseMovement) {
            finalY = worldHeight - tempPos.y; // Invert Y position
        }

        sprite.setCenterX(finalX);
        sprite.setCenterY(finalY);
        rectangle.setPosition(sprite.getX(), sprite.getY());

        float targetX = playerHitbox.x;
        float targetY = playerHitbox.y;
        toPlayer.set(targetX - finalX, targetY - finalY); // Use finalX, finalY for rotation
        sprite.setRotation(toPlayer.angleDeg() + 90);
    }

    @Override
    public boolean isFinished() {
        return pathTime >= pathDuration;
    }

    @Override
    public void reset() {
        pathTime = 0;
    }
}
