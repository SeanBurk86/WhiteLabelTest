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

/** Reads/writes one data/movement_patterns/&lt;id&gt;.json at a time - filename == id, the same
 *  convention whitelabeltest.enemy.PatternRegistry.load() relies on in the game itself (see
 *  PatternIds.movementPatternIds(), which lists the ids this reads from). MovementPatternDef
 *  implements Json.Serializable with its own hand-written read()/write() (not plain reflection -
 *  see that class), which Json.fromJson()/toJson() already call automatically, so this needs no
 *  special handling beyond the same String-based read/write technique StageLibrary/EditorDocument
 *  already use (no live Gdx.files, since there's no LibGDX Application in this JavaFX app). */
public class MovementPatternLibrary {
    private static final Path DIR = Path.of("data/movement_patterns");

    /** A pattern this editor can safely display and edit as a waypoint list: top-level type
     *  "WaypointPath" (the curved, tension/orientation/sound/weapon-set-aware system - see
     *  WaypointPathMovement) whose sub-patterns (if any) are ALL type "MoveToPoint". Deliberately
     *  does NOT include the older "Sequence"-of-"MoveToPoint" shape ~18 shipped enemies still use
     *  (straight-line, time-boxed legs via SequencedMovementPattern) - silently reinterpreting one
     *  of those as a WaypointPath would change its already-tuned in-game behavior out from under it.
     *  A "Sequence" pattern (or anything else this editor doesn't specifically understand) instead
     *  falls into the existing "not a clean waypoint list, offer Convert" bucket - see
     *  PropertiesPanel.refreshMovementPathBox()'s own doc - which replaces it with a fresh, empty
     *  WaypointPath rather than trying to reinterpret the old one. */
    public static boolean isWaypointSequence(MovementPatternDef def) {
        if (def == null || !"WaypointPath".equals(def.type)) return false;
        if (def.patterns == null) return true;
        for (MovementPatternDef sub : def.patterns) {
            if (!"MoveToPoint".equals(sub.type)) return false;
        }
        return true;
    }

    /** Resolves + loads whichever pattern an enemy-spawn trigger's movement actually uses - purely
     *  this trigger's own `movementPattern`, the same field GenericEnemy.initWithDefinition() reads
     *  in the real game - or null if it's unset or doesn't resolve to a file that actually exists on
     *  disk. Movement isn't part of EnemyDefinition at all (see that class's own doc), so there's no
     *  type-level fallback to fall back to here anymore. Reloads from disk on every call, so callers
     *  that mutate the returned object (StageCanvas's path-edit mode) must cache the result
     *  themselves rather than calling this again mid-edit, which would silently discard any unsaved
     *  changes. */
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

    /** A fresh, empty WaypointPath ready to have points added - see StageCanvas's click-to-add.
     *  Path-level fields (closePath/globalSpeed/flipX/flipY) keep their MovementPatternDef defaults
     *  (off/1.0/off/off). Not written to disk until save() is called. */
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

    private static Path pathFor(String id) {
        return DIR.resolve(id + ".json");
    }
}
