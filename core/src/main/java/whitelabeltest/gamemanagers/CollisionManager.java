package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.effects.HitEffect;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.effects.PointGem;

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
import whitelabeltest.player.weapons.OrbitWeapon;
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

    /** Point gems (see PointGem/GameController.destroyEnemy) are only collectible via the wider
     *  graze halo, same as weapon powerups - they home into it once the player stops firing. */
    public void checkPlayerGemCollisions(Player player, Array<PointGem> gems, ScoreManager scoreManager, AudioManager audio, AssetManager assets) {
        for (int i = gems.size - 1; i >= 0; i--) {
            PointGem gem = gems.get(i);
            if (Intersector.overlaps(player.getGrazeHitbox(), gem.getRectangle())) {
                scoreManager.addBonus(assets.getGameBalance().gemPoints);
                scoreManager.registerGemCollected();
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
                    // A paired enemy's death is deferred to resolvePairedEnemyDeaths() - see
                    // Enemy.getPairId() - instead of scored/destroyed immediately here, since
                    // whether it actually dies depends on whether its partner also crossed zero
                    // this same frame, which isn't known until every enemy's damage for the frame
                    // has been applied.
                    if (enemy.takeDamage(bullet.getDamage()) && enemy.getPairId() == null) {
                        scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), bullet.getChainWindow());
                    }
                    bullet.onHit(enemy, bullets, assets);
                    spawnHitEffect(bullet, entityManager);
                    if (bullet instanceof OrbitWeapon) audio.playOrbitGong();
                }

                if (bullet.shouldDestroyOnCollision()) {
                    bullets.removeIndex(j);
                    ObjectPools.freeWeapon(bullet);
                }

                // Only stop checking bullets against THIS enemy once it's actually gone - a
                // destroyed bullet just means that one bullet is done, not that every other bullet
                // already overlapping the same enemy this frame should be skipped. That distinction
                // barely matters for small enemies (rarely more than one bullet overlaps at once),
                // but a large stationary boss can have many rapid-fire bullets overlapping it in a
                // single frame - breaking here after the first one meant every other bullet already
                // touching it got skipped entirely, and fast bullets had already flown past its
                // hitbox by the next frame, i.e. they'd visibly "pass through" without ever hitting.
                if (!enemy.isActive()) break;
            }
        }
    }

    /** Lets an enemy bullet damage another (non-source) enemy on contact, but only one that opts in
     *  via Enemy.isDamageableByEnemyBullets() - the tutorial's bullet-streaming drill (see
     *  SpawnScheduler.InvincibilityWindow) relies on it: the player kites the streaming emitter's
     *  continuous aimed fire across a set of slow PowerCarrier targets, which take damage from it
     *  exactly like they would from the player's own weapon. Everything else defaults to false so
     *  a bullet passing near an unrelated enemy - e.g. several stationary tutorial emitters sharing
     *  one spawn point - doesn't get silently eaten by it. A bullet never damages the enemy that
     *  fired it (EnemyBullet.getSourceEnemy()), and is consumed on its first hit against a
     *  damageable enemy - unlike player bullets there's no piercing flag to check, since every
     *  enemy bullet type in this codebase is single-use against the player too. */
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
            if (!overlaps(haloHitbox, enemy)) continue;

            player.markHaloDamaged(enemy);
            player.triggerHaloBashFlash();
            audio.playHaloBash();
            scoreManager.registerWeaponHit(0.1f, 2.0f);
            if (enemy.takeDamage(player.getHaloDashDamage()) && enemy.getPairId() == null) {
                scoreManager.addScore(GameController.destroyEnemy(audio, entityManager, assets, worldWidth, worldHeight, enemy, scoreManager), 2.0f);
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

    // How long (seconds) a paired enemy that's crossed zero first keeps waiting, mid-death, for
    // its partner to also cross zero - see resolvePairedEnemyDeaths().
    private static final float PAIR_GRACE_WINDOW = 0.35f;

    /** Finalizes or reverses every paired enemy's death - see EnemyDefinition.pairId. Must run
     *  once per update(), after every other collision check above has applied this frame's damage
     *  to every enemy (checkBulletEnemyCollisions and the rest all defer a paired enemy's
     *  destroyEnemy() call rather than firing it inline, precisely so this can see the whole
     *  frame's damage before deciding). For each paired enemy that's crossed zero (isDying(), not
     *  yet isPairResolved()): if its partner has also crossed zero, both are genuine kills -
     *  finalize them with the normal destroyEnemy() score/explosion/drop path, exactly once each
     *  (markPairResolved() stops a later frame, while the death animation is still playing out,
     *  from re-triggering this). Otherwise the first one to die holds in place - still isDying(),
     *  not yet revived - for up to PAIR_GRACE_WINDOW seconds, giving the partner a real window to
     *  follow it down rather than requiring a single-frame-perfect hit; only once that window
     *  elapses without the partner also dying are both revived to full health, so a
     *  half-simultaneous attempt can't chip away one side of the pair while leaving the other
     *  untouched. */
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

    /** Same hitbox math as overlaps(Circle, EnemyBullet) above (scale/offset/hitRadius/rotation),
     *  just tested against an axis-aligned enemy rectangle instead of the player's circular one. */
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
        return overlapsRotated(aabb, new Rectangle(effX, effY, effWidth, effHeight), pivotX, pivotY, rotation);
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

    /** Same SAT test as overlapsRotated() above, but for two rectangles that may BOTH be rotated
     *  (e.g. a spinning IceSkull hit by an AimedEnemyBullet, which turns to face its target) -
     *  overlapsRotated() only handles one rotated side against a plain axis-aligned one. Tests all
     *  4 axes (one pair of edge normals per rectangle) instead of overlapsRotated()'s world-axes +
     *  one rotated pair, which is what actually changes: each rectangle's own edges are always
     *  perpendicular to each other, so a 0-degree rotation's axes reduce to the same (1,0)/(0,1)
     *  pair overlapsRotated() hardcodes for its unrotated side - this is a strict generalization,
     *  just written separately to avoid touching overlapsRotated()'s already-proven bullet-collision
     *  callers. */
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

    /** Same closest-point-on-box math as the rotation branch of overlaps(Circle, EnemyBullet)
     *  above, generalized to any rotated rectangle/pivot pair - used for a circular hitbox (the
     *  player's ship, the halo dash, a thunderbolt blast, or a circular enemy-bullet hitRadius)
     *  against a possibly-rotated enemy. */
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

    /** Circle vs a (possibly rotated) enemy hitbox - see Enemy.getRotation()/getRotationPivotX/Y().
     *  Falls back to a plain circle-vs-AABB test (Intersector.overlaps' own fast path) whenever the
     *  enemy isn't rotated, i.e. every enemy as before this method existed. */
    private boolean overlaps(Circle circle, Enemy enemy) {
        Rectangle r = enemy.getRectangle();
        float rotation = enemy.getRotation();
        if (rotation == 0f) return Intersector.overlaps(circle, r);
        return overlapsCircleRotatedRect(circle.x, circle.y, circle.radius, r.x, r.y, r.width, r.height,
            enemy.getRotationPivotX(), enemy.getRotationPivotY(), rotation);
    }

    /** enemy.getRectangle() vs a player weapon bullet, both accounted for their own rotation (see
     *  Enemy.getRotation()/Weapon.getRotation()). Reuses the existing single-rotated-side
     *  overlapsRotated() for the (overwhelmingly common) case where at most one side is actually
     *  rotated, only falling through to the full two-sided SAT when both are. */
    private boolean overlaps(Enemy enemy, Weapon bullet) {
        Rectangle enemyRect = enemy.getRectangle();
        float enemyRot = enemy.getRotation();
        Rectangle bulletRect = bullet.getRectangle();
        float bulletRot = bullet.getRotation();

        if (enemyRot == 0f && bulletRot == 0f) return enemyRect.overlaps(bulletRect);
        if (enemyRot == 0f) return overlapsRotated(enemyRect, bulletRect, bullet.getRotationPivotX(), bullet.getRotationPivotY(), bulletRot);
        if (bulletRot == 0f) return overlapsRotated(bulletRect, enemyRect, enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemyRot);
        return overlapsRotatedRects(
            enemyRect.x, enemyRect.y, enemyRect.width, enemyRect.height, enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemyRot,
            bulletRect.x, bulletRect.y, bulletRect.width, bulletRect.height, bullet.getRotationPivotX(), bullet.getRotationPivotY(), bulletRot);
    }

    /** Same hitbox math as overlaps(Rectangle, EnemyBullet) above (scale/offset/hitRadius/rotation
     *  on the bullet's side), but also accounts for the enemy's own rotation (see
     *  Enemy.getRotation()) instead of assuming it's always an axis-aligned box. */
    private boolean overlaps(Enemy enemy, EnemyBullet bullet) {
        Rectangle enemyRect = enemy.getRectangle();
        float enemyRot = enemy.getRotation();

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
                enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemyRot);
        }

        if (enemyRot == 0f && rotation == 0f) {
            scratchHitbox.set(effX, effY, effWidth, effHeight);
            return enemyRect.overlaps(scratchHitbox);
        }
        if (enemyRot == 0f) {
            float pivotX = bullet.getRotationPivotX() + worldOffsetX;
            float pivotY = bullet.getRotationPivotY() + worldOffsetY;
            return overlapsRotated(enemyRect, new Rectangle(effX, effY, effWidth, effHeight), pivotX, pivotY, rotation);
        }
        if (rotation == 0f) {
            scratchHitbox.set(effX, effY, effWidth, effHeight);
            return overlapsRotated(scratchHitbox, enemyRect, enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemyRot);
        }
        float pivotX = bullet.getRotationPivotX() + worldOffsetX;
        float pivotY = bullet.getRotationPivotY() + worldOffsetY;
        return overlapsRotatedRects(
            enemyRect.x, enemyRect.y, enemyRect.width, enemyRect.height, enemy.getRotationPivotX(), enemy.getRotationPivotY(), enemyRot,
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
