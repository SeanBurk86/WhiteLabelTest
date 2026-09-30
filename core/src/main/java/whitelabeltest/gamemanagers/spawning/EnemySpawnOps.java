package whitelabeltest.gamemanagers.spawning;

import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.effects.PointGem;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.GenericEnemy;
import whitelabeltest.enemy.HealthPhase;
import whitelabeltest.gamemanagers.trigger.Trigger;

/** Spawn / silence / despawn / waypoint-gem / sprite-cue actions shared by SpawnScheduler and
 *  TriggerManager. */
public final class EnemySpawnOps {
    private EnemySpawnOps() {}

    public static void spawnEnemy(EntityManager entityManager, ObjectMap<String, EnemyDefinition> enemyDefinitions, AssetManager assets,
                                   float worldWidth, float worldHeight, String type, float x, float y, float offsetX, float offsetY,
                                   String movementPattern, String firingPattern, boolean inverseMovement, Integer powerup) {
        spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight, type, x, y, offsetX, offsetY,
            movementPattern, firingPattern, inverseMovement, powerup, null, 0f);
    }

    /** @param entranceTrigger TriggerManager only: GenericEnemy builds the enterFromAbove entrance from
     *  it once the real sprite size is known. cameraSpeed is the camera speed at fire time. */
    public static void spawnEnemy(EntityManager entityManager, ObjectMap<String, EnemyDefinition> enemyDefinitions, AssetManager assets,
                                   float worldWidth, float worldHeight, String type, float x, float y, float offsetX, float offsetY,
                                   String movementPattern, String firingPattern, boolean inverseMovement, Integer powerup,
                                   Trigger entranceTrigger, float cameraSpeed) {
        spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight, type, x, y, offsetX, offsetY,
            movementPattern, firingPattern, inverseMovement, powerup, entranceTrigger, cameraSpeed, null);
    }

    /** @param healthPhases null or empty for none. */
    public static void spawnEnemy(EntityManager entityManager, ObjectMap<String, EnemyDefinition> enemyDefinitions, AssetManager assets,
                                   float worldWidth, float worldHeight, String type, float x, float y, float offsetX, float offsetY,
                                   String movementPattern, String firingPattern, boolean inverseMovement, Integer powerup,
                                   Trigger entranceTrigger, float cameraSpeed, Array<HealthPhase> healthPhases) {
        EnemyDefinition def = enemyDefinitions.get(type);
        if (def == null) return;

        Texture tex = assets.getTexture(def.texture);
        Texture bulletTex = assets.getTexture(def.bulletTexture);
        Texture spawnTex = def.spawnTexture != null ? assets.getTexture(def.spawnTexture) : null;
        Texture deathTex = def.deathTexture != null ? assets.getTexture(def.deathTexture) : null;

        GenericEnemy enemy = ObjectPools.genericEnemyPool.obtain();

        def.inverseMovement = inverseMovement;

        enemy.initWithDefinition(def, tex, bulletTex, spawnTex, deathTex, worldWidth, worldHeight, x, y, offsetX, offsetY, movementPattern, firingPattern, entranceTrigger, cameraSpeed);

        if (powerup != null) enemy.setGuaranteedPowerup(powerup);
        enemy.setHealthPhases(healthPhases);
        entityManager.getEnemies().add(enemy);
    }

    /** Stops every active enemy of definition defId from firing. */
    public static void silenceMatching(EntityManager entityManager, String defId) {
        for (Enemy enemy : entityManager.getEnemies()) {
            if (enemy.isActive() && defId.equals(enemy.getDefinitionId())) enemy.silenceFiring();
        }
    }

    /** Removes every active enemy of definition defId with no death animation, score or drops. */
    public static void despawnMatching(EntityManager entityManager, String defId) {
        Array<Enemy> enemies = entityManager.getEnemies();
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy enemy = enemies.get(i);
            if (enemy.isActive() && defId.equals(enemy.getDefinitionId())) {
                ObjectPools.freeEnemy(enemy);
                enemies.removeIndex(i);
            }
        }
    }

    /** Places a stationary PointGem at (x, y) (no pop, gravity or homing). */
    public static void spawnWaypointGem(EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, float x, float y) {
        Animation<TextureRegion> gemAnimation =
            AnimationCache.get(assets.pointGemTexture, 6, 4, 24, 0.05f, Animation.PlayMode.LOOP);
        PointGem gem = ObjectPools.pointGemPool.obtain();
        gem.init(gemAnimation, x, y, worldWidth, worldHeight, true);
        entityManager.getPointGems().add(gem);
    }

    /** Plays a one-off sprite-sheet animation at (x, y). size is the draw height; width follows the
     *  frame's aspect ratio. */
    public static void spawnSpriteCue(EntityManager entityManager, AssetManager assets, String texturePath, float x, float y, float size,
                                       int columns, int rows, int frameCount, float frameDuration) {
        Texture texture = assets.ensureTexture(texturePath);
        if (texture == null) return;

        Animation<TextureRegion> animation =
            AnimationCache.get(texture, columns, rows, frameCount, frameDuration, Animation.PlayMode.NORMAL);

        float frameAspect = (texture.getWidth() / (float) columns) / (texture.getHeight() / (float) rows);
        float height = size;
        float width = height * frameAspect;
        entityManager.spawnScheduledSprite(animation, x, y, width, height);
    }
}
