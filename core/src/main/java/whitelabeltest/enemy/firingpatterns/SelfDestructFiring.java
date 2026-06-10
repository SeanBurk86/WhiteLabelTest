package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.DrifterBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SelfDestructFiring implements FiringPattern {
    private final float triggerDistance;
    private boolean triggered = false;

    public SelfDestructFiring(float triggerDistance) {
        this.triggerDistance = triggerDistance;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        if (triggered) return;

        Vector2 targetPos = new Vector2(playerHitbox.x + playerHitbox.width / 2, playerHitbox.y + playerHitbox.height / 2);
        Vector2 currentPos = new Vector2(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);

        if (currentPos.dst(targetPos) <= triggerDistance) {
            triggered = true;
            float centerX = currentPos.x;
            float centerY = currentPos.y;

            for (int i = 0; i < 8; i++) {
                float angle = i * 45f;
                Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
                DrifterBullet b = ObjectPools.drifterBulletPool.obtain();
                b.init(bulletTexture, centerX, centerY, dir.x, dir.y);
                enemyBullets.add(b);
            }
        }
    }

    public boolean isTriggered() { return triggered; }

    @Override
    public void reset() {
        triggered = false;
    }
}
