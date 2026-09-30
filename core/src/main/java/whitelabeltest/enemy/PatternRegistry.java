package whitelabeltest.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/** The movement/firing/bullet/explosion pattern library by id: data/movement_patterns/*.json and
 *  data/firing_patterns/*.json (one pattern per file, filename = id), bullets.json and
 *  explosion_patterns.json. */
public final class PatternRegistry {
    private static final ObjectMap<String, MovementPatternDef> movementPatterns = new ObjectMap<>();
    private static final ObjectMap<String, FiringPatternDef> firingPatterns = new ObjectMap<>();
    private static final ObjectMap<String, BulletDef> bulletDefs = new ObjectMap<>();
    private static final ObjectMap<String, ExplosionPatternDef> explosionPatterns = new ObjectMap<>();

    private PatternRegistry() {}

    public static void load(Json json) {
        movementPatterns.clear();
        for (FileHandle f : listJson("data/movement_patterns")) {
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

        firingPatterns.clear();
        for (FileHandle f : listJson("data/firing_patterns")) {
            FiringPatternDef def = json.fromJson(FiringPatternDef.class, f);
            firingPatterns.put(def.id, def);
        }
    }

    /** Every .json directly inside `dir`. From the project (working directory = assets/) this lists
     *  the folder live, so freshly saved patterns are found. In a packaged jar a directory can't be
     *  listed, so it falls back to assets.txt (written by build.gradle's generateAssetList task). */
    private static Array<FileHandle> listJson(String dir) {
        Array<FileHandle> files = new Array<>();
        FileHandle localDir = Gdx.files.local(dir);
        if (localDir.isDirectory()) {
            files.addAll(localDir.list("json"));
            return files;
        }
        String prefix = dir + "/";
        for (String line : Gdx.files.internal("assets.txt").readString("UTF-8").split("\\r?\\n")) {
            String path = line.trim().replace('\\', '/');
            if (path.startsWith(prefix) && path.endsWith(".json") && path.indexOf('/', prefix.length()) < 0) {
                files.add(Gdx.files.internal(path));
            }
        }
        if (files.isEmpty()) Gdx.app.error("PatternRegistry", "no pattern files found for " + dir);
        return files;
    }

    public static MovementPatternDef getMovement(String id) {
        return id != null ? movementPatterns.get(id) : null;
    }

    public static FiringPatternDef getFiring(String id) {
        return id != null ? firingPatterns.get(id) : null;
    }

    /** Registers or replaces a movement pattern in memory (editors, synthetic wave patterns). */
    public static void putMovement(String id, MovementPatternDef def) {
        movementPatterns.put(id, def);
    }

    /** Registers or replaces a firing pattern in memory. */
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

    /** Registers or replaces a bullet definition in memory. */
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