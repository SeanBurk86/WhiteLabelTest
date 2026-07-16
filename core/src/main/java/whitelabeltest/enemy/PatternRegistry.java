package whitelabeltest.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** Holds the named movement/firing/explosion pattern library (movement_patterns.json,
 * firing_patterns.json, bullets.json, explosion_patterns.json) so enemy definitions can
 * reference a pattern by id instead of embedding it inline. */
public final class PatternRegistry {
    private static final ObjectMap<String, MovementPatternDef> movementPatterns = new ObjectMap<>();
    private static final ObjectMap<String, FiringPatternDef> firingPatterns = new ObjectMap<>();
    private static final ObjectMap<String, BulletDef> bulletDefs = new ObjectMap<>();
    private static final ObjectMap<String, ExplosionPatternDef> explosionPatterns = new ObjectMap<>();

    private PatternRegistry() {}

    public static void load(Json json) {
        movementPatterns.clear();
        @SuppressWarnings("unchecked")
        Array<MovementPatternDef> mDefs = json.fromJson(Array.class, MovementPatternDef.class, Gdx.files.internal("movement_patterns.json"));
        for (MovementPatternDef def : mDefs) movementPatterns.put(def.id, def);

        bulletDefs.clear();
        @SuppressWarnings("unchecked")
        Array<BulletDef> bDefs = json.fromJson(Array.class, BulletDef.class, Gdx.files.internal("bullets.json"));
        for (BulletDef def : bDefs) bulletDefs.put(def.id, def);

        explosionPatterns.clear();
        @SuppressWarnings("unchecked")
        Array<ExplosionPatternDef> eDefs = json.fromJson(Array.class, ExplosionPatternDef.class, Gdx.files.internal("explosion_patterns.json"));
        for (ExplosionPatternDef def : eDefs) explosionPatterns.put(def.id, def);

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

    public static BulletDef getBullet(String id) {
        return id != null ? bulletDefs.get(id) : null;
    }

    public static ObjectMap.Values<BulletDef> getBulletDefs() {
        return bulletDefs.values();
    }

    public static ExplosionPatternDef getExplosion(String id) {
        return id != null ? explosionPatterns.get(id) : null;
    }

    public static ObjectMap.Values<ExplosionPatternDef> getExplosionDefs() {
        return explosionPatterns.values();
    }
}