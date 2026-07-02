package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.player.Player;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.Weapon;

public class EntityManager {
    private final Player player;
    private final Array<Enemy> enemies;
    private final Array<Weapon> bullets;
    private final Array<EnemyBullet> enemyBullets;
    private final Array<Powerup> powerups;
    private final Array<ExplosionEffect> explosions;

    private final float worldWidth;
    private final float worldHeight;

    public EntityManager(AssetManager assets, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.player = new Player(assets, worldWidth, worldHeight);
        this.enemies = new Array<>();
        this.bullets = new Array<>();
        this.enemyBullets = new Array<>();
        this.powerups = new Array<>();
        this.explosions = new Array<>();
    }

    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio) {
        player.update(delta, input, assets, audio, bullets, enemies);

        updateCollections(delta, assets);
    }

    private void updateCollections(float delta, AssetManager assets) {
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

        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.update(delta, enemyBullets, assets.enemyBulletTexture, player.getHitbox());

            if (e.isOffScreen()) {
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
    }

    public void draw(SpriteBatch batch) {
        // Z-order: Weapons (bullets) drawn behind everything else
        for (Weapon b : bullets) b.draw(batch);

        // Enemies and their bullets
        for (Enemy e : enemies) e.draw(batch);
        for (EnemyBullet eb : enemyBullets) eb.draw(batch);

        // Powerups and particles
        for (Powerup p : powerups) p.draw(batch);
        for (ExplosionEffect e : explosions) e.draw(batch);

        // Player drawn on very top
        player.draw(batch);
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
        player.reset();
    }

    public void destroyAllEnemyBullets() {
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            EnemyBullet b = enemyBullets.get(i);
            enemyBullets.removeIndex(i);
            ObjectPools.freeEnemyBullet(b);
        }
    }

    public Player getPlayer() { return player; }
    public Array<Enemy> getEnemies() { return enemies; }
    public Array<Weapon> getBullets() { return bullets; }
    public Array<EnemyBullet> getEnemyBullets() { return enemyBullets; }
    public Array<Powerup> getPowerups() { return powerups; }
    public Array<ExplosionEffect> getExplosions() { return explosions; }
}
