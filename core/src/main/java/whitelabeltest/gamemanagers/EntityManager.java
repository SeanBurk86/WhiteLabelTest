package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.spawning.EnemySpawnRegistry;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.effects.BulletCancelEffect;
import whitelabeltest.gamemanagers.effects.ExplosionEffect;
import whitelabeltest.gamemanagers.effects.HitEffect;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.gamemanagers.effects.PointGem;
import whitelabeltest.gamemanagers.effects.ScheduledSpriteEffect;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.WeaponLoadout;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.GreenLightningBurst;
import whitelabeltest.player.weapons.ThunderboltWeapon;
import whitelabeltest.player.weapons.Weapon;

/** Owns the player and every pooled entity list (enemies, bullets, pickups, effects); updates and
 *  draws them in a fixed layer order. */
public class EntityManager {
    private final Player player;
    private final Array<Enemy> enemies;
    private final Array<Weapon> bullets;
    private final Array<EnemyBullet> enemyBullets;
    private final Array<Powerup> powerups;
    private final Array<ExplosionEffect> explosions;
    private final Array<HitEffect> hitEffects;
    private final Array<BulletCancelEffect> bulletCancelEffects;
    private final Array<PointGem> pointGems;
    private final Array<GreenLightningBurst> greenLightningBursts;
    private final Array<ScheduledSpriteEffect> scheduledSprites;

    private final float worldWidth;
    private final float worldHeight;

    private final Animation<TextureRegion> bombAnimation;
    private final float bombDrawWidth, bombDrawHeight;
    private float bombAnimationTime;
    private boolean bombActive;
    private boolean bossKilled;

    private static final float BULLET_CANCEL_SIZE_SCALE = 4f;

    public EntityManager(AssetManager assets, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        // 480x480 frames exported as a grid to stay within GPU max texture size.
        int bombFrameWidth  = 480;
        int bombFrameHeight = 480;
        TextureRegion[][] bombGrid   = TextureRegion.split(assets.bombSpriteTexture, bombFrameWidth, bombFrameHeight);
        TextureRegion[]   bombFrames = new TextureRegion[104];
        int bombIdx = 0;
        outer:
        for (TextureRegion[] row : bombGrid)
            for (TextureRegion cell : row) {
                if (bombIdx >= 104) break outer;
                bombFrames[bombIdx++] = cell;
            }
        bombAnimation = new Animation<>(1f / 60f, bombFrames);
        bombAnimation.setPlayMode(Animation.PlayMode.NORMAL);
        bombDrawWidth = worldWidth;
        bombDrawHeight = worldWidth * ((float) bombFrameHeight / bombFrameWidth);

        this.player = new Player(assets, worldWidth, worldHeight);
        this.enemies = new Array<>();
        this.bullets = new Array<>();
        this.enemyBullets = new Array<>();
        this.powerups = new Array<>();
        this.explosions = new Array<>();
        this.hitEffects = new Array<>();
        this.bulletCancelEffects = new Array<>();
        this.pointGems = new Array<>();
        this.greenLightningBursts = new Array<>();
        this.scheduledSprites = new Array<>();

        EnemySpawnRegistry.init(assets, enemies, worldWidth, worldHeight);
    }

    public void triggerBombEffect() {
        bombActive = true;
        bombAnimationTime = 0f;
    }

    /** Spawns a scripted sprite cue (see EnemySpawnOps.spawnSpriteCue()). */
    public void spawnScheduledSprite(Animation<TextureRegion> animation, float x, float y, float width, float height) {
        ScheduledSpriteEffect effect = ObjectPools.scheduledSpriteEffectPool.obtain();
        effect.init(animation, x, y, width, height);
        scheduledSprites.add(effect);
    }

