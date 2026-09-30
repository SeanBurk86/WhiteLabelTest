package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.effects.HitEffect;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.effects.PointGem;

import com.badlogic.gdx.graphics.Texture;
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
import whitelabeltest.enemy.EnemyHitboxes;
import whitelabeltest.enemy.HitboxDef;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;
import whitelabeltest.player.weapons.GreenLightningBurst;
import whitelabeltest.player.weapons.OrbitWeapon;
import whitelabeltest.player.weapons.ReflectedBolt;
import whitelabeltest.player.weapons.ThunderboltWeapon;
import whitelabeltest.player.weapons.Weapon;

/** All hit tests: player vs enemies/bullets/pickups, graze, reflect shield, player weapons vs
 *  enemies, enemy bullets vs damageable enemies, Hyper Attack hits, and paired-enemy death
 *  resolution. Supports rotated shapes, scaled/offset bullet hitboxes and custom enemy hitboxes. */
public class CollisionManager {
    private final Rectangle collisionHighlight;
    // Scratch rect for a bullet's scaled/offset hitbox (avoids per-check allocation).
    private final Rectangle scratchHitbox = new Rectangle();
    // Separate scratch for a rotated bullet box, so it can't alias the other side of the test.
    private final Rectangle scratchRotatedBox = new Rectangle();

    public CollisionManager() {
        this.collisionHighlight = new Rectangle();
    }

    public boolean checkPlayerEnemyCollisions(Player player, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue;
            if (enemy.isGround()) continue;
            if (overlaps(player.getHitbox(), enemy)) {
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

    /** While the reflect shield is up, each enemy bullet touching it becomes a ReflectedBolt with the
     *  same sprite and damage, homing on the enemy that fired it. Runs before the player-hit check so
     *  a reflected bullet can't also hit the player that frame. */
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

        // The hitbox offset is in the bullet's unrotated frame; rotate it into world space.
        float offsetX = bullet.getHitboxOffsetX();
        float offsetY = bullet.getHitboxOffsetY();
        float cosR = MathUtils.cosDeg(rotation);
        float sinR = MathUtils.sinDeg(rotation);
        float worldOffsetX = offsetX * cosR - offsetY * sinR;
        float worldOffsetY = offsetX * sinR + offsetY * cosR;

        // Hitbox = rect scaled around its center, then shifted by the offset.
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

        // The pivot moves with the offset. Work in the box's local frame relative to the pivot, which
        // may be the center or an edge (LaserBullet pivots on its bottom edge).
        float pivotX = bullet.getRotationPivotX() + worldOffsetX;
        float pivotY = bullet.getRotationPivotY() + worldOffsetY;
        float dx = circle.x - pivotX;
        float dy = circle.y - pivotY;

        float cos = MathUtils.cosDeg(-rotation);
        float sin = MathUtils.sinDeg(-rotation);
        float localX = dx * cos - dy * sin;
        float localY = dx * sin + dy * cos;

        // The offset shift cancels out of the box-relative-to-pivot extent.
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
            // Weapon powerups are collected by the wider graze halo, not the tight ship hitbox.
            Circle pickupHitbox = p instanceof WeaponPowerup ? player.getGrazeHitbox() : player.getHitbox();
            if (Intersector.overlaps(pickupHitbox, p.getRectangle())) {
                p.apply(player);
                audio.playPowerup();
                powerups.removeIndex(i);
                ObjectPools.freePowerup(p);
            }
        }
    }

    /** Point gems are collected by the graze halo (they home in when the player stops firing). */
    public void checkPlayerGemCollisions(Player player, Array<PointGem> gems, ScoreManager scoreManager, AudioManager audio, AssetManager assets) {
        for (int i = gems.size - 1; i >= 0; i--) {
            PointGem gem = gems.get(i);
            if (Intersector.overlaps(player.getGrazeHitbox(), gem.getRectangle())) {
                // Bigger gems (dropped close to the player) are worth proportionally more.
                scoreManager.addBonus(Math.round(assets.getGameBalance().gemPoints * gem.getValueScale()) * gem.getRepresents());
                scoreManager.registerGemCollected(gem.getRepresents());
                audio.playPointGem();
                gems.removeIndex(i);
                ObjectPools.freePointGem(gem);
            }
        }
    }

