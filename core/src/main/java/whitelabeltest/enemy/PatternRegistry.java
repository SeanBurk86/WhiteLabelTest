package whitelabeltest.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** Holds the named movement/firing pattern library (movement_patterns.json, firing_patterns.json)
 * so enemy definitions can reference a pattern by id instead of embedding it inline. */
public final class PatternRegistry {
    private static final ObjectMap<String, MovementPatternDef> movementPatterns = new ObjectMap<>();
    private static final ObjectMap<String, FiringPatternDef> firingPatterns = new ObjectMap<>();

    private PatternRegistry() {}

    public static void load(Json json) {
        movementPatterns.clear();
        @SuppressWarnings("unchecked")
        Array<MovementPatternDef> mDefs = json.fromJson(Array.class, MovementPatternDef.class, Gdx.files.internal("movement_patterns.json"));
        for (MovementPatternDef def : mDefs) movementPatterns.put(def.id, def);

        firingPatterns.clear();
        @SuppressWarnings("unchecked")
        Array<FiringPatternDef> fDefs = json.fromJson(Array.class, FiringPatternDef.class, Gdx.files.internal("firing_patterns.json"));
        for (FiringPatternDef def : fDefs) firingPatterns.put(def.id, def);
    }

    public static MovementPatternDef getMovement(String id) {
        return id != null ? movementPatterns.get(id) : null;
    }

    public static FiringPatternDef getFiring(String id) {
        return id != null ? firingPatterns.get(id) : null;
    }
}