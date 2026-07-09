package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.DrifterBullet;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class SelfDestructFiring implements FiringPattern {
    private final float triggerDistance;
    private final float bulletSize;
    private final float bulletSpeed;
    private boolean triggered = false;

    private static final float DEFAULT_SPEED = 4f;
    private final Animation<TextureRegion> spriteOverride;

    public SelfDestructFiring(float triggerDistance) {
        this(triggerDistance, 0.25f, DEFAULT_SPEED, null);
    }

    public SelfDestructFiring(float triggerDistance, float bulletSize) {
        this(triggerDistance, bulletSize, DEFAULT_SPEED, null);
    }

    public SelfDestructFiring(float triggerDistance, float bulletSize, float bulletSpeed) {
        this(triggerDistance, bulletSize, bulletSpeed, null);
    }

    /** @param spriteOverride pass null to use the enemy's default bullet animation */
    public SelfDestructFiring(float triggerDistance, float bulletSize, float bulletSpeed, Animation<TextureRegion> spriteOverride) {
        this.triggerDistance = triggerDistance;
        this.bulletSize = bulletSize;
        this.bulletSpeed = bulletSpeed;
        this.spriteOverride = spriteOverride;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Animation<TextureRegion> bulletAnimation, Circle playerHitbox) {
        if (triggered) return;

        Vector2 targetPos = new Vector2(playerHitbox.x, playerHitbox.y);
        Vector2 currentPos = new Vector2(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);

        if (currentPos.dst(targetPos) <= triggerDistance) {
            triggered = true;
            float centerX = currentPos.x;
            float centerY = currentPos.y;
            Animation<TextureRegion> animation = spriteOverride != null ? spriteOverride : bulletAnimation;

            for (int i = 0; i < 8; i++) {
                float angle = i * 45f;
                Vector2 dir = new Vector2(1, 0).setAngleDeg(angle);
                DrifterBullet b = ObjectPools.drifterBulletPool.obtain();
                b.init(animation, centerX, centerY, dir.x, dir.y, bulletSize, bulletSpeed);
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
