package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class SplineMovement implements MovementPattern {
    private final CatmullRomSpline<Vector2> path;
    private float pathTime = 0;
    private final float pathDuration;
    private final Vector2 tempPos = new Vector2();
    private final Vector2 toPlayer = new Vector2();

    public SplineMovement(float worldWidth, float worldHeight, float duration) {
        this.pathDuration = duration;

        // Initial path generation (can be inverted later)
        float startX = MathUtils.random(1f, worldWidth - 1f);
        Vector2[] points = new Vector2[] {
            new Vector2(startX, worldHeight + 1),
            new Vector2(startX, worldHeight),
            new Vector2(startX - 2f, worldHeight - 3f),
            new Vector2(startX + 2f, worldHeight - 6f),
            new Vector2(startX, -1f),
            new Vector2(startX, -2f)
        };
        this.path = new CatmullRomSpline<>(points, false);
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Rectangle playerHitbox, boolean inverseMovement) {
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

        float targetX = playerHitbox.x + playerHitbox.width / 2;
        float targetY = playerHitbox.y + playerHitbox.height / 2;
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
