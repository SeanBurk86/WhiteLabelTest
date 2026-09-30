package whitelabeltest.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** Parses data/enemies.json into an id-keyed map. */
public final class EnemyDefinitionLoader {
    private EnemyDefinitionLoader() {}

    public static ObjectMap<String, EnemyDefinition> load() {
        Json json = new Json();
        @SuppressWarnings("unchecked")
        Array<EnemyDefinition> defs = json.fromJson(Array.class, EnemyDefinition.class, Gdx.files.internal("data/enemies.json"));
        ObjectMap<String, EnemyDefinition> result = new ObjectMap<>();
        for (EnemyDefinition def : defs) {
            result.put(def.id, def);
        }
        return result;
    }
}
