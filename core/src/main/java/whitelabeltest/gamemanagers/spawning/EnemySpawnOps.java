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
import whitelabeltest.gamemanagers.trigger.Trigger;

/** Enemy-instantiation and sprite-cue mechanics shared by SpawnScheduler (wall-clock-timed spawn
 *  events) and TriggerManager (camera-position-timed triggers - see whitelabeltest.gamemanagers.
 *  trigger.Trigger) - both fire the same handful of things (spawn an enemy, silence/despawn one by
 *  definition id, drop a waypoint gem, play a scripted sprite cue), so the actual pooling/init work
 *  lives here once instead of twice. */
public final class EnemySpawnOps {
    private EnemySpawnOps() {}

    public static void spawnEnemy(EntityManager entityManager, ObjectMap<String, EnemyDefinition> enemyDefinitions, AssetManager assets,
                                   float worldWidth, float worldHeight, String type, float x, float y, float offsetX, float offsetY,
                                   String movementPattern, String firingPattern, boolean inverseMovement, Integer powerup) {
        spawnEnemy(entityManager, enemyDefinitions, assets, worldWidth, worldHeight, type, x, y, offsetX, offsetY,
            movementPattern, firingPattern, inverseMovement, powerup, null, 0f);
    }

    /** @param entranceTrigger non-null only from TriggerManager.fire() - lets GenericEnemy build its
     *  own EnemyEntranceMovement.build() call once the real spawn sprite's true size is known (see
     *  that method's own doc on why it can't be built any earlier than that), using entranceTrigger's
     *  own fields (x/y/distance/spawnLead/enterFromAbove) plus cameraSpeed (the camera's speed AT
     *  FIRE TIME, unused/irrelevant when entranceTrigger is null). */
    public static void spawnEnemy(EntityManager entityManager, ObjectMap<String, EnemyDefinition> enemyDefinitions, AssetManager assets,
                                   float worldWidth, float worldHeight, String type, float x, float y, float offsetX, float offsetY,
                                   String movementPattern, String firingPattern, boolean inverseMovement, Integer powerup,
                                   Trigger entranceTrigger, float cameraSpeed) {
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
        entityManager.getEnemies().add(enemy);
    }

    /** Stops every currently active enemy whose EnemyDefinition id matches defId from firing any
     *  further, without otherwise touching it. */
    public static void silenceMatching(EntityManager entityManager, String defId) {
        for (Enemy enemy : entityManager.getEnemies()) {
            if (enemy.isActive() && defId.equals(enemy.getDefinitionId())) enemy.silenceFiring();
        }
    }

    /** Silently removes every currently active enemy whose EnemyDefinition id matches defId, with
     *  no death animation/score/drops - scripted cleanup, not a kill. */
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

    /** Drops a stationary PointGem at (x, y), same animation as a gem an enemy would drop, but
     *  without that gem's pop/gravity/homing. */
    public static void spawnWaypointGem(EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, float x, float y) {
        Animation<TextureRegion> gemAnimation =
            AnimationCache.get(assets.pointGemTexture, 6, 4, 24, 0.05f, Animation.PlayMode.LOOP);
        PointGem gem = ObjectPools.pointGemPool.obtain();
        gem.init(gemAnimation, x, y, worldWidth, worldHeight, true);
        entityManager.getPointGems().add(gem);
    }

    /** Plays a one-off scripted sprite/animation at a fixed world position - texturePath/columns/
     *  rows/frameCount/frameDuration describe the sprite sheet the same way EnemyDefinition's
     *  animations do. size sets the draw height; width is derived from the sheet's per-frame aspect
     *  ratio so non-square art isn't squashed into a square. */
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
