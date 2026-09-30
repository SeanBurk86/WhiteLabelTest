package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Moves in a straight line and bounces off the play-area edges, keeping the enemy on screen (e.g.
 *  the tutorial's streaming targets). Reflects slightly inside the edge (MARGIN) so the enemy never
 *  becomes briefly undamageable by poking out of the play area. */
public class BounceMovement implements MovementPattern {
    private static final float MARGIN = 0.05f;

    private final float speed;
    private final float initialAngleDeg;
    private final Vector2 direction = new Vector2();
    private boolean appliedInverse;

    public BounceMovement(float speed, float angleDeg) {
        this.speed = speed;
        this.initialAngleDeg = angleDeg;
        this.direction.set(1, 0).setAngleDeg(angleDeg);
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        // inverseMovement mirrors the starting heading once, so it doesn't fight the bounces.
        if (!appliedInverse) {
            appliedInverse = true;
            if (inverseMovement) direction.scl(-1f);
        }

        sprite.translate(direction.x * speed * delta, direction.y * speed * delta);
        rectangle.setPosition(sprite.getX(), sprite.getY());

        if (rectangle.x <= MARGIN) {
            rectangle.x = MARGIN;
            sprite.setX(MARGIN);
            direction.x = Math.abs(direction.x);
        } else if (rectangle.x + rectangle.width >= worldWidth - MARGIN) {
            rectangle.x = worldWidth - MARGIN - rectangle.width;
            sprite.setX(rectangle.x);
            direction.x = -Math.abs(direction.x);
        }

        if (rectangle.y <= MARGIN) {
            rectangle.y = MARGIN;
            sprite.setY(MARGIN);
            direction.y = Math.abs(direction.y);
        } else if (rectangle.y + rectangle.height >= worldHeight - MARGIN) {
            rectangle.y = worldHeight - MARGIN - rectangle.height;
            sprite.setY(rectangle.y);
            direction.y = -Math.abs(direction.y);
        }

        sprite.setRotation(direction.angleDeg() + 90f);
    }

    @Override
    public boolean isFinished() { return false; }

    @Override
    public void reset() {
        direction.set(1, 0).setAngleDeg(initialAngleDeg);
        appliedInverse = false;
    }
}
