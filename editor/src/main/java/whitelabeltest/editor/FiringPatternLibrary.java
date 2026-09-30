package whitelabeltest.editor;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import whitelabeltest.enemy.FiringPatternDef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves data/firing_patterns/&lt;id&gt;.json (file name = id) with plain file I/O. */
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
