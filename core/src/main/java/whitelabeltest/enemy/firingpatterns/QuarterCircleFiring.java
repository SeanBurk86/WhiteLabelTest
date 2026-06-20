package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.AimedEnemyBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class QuarterCircleFiring implements FiringPattern {
    private final float fireRate;
    private float shootTimer;
    private static final int NUM_BULLETS = 5;
    private static final float SPREAD_DEGREES = 90f;

    public QuarterCircleFiring(float fireRate) {
        this.fireRate = fireRate;
        this.shootTimer = 0;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;
        float playerX = playerHitbox.x + playerHitbox.width / 2;
        float playerY = playerHitbox.y + playerHitbox.height / 2;

        float aimAngle = new Vector2(playerX - centerX, playerY - centerY).angleDeg();
        float startAngle = aimAngle - SPREAD_DEGREES / 2;
        float step = SPREAD_DEGREES / (NUM_BULLETS - 1);

        for (int i = 0; i < NUM_BULLETS; i++) {
            float angle = startAngle + i * step;
            Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
            AimedEnemyBullet b = ObjectPools.aimedBulletPool.obtain();
            b.init(bulletTexture, centerX, centerY, centerX + dir.x, centerY + dir.y);
            enemyBullets.add(b);
        }
    }

    @Override
    public void reset() {
        shootTimer = 0;
    }
}