    /** @param groundScrollSpeed scroll applied to ground enemies not attached to a background layer;
     *  attached ones use their layer's speed. background may be null. */
    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio, boolean weaponsDisabled, boolean hyperAttackDisabled, float groundScrollSpeed, ScrollingBackground background) {
        if (bombActive) {
            bombAnimationTime += delta;
            if (bombAnimation.isAnimationFinished(bombAnimationTime)) bombActive = false;
        }
        player.update(delta, input, assets, audio, bullets, enemies, weaponsDisabled, hyperAttackDisabled);

        updateCollections(delta, assets, input, groundScrollSpeed, background, audio);
    }

    private void updateCollections(float delta, AssetManager assets, InputManager input, float groundScrollSpeed, ScrollingBackground background, AudioManager audio) {
        for (int i = bullets.size - 1; i >= 0; i--) {
            Weapon b = bullets.get(i);
            b.updateWithEnemies(delta, enemies);
            if (b.isOffScreen(worldHeight)) {
                bullets.removeIndex(i);
                ObjectPools.freeWeapon(b);
            }
        }

        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            b.update(delta);
            if (b.isOffScreen()) {
                enemyBullets.removeIndex(i);
                ObjectPools.freeEnemyBullet(b);
            }
        }

        // Enemies hold fire while the player is dead or in post-respawn invincibility.
        boolean firingPaused = player.isDead() || player.isInvincible();
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.update(delta, enemyBullets, player.getHitbox(), player.getGrazeHitbox(), firingPaused, resolveGroundScrollSpeed(e, groundScrollSpeed, background), audio);

            if (e.isOffScreen()) {
                if (e.isBoss() && e.isDying()) notifyBossKilled();
                enemies.removeIndex(i);
                ObjectPools.freeEnemy(e);
            }
        }

        for (int i = powerups.size - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            p.update(delta);
            if (p.isOffScreen()) {
                powerups.removeIndex(i);
                ObjectPools.freePowerup(p);
            }
        }

        for (int i = explosions.size - 1; i >= 0; i--) {
            ExplosionEffect e = explosions.get(i);
            e.update(delta);
            if (e.isFinished()) {
                explosions.removeIndex(i);
                ObjectPools.freeExplosion(e);
            }
        }

        for (int i = hitEffects.size - 1; i >= 0; i--) {
            HitEffect h = hitEffects.get(i);
            h.update(delta);
            if (h.isFinished()) {
                hitEffects.removeIndex(i);
                ObjectPools.freeHitEffect(h);
            }
        }

        for (int i = bulletCancelEffects.size - 1; i >= 0; i--) {
            BulletCancelEffect e = bulletCancelEffects.get(i);
            e.update(delta);
            if (e.isFinished()) {
                bulletCancelEffects.removeIndex(i);
                ObjectPools.freeBulletCancelEffect(e);
            }
        }

        for (int i = greenLightningBursts.size - 1; i >= 0; i--) {
            GreenLightningBurst b = greenLightningBursts.get(i);
            b.update(delta);
            if (b.isFinished()) {
                greenLightningBursts.removeIndex(i);
                ObjectPools.freeGreenLightningBurst(b);
            }
        }

        for (int i = scheduledSprites.size - 1; i >= 0; i--) {
            ScheduledSpriteEffect s = scheduledSprites.get(i);
            s.update(delta);
            if (s.isFinished()) {
                scheduledSprites.removeIndex(i);
                ObjectPools.freeScheduledSpriteEffect(s);
            }
        }

        boolean playerFiring = input.isShooting();
        for (int i = pointGems.size - 1; i >= 0; i--) {
            PointGem gem = pointGems.get(i);
            gem.update(delta, playerFiring, player.getGrazeHitbox());
            if (gem.isOffScreen()) {
                pointGems.removeIndex(i);
                ObjectPools.freePointGem(gem);
            }
        }
    }

    /** The layer's scroll speed for a layer-attached enemy, else groundScrollSpeed. */
    private float resolveGroundScrollSpeed(Enemy e, float groundScrollSpeed, ScrollingBackground background) {
        if (background == null || e.getBackgroundLayer() < 0) return groundScrollSpeed;
        return background.getLayerScrollSpeed(e.getBackgroundLayer(), groundScrollSpeed);
    }

    public void draw(SpriteBatch batch) {
        draw(batch, 0);
    }

    /** @param attachedLayerCount number of background layers whose attached enemies were already
     *  drawn between layers (see drawEnemiesAttachedToLayer()); those are skipped here. Pass 0 when
     *  no layer stack is drawn this frame (video/shader background), so every enemy draws here. */
    public void draw(SpriteBatch batch, int attachedLayerCount) {
        for (Powerup p : powerups) p.draw(batch);
        for (PointGem g : pointGems) g.draw(batch);
        for (Weapon b : bullets) {
            if (!(b instanceof ThunderboltWeapon)) b.draw(batch);
        }
        drawThunderboltBolts(batch);

        for (Enemy e : enemies) if (!isAttached(e, attachedLayerCount)) e.drawShadow(batch);
        for (Enemy e : enemies) if (e.isGround() && !isAttached(e, attachedLayerCount)) e.draw(batch);
        for (ExplosionEffect e : explosions) e.draw(batch);
        for (Enemy e : enemies) if (!e.isGround() && !isAttached(e, attachedLayerCount)) e.draw(batch);
        for (HitEffect h : hitEffects) h.draw(batch);
        for (BulletCancelEffect e : bulletCancelEffects) e.draw(batch);
        for (GreenLightningBurst b : greenLightningBursts) b.draw(batch);
        if (bombActive) {
            TextureRegion frame = bombAnimation.getKeyFrame(bombAnimationTime);
            float bx = worldWidth / 2f - bombDrawWidth / 2f;
            float by = worldHeight / 2f - bombDrawHeight / 2f;
            batch.setColor(1f, 1f, 1f, 1f);
            batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
            batch.draw(frame, bx, by, bombDrawWidth, bombDrawHeight);
            batch.flush();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        player.draw(batch);
        drawEnemyBulletsByTexture(batch);

        for (ScheduledSpriteEffect s : scheduledSprites) s.draw(batch);
    }

    /** Draws enemies attached to background layer layerIndex, called right after that layer so they
     *  sit between it and the next. */
    public void drawEnemiesAttachedToLayer(SpriteBatch batch, int layerIndex) {
        for (Enemy e : enemies) if (e.getBackgroundLayer() == layerIndex) e.drawShadow(batch);
        for (Enemy e : enemies) if (e.getBackgroundLayer() == layerIndex) e.draw(batch);
    }

    /** True if e is attached to an in-range layer (already drawn by drawEnemiesAttachedToLayer()). */
    private boolean isAttached(Enemy e, int layerCount) {
        int layer = e.getBackgroundLayer();
        return layer >= 0 && layer < layerCount;
    }

    /** Draws all Thunderbolt outlines (normal blend), then all glows/cores in one shared GL_MAX blend
     *  section, so many concurrent strikes cost two batch flushes instead of two each. */
    private void drawThunderboltBolts(SpriteBatch batch) {
        for (Weapon b : bullets) {
            if (b instanceof ThunderboltWeapon) ((ThunderboltWeapon) b).drawOutline(batch);
        }

        boolean blendSectionOpen = false;
        int srcFunc = 0, dstFunc = 0;

        for (Weapon b : bullets) {
            if (!(b instanceof ThunderboltWeapon)) continue;
            if (!blendSectionOpen) {
                srcFunc = batch.getBlendSrcFunc();
                dstFunc = batch.getBlendDstFunc();
                batch.flush();
                Gdx.gl.glBlendEquation(ThunderboltWeapon.GL_MAX);
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                blendSectionOpen = true;
            }
            ((ThunderboltWeapon) b).drawGlowAndCore(batch);
        }

        if (blendSectionOpen) {
            batch.flush();
            Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
            batch.setBlendFunction(srcFunc, dstFunc);
        }
    }

    public void reset(WeaponLoadout loadout) {
        for (Enemy e : enemies) ObjectPools.freeEnemy(e);
        enemies.clear();
        for (Weapon b : bullets) ObjectPools.freeWeapon(b);
        bullets.clear();
        for (EnemyBullet eb : enemyBullets) ObjectPools.freeEnemyBullet(eb);
        enemyBullets.clear();
        for (Powerup p : powerups) ObjectPools.freePowerup(p);
        powerups.clear();
        for (ExplosionEffect e : explosions) ObjectPools.freeExplosion(e);
        explosions.clear();
        for (HitEffect h : hitEffects) ObjectPools.freeHitEffect(h);
        hitEffects.clear();
        for (BulletCancelEffect e : bulletCancelEffects) ObjectPools.freeBulletCancelEffect(e);
        bulletCancelEffects.clear();
        for (PointGem g : pointGems) ObjectPools.freePointGem(g);
        pointGems.clear();
        for (GreenLightningBurst b : greenLightningBursts) ObjectPools.freeGreenLightningBurst(b);
        greenLightningBursts.clear();
        for (ScheduledSpriteEffect s : scheduledSprites) ObjectPools.freeScheduledSpriteEffect(s);
        scheduledSprites.clear();
        player.reset(loadout);
    }

    /** Removes every non-player entity (after a seek or practice restart). The player is untouched. */
    public void clearWorld() {
        for (Enemy e : enemies) ObjectPools.freeEnemy(e);
        enemies.clear();
        for (Weapon b : bullets) ObjectPools.freeWeapon(b);
        bullets.clear();
        for (EnemyBullet eb : enemyBullets) ObjectPools.freeEnemyBullet(eb);
        enemyBullets.clear();
        for (Powerup p : powerups) ObjectPools.freePowerup(p);
        powerups.clear();
        for (ExplosionEffect e : explosions) ObjectPools.freeExplosion(e);
        explosions.clear();
        for (HitEffect h : hitEffects) ObjectPools.freeHitEffect(h);
        hitEffects.clear();
        for (BulletCancelEffect e : bulletCancelEffects) ObjectPools.freeBulletCancelEffect(e);
        bulletCancelEffects.clear();
        for (PointGem g : pointGems) ObjectPools.freePointGem(g);
        pointGems.clear();
        for (GreenLightningBurst b : greenLightningBursts) ObjectPools.freeGreenLightningBurst(b);
        greenLightningBursts.clear();
        for (ScheduledSpriteEffect s : scheduledSprites) ObjectPools.freeScheduledSpriteEffect(s);
        scheduledSprites.clear();
    }

    public void destroyAllPlayerBullets() {
        for (int i = bullets.size - 1; i >= 0; i--) {
            ObjectPools.freeWeapon(bullets.get(i));
        }
        bullets.clear();
    }

    public void destroyAllEnemyBullets(AssetManager assets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            enemyBullets.removeIndex(i);
            spawnBulletCancelEffect(b, assets);
            ObjectPools.freeEnemyBullet(b);
        }
    }

    /** Destroys every bullet fired by source (EnemyDefinition.bulletCancel). Call before source is
     *  freed: the match is by reference, and a pooled instance can be reused. */
    public void destroyEnemyBullets(Enemy source, AssetManager assets) {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            if (b.getSourceEnemy() != source) continue;
            enemyBullets.removeIndex(i);
            spawnBulletCancelEffect(b, assets);
            ObjectPools.freeEnemyBullet(b);
        }
    }

    /** The effect shown when a bullet is cancelled (by a bomb or its source's death). */
    private void spawnBulletCancelEffect(EnemyBullet b, AssetManager assets) {
        Rectangle rect = b.getRectangle();
        BulletCancelEffect effect = ObjectPools.bulletCancelEffectPool.obtain();
        effect.init(assets, rect.x + rect.width / 2f, rect.y + rect.height / 2f, Math.max(rect.width, rect.height) * BULLET_CANCEL_SIZE_SCALE);
        bulletCancelEffects.add(effect);
    }

    public void notifyBossKilled() { bossKilled = true; }
    public boolean consumeBossKilled() { boolean v = bossKilled; bossKilled = false; return v; }

    public Player getPlayer() { return player; }
    public Array<Enemy> getEnemies() { return enemies; }
    public Array<Weapon> getBullets() { return bullets; }
    public Array<EnemyBullet> getEnemyBullets() { return enemyBullets; }
    public Array<Powerup> getPowerups() { return powerups; }
    public Array<ExplosionEffect> getExplosions() { return explosions; }
    public Array<HitEffect> getHitEffects() { return hitEffects; }
    public Array<BulletCancelEffect> getBulletCancelEffects() { return bulletCancelEffects; }
    public Array<PointGem> getPointGems() { return pointGems; }
    public Array<GreenLightningBurst> getGreenLightningBursts() { return greenLightningBursts; }

    // Scratch list of distinct enemy bullet textures this frame.
    private final Array<Texture> enemyBulletTextures = new Array<>(false, 8);

    /** Draws enemy bullets grouped by texture, so each texture costs one batch flush instead of one
     *  per switch. Sprite-less bullets (lasers) draw last. Index loops because libGDX Arrays can't
     *  nest iterators. */
    private void drawEnemyBulletsByTexture(SpriteBatch batch) {
        enemyBulletTextures.clear();
        for (int i = 0; i < enemyBullets.size; i++) {
            Sprite sprite = enemyBullets.get(i).getSprite();
            Texture texture = sprite != null ? sprite.getTexture() : null;
            if (texture != null && !enemyBulletTextures.contains(texture, true)) enemyBulletTextures.add(texture);
        }
        for (int t = 0; t < enemyBulletTextures.size; t++) {
            Texture texture = enemyBulletTextures.get(t);
            for (int i = 0; i < enemyBullets.size; i++) {
                EnemyBullet bullet = enemyBullets.get(i);
                Sprite sprite = bullet.getSprite();
                if (sprite != null && sprite.getTexture() == texture) bullet.draw(batch);
            }
        }
        for (int i = 0; i < enemyBullets.size; i++) {
            EnemyBullet bullet = enemyBullets.get(i);
            Sprite sprite = bullet.getSprite();
            if (sprite == null || sprite.getTexture() == null) bullet.draw(batch);
        }
    }
}
