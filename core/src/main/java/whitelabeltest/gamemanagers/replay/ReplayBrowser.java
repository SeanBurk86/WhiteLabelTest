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

public class ReplayBrowser {
    // How many rows one screenful shows - see setPageSize(). Each consumer (ReplaySelectScreen,
    // UIManager's debug overlay) sets this to whatever actually fits its own layout; this default
    // matches the debug overlay's tighter line spacing, so it stays sane even if a caller forgets.
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

    /** How many rows getDisplayNames() returns at once - call this once after open() if the
     *  caller's layout needs something other than DEFAULT_PAGE_SIZE (e.g. ReplaySelectScreen's
     *  bigger font fits fewer rows than the debug overlay). */
    public void setPageSize(int pageSize) { this.pageSize = Math.max(1, pageSize); }

    public int getPageCount() { return files.size == 0 ? 1 : (files.size + pageSize - 1) / pageSize; }
    public int getCurrentPage() { return files.size == 0 ? 0 : selectedIndex / pageSize; }
    /** Row index of the current selection *within* the page getDisplayNames() just returned - use
     *  this instead of getSelectedIndex() to highlight the right row once the list is paged. */
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
        // Filenames embed epoch millis (replay_<epochMillis>.json), so a reverse string sort is
        // also a reverse chronological sort - newest recordings first.
        files.sort(new Comparator<FileHandle>() {
            @Override
            public int compare(FileHandle a, FileHandle b) {
                return b.name().compareTo(a.name());
            }
        });
        selectedIndex = 0;
    }

    /** Labels for just the current page (see getCurrentPage()/setPageSize()), not the whole list -
     *  pair with getSelectedIndexInPage() to highlight the right row. */
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

    /** direction -1 moves the highlight up, +1 moves it down - input-source-agnostic, used by both
     *  handleInput() (debug menu) and ReplaySelectScreen (its own direct Gdx.input polling). */
    public void moveSelection(int direction) {
        if (files.size == 0) return;
        selectedIndex = (selectedIndex + direction + files.size) % files.size;
    }

    /** direction -1 jumps a full page back, +1 a full page forward - wraps like moveSelection(),
     *  and lands on the first row of the destination page. No-op with one page or fewer. */
    public void movePage(int direction) {
        if (files.size == 0) return;
        int pageCount = getPageCount();
        if (pageCount <= 1) return;
        int page = (getCurrentPage() + direction + pageCount) % pageCount;
        selectedIndex = Math.min(page * pageSize, files.size - 1);
    }

    /** Loads and returns the currently-highlighted replay, or null (with getStatusMessage() set) if
     *  it fails to parse. Input-source-agnostic, same as moveSelection(). */
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
