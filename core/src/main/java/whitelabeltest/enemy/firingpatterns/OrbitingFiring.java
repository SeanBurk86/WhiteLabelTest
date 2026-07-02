package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.OrbitingBullet;
import whitelabeltest.gamemanagers.ObjectPools;

public class OrbitingFiring implements FiringPattern {
    private final float fireRate;
    private float shootTimer;

    private static final float CENTER_SPEED = 4.0f;
    private static final float ORBIT_RADIUS = 0.4f;
    private static final float ORBIT_SPEED = 5.0f; // radians per second

    public OrbitingFiring(float fireRate) {
        this.fireRate = fireRate;
        this.shootTimer = fireRate; // fire immediately on first update
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Circle playerHitbox) {
        shootTimer += delta;
        if (shootTimer < fireRate) return;
        shootTimer = 0;

        float centerX = sprite.getX() + sprite.getWidth() / 2;
        float centerY = sprite.getY() + sprite.getHeight() / 2;
        float playerX = playerHitbox.x;
        float playerY = playerHitbox.y;

        Vector2 vel = new Vector2(playerX - centerX, playerY - centerY).nor().scl(CENTER_SPEED);

        OrbitingBullet b1 = ObjectPools.orbitingBulletPool.obtain();
        b1.init(bulletTexture, centerX, centerY, vel.x, vel.y, ORBIT_RADIUS, ORBIT_SPEED, 0);
        enemyBullets.add(b1);

        OrbitingBullet b2 = ObjectPools.orbitingBulletPool.obtain();
        b2.init(bulletTexture, centerX, centerY, vel.x, vel.y, ORBIT_RADIUS, ORBIT_SPEED, MathUtils.PI);
        enemyBullets.add(b2);
    }

    @Override
    public void reset() {
        shootTimer = fireRate;
    }
}