package whitelabeltest.gamemanagers;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;
import whitelabeltest.player.weapons.Weapon;

public class CollisionManager {
    private final Rectangle collisionHighlight;

    public CollisionManager() {
        this.collisionHighlight = new Rectangle();
    }

    public boolean checkPlayerEnemyCollisions(Player player, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue;
            if (enemy.isGround()) continue;
            if (Intersector.overlaps(player.getHitbox(), enemy.getRectangle())) {
                collisionHighlight.set(enemy.getRectangle());
                return true;
            }
        }
        return false;
    }

    public boolean checkPlayerBulletCollisions(Player player, Array<EnemyBullet> enemyBullets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (overlaps(player.getHitbox(), bullet)) {
                collisionHighlight.set(bullet.getRectangle());
                return true;
            }
        }
        return false;
    }

    public boolean checkGrazeCollisions(Player player, Array<EnemyBullet> enemyBullets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (overlaps(player.getGrazeHitbox(), bullet)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(Circle circle, EnemyBullet bullet) {
        Rectangle rect = bullet.getRectangle();
        float rotation = bullet.getRotation();
        if (rotation == 0f) return Intersector.overlaps(circle, rect);

        float pivotX = rect.x + rect.width / 2f;
        float pivotY = rect.y;
        float dx = circle.x - pivotX;
        float dy = circle.y - pivotY;

        float cos = MathUtils.cosDeg(-rotation);
        float sin = MathUtils.sinDeg(-rotation);
        float localX = dx * cos - dy * sin;
        float localY = dx * sin + dy * cos;

        float halfWidth = rect.width / 2f;
        float closestX = MathUtils.clamp(localX, -halfWidth, halfWidth);
        float closestY = MathUtils.clamp(localY, 0f, rect.height);

        float distX = localX - closestX;
        float distY = localY - closestY;
        return distX * distX + distY * distY <= circle.radius * circle.radius;
    }

    public void checkPlayerPowerupCollisions(Player player, Array<Powerup> powerups, AudioManager audio) {
        for (int i = powerups.size - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            if (Intersector.overlaps(player.getHitbox(), p.getRectangle())) {
                p.apply(player);
                audio.playPowerup();
                powerups.removeIndex(i);
                ObjectPools.freePowerup(p);
            }
        }
    }

    public void checkBulletPowerupCollisions(Array<Weapon> bullets, Array<Powerup> powerups, AssetManager assets) {
        for (int i = powerups.size - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            if (!(p instanceof WeaponPowerup)) continue;
            WeaponPowerup wp = (WeaponPowerup) p;
            for (int j = bullets.size - 1; j >= 0; j--) {
                Weapon bullet = bullets.get(j);
                if (overlaps(p.getRectangle(), bullet)) {
                    if (wp.registerHit()) {
                        GameController.cyclePowerupType(wp, assets);
                    }
                    if (bullet.shouldDestroyOnCollision()) {
                        bullets.removeIndex(j);
                        ObjectPools.freeWeapon(bullet);
                    }
                    break;
                }
            }
        }
    }

    public void checkBulletEnemyCollisions(Array<Weapon> bullets, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue; // bullets pass through entering/dying enemies
            for (int j = bullets.size - 1; j >= 0; j--) {
                Weapon bullet = bullets.get(j);
                if (!overlaps(enemy.getRectangle(), bullet)) continue;

                if (!bullet.hasDamaged(enemy)) {
                    bullet.markDamaged(enemy);
                    if (enemy.takeDamage(bullet.getDamage())) {
                        scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy), bullet.getChainWindow());
                    }
                    bullet.onHit(enemy, bullets, assets);
                }

                if (bullet.shouldDestroyOnCollision()) {
                    bullets.removeIndex(j);
                    ObjectPools.freeWeapon(bullet);
                }

                if (bullet.shouldDestroyOnCollision() || !enemy.isActive()) break;
            }
        }
    }

    private boolean overlaps(Rectangle aabb, Weapon bullet) {
        float rotation = bullet.getRotation();
        if (rotation == 0f) return aabb.overlaps(bullet.getRectangle());
        return overlapsRotated(aabb, bullet.getRectangle(), bullet.getRotationPivotX(), bullet.getRotationPivotY(), rotation);
    }

    private static boolean overlapsRotated(Rectangle aabb, Rectangle local, float pivotX, float pivotY, float rotationDeg) {
        float[] ax = {aabb.x, aabb.x + aabb.width, aabb.x + aabb.width, aabb.x};
        float[] ay = {aabb.y, aabb.y, aabb.y + aabb.height, aabb.y + aabb.height};

        float cos = MathUtils.cosDeg(rotationDeg);
        float sin = MathUtils.sinDeg(rotationDeg);
        float[] lx = {local.x, local.x + local.width, local.x + local.width, local.x};
        float[] ly = {local.y, local.y, local.y + local.height, local.y + local.height};
        float[] bx = new float[4];
        float[] by = new float[4];
        for (int i = 0; i < 4; i++) {
            float dx = lx[i] - pivotX, dy = ly[i] - pivotY;
            bx[i] = pivotX + dx * cos - dy * sin;
            by[i] = pivotY + dx * sin + dy * cos;
        }

        return projectionsOverlap(ax, ay, bx, by, 1f, 0f)
            && projectionsOverlap(ax, ay, bx, by, 0f, 1f)
            && projectionsOverlap(ax, ay, bx, by, cos, sin)
            && projectionsOverlap(ax, ay, bx, by, -sin, cos);
    }

    private static boolean projectionsOverlap(float[] ax, float[] ay, float[] bx, float[] by, float axisX, float axisY) {
        float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float proj = ax[i] * axisX + ay[i] * axisY;
            minA = Math.min(minA, proj);
            maxA = Math.max(maxA, proj);
        }
        float minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float proj = bx[i] * axisX + by[i] * axisY;
            minB = Math.min(minB, proj);
            maxB = Math.max(maxB, proj);
        }
        return minA <= maxB && minB <= maxA;
    }

    public Rectangle getCollisionHighlight() {
        return collisionHighlight;
    }

    public void reset() {
        collisionHighlight.set(0, 0, 0, 0);
    }
}
