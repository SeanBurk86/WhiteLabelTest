package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;

public class SequencedFiringPattern implements FiringPattern {
    private final Array<FiringPattern> patterns;
    private final float[] durations;
    private int currentIndex;
    private float timer;

    public SequencedFiringPattern(Array<FiringPattern> patterns, float[] durations) {
        this.patterns = patterns;
        this.durations = durations;
        this.currentIndex = 0;
        this.timer = 0;
    }

    @Override
    public void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        if (patterns.size == 0) return;

        timer += delta;
        if (timer >= durations[currentIndex]) {
            timer = 0;
            patterns.get(currentIndex).reset();
            currentIndex = (currentIndex + 1) % patterns.size;
        }

        patterns.get(currentIndex).update(delta, self, sprite, rectangle, enemyBullets, bulletAnimation, playerHitbox);
    }

    @Override
    public void reset() {
        currentIndex = 0;
        timer = 0;
        for (FiringPattern p : patterns) p.reset();
    }

    // Same step the timer-expiry branch of update() takes, just triggered externally instead of by
    // durations[currentIndex] elapsing - see Enemy.advanceFiringPattern().
    @Override
    public void advance() {
        if (patterns.size == 0) return;
        timer = 0;
        patterns.get(currentIndex).reset();
        currentIndex = (currentIndex + 1) % patterns.size;
    }
}