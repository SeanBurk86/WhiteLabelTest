package whitelabeltest.editor;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import whitelabeltest.enemy.FiringPatternDef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads/writes one data/firing_patterns/&lt;id&gt;.json at a time - filename == id, the same
 *  convention whitelabeltest.enemy.PatternRegistry.load() relies on in the game itself (see
 *  PatternIds.firingPatternIds()). FiringPatternDef implements Json.Serializable with its own
 *  hand-written read()/write() (not plain reflection), which Json.fromJson()/toJson() already call
 *  automatically - same String-based read/write technique MovementPatternLibrary/StageLibrary/
 *  EditorDocument all already use (no live Gdx.files, since there's no LibGDX Application in this
 *  JavaFX app). See FiringPatternEditorDialog for the actual editing UI this backs. */
public class FiringPatternLibrary {
    private static final Path DIR = Path.of("data/firing_patterns");

    public FiringPatternDef load(String id) {
        Path path = pathFor(id);
        try {
            String text = Files.readString(path);
            Json json = new Json();
            return json.fromJson(FiringPatternDef.class, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path.toAbsolutePath(), e);
        }
    }

    public FiringPatternDef createNew(String id) {
        FiringPatternDef def = new FiringPatternDef();
        def.id = id;
        def.type = "Aimed";
        return def;
    }

    public void save(FiringPatternDef def) {
        Path path = pathFor(def.id);
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        String text = json.prettyPrint(json.toJson(def, FiringPatternDef.class));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path.toAbsolutePath(), e);
        }
    }

    public void delete(String id) {
        try {
            Files.deleteIfExists(pathFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + pathFor(id).toAbsolutePath(), e);
        }
    }

    public boolean exists(String id) {
        return Files.exists(pathFor(id));
    }

    private static Path pathFor(String id) {
        return DIR.resolve(id + ".json");
    }
}
