package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.trigger.Trigger;
import whitelabeltest.gamemanagers.trigger.TriggerManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The open trigger file (TriggerManager.TriggerFile: cameraSpeed + triggers), with dirty
 *  tracking and change listeners. Uses plain file I/O. */
public class EditorDocument {
    private TriggerManager.TriggerFile file;
    private Path path;
    private boolean dirty;
    // The stage this file belongs to (for background art). Not saved in the trigger file.
    private StageDefinition stageDefinition;
    private final java.util.List<Runnable> changeListeners = new java.util.ArrayList<>();

    public EditorDocument() {
        file = new TriggerManager.TriggerFile();
        file.cameraSpeed = 1f;
        file.triggers = new Array<>();
    }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }

    private void fireChanged() {
        for (Runnable listener : changeListeners) listener.run();
    }

    public TriggerManager.TriggerFile getFile() { return file; }
    public Array<Trigger> getTriggers() { return file.triggers; }
    public Path getPath() { return path; }
    public boolean isDirty() { return dirty; }
    public StageDefinition getStageDefinition() { return stageDefinition; }

    /** Call after load()/newTriggerFile(); notifies listeners. */
    public void setStageDefinition(StageDefinition stageDefinition) {
        this.stageDefinition = stageDefinition;
        fireChanged();
    }

    public void load(Path p) {
        try {
            String text = Files.readString(p);
            Json json = new Json();
            TriggerManager.TriggerFile loaded = json.fromJson(TriggerManager.TriggerFile.class, text);
            this.file = loaded != null ? loaded : new TriggerManager.TriggerFile();
            if (this.file.triggers == null) this.file.triggers = new Array<>();
            this.path = p;
            this.dirty = false;
            fireChanged();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + p, e);
        }
    }

    /** An empty, dirty document at `p` (the caller creates the file entry). */
    public void newTriggerFile(Path p) {
        file = new TriggerManager.TriggerFile();
        file.cameraSpeed = 1f;
        file.triggers = new Array<>();
        path = p;
        dirty = true;
        fireChanged();
    }

    public void addTrigger(Trigger trigger) {
        file.triggers.add(trigger);
        markDirty();
    }

    public void removeTrigger(Trigger trigger) {
        file.triggers.removeValue(trigger, true);
        markDirty();
    }

    /** Call after changing a trigger in place; notifies listeners. */
    public void markDirty() {
        dirty = true;
        fireChanged();
    }

    public void save() {
        if (path == null) return;
        writeToDisk(path);
        dirty = false;
        fireChanged();
    }

    public void saveAs(Path p) {
        path = p;
        save();
    }

    public void saveAsDialog(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Trigger File As");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Trigger JSON", "*_triggers.json"));
        if (path != null) {
            chooser.setInitialDirectory(path.getParent().toFile());
            chooser.setInitialFileName(path.getFileName().toString());
        }
        java.io.File chosen = chooser.showSaveDialog(stage);
        if (chosen != null) saveAs(chosen.toPath());
    }

    /** Pretty-printed standard JSON. */
    private void writeToDisk(Path p) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        String text = json.prettyPrint(json.toJson(file, TriggerManager.TriggerFile.class));
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save " + p, e);
        }
    }

    public String describe() {
        String name = path != null ? path.getFileName().toString() : "(new file)";
        return name + (dirty ? " *unsaved*" : "") + "  -  cameraSpeed=" + file.cameraSpeed
            + "  -  " + file.triggers.size + " triggers";
    }
}
