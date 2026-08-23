package whitelabeltest.gamemanagers.replay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.SerializationException;

/** Debug-only: persists named spawn-schedule time bookmarks across runs so testers can jump
 *  straight back to a wave they were tuning instead of waiting through the whole level again. */
public class DebugSaveStateManager {
    private static final String SAVE_FILE = "data/debug_savestates.json";

    private final Array<DebugSaveState> saveStates = new Array<>();

    public DebugSaveStateManager() {
        load();
    }

    private void load() {
        FileHandle file = Gdx.files.local(SAVE_FILE);
        if (!file.exists()) return;

        try {
            Json json = new Json();
            @SuppressWarnings("unchecked")
            Array<DebugSaveState> loaded = json.fromJson(Array.class, DebugSaveState.class, file);
            if (loaded != null) saveStates.addAll(loaded);
        } catch (SerializationException e) {
            Gdx.app.error("DebugSaveStateManager", "Error parsing " + SAVE_FILE, e);
        }
    }

    private void save() {
        Json json = new Json();
        Gdx.files.local(SAVE_FILE).writeString(json.prettyPrint(saveStates), false);
    }

    public Array<DebugSaveState> getSaveStates() { return saveStates; }

    public void addSaveState(String label, float time) {
        saveStates.add(new DebugSaveState(label, time));
        save();
    }

    public void removeSaveState(int index) {
        if (index < 0 || index >= saveStates.size) return;
        saveStates.removeIndex(index);
        save();
    }
}
