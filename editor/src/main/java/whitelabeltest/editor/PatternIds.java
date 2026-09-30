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

/** Id and asset path lists for the editor's combo boxes: movement and firing pattern files,
 *  explosion and bullet ids (read from their JSON arrays), sound effects and textures. */
public final class PatternIds {
    private PatternIds() {}

    public static List<String> movementPatternIds() {
        return listJsonFilenames(Path.of("data/movement_patterns"));
    }

    public static List<String> firingPatternIds() {
        return listJsonFilenames(Path.of("data/firing_patterns"));
    }

    public static List<String> explosionPatternIds() {
        return listJsonIds(Path.of("data/explosion_patterns.json"));
    }

    /** Ids from data/bullets.json. */
    public static List<String> bulletIds() {
        return listJsonIds(Path.of("data/bullets.json"));
    }

    /** Sound effect paths relative to assets/, e.g. "audio/sfx/hawkscreech.mp3". */
    public static List<String> sfxPaths() {
        Path dir = Path.of("audio/sfx");
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg"))
                .map(name -> "audio/sfx/" + name)
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + dir.toAbsolutePath(), e);
        }
    }

    /** e.g. "images/enemies/ICE006.png". */
    public static List<String> enemyTexturePaths() {
        return listImagePaths(Path.of("images/enemies"), "images/enemies/");
    }

    /** e.g. "images/bullets/enemybullet.png". */
    public static List<String> bulletTexturePaths() {
        return listImagePaths(Path.of("images/bullets"), "images/bullets/");
    }

    private static List<String> listImagePaths(Path dir, String prefix) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                .map(name -> prefix + name)
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + dir.toAbsolutePath(), e);
        }
    }

    private static List<String> listJsonIds(Path path) {
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
