package whitelabeltest.gamemanagers.spawning;

import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.enemy.PatternRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Debug-only tool: edit the currently-loaded stage's spawn schedule (SpawnScheduler.SpawnEvent
 *  list) live from the debug menu - browse events one at a time (like PatternPreviewer's enemy id
 *  picker), add/remove them, and edit every field: time, event kind (a normal enemy spawn vs.
 *  silence/despawn-matching, a waypoint gem, or a weapon swap), enemy/def id, position, powerup,
 *  formation offset, and movement/firing pattern overrides.
 *
 *  Loads and saves the WHOLE schedule file (SpawnScheduler.ScheduleFile), not just its events
 *  list, so every other cue type already authored in the file (text/sound/sprite cues, gates,
 *  timing windows) round-trips untouched even though this editor doesn't expose them - see
 *  open()/saveToDisk(). Nothing is written to disk until the "Save Schedule To Disk" row is
 *  confirmed, same "edit a working copy, explicit save" convention as PatternPreviewer - and
 *  since SpawnScheduler itself only reads its schedule file once, at stage load, saved changes
 *  don't take effect on the currently-running stage until it's reloaded (see the status message
 *  after saving). */
public class SpawnScheduleEditor {
    private static final String NONE_LABEL = "(none)";
    private static final String[] KIND_OPTIONS = {"Enemy Spawn", "Silence Matching", "Despawn Matching", "Waypoint Gem", "Swap Weapon"};
    private static final Array<String> WEAPON_ID_OPTIONS = Array.with(NONE_LABEL, "BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt");

    private interface FloatGetter { float get(); }
    private interface FloatSetter { void set(float value); }
    private interface BoolGetter { boolean get(); }
    private interface BoolSetter { void set(boolean value); }

    private static final class Row {
        final int indent;
        final Supplier<String> label;
        final Runnable onLeft;
        final Runnable onRight;
        final Runnable onConfirm;
        final Runnable onDelete;

        Row(int indent, Supplier<String> label, Runnable onLeft, Runnable onRight, Runnable onConfirm, Runnable onDelete) {
            this.indent = indent;
            this.label = label;
            this.onLeft = onLeft;
            this.onRight = onRight;
            this.onConfirm = onConfirm;
            this.onDelete = onDelete;
        }
    }

    public static final class DisplayRow {
        public final int indent;
        public final String label;

        DisplayRow(int indent, String label) {
            this.indent = indent;
            this.label = label;
        }
    }

    private boolean active;
    private AssetManager assets;
    private float worldWidth, worldHeight;
    private String scheduleFilePath;
    private SpawnScheduler.ScheduleFile workingFile;
    private int selectedEventIndex;
    private final Array<Row> rows = new Array<>();
    private int selectedRow;
    private boolean dirty;
    // Run at the end of a successful saveToDisk() - see open(). GameController wires this to a
    // fresh reload of the currently-selected stage: SpawnScheduler only reads its schedule file
    // once, at construction, so without this a save would sit correctly on disk but never reach
    // the currently-running game, which was the actual cause of a saved event appearing not to
    // spawn - the fix isn't in dispatch logic (SpawnScheduler already sorts by time and fires
    // correctly regardless of a file's authored order), it's that nothing was pulling the save
    // back into the live session at all.
    private Runnable onSavedToDisk;

    // ---- In-menu text entry (typed numeric entry) - same InputAdapter approach as
    // PatternPreviewer.promptTextEntry, since Gdx.input.getTextInput is a no-op on lwjgl3 desktop.
    private boolean textEntryActive;
    private String textEntryTitle;
    private StringBuilder textEntryBuffer;
    private Consumer<String> textEntryCallback;
    private InputProcessor previousInputProcessor;
    private boolean suppressNextMenuInput;

    public boolean isActive() { return active; }
    public int getSelectedRow() { return selectedRow; }
    public boolean isTextEntryActive() { return textEntryActive; }
    public String getTextEntryTitle() { return textEntryTitle; }
    public String getTextEntryText() { return textEntryBuffer != null ? textEntryBuffer.toString() : ""; }

    public Array<DisplayRow> getDisplayRows() {
        Array<DisplayRow> out = new Array<>(rows.size);
        for (Row r : rows) out.add(new DisplayRow(r.indent, r.label.get()));
        return out;
    }

