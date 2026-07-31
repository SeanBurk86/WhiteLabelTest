package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;
import whitelabeltest.player.weapons.GreenLightningBurst;
import whitelabeltest.player.weapons.ReflectedBolt;
import whitelabeltest.player.weapons.Weapon;

public class CollisionManager {
    private final Rectangle collisionHighlight;
    // Scratch buffer for the scaled/offset hitbox rect built in overlaps(Circle, EnemyBullet) -
    // reused every call instead of allocating a Rectangle per bullet-vs-player check.
    private final Rectangle scratchHitbox = new Rectangle();

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

    /** While the player's reflect shield is active, any enemy bullet touching it is destroyed
     *  and replaced with a ReflectedBolt - copying that bullet's own sprite and homing on
     *  whichever enemy fired it (falling back to straight up if that enemy's gone) - dealing
     *  back the destroyed bullet's own damage. Runs before checkPlayerBulletCollisions so a
     *  reflected bullet never also registers as a hit on the player's own (much smaller) hitbox
     *  that same frame. A bullet with no sprite to copy (shouldn't happen in practice) is simply
     *  left alone rather than reflected. */
    public void checkShieldReflections(Player player, Array<EnemyBullet> enemyBullets, Array<Weapon> bullets, AssetManager assets) {
        if (!player.isShieldActive()) return;

        Circle shield = player.getShieldHitbox();
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            if (!overlaps(shield, bullet)) continue;

            Sprite sourceSprite = bullet.getSprite();
            if (sourceSprite == null) continue;

            ReflectedBolt bolt = ObjectPools.reflectedBoltPool.obtain();
            Rectangle rect = bullet.getRectangle();
            bolt.init(sourceSprite, rect.x + rect.width / 2f, rect.y + rect.height / 2f, bullet.getDamage(), bullet.getSourceEnemy());
            bullets.add(bolt);

            enemyBullets.removeIndex(i);
            ObjectPools.freeEnemyBullet(bullet);
        }
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

        // getHitboxOffsetX/Y() is defined in the bullet's own unrotated frame so it stays attached
        // to (and turns with) the sprite - rotate it into world space before using it. A no-op for
        // rotation=0 or an unset offset, i.e. every bullet type that predates this field.
        float offsetX = bullet.getHitboxOffsetX();
        float offsetY = bullet.getHitboxOffsetY();
        float cosR = MathUtils.cosDeg(rotation);
        float sinR = MathUtils.sinDeg(rotation);
        float worldOffsetX = offsetX * cosR - offsetY * sinR;
        float worldOffsetY = offsetX * sinR + offsetY * cosR;

        // The hitbox rect, scaled around rect's own center and then shifted by the (now
        // world-space) offset - reduces to rect itself when scale=1/offset=(0,0), the default for
        // every bullet type that doesn't set BulletDef.hitboxScale/hitboxOffsetX/hitboxOffsetY.
        float scale = bullet.getHitboxScale();
        float effWidth = rect.width * scale;
        float effHeight = rect.height * scale;
        float effX = rect.x + (rect.width - effWidth) / 2f + worldOffsetX;
        float effY = rect.y + (rect.height - effHeight) / 2f + worldOffsetY;

        float hitRadius = bullet.getHitRadius();
        if (hitRadius >= 0f) {
            float dx = circle.x - (effX + effWidth / 2f);
            float dy = circle.y - (effY + effHeight / 2f);
            float radiusSum = circle.radius + hitRadius * scale;
            return dx * dx + dy * dy <= radiusSum * radiusSum;
        }

        if (rotation == 0f) {
            scratchHitbox.set(effX, effY, effWidth, effHeight);
            return Intersector.overlaps(circle, scratchHitbox);
        }

        // The pivot moves by the same world-space offset the box itself moved by (it's defined
        // relative to the unrotated rect, which the offset displaces before rotation is applied
        // around it), then localMin/MaxX/Y express the box's extent relative to that pivot - e.g.
        // symmetric [-halfWidth, halfWidth] for a bullet that pivots on its own center, or
        // [0, height] for one like LaserBullet that pivots on the rect's bottom edge - instead of
        // assuming either convention.
        float pivotX = bullet.getRotationPivotX() + worldOffsetX;
        float pivotY = bullet.getRotationPivotY() + worldOffsetY;
        float dx = circle.x - pivotX;
        float dy = circle.y - pivotY;

        float cos = MathUtils.cosDeg(-rotation);
        float sin = MathUtils.sinDeg(-rotation);
        float localX = dx * cos - dy * sin;
        float localY = dx * sin + dy * cos;

        // effX/effY and pivotX/pivotY both carry the same worldOffset shift, so it cancels here -
        // the box's extent relative to the pivot doesn't depend on where the offset moved it to.
        float minX = effX - pivotX;
        float minY = effY - pivotY;
        float closestX = MathUtils.clamp(localX, minX, minX + effWidth);
        float closestY = MathUtils.clamp(localY, minY, minY + effHeight);