    public void checkBulletEnemyCollisions(Array<Weapon> bullets, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue; // bullets pass through entering/dying enemies
            for (int j = bullets.size - 1; j >= 0; j--) {
                Weapon bullet = bullets.get(j);
                if (!overlaps(enemy, bullet)) continue;

                if (!bullet.hasDamaged(enemy)) {
                    bullet.markDamaged(enemy);
                    scoreManager.registerWeaponHit(bullet.getFireRate() / 2f, bullet.getChainWindow());
                    // A paired enemy's death waits for resolvePairedEnemyDeaths(), once the whole
                    // frame's damage is known.
                    if (enemy.takeDamage(bullet.getDamage()) && enemy.getPairId() == null) {
                        scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), bullet.getChainWindow());
                    }
                    bullet.onHit(enemy, bullets, assets);
                    if (bullet instanceof ThunderboltWeapon bolt && bolt.getArcTargets() > 0) {
                        bolt.spawnArcs(bullets, enemy, enemies);
                    }
                    spawnHitEffect(bullet, entityManager);
                    if (bullet instanceof OrbitWeapon) audio.playOrbitGong();
                }

                if (bullet.shouldDestroyOnCollision()) {
                    bullets.removeIndex(j);
                    ObjectPools.freeWeapon(bullet);
                }

