package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SweepFiring implements FiringPattern {
    private final float bulletInterval;
    private static final float SWEEP_DURATION = 2.0f;
    private static final float SWEEP_START_ANGLE = 225f; // down-left
    private static final float SWEEP_END_ANGLE = 315f;   // down-right

    private float bulletTimer;
    private float sweepT;         // 0 = left edge, 1 = right edge
    private float sweepDirection; // +1 = left→right, -1 = right→left

    public SweepFiring(float fireRate) {
        this.bulletInterval = fireRate;
        this.bulletTimer = bulletInterval; // fire immediately on first update
        this.sweepT = 0f;
        this.sweepDirection = 1f;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        sweepT += sweepDirection * delta / SWEEP_DURATION;
        if (sweepT >= 1f) { sweepT = 1f; sweepDirection = -1f; }
        else if (sweepT <= 0f) { sweepT = 0f; sweepDirection = 1f; }

        bulletTimer += delta;
        if (bulletTimer < bulletInterval) return;
        bulletTimer = 0;

        float angle = SWEEP_START_ANGLE + sweepT * (SWEEP_END_ANGLE - SWEEP_START_ANGLE);
        Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;

        AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
        b.init(bulletTexture, centerX, centerY, centerX + dir.x, centerY + dir.y);
        enemyBullets.add(b);
    }

    @Override
    public void reset() {
        bulletTimer = bulletInterval;
        sweepT = 0f;
        sweepDirection = 1f;
    }
}