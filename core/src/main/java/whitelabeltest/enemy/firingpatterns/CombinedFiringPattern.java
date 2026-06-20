package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;

public class CombinedFiringPattern implements FiringPattern {
    private final Array<FiringPattern> patterns;

    public CombinedFiringPattern(Array<FiringPattern> patterns) {
        this.patterns = patterns;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        for (FiringPattern p : patterns) {
            p.update(delta, sprite, rectangle, enemyBullets, bulletTexture, playerHitbox);
        }
    }

    @Override
    public void reset() {
        for (FiringPattern p : patterns) p.reset();
    }
}