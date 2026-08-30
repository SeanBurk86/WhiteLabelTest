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

/** Reads data/stages.json and data/enemies.json once at startup - the stage picker (Open Stage...)
 *  and enemy palette source their lists from here. Every path is relative to the assets root, the
 *  same convention every existing *.json data file already uses (e.g.
 *  StageDefinition.spawnSchedule = "data/stages/stage1_schedule.json") - the editor's run task sets
 *  its working directory to assets/ (see editor/build.gradle) specifically so these plain relative
 *  paths resolve exactly the way the game itself resolves them, with no translation. */
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

    /** Writes the current in-memory enemy list back to data/enemies.json - see
     *  EnemyDefinitionPanel, the only caller. Every EnemyDefinition instance here is shared by
     *  reference with whatever a placed Trigger's TriggerNode looked up (findEnemy()), so edits
     *  already show up live on the canvas before this is even called; this just persists them. */
    public void saveEnemies() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        writeText(ENEMIES_JSON, json.prettyPrint(json.toJson(enemies, Array.class, EnemyDefinition.class)));
    }

    /** For a stage with no triggerFile yet: writes a fresh, empty trigger JSON at the conventional
     *  data/stages/&lt;id&gt;_triggers.json path and patches only that one stage's triggerFile field
     *  into stages.json (re-serializing the whole array - every other field/stage round-trips
     *  untouched through the same Json class the game itself parses stages.json with). Returns the
     *  new file's path so the caller can immediately EditorDocument.load() it. */
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
