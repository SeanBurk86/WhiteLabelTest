package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class SeekingMovement implements MovementPattern {
    private final float speed;
    private final float stopDistance;
    private boolean finished = false;
    private final Vector2 tempDir = new Vector2();

    public SeekingMovement(float speed, float stopDistance) {
        this.speed = speed;
        this.stopDistance = stopDistance;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Rectangle playerHitbox, boolean inverseMovement) {
        Vector2 targetPos = new Vector2(playerHitbox.x + playerHitbox.width / 2, playerHitbox.y + playerHitbox.height / 2);
        Vector2 currentPos = new Vector2(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);

        float dist = currentPos.dst(targetPos);

        if (dist <= stopDistance) {
            finished = true;
        } else {
            tempDir.set(targetPos).sub(currentPos).nor();
            if (inverseMovement) {
                tempDir.scl(-1); // Move away from player
            }
            sprite.translate(tempDir.x * speed * delta, tempDir.y * speed * delta);
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
