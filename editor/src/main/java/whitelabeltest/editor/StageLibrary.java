package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.trigger.TriggerManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads data/stages.json and data/enemies.json at startup and saves edits back. Paths are
 *  relative to assets/, the editor's working directory. */
public class StageLibrary {
    private static final Path STAGES_JSON = Path.of("data/stages.json");
    private static final Path ENEMIES_JSON = Path.of("data/enemies.json");

    private Array<StageDefinition> stages;
    private Array<EnemyDefinition> enemies;

    public StageLibrary() {
        reload();
    }

    public void reload() {
        Json json = new Json();
        stages = readArray(json, STAGES_JSON, StageDefinition.class);
        enemies = readArray(json, ENEMIES_JSON, EnemyDefinition.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> Array<T> readArray(Json json, Path path, Class<T> type) {
        try {
            String text = Files.readString(path);
            Array<T> result = json.fromJson(Array.class, type, text);
            return result != null ? result : new Array<>();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path.toAbsolutePath(), e);
        }
    }

    public Array<StageDefinition> getStages() { return stages; }
    public Array<EnemyDefinition> getEnemies() { return enemies; }

    public EnemyDefinition findEnemy(String id) {
        for (EnemyDefinition def : enemies) if (def.id.equals(id)) return def;
        return null;
    }

    /** Adds a blank enemy in memory (saved by saveEnemies()). The caller checks the id is unused. */
    public EnemyDefinition createEnemy(String id) {
        EnemyDefinition def = new EnemyDefinition();
        def.id = id;
        enemies.add(def);
        return def;
    }

    /** Instances are shared with the canvas, so edits show immediately; this persists them. */
    public void saveEnemies() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        writeText(ENEMIES_JSON, json.prettyPrint(json.toJson(enemies, Array.class, EnemyDefinition.class)));
    }

    /** As saveEnemies(), for stages. */
    public void saveStages() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        writeText(STAGES_JSON, json.prettyPrint(json.toJson(stages, Array.class, StageDefinition.class)));
    }

    /** Creates an empty data/stages/&lt;id&gt;_triggers.json, sets the stage's triggerFile and saves
     *  stages.json. Returns the new path. */
    public Path createTriggerFileForStage(StageDefinition stage) {
        String relativePath = "data/stages/" + stage.id + "_triggers.json";
        Path triggerPath = Path.of(relativePath);

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);

        TriggerManager.TriggerFile fresh = new TriggerManager.TriggerFile();
        fresh.cameraSpeed = 1f;
        fresh.triggers = new Array<>();
        writeJson(json, triggerPath, fresh, TriggerManager.TriggerFile.class);

        stage.triggerFile = relativePath;
        writeText(STAGES_JSON, json.prettyPrint(json.toJson(stages, Array.class, StageDefinition.class)));

        return triggerPath;
    }

    private static <T> void writeJson(Json json, Path path, T value, Class<?> knownType) {
        writeText(path, json.prettyPrint(json.toJson(value, knownType)));
    }

    private static void writeText(Path path, String text) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path.toAbsolutePath(), e);
        }
    }
}
