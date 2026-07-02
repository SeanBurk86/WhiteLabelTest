package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
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
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Circle playerHitbox) {
        if (patterns.size == 0) return;

        timer += delta;
        if (timer >= durations[currentIndex]) {
            timer = 0;
            patterns.get(currentIndex).reset();
            currentIndex = (currentIndex + 1) % patterns.size;
        }

        patterns.get(currentIndex).update(delta, sprite, rectangle, enemyBullets, bulletTexture, playerHitbox);
    }

    @Override
    public void reset() {
        currentIndex = 0;
        timer = 0;
        for (FiringPattern p : patterns) p.reset();
    }
}