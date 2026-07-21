package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.ThunderboltWeapon;
import whitelabeltest.player.weapons.Weapon;

public class EntityManager {
    private final Player player;
    private final Array<Enemy> enemies;
    private final Array<Weapon> bullets;
    private final Array<EnemyBullet> enemyBullets;
    private final Array<Powerup> powerups;
    private final Array<ExplosionEffect> explosions;
    private final Array<PlayerTrailEffect> trails;
    private final Array<HitEffect> hitEffects;
    private final Array<PointGem> pointGems;

    private final float worldWidth;
    private final float worldHeight;

    private final Animation<TextureRegion> bombAnimation;
    private final float bombDrawWidth, bombDrawHeight;
    private float bombAnimationTime;
    private boolean bombActive;
    private boolean bossKilled;

    private float trailSpawnTimer;
    private static final float TRAIL_SPAWN_INTERVAL = 0.02f;

    public EntityManager(AssetManager assets, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        // Frame size is 480×480; sprite is exported as a grid (e.g. 13 cols × 8 rows)
        // so that the texture width stays within GPU max-texture-size limits.
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
        this.trails = new Array<>();
        this.hitEffects = new Array<>();
        this.pointGems = new Array<>();

        EnemySpawnRegistry.init(assets, enemies, worldWidth, worldHeight);
    }

    public void triggerBombEffect() {
        bombActive = true;
        bombAnimationTime = 0f;
    }

    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio) {
        if (bombActive) {
            bombAnimationTime += delta;
            if (bombAnimation.isAnimationFinished(bombAnimationTime)) bombActive = false;
        }
        player.update(delta, input, assets, audio, bullets, enemies);
        updateTrail(delta, input);

        updateCollections(delta, assets, input);
    }

    private void updateTrail(float delta, InputManager input) {
        if (player.isDead() || input.getMoveDirection().len2() < 0.0001f) {
            trailSpawnTimer = 0f;
            return;
        }

        trailSpawnTimer += delta;
        if (trailSpawnTimer >= TRAIL_SPAWN_INTERVAL) {
            trailSpawnTimer = 0f;
            PlayerTrailEffect trail = ObjectPools.trailPool.obtain();
            trail.init(player.getCurrentFrame(), player.getX(), player.getY(), player.getWidth(), player.getHeight());
            trails.add(trail);

            PlayerTrailEffect haloTrail = ObjectPools.trailPool.obtain();
            haloTrail.init(player.getHaloFrame(), player.getHaloX(), player.getHaloY(), player.getHaloWidth(), player.getHaloHeight());
            trails.add(haloTrail);
        }
    }

    private void updateCollections(float delta, AssetManager assets, InputManager input) {
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

        // Enemies stop shooting while the player is dead or still in their post-respawn
        // invincibility window, so nothing can hit a ship that isn't fully back in play yet.
        boolean firingPaused = player.isDead() || player.isInvincible();
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.update(delta, enemyBullets, player.getHitbox(), firingPaused);

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

        for (int i = trails.size - 1; i >= 0; i--) {
            PlayerTrailEffect t = trails.get(i);
            t.update(delta);
            if (t.isFinished()) {
                trails.removeIndex(i);
                ObjectPools.freeTrail(t);
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

    public void draw(SpriteBatch batch) {
        for (Powerup p : powerups) p.draw(batch);
        for (PointGem g : pointGems) g.draw(batch);
        for (PlayerTrailEffect t : trails) t.draw(batch);
        for (Weapon b : bullets) {
            if (!(b instanceof ThunderboltWeapon)) b.draw(batch);
        }
        drawThunderboltBolts(batch);

        for (Enemy e : enemies) e.drawShadow(batch);
        for (Enemy e : enemies) if (e.isGround()) e.draw(batch);
        for (ExplosionEffect e : explosions) e.draw(batch);
        for (Enemy e : enemies) if (!e.isGround()) e.draw(batch);
        for (HitEffect h : hitEffects) h.draw(batch);
        for (EnemyBullet eb : enemyBullets) eb.draw(batch);
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
    }

    /** Draws every active Thunderbolt strike's bolt sprites inside one shared GL_MAX blend
     *  section, instead of each strike's own draw() flushing the batch and toggling
     *  glBlendEquation independently (see ThunderboltWeapon.draw()/drawBolts()). A level-4 fire
     *  spawns up to 6 concurrent strikes, so batching this here turns what would be up to a dozen
     *  forced flushes (each a GPU sync point) per frame into just two. */
    private void drawThunderboltBolts(SpriteBatch batch) {
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
            ((ThunderboltWeapon) b).drawBolts(batch);
        }

        if (blendSectionOpen) {
            batch.flush();
            Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
            batch.setBlendFunction(srcFunc, dstFunc);
        }
    }

    public void reset() {
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
        for (PlayerTrailEffect t : trails) ObjectPools.freeTrail(t);
        trails.clear();
        for (HitEffect h : hitEffects) ObjectPools.freeHitEffect(h);
        hitEffects.clear();
        for (PointGem g : pointGems) ObjectPools.freePointGem(g);
        pointGems.clear();
        trailSpawnTimer = 0f;
        player.reset();
    }

    /** Debug-only: wipes every non-player entity so a spawn-schedule seek doesn't leave stale
     *  enemies/bullets from the old point in time on screen. Player (weapons/score/lives) is untouched. */
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
        for (PointGem g : pointGems) ObjectPools.freePointGem(g);
        pointGems.clear();
    }

    public void destroyAllPlayerBullets() {
        for (int i = bullets.size - 1; i >= 0; i--) {
            ObjectPools.freeWeapon(bullets.get(i));
        }
        bullets.clear();
    }

    public void destroyAllEnemyBullets() {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            enemyBullets.removeIndex(i);
            ObjectPools.freeEnemyBullet(b);
        }
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
    public Array<PointGem> getPointGems() { return pointGems; }
}