    /** @param onSavedToDisk run once, at the end of a successful saveToDisk() - see the field's
     *  own doc for why this exists (a save must reach the currently-running game, not just disk). */
    public void open(AssetManager assets, String scheduleFilePath, float worldWidth, float worldHeight, Runnable onSavedToDisk) {
        active = true;
        this.assets = assets;
        this.scheduleFilePath = scheduleFilePath;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.onSavedToDisk = onSavedToDisk;
        this.dirty = false;
        this.selectedRow = 0;
        this.selectedEventIndex = 0;

        Json json = new Json();
        SpawnScheduler.ScheduleFile loaded = null;
        try {
            loaded = json.fromJson(SpawnScheduler.ScheduleFile.class, Gdx.files.internal(scheduleFilePath));
        } catch (Exception e) {
            Gdx.app.error("SpawnScheduleEditor", "Error parsing " + scheduleFilePath, e);
        }
        workingFile = loaded != null ? loaded : new SpawnScheduler.ScheduleFile();
        if (workingFile.events == null) workingFile.events = new Array<>();

        rebuildRows();
    }

    public void close() {
        if (!active) return;
        if (textEntryActive) finishTextEntry(false);
        active = false;
    }

    /** @return true if the delete key was consumed by the selected row (removing an event) rather
     *  than falling through to closing the whole screen - same contract as PatternPreviewer.handleInput. */
    public boolean handleInput(InputManager input) {
        if (textEntryActive) return false;
        if (suppressNextMenuInput) {
            suppressNextMenuInput = false;
            return false;
        }
        if (rows.size == 0) return false;

        if (input.isDebugMenuUpJustPressed()) selectedRow = (selectedRow - 1 + rows.size) % rows.size;
        if (input.isDebugMenuDownJustPressed()) selectedRow = (selectedRow + 1) % rows.size;

        Row row = rows.get(selectedRow);
        if (input.isDebugMenuLeftJustPressed() && row.onLeft != null) row.onLeft.run();
        if (input.isDebugMenuRightJustPressed() && row.onRight != null) row.onRight.run();
        if (input.isDebugMenuConfirmJustPressed() && row.onConfirm != null) row.onConfirm.run();
        if (input.isDebugMenuDeleteJustPressed() && row.onDelete != null) {
            row.onDelete.run();
            return true;
        }
        return false;
    }

    // ---- Row tree building ----------------------------------------------------------------------

    private void rebuildRows() {
        int prevSelected = selectedRow;
        rows.clear();

        Array<SpawnScheduler.SpawnEvent> events = workingFile.events;

        rows.add(actionRow(0, () -> dirty ? "[ SAVE & RELOAD STAGE ]  *unsaved*" : "[ SAVE & RELOAD STAGE ]", this::saveToDisk));
        rows.add(headerRow(0, ""));

        rows.add(headerRow(0, "Schedule: " + scheduleFilePath));
        rows.add(headerRow(0, events.size + " event(s)"));
        rows.add(actionRow(0, () -> "[+ Add Spawn Event]", this::addEvent));
        rows.add(headerRow(0, ""));

        if (events.size == 0) {
            rows.add(headerRow(0, "(no events - add one above)"));
        } else {
            selectedEventIndex = MathUtils.clamp(selectedEventIndex, 0, events.size - 1);
            SpawnScheduler.SpawnEvent event = events.get(selectedEventIndex);

            rows.add(new Row(0,
                () -> "Event " + (selectedEventIndex + 1) + "/" + events.size + " @ " + formatFloat(event.time) + "s  [" + eventKind(event) + "]",
                () -> { selectedEventIndex = (selectedEventIndex - 1 + events.size) % events.size; applyChange(); },
                () -> { selectedEventIndex = (selectedEventIndex + 1) % events.size; applyChange(); },
                null,
                this::removeSelectedEvent));
            rows.add(headerRow(1, "</> cycle event   Del (on this row) = remove event"));

            appendEventFieldRows(event);
        }

        selectedRow = rows.size == 0 ? 0 : MathUtils.clamp(prevSelected, 0, rows.size - 1);
    }

