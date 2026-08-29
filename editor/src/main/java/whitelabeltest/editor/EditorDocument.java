package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import whitelabeltest.gamemanagers.trigger.Trigger;
import whitelabeltest.gamemanagers.trigger.TriggerManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the trigger file currently open in the editor - the exact same
 *  TriggerManager.TriggerFile shape (cameraSpeed + triggers[]) the game's TriggerManager parses, so
 *  save()/load() are a straight read/write of that one format, no translation. Never touches
 *  Gdx.files (no live LibGDX Application exists in this JavaFX app) - reads/writes plain text via
 *  java.nio.file.Files and hands it to Json's String-based overloads instead, same technique
 *  StageLibrary uses. */
public class EditorDocument {
    private TriggerManager.TriggerFile file;
    private Path path;
    private boolean dirty;
    // Several parts of the UI (canvas, status bar, properties panel) each need to react to a
    // load/save/mutation independently, so this is a list rather than a single Runnable slot.
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

    /** Starts a brand-new, empty trigger file in memory (cameraSpeed 1.0, no triggers) at the given
     *  path - the caller (StageLibrary, for a stage with no triggerFile yet) is responsible for
     *  actually creating the file/stages.json entry on disk; this just points the open document at
     *  it and marks it dirty so the first Save writes it for real. */
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

    /** Call after mutating a Trigger's fields in place (e.g. from PropertiesPanel or a drag
     *  reposition) - there's nothing to re-add, just marks the document unsaved. */
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

    /** Same OutputType.json + prettyPrint technique SpawnScheduleEditor.saveToDisk() already uses
     *  for the equivalent schedule-file round trip - see that class's doc. */
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
