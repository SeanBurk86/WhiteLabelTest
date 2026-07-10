package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;

public class SquadronMovement implements MovementPattern {
    private final MovementPattern leader;
    private final float offsetX;
    private final float offsetY;

    public SquadronMovement(MovementPattern leader, float offsetX, float offsetY) {
        this.leader = leader;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        sprite.translate(-offsetX, -offsetY);

        leader.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, inverseMovement);

        sprite.translate(offsetX, offsetY);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public boolean isFinished() {
        return leader.isFinished();
    }

    @Override
    public void reset() {
        leader.reset();
    }
}
