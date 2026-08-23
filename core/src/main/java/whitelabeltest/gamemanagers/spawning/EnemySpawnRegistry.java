package whitelabeltest.gamemanagers.spawning;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.AssetManager;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.GenericEnemy;

/** Lets a firing pattern (e.g. SpawnEnemyFiring) spawn other enemies by type id,
 * without threading AssetManager/enemies references through every FiringPattern implementation. */
public final class EnemySpawnRegistry {
    private static AssetManager assets;
    private static Array<Enemy> enemies;
    private static float worldWidth;
    private static float worldHeight;

    private EnemySpawnRegistry() {}

    public static void init(AssetManager assets, Array<Enemy> enemies, float worldWidth, float worldHeight) {
        EnemySpawnRegistry.assets = assets;
        EnemySpawnRegistry.enemies = enemies;
        EnemySpawnRegistry.worldWidth = worldWidth;
        EnemySpawnRegistry.worldHeight = worldHeight;
    }

    /** Resolves a texture by asset path, for firing patterns that want a sprite other than
     * their enemy's default bullet texture (e.g. a per-pattern override in FiringPatternDef). */
    public static Texture getTexture(String path) {
        return (assets != null && path != null) ? assets.getTexture(path) : null;
    }

    public static GenericEnemy spawn(String enemyTypeId, float x, float y) {
        if (assets == null || enemies == null || enemyTypeId == null) return null;

        EnemyDefinition def = assets.getEnemyDefinition(enemyTypeId);
        if (def == null) return null;

        Texture texture = assets.getTexture(def.texture);
        Texture bulletTexture = assets.getTexture(def.bulletTexture);
        Texture spawnTexture = def.spawnTexture != null ? assets.getTexture(def.spawnTexture) : null;
        Texture deathTexture = def.deathTexture != null ? assets.getTexture(def.deathTexture) : null;

        GenericEnemy enemy = ObjectPools.genericEnemyPool.obtain();
        enemy.initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, x, y);
        enemies.add(enemy);
        return enemy;
    }
}