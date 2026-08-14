package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Travels in a straight line at movementAngle and reflects off the play area's edges (screensaver-
 *  style) instead of exiting it - unlike StraightMovement, which just keeps going until the enemy
 *  drifts off-screen. Used for enemies meant to stay put and available - e.g. the tutorial's
 *  bullet-streaming drill, where the player needs its PowerCarrier targets to keep patrolling
 *  in view long enough to lead a sustained stream through them instead of drifting off and forcing
 *  a respawn.
 *
 *  Reflects a hair inside worldWidth/worldHeight (see MARGIN) rather than exactly at the edge, so
 *  the sprite never leaves BaseEnemy.isFullyOnScreen()'s bounds - takeDamage() refuses damage
 *  while that's false, so bouncing exactly on the boundary (or past it, on a fast-moving/large
 *  sprite) could otherwise make the enemy briefly untouchable right as it turns around. */
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
        // inverseMovement mirrors the starting heading for a formation - applied once (not
        // re-derived every frame like StraightMovement does) so it doesn't fight with the bounces
        // below, which already track the enemy's current real heading.
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
