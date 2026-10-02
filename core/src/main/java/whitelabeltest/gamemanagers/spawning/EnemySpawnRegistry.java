package whitelabeltest.gamemanagers.spawning;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.AssetManager;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.GenericEnemy;

/** Static access for firing patterns (e.g. SpawnEnemyFiring) to spawn enemies by id, without
 *  passing AssetManager and the enemy list through every FiringPattern. */
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

    /** What the registry spawns into, so a second simulation (a ghost run) can swap its own in
     *  and the live run's back. */
    public record State(AssetManager assets, Array<Enemy> enemies, float worldWidth, float worldHeight) {}

    public static State getState() {
        return new State(assets, enemies, worldWidth, worldHeight);
    }

    public static void setState(State state) {
        init(state.assets(), state.enemies(), state.worldWidth(), state.worldHeight());
    }

    /** Texture lookup for firing patterns that override their enemy's bullet texture. */
    public static Texture getTexture(String path) {
        return (assets != null && path != null) ? assets.getTexture(path) : null;
    }

    public static GenericEnemy spawn(String enemyTypeId, float x, float y) {
        return spawn(enemyTypeId, x, y, null);
    }

    /** @param movementPatternId optional. Movement isn't part of EnemyDefinition, so this is the only
     *  way to give the spawn motion (used by PatternPreviewer). */
    public static GenericEnemy spawn(String enemyTypeId, float x, float y, String movementPatternId) {
        if (assets == null || enemies == null || enemyTypeId == null) return null;

        EnemyDefinition def = assets.getEnemyDefinition(enemyTypeId);
        if (def == null) return null;

        Texture texture = assets.getTexture(def.texture);
        Texture bulletTexture = assets.getTexture(def.bulletTexture);
        Texture spawnTexture = def.spawnTexture != null ? assets.getTexture(def.spawnTexture) : null;
        Texture deathTexture = def.deathTexture != null ? assets.getTexture(def.deathTexture) : null;

        GenericEnemy enemy = ObjectPools.genericEnemyPool.obtain();
        enemy.initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, x, y, Float.NaN, Float.NaN, movementPatternId);
        enemies.add(enemy);
        return enemy;
    }
}