                // Keep testing other bullets until the enemy itself is gone: a big boss can overlap
                // many fast bullets in one frame, and skipping them lets them pass through.
                if (!enemy.isActive()) break;
            }
        }
    }

    /** Enemy bullets damage enemies that opt in (EnemyDefinition.damageableByEnemyBullets), never
     *  their own source. Used by the tutorial's bullet-streaming drill. The bullet is consumed on
     *  its first hit. */
    public void checkEnemyBulletEnemyCollisions(Array<EnemyBullet> enemyBullets, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet bullet = enemyBullets.get(i);
            Enemy source = bullet.getSourceEnemy();
            boolean consumed = false;
            for (int j = enemies.size - 1; j >= 0; j--) {
                Enemy enemy = enemies.get(j);
                if (!enemy.isActive() || enemy == source || !enemy.isDamageableByEnemyBullets()) continue;
                if (!overlaps(enemy, bullet)) continue;

                if (enemy.takeDamage(bullet.getDamage()) && enemy.getPairId() == null) {
                    scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), 1f);
                }
                consumed = true;
                break;
            }

            if (consumed) {
                enemyBullets.removeIndex(i);
                ObjectPools.freeEnemyBullet(bullet);
            }
        }
    }

    /** BasicWeapon Hyper Attack: while the halo launches forward it deals a flat hit once to each
     *  enemy it touches. */
    public void checkHaloDashCollisions(Player player, Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager) {
        if (!player.isHaloDashing()) return;

        Circle haloHitbox = player.getHaloHitbox();
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isActive()) continue;
            if (player.hasHaloDamaged(enemy)) continue;
            if (!overlaps(haloHitbox, enemy)) continue;

            player.markHaloDamaged(enemy);
            player.triggerHaloBashFlash();
            spawnHaloCollisionEffect(haloHitbox, player.getHaloCollisionScale(), enemy, entityManager, assets);
            audio.playHaloBash();
            scoreManager.registerWeaponHit(0.1f, 2.0f);
            if (enemy.takeDamage(player.getHaloDashDamage()) && enemy.getPairId() == null) {
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), 2.0f);
            }
        }
    }

    // halo-collision.png: one row of 5 frames, played once per hit.
    private static final String HALO_COLLISION_TEXTURE = "images/weapons/halo-collision.png";
    private static final float HALO_COLLISION_FRAME_DURATION = 0.05f;
    private static final float HALO_COLLISION_FRAME_ASPECT = 82f / 92f;

    /** Plays the halo-collision burst at the point on the enemy's bounds nearest the halo center. */
    private void spawnHaloCollisionEffect(Circle haloHitbox, float sizeInHaloDiameters, Enemy enemy, EntityManager entityManager, AssetManager assets) {
        Texture texture = assets.ensureTexture(HALO_COLLISION_TEXTURE);
        if (texture == null) return;
        Animation<TextureRegion> animation =
            AnimationCache.get(texture, 5, 1, 5, HALO_COLLISION_FRAME_DURATION, Animation.PlayMode.NORMAL);

        Rectangle bounds = enemy.getRectangle();
        float x = Math.max(bounds.x, Math.min(haloHitbox.x, bounds.x + bounds.width));
        float y = Math.max(bounds.y, Math.min(haloHitbox.y, bounds.y + bounds.height));
        float height = haloHitbox.radius * 2f * sizeInHaloDiameters;
        HitEffect effect = ObjectPools.hitEffectPool.obtain();
        effect.init(animation, x, y, height * HALO_COLLISION_FRAME_ASPECT, height);
        entityManager.getHitEffects().add(effect);
    }

    /** Thunderbolt Hyper Attack: when the charged bomb is released (a one-shot pending flag on
     *  Player), damages every enemy in the blast radius by the charge tier's damage and arcs green
     *  lightning to each. */
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
            if (!overlaps(blast, enemy)) continue;

            Rectangle rect = enemy.getRectangle();
            hitPoints.add(new Vector2(rect.x + rect.width / 2f, rect.y + rect.height / 2f));

            scoreManager.registerWeaponHit(0.1f, 2.5f);
            if (enemy.takeDamage(damage) && enemy.getPairId() == null) {
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), 2.5f);
            }
        }

        if (hitPoints.size > 0) {
            GreenLightningBurst burst = ObjectPools.greenLightningBurstPool.obtain();
            burst.init(assets.pixelTexture, assets.circleTexture, originX, originY, hitPoints);
            entityManager.getGreenLightningBursts().add(burst);
        }

        audio.playThunderboltHyperExplosion();
    }

    // Seconds a paired enemy that died first waits for its partner.
    private static final float PAIR_GRACE_WINDOW = 0.35f;

    /** Resolves paired enemies (EnemyDefinition.pairId); runs after all other damage this frame. If
     *  both partners are dying, both are destroyed normally (once each). Otherwise the first waits
     *  up to PAIR_GRACE_WINDOW for the partner, then both revive to full health. */
    public void resolvePairedEnemyDeaths(Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, ScoreManager scoreManager, float delta) {
        for (int i = 0; i < enemies.size; i++) {
            Enemy enemy = enemies.get(i);
            String pairId = enemy.getPairId();
            if (pairId == null || !enemy.isDying() || enemy.isPairResolved()) continue;

            Enemy partner = findPairPartner(enemies, enemy, pairId);
            if (partner != null && partner.isDying()) {
                enemy.markPairResolved();
                partner.markPairResolved();
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), 1f);
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, partner, scoreManager), 1f);
                continue;
            }

            float graceRemaining = enemy.getPairGraceTimer();
            if (graceRemaining < 0f) {
                enemy.setPairGraceTimer(PAIR_GRACE_WINDOW);
            } else if (graceRemaining - delta <= 0f) {
                enemy.reviveFully();
                if (partner != null) partner.reviveFully();
            } else {
                enemy.setPairGraceTimer(graceRemaining - delta);
            }
        }
    }

    private static Enemy findPairPartner(Array<Enemy> enemies, Enemy self, String pairId) {
        for (int i = 0; i < enemies.size; i++) {
            Enemy other = enemies.get(i);
            if (other != self && pairId.equals(other.getPairId())) return other;
        }
        return null;
    }

    /** Plays the weapon's hit animation (WeaponDefinition.hitTexture), if any, at the bullet. */
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

    /** overlaps(Circle, EnemyBullet) against an axis-aligned rectangle. */
    private boolean overlaps(Rectangle aabb, EnemyBullet bullet) {
        Rectangle rect = bullet.getRectangle();
        float rotation = bullet.getRotation();

        float offsetX = bullet.getHitboxOffsetX();
        float offsetY = bullet.getHitboxOffsetY();
        float cosR = MathUtils.cosDeg(rotation);
        float sinR = MathUtils.sinDeg(rotation);
        float worldOffsetX = offsetX * cosR - offsetY * sinR;
        float worldOffsetY = offsetX * sinR + offsetY * cosR;

        float scale = bullet.getHitboxScale();
        float effWidth = rect.width * scale;
        float effHeight = rect.height * scale;
        float effX = rect.x + (rect.width - effWidth) / 2f + worldOffsetX;
        float effY = rect.y + (rect.height - effHeight) / 2f + worldOffsetY;

        float hitRadius = bullet.getHitRadius();
        if (hitRadius >= 0f) {
            float cx = effX + effWidth / 2f;
            float cy = effY + effHeight / 2f;
            float closestX = MathUtils.clamp(cx, aabb.x, aabb.x + aabb.width);
            float closestY = MathUtils.clamp(cy, aabb.y, aabb.y + aabb.height);
            float dx = cx - closestX;
            float dy = cy - closestY;
            float r = hitRadius * scale;
            return dx * dx + dy * dy <= r * r;
        }

        if (rotation == 0f) {
            scratchHitbox.set(effX, effY, effWidth, effHeight);
            return aabb.overlaps(scratchHitbox);
        }

        float pivotX = bullet.getRotationPivotX() + worldOffsetX;
        float pivotY = bullet.getRotationPivotY() + worldOffsetY;
        return overlapsRotated(aabb, scratchRotatedBox.set(effX, effY, effWidth, effHeight), pivotX, pivotY, rotation);
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

    /** Separating-axis test for two rectangles that may both be rotated (overlapsRotated() handles
     *  only one rotated side). */
    private static boolean overlapsRotatedRects(float x1, float y1, float w1, float h1, float pivotX1, float pivotY1, float rot1,
                                                 float x2, float y2, float w2, float h2, float pivotX2, float pivotY2, float rot2) {
        float[] ax = new float[4];
        float[] ay = new float[4];
        rotatedCorners(x1, y1, w1, h1, pivotX1, pivotY1, rot1, ax, ay);
        float[] bx = new float[4];
        float[] by = new float[4];
        rotatedCorners(x2, y2, w2, h2, pivotX2, pivotY2, rot2, bx, by);

        float cos1 = MathUtils.cosDeg(rot1), sin1 = MathUtils.sinDeg(rot1);
        float cos2 = MathUtils.cosDeg(rot2), sin2 = MathUtils.sinDeg(rot2);
        return projectionsOverlap(ax, ay, bx, by, cos1, sin1)
            && projectionsOverlap(ax, ay, bx, by, -sin1, cos1)
            && projectionsOverlap(ax, ay, bx, by, cos2, sin2)
            && projectionsOverlap(ax, ay, bx, by, -sin2, cos2);
    }

    private static void rotatedCorners(float x, float y, float w, float h, float pivotX, float pivotY, float rotationDeg, float[] outX, float[] outY) {
        float[] lx = {x, x + w, x + w, x};
        float[] ly = {y, y, y + h, y + h};
        if (rotationDeg == 0f) {
            System.arraycopy(lx, 0, outX, 0, 4);
            System.arraycopy(ly, 0, outY, 0, 4);
            return;
        }
        float cos = MathUtils.cosDeg(rotationDeg);
        float sin = MathUtils.sinDeg(rotationDeg);
        for (int i = 0; i < 4; i++) {
            float dx = lx[i] - pivotX, dy = ly[i] - pivotY;
            outX[i] = pivotX + dx * cos - dy * sin;
            outY[i] = pivotY + dx * sin + dy * cos;
        }
    }

    /** Circle vs a rectangle rotated about a pivot, via the closest point in the box's local frame. */
    private static boolean overlapsCircleRotatedRect(float cx, float cy, float radius,
                                                       float rectX, float rectY, float rectW, float rectH,
                                                       float pivotX, float pivotY, float rotationDeg) {
        if (rotationDeg == 0f) {
            float closestX = MathUtils.clamp(cx, rectX, rectX + rectW);
            float closestY = MathUtils.clamp(cy, rectY, rectY + rectH);
            float dx = cx - closestX, dy = cy - closestY;
            return dx * dx + dy * dy <= radius * radius;
        }

        float dx = cx - pivotX, dy = cy - pivotY;
        float cos = MathUtils.cosDeg(-rotationDeg);
        float sin = MathUtils.sinDeg(-rotationDeg);
        float localX = dx * cos - dy * sin;
        float localY = dx * sin + dy * cos;

        float minX = rectX - pivotX;
        float minY = rectY - pivotY;
        float closestX = MathUtils.clamp(localX, minX, minX + rectW);
        float closestY = MathUtils.clamp(localY, minY, minY + rectH);

        float distX = localX - closestX;
        float distY = localY - closestY;
        return distX * distX + distY * distY <= radius * radius;
    }

    /** Circle vs an enemy: its custom hitboxes if defined, else its (possibly rotated) sprite box. */
    private boolean overlaps(Circle circle, Enemy enemy) {
        Array<HitboxDef> boxes = enemy.getHitboxDefs();
        if (boxes == null) return overlapsCircleRect(circle, enemy.getRectangle(), enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemy.getRotation());
        Rectangle sprite = enemy.getRectangle();
        float rotation = enemy.getRotation();
        for (int i = 0; i < boxes.size; i++) {
            HitboxDef box = boxes.get(i);
            if (box.isCircle()) {
                Circle c = EnemyHitboxes.circle(box, sprite, rotation, scratchEnemyCircle);
                float rr = circle.radius + c.radius;
                float dx = circle.x - c.x, dy = circle.y - c.y;
                if (dx * dx + dy * dy <= rr * rr) return true;
            } else {
                Rectangle hb = EnemyHitboxes.rect(box, sprite, rotation, scratchEnemyBox);
                if (overlapsCircleRect(circle, hb, hb.x + hb.width / 2f, hb.y + hb.height / 2f, EnemyHitboxes.totalRotation(box, rotation))) return true;
            }
        }
        return false;
    }

    /** Circle vs `r` - the enemy's sprite box, or one of its rectangle hitboxes - turned by `rotation` degrees about
     *  (pivotX, pivotY). */
    private boolean overlapsCircleRect(Circle circle, Rectangle r, float pivotX, float pivotY, float rotation) {
        if (rotation == 0f) return Intersector.overlaps(circle, r);
        return overlapsCircleRotatedRect(circle.x, circle.y, circle.radius, r.x, r.y, r.width, r.height, pivotX, pivotY, rotation);
    }

    // Scratch shapes for custom enemy hitboxes (no per-test allocation).
    private final Rectangle scratchEnemyBox = new Rectangle();
    private final Circle scratchEnemyCircle = new Circle();

    /** Enemy vs a player weapon bullet, both possibly rotated. */
    private boolean overlaps(Enemy enemy, Weapon bullet) {
        Array<HitboxDef> boxes = enemy.getHitboxDefs();
        if (boxes == null) return overlapsRect(enemy.getRectangle(), enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemy.getRotation(), bullet);
        Rectangle sprite = enemy.getRectangle();
        float rotation = enemy.getRotation();
        Rectangle bulletRect = bullet.getRectangle();
        for (int i = 0; i < boxes.size; i++) {
            HitboxDef box = boxes.get(i);
            if (box.isCircle()) {
                Circle c = EnemyHitboxes.circle(box, sprite, rotation, scratchEnemyCircle);
                if (overlapsCircleRotatedRect(c.x, c.y, c.radius, bulletRect.x, bulletRect.y, bulletRect.width, bulletRect.height,
                    bullet.getRotationPivotX(), bullet.getRotationPivotY(), bullet.getRotation())) return true;
            } else {
                Rectangle hb = EnemyHitboxes.rect(box, sprite, rotation, scratchEnemyBox);
                if (overlapsRect(hb, hb.x + hb.width / 2f, hb.y + hb.height / 2f, EnemyHitboxes.totalRotation(box, rotation), bullet)) return true;
            }
        }
        return false;
    }

    /** `enemyRect` - the enemy's sprite box, or one of its rectangle hitboxes - turned by `enemyRot` degrees about
     *  (enemyPivotX, enemyPivotY), vs a bullet. */
    private boolean overlapsRect(Rectangle enemyRect, float enemyPivotX, float enemyPivotY, float enemyRot, Weapon bullet) {
        Rectangle bulletRect = bullet.getRectangle();
        float bulletRot = bullet.getRotation();

        if (enemyRot == 0f && bulletRot == 0f) return enemyRect.overlaps(bulletRect);
        if (enemyRot == 0f) return overlapsRotated(enemyRect, bulletRect, bullet.getRotationPivotX(), bullet.getRotationPivotY(), bulletRot);
        if (bulletRot == 0f) return overlapsRotated(bulletRect, enemyRect, enemyPivotX, enemyPivotY, enemyRot);
        return overlapsRotatedRects(
            enemyRect.x, enemyRect.y, enemyRect.width, enemyRect.height, enemyPivotX, enemyPivotY, enemyRot,
            bulletRect.x, bulletRect.y, bulletRect.width, bulletRect.height, bullet.getRotationPivotX(), bullet.getRotationPivotY(), bulletRot);
    }

    /** Enemy (possibly rotated, possibly custom hitboxes) vs an enemy bullet. */
    private boolean overlaps(Enemy enemy, EnemyBullet bullet) {
        Array<HitboxDef> boxes = enemy.getHitboxDefs();
        if (boxes == null) return overlapsRect(enemy.getRectangle(), enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemy.getRotation(), bullet);
        Rectangle sprite = enemy.getRectangle();
        float rotation = enemy.getRotation();
        for (int i = 0; i < boxes.size; i++) {
            HitboxDef box = boxes.get(i);
            if (box.isCircle()) {
                // Same test as the player ship vs a bullet.
                if (overlaps(EnemyHitboxes.circle(box, sprite, rotation, scratchEnemyCircle), bullet)) return true;
            } else {
                Rectangle hb = EnemyHitboxes.rect(box, sprite, rotation, scratchEnemyBox);
                if (overlapsRect(hb, hb.x + hb.width / 2f, hb.y + hb.height / 2f, EnemyHitboxes.totalRotation(box, rotation), bullet)) return true;
            }
        }
        return false;
    }

    /** `enemyRect` - the enemy's sprite box, or one of its rectangle hitboxes - turned by `enemyRot` degrees about
     *  (enemyPivotX, enemyPivotY), vs an enemy bullet. */
    private boolean overlapsRect(Rectangle enemyRect, float enemyPivotX, float enemyPivotY, float enemyRot, EnemyBullet bullet) {

        Rectangle rect = bullet.getRectangle();
        float rotation = bullet.getRotation();

        float offsetX = bullet.getHitboxOffsetX();
        float offsetY = bullet.getHitboxOffsetY();
        float cosR = MathUtils.cosDeg(rotation);
        float sinR = MathUtils.sinDeg(rotation);
        float worldOffsetX = offsetX * cosR - offsetY * sinR;
        float worldOffsetY = offsetX * sinR + offsetY * cosR;

        float scale = bullet.getHitboxScale();
        float effWidth = rect.width * scale;
        float effHeight = rect.height * scale;
        float effX = rect.x + (rect.width - effWidth) / 2f + worldOffsetX;
        float effY = rect.y + (rect.height - effHeight) / 2f + worldOffsetY;

        float hitRadius = bullet.getHitRadius();
        if (hitRadius >= 0f) {
            float cx = effX + effWidth / 2f;
            float cy = effY + effHeight / 2f;
            return overlapsCircleRotatedRect(cx, cy, hitRadius * scale, enemyRect.x, enemyRect.y, enemyRect.width, enemyRect.height,
                enemyPivotX, enemyPivotY, enemyRot);
        }

        if (enemyRot == 0f && rotation == 0f) {
            scratchHitbox.set(effX, effY, effWidth, effHeight);
            return enemyRect.overlaps(scratchHitbox);
        }
        if (enemyRot == 0f) {
            float pivotX = bullet.getRotationPivotX() + worldOffsetX;
            float pivotY = bullet.getRotationPivotY() + worldOffsetY;
            return overlapsRotated(enemyRect, scratchRotatedBox.set(effX, effY, effWidth, effHeight), pivotX, pivotY, rotation);
        }
        if (rotation == 0f) {
            scratchHitbox.set(effX, effY, effWidth, effHeight);
            return overlapsRotated(scratchHitbox, enemyRect, enemyPivotX, enemyPivotY, enemyRot);
        }
        float pivotX = bullet.getRotationPivotX() + worldOffsetX;
        float pivotY = bullet.getRotationPivotY() + worldOffsetY;
        return overlapsRotatedRects(
            enemyRect.x, enemyRect.y, enemyRect.width, enemyRect.height, enemyPivotX, enemyPivotY, enemyRot,
            effX, effY, effWidth, effHeight, pivotX, pivotY, rotation);
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