    private void appendEventFieldRows(SpawnScheduler.SpawnEvent event) {
        rows.add(numberRow(1, "Time", () -> event.time, v -> event.time = v, 0.25f, false));

        rows.add(new Row(1, () -> "Kind: " + eventKind(event),
            () -> { cycleKind(event, -1); applyChange(); },
            () -> { cycleKind(event, 1); applyChange(); },
            null, null));

        rows.add(idPickRow(1, "Enemy/Def Id", () -> event.type, assets.getEnemyIds(), v -> { event.type = v; applyChange(); }));
        rows.add(numberRow(1, "X", () -> event.x, v -> event.x = v, 0.25f, false));
        rows.add(numberRow(1, "Y", () -> event.y, v -> event.y = v, 0.25f, false));

        rows.add(new Row(1, () -> "Powerup: " + (event.powerup != null ? event.powerup : "none"),
            () -> { setPowerup(event, (event.powerup != null ? event.powerup : 0) - 1); applyChange(); },
            () -> { setPowerup(event, (event.powerup != null ? event.powerup : 0) + 1); applyChange(); },
            null, null));

        rows.add(toggleRow(1, "Inverse Movement", () -> event.inverseMovement, v -> event.inverseMovement = v));

        rows.add(nullableNumberRow(1, "Offset X (formation slot)", () -> event.offsetX, v -> event.offsetX = v, 0.1f));
        rows.add(actionRow(2, () -> "[Clear Offset X -> none]", () -> { event.offsetX = Float.NaN; applyChange(); }));
        rows.add(nullableNumberRow(1, "Offset Y (formation slot)", () -> event.offsetY, v -> event.offsetY = v, 0.1f));
        rows.add(actionRow(2, () -> "[Clear Offset Y -> none]", () -> { event.offsetY = Float.NaN; applyChange(); }));

        Array<String> movementOptions = new Array<>();
        movementOptions.add(NONE_LABEL);
        movementOptions.addAll(PatternRegistry.getMovementIds());
        rows.add(idPickRow(1, "Movement Override", () -> event.movementPattern != null ? event.movementPattern : NONE_LABEL, movementOptions,
            v -> { event.movementPattern = NONE_LABEL.equals(v) ? null : v; applyChange(); }));

        Array<String> firingOptions = new Array<>();
        firingOptions.add(NONE_LABEL);
        firingOptions.addAll(PatternRegistry.getFiringIds());
        rows.add(idPickRow(1, "Firing Override", () -> event.firingPattern != null ? event.firingPattern : NONE_LABEL, firingOptions,
            v -> { event.firingPattern = NONE_LABEL.equals(v) ? null : v; applyChange(); }));

        rows.add(idPickRow(1, "Swap Weapon Id", () -> event.swapWeaponId != null ? event.swapWeaponId : NONE_LABEL, WEAPON_ID_OPTIONS,
            v -> { event.swapWeaponId = NONE_LABEL.equals(v) ? null : v; applyChange(); }));
        rows.add(numberRow(1, "Swap Weapon Slot", () -> (float) event.weaponSlot, v -> event.weaponSlot = Math.round(v), 1f, true));
    }

    private void applyChange() {
        dirty = true;
        rebuildRows();
    }

    private void addEvent() {
        Array<SpawnScheduler.SpawnEvent> events = workingFile.events;
        SpawnScheduler.SpawnEvent event = new SpawnScheduler.SpawnEvent();
        float maxTime = 0f;
        for (SpawnScheduler.SpawnEvent e : events) maxTime = Math.max(maxTime, e.time);
        event.time = events.size == 0 ? 0f : maxTime + 1f;
        Array<String> enemyIds = assets.getEnemyIds();
        event.type = enemyIds.size > 0 ? enemyIds.first() : null;
        event.x = worldWidth / 2f;
        event.y = worldHeight + 0.5f;
        events.add(event);
        selectedEventIndex = events.size - 1;
        applyChange();
    }

    private void removeSelectedEvent() {
        Array<SpawnScheduler.SpawnEvent> events = workingFile.events;
        if (events.size == 0) return;
        events.removeIndex(selectedEventIndex);
        selectedEventIndex = MathUtils.clamp(selectedEventIndex, 0, Math.max(0, events.size - 1));
        applyChange();
    }

