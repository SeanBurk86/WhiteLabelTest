package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves data/movement_patterns/&lt;id&gt;.json (file name = id) with plain file I/O. */
public class MovementPatternLibrary {
    private static final Path DIR = Path.of("data/movement_patterns");

    /** A WaypointPath whose children are all MoveToPoint. Older Sequence-of-MoveToPoint patterns
     *  don't count (they behave differently); the panel offers to convert those. */
    public static boolean isWaypointSequence(MovementPatternDef def) {
        if (def == null || !"WaypointPath".equals(def.type)) return false;
        if (def.patterns == null) return true;
        for (MovementPatternDef sub : def.patterns) {
            if (!"MoveToPoint".equals(sub.type)) return false;
        }
        return true;
    }

    /** Loads trigger.movementPattern, or null if unset or missing. Reads from disk on every call,
     *  so callers that edit the result must cache it. */
    public MovementPatternDef resolveForTrigger(Trigger trigger) {
        String id = trigger.movementPattern;
        if (id == null || id.isBlank() || !exists(id)) return null;
        return load(id);
    }

    public MovementPatternDef load(String id) {
        Path path = pathFor(id);
        try {
            String text = Files.readString(path);
            Json json = new Json();
            return json.fromJson(MovementPatternDef.class, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path.toAbsolutePath(), e);
        }
    }

    /** An empty WaypointPath with default options. Not saved until save(). */
    public MovementPatternDef createNew(String id) {
        MovementPatternDef def = new MovementPatternDef();
        def.id = id;
        def.type = "WaypointPath";
        def.patterns = new Array<>();
        return def;
    }

    public void save(MovementPatternDef def) {
        Path path = pathFor(def.id);
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        String text = json.prettyPrint(json.toJson(def, MovementPatternDef.class));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path.toAbsolutePath(), e);
        }
    }

    public boolean exists(String id) {
        return Files.exists(pathFor(id));
    }

    /** A filesystem-safe, unused id based on hint (adds a numeric suffix if needed). */
    public String uniqueId(String hint) {
        String base = hint == null || hint.isBlank() ? "path" : hint.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
        if (!exists(base)) return base;
        int n = 2;
        while (exists(base + "_" + n)) n++;
        return base + "_" + n;
    }

    private static Path pathFor(String id) {
        return DIR.resolve(id + ".json");
    }
}
