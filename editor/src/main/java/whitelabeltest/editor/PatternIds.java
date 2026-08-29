package whitelabeltest.editor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Movement/firing pattern ids for the PropertiesPanel's override combo boxes - one file per id
 *  under data/movement_patterns/ and data/firing_patterns/ (filename == id), same convention
 *  whitelabeltest.enemy.PatternRegistry.load() already relies on in the game itself. */
public final class PatternIds {
    private PatternIds() {}

    public static List<String> movementPatternIds() {
        return listJsonFilenames(Path.of("data/movement_patterns"));
    }

    public static List<String> firingPatternIds() {
        return listJsonFilenames(Path.of("data/firing_patterns"));
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