    // ---- Event kind (silence/despawn/waypointGem/swapWeaponId are mutually exclusive flags on
    // SpawnEvent - see SpawnScheduler.update()'s dispatch order, mirrored here) --------------------

    private static String eventKind(SpawnScheduler.SpawnEvent event) {
        if (event.silence) return KIND_OPTIONS[1];
        if (event.despawn) return KIND_OPTIONS[2];
        if (event.waypointGem) return KIND_OPTIONS[3];
        if (event.swapWeaponId != null) return KIND_OPTIONS[4];
        return KIND_OPTIONS[0];
    }

    private static void cycleKind(SpawnScheduler.SpawnEvent event, int dir) {
        String current = eventKind(event);
        int idx = 0;
        for (int i = 0; i < KIND_OPTIONS.length; i++) {
            if (KIND_OPTIONS[i].equals(current)) { idx = i; break; }
        }
        int next = (idx + dir + KIND_OPTIONS.length) % KIND_OPTIONS.length;
        applyKind(event, KIND_OPTIONS[next]);
    }

    private static void applyKind(SpawnScheduler.SpawnEvent event, String kind) {
        event.silence = false;
        event.despawn = false;
        event.waypointGem = false;
        switch (kind) {
            case "Silence Matching" -> event.silence = true;
            case "Despawn Matching" -> event.despawn = true;
            case "Waypoint Gem" -> event.waypointGem = true;
            case "Swap Weapon" -> { if (event.swapWeaponId == null) event.swapWeaponId = "BasicWeapon"; }
            default -> event.swapWeaponId = null; // "Enemy Spawn" - no flags, no weapon swap either
        }
    }

    private static void setPowerup(SpawnScheduler.SpawnEvent event, int raw) {
        event.powerup = raw < 1 ? null : Math.min(raw, 3);
    }

    /** Writes the WHOLE working schedule file (including every cue type this editor doesn't
     *  expose - see class doc) back to the real assets/ JSON file, then runs onSavedToDisk (see
     *  its field doc) so the change reaches the currently-running game immediately instead of
     *  silently sitting on disk until some later, easy-to-forget manual reload. Only resolves to
     *  the true source file when launched via `gradlew run`/`:lwjgl3:run`, which pins the working
     *  directory to assets/ (see lwjgl3/build.gradle) - same mechanism PatternPreviewer.saveAll()
     *  relies on. */
    private void saveToDisk() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        Gdx.files.local(scheduleFilePath).writeString(
            json.prettyPrint(json.toJson(workingFile, SpawnScheduler.ScheduleFile.class)), false);

