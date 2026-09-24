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

        // One FiringPatternDef per file under data/firing_patterns/ (filename == id, e.g.
        // "BossAgniFiring.json") rather than one giant array in a single file - these can run
        // 100+ lines deep per pattern (Sequence/Combined trees), so splitting them out makes each
        // one findable and editable on its own instead of scrolling a ~2800-line file. See
        // PatternPreviewer.saveAll() for the write side. See listJson() for how the files are found.
        firingPatterns.clear();
        for (FileHandle f : listJson("data/firing_patterns")) {
            FiringPatternDef def = json.fromJson(FiringPatternDef.class, f);
            firingPatterns.put(def.id, def);
        }
    }

    /** Every .json file directly inside the asset folder dir. Run from the project (working directory =
     *  assets/, as `gradlew run` and the editor's Quick Play do) that's a live listing of the folder, so
     *  files the pattern previewer/editor just saved are picked up. A packaged build has no such folder -
     *  its assets are inside the jar, where a directory can't be listed (Gdx.files.local found nothing and
     *  every enemy lost its movement and firing patterns) - so there the list comes from assets.txt, the
     *  full asset listing build.gradle's generateAssetList task writes and packages on every build. */
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