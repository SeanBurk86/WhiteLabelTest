package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class SequencedMovementPattern implements MovementPattern {
    private final Array<MovementPattern> patterns;
    private final float[] durations;
    private int currentIndex;
    private float timer;

    public SequencedMovementPattern(Array<MovementPattern> patterns, float[] durations) {
        this.patterns = patterns;
        this.durations = durations;
        this.currentIndex = 0;
        this.timer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        if (patterns.size == 0) return;

        timer += delta;
        if (timer >= durations[currentIndex]) {
            timer = 0;
            patterns.get(currentIndex).reset();
            currentIndex = (currentIndex + 1) % patterns.size;
        }

        patterns.get(currentIndex).update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, inverseMovement);
    }

    @Override
    public boolean isFinished() { return false; }

    @Override
    public void reset() {
        currentIndex = 0;
        timer = 0;
        for (MovementPattern p : patterns) p.reset();
    }
}