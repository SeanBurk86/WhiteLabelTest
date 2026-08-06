package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;

public interface FiringPattern {
    void update(float delta, Enemy self, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox);
    void reset();

    // Forces this pattern to its next stage immediately, bypassing whatever timer would normally
    // gate the transition - see SequencedFiringPattern's override and Enemy.advanceFiringPattern().
    // A no-op default since only a Sequence has stages to step through.
    default void advance() {}
}
