package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.GenericEnemy;

import java.util.Comparator;

public class SpawnScheduler {
    public static class SpawnEvent {
        public float time;
        public String type;
        // NaN (the default when omitted from spawn_schedule.json) means "pick a default spawn
        // position"; any finite value is used literally, including negative or beyond the world
        // bounds so enemies can spawn off either edge of the screen (not just above/below it).
        public float x = Float.NaN;
        public float y = Float.NaN;
        public String powerup;
        public boolean inverseMovement = false;
        public boolean spawned = false;

        public SpawnEvent() {}
    }

    private float totalTime;
    private final float worldWidth;
    private final float worldHeight;
    private Array<SpawnEvent> schedule;
    private final ObjectMap<String, EnemyDefinition> enemyDefinitions;
    private final AssetManager assets;

    public SpawnScheduler(float worldWidth, float worldHeight, AssetManager assets) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = assets;
        this.totalTime = 0;
        this.enemyDefinitions = new ObjectMap<>();
        loadDefinitions();
        loadSchedule();
    }

    private void loadDefinitions() {
        Json json = new Json();
        @SuppressWarnings("unchecked")
        Array<EnemyDefinition> defs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("enemies.json"));
        for (EnemyDefinition def : defs) {
            enemyDefinitions.put(def.id, def);
        }
    }

    private void loadSchedule() {
        Json json = new Json();
        try {
            @SuppressWarnings("unchecked")
            Array<SpawnEvent> loadedSchedule = json.fromJson(Array.class, SpawnEvent.class, Gdx.files.internal("spawn_schedule.json"));
            this.schedule = loadedSchedule;
            if (this.schedule == null) this.schedule = new Array<>();
            schedule.sort(new Comparator<SpawnEvent>() {
                @Override
                public int compare(SpawnEvent e1, SpawnEvent e2) {
                    return Float.compare(e1.time, e2.time);
                }
            });
        } catch (SerializationException e) {
            Gdx.app.error("SpawnScheduler", "Error parsing spawn_schedule.json", e);
            this.schedule = new Array<>();
        }
    }

    public void update(float delta, EntityManager entityManager) {
        totalTime += delta;
        for (SpawnEvent event : schedule) {
            if (!event.spawned && totalTime >= event.time) {
                spawnEnemy(entityManager, event);
                event.spawned = true;
            }
        }
    }

    private void spawnEnemy(EntityManager entityManager, SpawnEvent event) {
        EnemyDefinition def = enemyDefinitions.get(event.type);
        if (def == null) return;

        Texture tex = assets.getTexture(def.texture);
        Texture bulletTex = assets.getTexture(def.bulletTexture);
        Texture spawnTex = def.spawnTexture != null ? assets.getTexture(def.spawnTexture) : null;
        Texture deathTex = def.deathTexture != null ? assets.getTexture(def.deathTexture) : null;

        GenericEnemy enemy = ObjectPools.genericEnemyPool.obtain();

        def.inverseMovement = event.inverseMovement;

        enemy.initWithDefinition(def, tex, bulletTex, spawnTex, deathTex, worldWidth, worldHeight, event.x, event.y);

        if (event.powerup != null) enemy.setGuaranteedPowerup(event.powerup);
        entityManager.getEnemies().add(enemy);
    }

    public void reset() {
        totalTime = 0;
        if (schedule != null) {
            for (SpawnEvent event : schedule) event.spawned = false;
        }
    }
}
