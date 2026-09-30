package whitelabeltest.gamemanagers.replay;
import whitelabeltest.gamemanagers.input.InputManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.SerializationException;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

/** Paged list of saved replay files, newest first; shared by ReplaySelectScreen and the debug menu. */
public class ReplayBrowser {
    // Rows per page unless the caller sets its own (see setPageSize()).
    private static final int DEFAULT_PAGE_SIZE = 10;

    private boolean active;
    private final Array<FileHandle> files = new Array<>();
    private int selectedIndex;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private ReplayData pendingSelection;
    private String statusMessage;

    public boolean isActive() { return active; }
    public int getSelectedIndex() { return selectedIndex; }
    public String getStatusMessage() { return statusMessage; }

    /** Rows per page, to fit the caller's layout. */
    public void setPageSize(int pageSize) { this.pageSize = Math.max(1, pageSize); }

    public int getPageCount() { return files.size == 0 ? 1 : (files.size + pageSize - 1) / pageSize; }
    public int getCurrentPage() { return files.size == 0 ? 0 : selectedIndex / pageSize; }
    /** The selection's row within the current page (for highlighting). */
    public int getSelectedIndexInPage() { return files.size == 0 ? 0 : selectedIndex % pageSize; }

    public void open() {
        active = true;
        statusMessage = null;
        refresh();
    }

    public void close() {
        active = false;
    }

    public String getFolderPath() {
        return Gdx.files.external(ReplayRecorder.REPLAY_DIR).file().getAbsolutePath();
    }

    private void refresh() {
        files.clear();
        FileHandle dir = Gdx.files.external(ReplayRecorder.REPLAY_DIR);
        if (dir.exists()) {
            for (FileHandle f : dir.list(".json")) files.add(f);
        }
        // Names are replay_<epochMillis>.json, so reverse name order = newest first.
        files.sort(new Comparator<FileHandle>() {
            @Override
            public int compare(FileHandle a, FileHandle b) {
                return b.name().compareTo(a.name());
            }
        });
        selectedIndex = 0;
    }

    /** Labels for the current page only. */
    public Array<String> getDisplayNames() {
        int start = getCurrentPage() * pageSize;
        int end = Math.min(start + pageSize, files.size);
        Array<String> names = new Array<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) names.add(formatLabel(files.get(i)));
        return names;
    }

    private String formatLabel(FileHandle f) {
        String name = f.nameWithoutExtension();
        try {
            long epochMillis = Long.parseLong(name.substring(name.indexOf('_') + 1));
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(epochMillis));
        } catch (Exception e) {
            return name;
        }
    }

    public ReplayData consumePendingSelection() {
        ReplayData d = pendingSelection;
        pendingSelection = null;
        return d;
    }

    /** -1 = up, +1 = down, wrapping. */
    public void moveSelection(int direction) {
        if (files.size == 0) return;
        selectedIndex = (selectedIndex + direction + files.size) % files.size;
    }

    /** -1/+1 = previous/next page (wrapping), landing on its first row. */
    public void movePage(int direction) {
        if (files.size == 0) return;
        int pageCount = getPageCount();
        if (pageCount <= 1) return;
        int page = (getCurrentPage() + direction + pageCount) % pageCount;
        selectedIndex = Math.min(page * pageSize, files.size - 1);
    }

    /** Loads the highlighted replay, or returns null and sets the status message on a parse error. */
    public ReplayData confirmSelection() {
        if (files.size == 0) return null;
        return load(files.get(selectedIndex));
    }

    public void handleInput(InputManager input) {
        if (files.size == 0) return;
        if (input.isDebugMenuUpJustPressed()) moveSelection(-1);
        if (input.isDebugMenuDownJustPressed()) moveSelection(1);
        if (input.isDebugMenuLeftJustPressed()) movePage(-1);
        if (input.isDebugMenuRightJustPressed()) movePage(1);
        if (input.isDebugMenuConfirmJustPressed()) {
            ReplayData data = confirmSelection();
            if (data != null) pendingSelection = data;
        }
    }

    private ReplayData load(FileHandle file) {
        try {
            return new Json().fromJson(ReplayData.class, file);
        } catch (SerializationException e) {
            Gdx.app.error("ReplayBrowser", "Error parsing " + file.path(), e);
            statusMessage = "Failed to load " + file.name();
            return null;
        }
    }
}
