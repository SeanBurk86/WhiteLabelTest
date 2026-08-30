package whitelabeltest.editor;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Movement/firing/explosion pattern ids for the properties panels' combo boxes - one file per id
 *  under data/movement_patterns/ and data/firing_patterns/ (filename == id), same convention
 *  whitelabeltest.enemy.PatternRegistry.load() already relies on in the game itself. Explosion
 *  patterns are the odd one out (one array in data/explosion_patterns.json, each entry carrying its
 *  own "id" field, and ExplosionPatternDef implements a custom Json.Serializable) - read as a raw
 *  JsonValue tree instead of deserializing, since all that's needed here is the id list. */
public final class PatternIds {
    private PatternIds() {}

    public static List<String> movementPatternIds() {
        return listJsonFilenames(Path.of("data/movement_patterns"));
    }

    public static List<String> firingPatternIds() {
        return listJsonFilenames(Path.of("data/firing_patterns"));
    }

    public static List<String> explosionPatternIds() {
        Path path = Path.of("data/explosion_patterns.json");
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            String text = Files.readString(path);
            JsonValue root = new JsonReader().parse(text);
            List<String> ids = new ArrayList<>();
            for (JsonValue entry = root.child; entry != null; entry = entry.next) {
                String id = entry.getString("id", null);
                if (id != null) ids.add(id);
            }
            ids.sort(Comparator.naturalOrder());
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path.toAbsolutePath(), e);
        }
    }

    private static List<String> listJsonFilenames(Path dir) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> ids = files
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + dir.toAbsolutePath(), e);
        }
    }
}
