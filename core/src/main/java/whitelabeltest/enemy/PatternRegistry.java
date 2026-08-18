package whitelabeltest.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** Holds the named movement/firing/explosion pattern library (movement_patterns/*.json,
 * firing_patterns/*.json, bullets.json, explosion_patterns.json) so enemy definitions can
 * reference a pattern by id instead of embedding it inline. */
public final class PatternRegistry {
    private static final ObjectMap<String, MovementPatternDef> movementPatterns = new ObjectMap<>();
    private static final ObjectMap<String, FiringPatternDef> firingPatterns = new ObjectMap<>();
    private static final ObjectMap<String, BulletDef> bulletDefs = new ObjectMap<>();
    private static final ObjectMap<String, ExplosionPatternDef> explosionPatterns = new ObjectMap<>();

    private PatternRegistry() {}

    public static void load(Json json) {
        // One MovementPatternDef per file under data/movement_patterns/ (filename == id) -
        // same split, and same reasoning, as firing_patterns/ below.
        movementPatterns.clear();
        FileHandle movementDir = Gdx.files.local("data/movement_patterns");
        for (FileHandle f : movementDir.list("json")) {
            MovementPatternDef def = json.fromJson(MovementPatternDef.class, f);
            movementPatterns.put(def.id, def);
        }

        bulletDefs.clear();
        @SuppressWarnings("unchecked")
        Array<BulletDef> bDefs = json.fromJson(Array.class, BulletDef.class, Gdx.files.internal("data/bullets.json"));
        for (BulletDef def : bDefs) bulletDefs.put(def.id, def);

        explosionPatterns.clear();
        @SuppressWarnings("unchecked")
        Array<ExplosionPatternDef> eDefs = json.fromJson(Array.class, ExplosionPatternDef.class, Gdx.files.internal("data/explosion_patterns.json"));
        for (ExplosionPatternDef def : eDefs) explosionPatterns.put(def.id, def);

        // One FiringPatternDef per file under data/firing_patterns/ (filename == id, e.g.
        // "BossAgniFiring.json") rather than one giant array in a single file - these can run
        // 100+ lines deep per pattern (Sequence/Combined trees), so splitting them out makes each
        // one findable and editable on its own instead of scrolling a ~2800-line file. See
        // PatternPreviewer.saveAll() for the write side. Uses Gdx.files.local (not internal) for
        // the directory listing - same reasoning as PatternPreviewer.collectPngFiles().
        firingPatterns.clear();
        FileHandle firingDir = Gdx.files.local("data/firing_patterns");
        for (FileHandle f : firingDir.list("json")) {
            FiringPatternDef def = json.fromJson(FiringPatternDef.class, f);
            firingPatterns.put(def.id, def);
        }
    }

    public static MovementPatternDef getMovement(String id) {
        return id != null ? movementPatterns.get(id) : null;
    }

    public static FiringPatternDef getFiring(String id) {
        return id != null ? firingPatterns.get(id) : null;
    }

    /** Registers (or overwrites) a movement pattern under an id — used by the debug pattern
     *  previewer to install a live-edited working copy without touching the JSON-loaded set. */
    public static void putMovement(String id, MovementPatternDef def) {
        movementPatterns.put(id, def);
    }

    /** Registers (or overwrites) a firing pattern under an id — used by the debug pattern
     *  previewer to install a live-edited working copy without touching the JSON-loaded set. */
    public static void putFiring(String id, FiringPatternDef def) {
        firingPatterns.put(id, def);
    }

    public static Array<String> getMovementIds() {
        Array<String> ids = movementPatterns.keys().toArray();
        ids.sort();
        return ids;
    }

    public static Array<String> getFiringIds() {
        Array<String> ids = firingPatterns.keys().toArray();
        ids.sort();
        return ids;
    }

    public static BulletDef getBullet(String id) {
        return id != null ? bulletDefs.get(id) : null;
    }

    /** Registers (or overwrites) a bullet definition under an id — used by the debug pattern
     *  previewer to install a live-edited working copy without touching the JSON-loaded set. */
    public static void putBullet(String id, BulletDef def) {
        bulletDefs.put(id, def);
    }

    public static ObjectMap.Values<BulletDef> getBulletDefs() {
        return bulletDefs.values();
    }

    public static Array<String> getBulletIds() {
        Array<String> ids = bulletDefs.keys().toArray();
        ids.sort();
        return ids;
    }

    public static ExplosionPatternDef getExplosion(String id) {
        return id != null ? explosionPatterns.get(id) : null;
    }

    public static ObjectMap.Values<ExplosionPatternDef> getExplosionDefs() {
        return explosionPatterns.values();
    }

    public static Array<String> getExplosionIds() {
        Array<String> ids = explosionPatterns.keys().toArray();
        ids.sort();
        return ids;
    }
}