        dirty = false;
        if (onSavedToDisk != null) onSavedToDisk.run();
    }

    // ---- In-menu text entry -------------------------------------------------------------------

    private void promptNumber(String name, float current, boolean isInt, FloatSetter setter) {
        String title = "Enter value for " + name + " (current: " + (isInt ? String.valueOf(Math.round(current)) : formatFloat(current)) + ")";
        Predicate<Character> filter = isInt
            ? c -> Character.isDigit(c) || c == '-'
            : c -> Character.isDigit(c) || c == '-' || c == '.';
        promptTextEntry(title, filter, text -> {
            try {
                float parsed = Float.parseFloat(text);
                setter.set(isInt ? Math.round(parsed) : snap(parsed));
                applyChange();
            } catch (NumberFormatException ignored) {
                // Leave the field unchanged on unparsable input (e.g. a bare "-" or ".").
            }
        });
    }

    private void promptTextEntry(String title, Predicate<Character> charFilter, Consumer<String> onEntered) {
        textEntryActive = true;
        textEntryTitle = title;
        textEntryBuffer = new StringBuilder();
        textEntryCallback = onEntered;
        previousInputProcessor = Gdx.input.getInputProcessor();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
                if (!textEntryActive) return false;
                if (textEntryBuffer.length() < 40 && charFilter.test(character)) {
                    textEntryBuffer.append(character);
                }
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (!textEntryActive) return false;
                if (keycode == Input.Keys.BACKSPACE) {
                    if (textEntryBuffer.length() > 0) textEntryBuffer.setLength(textEntryBuffer.length() - 1);
                    return true;
                }
                if (keycode == Input.Keys.ENTER) {
                    finishTextEntry(true);
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    finishTextEntry(false);
                    return true;
                }
                return false;
            }
        });
    }

    private void finishTextEntry(boolean confirmed) {
        Gdx.input.setInputProcessor(previousInputProcessor);
        textEntryActive = false;
        suppressNextMenuInput = true;
        String text = confirmed && textEntryBuffer != null ? textEntryBuffer.toString().trim() : null;
        Consumer<String> callback = textEntryCallback;
        textEntryCallback = null;
        textEntryBuffer = null;
        if (text != null && !text.isEmpty() && callback != null) callback.accept(text);
    }

    // ---- Row factories --------------------------------------------------------------------------

    private Row headerRow(int indent, String text) {
        return new Row(indent, () -> text, null, null, null, null);
    }

    private Row actionRow(int indent, Supplier<String> label, Runnable onConfirm) {
        return new Row(indent, label, null, null, onConfirm, null);
    }

    private Row numberRow(int indent, String name, FloatGetter getter, FloatSetter setter, float step, boolean isInt) {
        Supplier<String> label = () -> name + ": " + (isInt ? String.valueOf(Math.round(getter.get())) : formatFloat(getter.get()));
        Runnable dec = () -> { setter.set(isInt ? getter.get() - step : snap(getter.get() - step)); applyChange(); };
        Runnable inc = () -> { setter.set(isInt ? getter.get() + step : snap(getter.get() + step)); applyChange(); };
        Runnable typeIn = () -> promptNumber(name, getter.get(), isInt, setter);
        return new Row(indent, label, dec, inc, typeIn, null);
    }

    // Same NaN-means-"none" convention as SpawnEvent.offsetX/offsetY themselves - left/right starts
    // nudging from 0 the first time a "none" field is touched, and typed entry (Confirm) always
    // sets a concrete value; a companion "[Clear -> none]" action row (see appendEventFieldRows)
    // is the only way back to "none" once set, so a stepper press near 0 can't accidentally re-null it.
    private Row nullableNumberRow(int indent, String name, FloatGetter getter, FloatSetter setter, float step) {
        Supplier<String> label = () -> name + ": " + (Float.isNaN(getter.get()) ? "none" : formatFloat(getter.get()));
        Runnable dec = () -> { float cur = getter.get(); setter.set(snap(Float.isNaN(cur) ? -step : cur - step)); applyChange(); };
        Runnable inc = () -> { float cur = getter.get(); setter.set(snap(Float.isNaN(cur) ? step : cur + step)); applyChange(); };
        Runnable typeIn = () -> promptNumber(name, Float.isNaN(getter.get()) ? 0f : getter.get(), false, setter);
        return new Row(indent, label, dec, inc, typeIn, null);
    }

    private static float snap(float value) {
        return Math.round(value * 100f) / 100f;
    }

    private static String formatFloat(float value) {
        if (value == Math.round(value)) return String.valueOf(Math.round(value));
        return String.valueOf(value);
    }

    private Row toggleRow(int indent, String name, BoolGetter getter, BoolSetter setter) {
        Supplier<String> label = () -> name + ": " + (getter.get() ? "true" : "false");
        Runnable flip = () -> { setter.set(!getter.get()); applyChange(); };
        return new Row(indent, label, flip, flip, flip, null);
    }

    private Row idPickRow(int indent, String name, Supplier<String> current, Array<String> ids, Consumer<String> onSelect) {
        Supplier<String> label = () -> name + ": " + (current.get() != null ? current.get() : "-");
        Runnable prev = () -> cycleId(ids, current.get(), -1, onSelect);
        Runnable next = () -> cycleId(ids, current.get(), 1, onSelect);
        return new Row(indent, label, prev, next, null, null);
    }

    private static void cycleId(Array<String> ids, String currentId, int dir, Consumer<String> onSelect) {
        if (ids.size == 0) return;
        int idx = ids.indexOf(currentId, false);
        if (idx < 0) idx = 0;
        int next = (idx + dir + ids.size) % ids.size;
        onSelect.accept(ids.get(next));
    }
}