        float distX = localX - closestX;
        float distY = localY - closestY;
        return distX * distX + distY * distY <= circle.radius * circle.radius;
    }

    public void checkPlayerPowerupCollisions(Player player, Array<Powerup> powerups, AudioManager audio) {
        for (int i = powerups.size - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            // Weapon powerups are collectible from the wider graze halo, not just the ship's tight
            // hitbox, so drifting through the halo picks them up without needing to touch them directly.
            Circle pickupHitbox = p instanceof WeaponPowerup ? player.getGrazeHitbox() : player.getHitbox();
            if (Intersector.overlaps(pickupHitbox, p.getRectangle())) {
                p.apply(player);
                audio.playPowerup();
                powerups.removeIndex(i);
                ObjectPools.freePowerup(p);
            }
        }
    }

    private static final int GEM_POINTS = 100;

    /** Point gems (see PointGem/GameController.destroyEnemy) are only collectible via the wider
     *  graze halo, same as weapon powerups - they home into it once the player stops firing. */
    public void checkPlayerGemCollisions(Player player, Array<PointGem> gems, ScoreManager scoreManager, AudioManager audio) {
        for (int i = gems.size - 1; i >= 0; i--) {
            PointGem gem = gems.get(i);
            if (Intersector.overlaps(player.getGrazeHitbox(), gem.getRectangle())) {
                scoreManager.addBonus(GEM_POINTS);
                audio.playPointGem();
                gems.removeIndex(i);
                ObjectPools.freePointGem(gem);
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
                    scoreManager.registerWeaponHit(bullet.getFireRate() / 2f, bullet.getChainWindow());
                    if (enemy.takeDamage(bullet.getDamage())) {
                        scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy), bullet.getChainWindow());
                    }
                    bullet.onHit(enemy, bullets, assets);
                    spawnHitEffect(bullet, entityManager);
                }

                if (bullet.shouldDestroyOnCollision()) {
                    bullets.removeIndex(j);
                    ObjectPools.freeWeapon(bullet);
                }

                if (bullet.shouldDestroyOnCollision() || !enemy.isActive()) break;
            }
        }
    }

    /** BasicWeapon's Hyper Attack dash (see Player.triggerBasicHyperAttack): while the halo is
     *  actively launching forward, it deals a flat, weapon-level-independent hit to anything it
     *  clips - once per enemy for the whole dash, tracked via Player.hasHaloDamaged/markHaloDamaged
     *  the same way a lingering bullet tracks its own hits - with the same kill/score handling a
     *  normal bullet hit gets. */
    public void checkHaloDashCollisions(Player player, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        if (!player.isHaloDashing()) return;

        Circle haloHitbox = player.getHaloHitbox();
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue;
            if (player.hasHaloDamaged(enemy)) continue;
            if (!Intersector.overlaps(haloHitbox, enemy.getRectangle())) continue;

            player.markHaloDamaged(enemy);
            player.triggerHaloBashFlash();
            audio.playHaloBash();
            scoreManager.registerWeaponHit(0.1f, 2.0f);
            if (enemy.takeDamage(player.getHaloDashDamage())) {
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy), 2.0f);
            }
        }
    }

    /** ThunderboltWeapon's Hyper Attack detonation (see Player.triggerThunderboltHyperAttack/
     *  updateThunderboltCharge): once the charged bomb is released, this is called once - the
     *  pending-flag on Player is what keeps it from firing again the following frame - and deals
     *  its charge tier's damage to every active enemy within the blast radius in one pass, with
     *  the same kill/score handling a normal hit gets, then spawns the green lightning arcing out
     *  to each of them and plays thunderbolthyperexplosion.wav. */
    public void checkThunderboltDetonation(Player player, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        if (!player.hasPendingThunderboltDetonation()) return;
        player.clearPendingThunderboltDetonation();

        float originX = player.getThunderboltDetonationX();
        float originY = player.getThunderboltDetonationY();
        Circle blast = new Circle(originX, originY, player.getThunderboltDetonationRadius());
        int damage = player.getThunderboltDetonationDamage();

        Array<Vector2> hitPoints = new Array<>(false, 8);
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue;
            if (!Intersector.overlaps(blast, enemy.getRectangle())) continue;

            Rectangle rect = enemy.getRectangle();
            hitPoints.add(new Vector2(rect.x + rect.width / 2f, rect.y + rect.height / 2f));

            scoreManager.registerWeaponHit(0.1f, 2.5f);
            if (enemy.takeDamage(damage)) {
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy), 2.5f);
            }
        }

        if (hitPoints.size > 0) {
            GreenLightningBurst burst = ObjectPools.greenLightningBurstPool.obtain();
            burst.init(assets.pixelTexture, assets.circleTexture, originX, originY, hitPoints);
            entityManager.getGreenLightningBursts().add(burst);
        }

        audio.playThunderboltHyperExplosion();
    }

    /** Spawns this bullet's impact animation (see WeaponDefinition.hitTexture) at the bullet's
     *  own position - literally where it hit - if its weapon definition set one. */
    private void spawnHitEffect(Weapon bullet, EntityManager entityManager) {
        Animation<TextureRegion> hitAnimation = bullet.getHitAnimation();
        if (hitAnimation == null) return;

        Rectangle rect = bullet.getRectangle();
        HitEffect effect = ObjectPools.hitEffectPool.obtain();
        effect.init(hitAnimation, rect.x + rect.width / 2f, rect.y + rect.height / 2f, bullet.getHitEffectSize());
        entityManager.getHitEffects().add(effect);
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
