package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import whitelabeltest.enemy.BulletSpeedPhase;
import whitelabeltest.enemy.FiringPatternDef;

import java.util.List;

import static whitelabeltest.editor.FormControls.checkBox;
import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.fieldLabel;
import static whitelabeltest.editor.FormControls.numberRow;
import static whitelabeltest.editor.FormControls.sectionLabel;
import static whitelabeltest.editor.FormControls.withBlank;

/** Builds the tabbed field editor for one FiringPatternDef - shared by FiringPatternEditorDialog
 *  (the top-level pattern being edited) and, recursively, by its own "Sub-Patterns" tab (a
 *  Sequence/Combined pattern's nested FiringPatternDef entries, each just as editable as the
 *  top-level one - see build()'s own doc). Every FiringPatternDef field this codebase actually
 *  reads (see that class and PatternFactory.createFiring()'s per-type dispatch) has a row
 *  somewhere in here - grouped by SHMUP Creator's own Weapon Editor layout (General/Pattern/
 *  Bullet/Sub-Patterns tabs) as a UX guide, NOT by which fields a given `type` actually consults:
 *  unlike PropertiesPanel's per-Trigger-kind field visibility, every row here always shows
 *  regardless of the current `type` - the flat FiringPatternDef file format already tolerates
 *  fields a type ignores (see that class's write(), which only omits DEFAULT-valued fields, not
 *  type-inapplicable ones), and PatternPreviewer (the existing in-game debug editor this mirrors
 *  field-for-field) shows type-specific rows dynamically only because it's rebuilt every single
 *  edit anyway - not worth replicating that complexity here for a form saved by hand. */
final class FiringPatternFieldsEditor {
    private FiringPatternFieldsEditor() {}

    static final List<String> TYPES = List.of("None", "SelfDestruct", "ExplodingAimed", "BurstAimed", "Sweep",
        "SineWave", "Orbiting", "Wall", "PolkaDot", "RadialNearMiss", "SpawnEnemy", "Aimed", "QuarterCircle",
        "AimedAtPoint", "Laser", "Sequence", "Combined");

    private static final String NONE_LABEL = "(none)";
    // Matches PatternPreviewer's own HITBOX_SHAPE_OPTIONS exactly - see HitboxSpec.Shape.
    private static final List<String> HITBOX_SHAPE_OPTIONS = List.of(NONE_LABEL, "Circle", "Rectangle");

    /** @param onDirty called after ANY field on `def` (or, transitively, one of its nested
     *  sub-patterns) commits an edit - the caller's own hook to mark the document/list/preview
     *  stale. Rebuilding this whole TabPane on every edit (the way PatternPreviewer's live
     *  in-game version does, to keep type-conditional rows in sync) isn't needed here since every
     *  row is always shown - onDirty only needs to repaint the static preview and flag unsaved
     *  changes, not rebuild this form out from under whatever field the user is mid-edit on. */
    static TabPane build(FiringPatternDef def, Runnable onDirty) {
        Tab general = tab("General", buildGeneral(def, onDirty));
        Tab pattern = tab("Pattern", buildPattern(def, onDirty));
        Tab bullet = tab("Bullet", buildBullet(def, onDirty));
        Tab subPatterns = tab("Sub-Patterns", buildSubPatterns(def, onDirty));
        TabPane tabs = new TabPane(general, pattern, bullet, subPatterns);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private static Tab tab(String name, javafx.scene.Node content) {
        return new Tab(name, content);
    }

    private static ScrollPane scrollOf(VBox box) {
        box.setPadding(new Insets(8));
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // --- General: identity, type, timing, the bullet this pattern fires -------------------------

    private static ScrollPane buildGeneral(FiringPatternDef def, Runnable onDirty) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel(def.id != null ? def.id : "(new pattern)"));
        box.getChildren().add(comboRow("Type", TYPES, def.type, v -> { def.type = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Fire rate (sec)", def.fireRate, v -> { def.fireRate = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Duration (sec)", def.duration, v -> { def.duration = v; onDirty.run(); }));
        box.getChildren().add(new Label("Duration is a Sequence step's own hold time, or Laser's beam duration."));
        box.getChildren().add(new Separator());
        box.getChildren().add(FormControls.textRow("Spawn type (SpawnEnemy)", def.spawnType, v -> { def.spawnType = v.isBlank() ? null : v; onDirty.run(); }));
        box.getChildren().add(comboRow("Bullet id", withBlank(PatternIds.bulletIds()), def.bulletId != null ? def.bulletId : "",
            v -> { def.bulletId = v.isEmpty() ? null : v; onDirty.run(); }));
        box.getChildren().add(numberRow("Phase offset (BurstAimed)", def.phaseOffset, v -> { def.phaseOffset = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Burst interval (BurstAimed)", def.burstInterval, v -> { def.burstInterval = v; onDirty.run(); }));
        return scrollOf(box);
    }

    // --- Pattern: shot geometry - spread/aim/sweep/orbit/wall/near-miss, all in one flat list ----

    private static ScrollPane buildPattern(FiringPatternDef def, Runnable onDirty) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel("Spread"));
        box.getChildren().add(numberRow("Spread angle (deg)", def.spreadDegrees, v -> { def.spreadDegrees = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Number of bullets", def.numBullets, v -> { def.numBullets = v.intValue(); onDirty.run(); }));
        box.getChildren().add(numberRow("Emitter offset X", def.offsetX, v -> { def.offsetX = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Emitter offset Y", def.offsetY, v -> { def.offsetY = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Fixed fire angle (deg, Laser)", def.fireAngle, v -> { def.fireAngle = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Aim (Aimed/QuarterCircle/AimedAtPoint)"));
        box.getChildren().add(numberRow("Quarter-circle fixed angle (deg)", def.quarterCircleFixedAngle, v -> { def.quarterCircleFixedAngle = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Target X (AimedAtPoint)", def.targetX, v -> { def.targetX = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Target Y (AimedAtPoint)", def.targetY, v -> { def.targetY = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Target offset X", def.targetOffsetX, v -> { def.targetOffsetX = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Target offset Y", def.targetOffsetY, v -> { def.targetOffsetY = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Sweep"));
        box.getChildren().add(numberRow("Sweep duration (sec)", def.sweepDuration, v -> { def.sweepDuration = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Sweep start angle (deg)", def.sweepStartAngle, v -> { def.sweepStartAngle = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Sweep end angle (deg)", def.sweepEndAngle, v -> { def.sweepEndAngle = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Sine Wave"));
        box.getChildren().add(numberRow("Amplitude", def.amplitude, v -> { def.amplitude = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Frequency", def.frequency, v -> { def.frequency = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Orbiting"));
        box.getChildren().add(numberRow("Orbit radius", def.orbitRadius, v -> { def.orbitRadius = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Orbit speed (rad/sec)", def.orbitSpeed, v -> { def.orbitSpeed = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Laser"));
        box.getChildren().add(numberRow("Beam length", def.length, v -> { def.length = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Angular speed (deg/sec)", def.angularSpeed, v -> { def.angularSpeed = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Wall / Polka Dot"));
        box.getChildren().add(numberRow("Wall margin X", def.wallMarginX, v -> { def.wallMarginX = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Wall spacing", def.wallSpacing, v -> { def.wallSpacing = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Gap lane start", def.gapLaneStart, v -> { def.gapLaneStart = v.intValue(); onDirty.run(); }));
        box.getChildren().add(numberRow("Gap lane count", def.gapLaneCount, v -> { def.gapLaneCount = v.intValue(); onDirty.run(); }));
        box.getChildren().add(FormControls.textRow("Gap lane sequence (comma-separated)", formatIntArray(def.gapLaneSequence),
            v -> { def.gapLaneSequence = parseIntArray(v); onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Radial Near-Miss"));
        box.getChildren().add(numberRow("Near-miss distance", def.nearMissDistance, v -> { def.nearMissDistance = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Volley count", def.volleyCount, v -> { def.volleyCount = v.intValue(); onDirty.run(); }));
        return scrollOf(box);
    }

    // --- Bullet: the projectile itself - size/speed/hitbox/damage/animation ----------------------

    private static ScrollPane buildBullet(FiringPatternDef def, Runnable onDirty) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel("Motion"));
        box.getChildren().add(numberRow("Bullet size", def.bulletSize, v -> { def.bulletSize = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Bullet speed", def.bulletSpeed, v -> { def.bulletSpeed = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Acceleration", def.bulletAcceleration, v -> { def.bulletAcceleration = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Min speed", def.bulletMinSpeed, v -> { def.bulletMinSpeed = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Max speed", def.bulletMaxSpeed, v -> { def.bulletMaxSpeed = v; onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Speed phases"));
        box.getChildren().add(buildSpeedPhases(def, onDirty));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Hitbox"));
        box.getChildren().add(comboRow("Shape", HITBOX_SHAPE_OPTIONS, def.hitboxShape != null ? def.hitboxShape : NONE_LABEL,
            v -> { def.hitboxShape = NONE_LABEL.equals(v) ? null : v; onDirty.run(); }));
        box.getChildren().add(numberRow("Hitbox scale", def.hitboxScale, v -> { def.hitboxScale = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Hitbox offset X", def.hitboxOffsetX, v -> { def.hitboxOffsetX = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Hitbox offset Y", def.hitboxOffsetY, v -> { def.hitboxOffsetY = v; onDirty.run(); }));
        box.getChildren().add(numberRow("Damage", def.bulletDamage, v -> { def.bulletDamage = v.intValue(); onDirty.run(); }));

        box.getChildren().add(new Separator());
        box.getChildren().add(sectionLabel("Animation (overrides the bullet/enemy default)"));
        box.getChildren().add(FormControls.textRow("Texture (path under assets/)", def.bulletTexture, v -> { def.bulletTexture = v.isBlank() ? null : v; onDirty.run(); }));
        box.getChildren().add(numberRow("Frame count", def.bulletFrameCount, v -> { def.bulletFrameCount = v.intValue(); onDirty.run(); }));
        box.getChildren().add(numberRow("Columns", def.bulletColumns, v -> { def.bulletColumns = v.intValue(); onDirty.run(); }));
        box.getChildren().add(numberRow("Rows", def.bulletRows, v -> { def.bulletRows = v.intValue(); onDirty.run(); }));
        box.getChildren().add(numberRow("Frame duration", def.bulletFrameDuration, v -> { def.bulletFrameDuration = v; onDirty.run(); }));
        return scrollOf(box);
    }

    /** One row per BulletSpeedPhase (acceleration + duration) plus Add/Remove - see that class's
     *  own doc for what the sequence means. Rebuilds `holder` in place on every add/remove so the
     *  row list always matches def.bulletSpeedPhases exactly, the same "clear and re-add" idiom
     *  every other panel in this editor already uses on a structural (not per-field) change. */
    private static VBox buildSpeedPhases(FiringPatternDef def, Runnable onDirty) {
        VBox holder = new VBox(4);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            holder.getChildren().clear();
            if (def.bulletSpeedPhases != null) {
                for (int i = 0; i < def.bulletSpeedPhases.size; i++) {
                    BulletSpeedPhase phase = def.bulletSpeedPhases.get(i);
                    int index = i;
                    HBox row = new HBox(6,
                        numberRow("Accel " + index, phase.acceleration, v -> { phase.acceleration = v; onDirty.run(); }),
                        numberRow("Duration " + index, phase.duration, v -> { phase.duration = v; onDirty.run(); }));
                    Button remove = new Button("Remove");
                    remove.setOnAction(e -> {
                        def.bulletSpeedPhases.removeIndex(index);
                        refresh[0].run();
                        onDirty.run();
                    });
                    row.getChildren().add(remove);
                    holder.getChildren().add(row);
                }
            }
            Button add = new Button("Add Phase");
            add.setOnAction(e -> {
                if (def.bulletSpeedPhases == null) def.bulletSpeedPhases = new Array<>();
                def.bulletSpeedPhases.add(new BulletSpeedPhase());
                refresh[0].run();
                onDirty.run();
            });
            CheckBox loop = checkBox("Loop", def.bulletSpeedPhasesLoop, v -> { def.bulletSpeedPhasesLoop = v; onDirty.run(); });
            holder.getChildren().add(new HBox(10, add, loop));
        };
        refresh[0].run();
        return holder;
    }

    // --- Sub-Patterns: Sequence/Combined's own nested FiringPatternDef list -----------------------

    /** A Sequence/Combined pattern's own children - each one just as editable as the top-level
     *  pattern, via a recursive build() call the moment it's selected in the list (see selection
     *  listener below). Meaningless for any other `type` (nothing reads def.patterns then), but
     *  shown unconditionally like every other tab here - see build()'s own doc. */
    private static VBox buildSubPatterns(FiringPatternDef def, Runnable onDirty) {
        VBox root = new VBox(6);
        root.setPadding(new Insets(8));
        root.getChildren().add(new Label("Only read when Type is Sequence or Combined."));

        ListView<FiringPatternDef> list = new ListView<>();
        list.setPrefHeight(140);
        // Fixed, not just preferred: without this, selecting a sub-pattern below drops a
        // potentially-tall nested TabPane into subEditorHolder (see its own vgrow-ALWAYS below),
        // and a plain VBox squeezes a non-growing sibling with only a "preferred" height down
        // toward its own tiny default minimum once the total no longer fits - which is exactly
        // what made the list of OTHER sub-patterns disappear the moment you opened one to edit it.
        list.setMinHeight(140);
        if (def.patterns != null) {
            for (FiringPatternDef sub : def.patterns) list.getItems().add(sub);
        }

        // Writes list.getItems()' current order back onto def.patterns (the Array that's actually
        // serialized - see FiringPatternDef.write()) so a Sequence/Combined's own child ORDER (which
        // is semantically load-bearing - PatternFactory.createFiring()'s "Sequence" case walks
        // def.patterns in file order) survives a drag-reorder, not just the ListView's own display.
        Runnable syncOrderToDef = () -> {
            if (def.patterns == null) def.patterns = new Array<>();
            def.patterns.clear();
            for (FiringPatternDef sub : list.getItems()) def.patterns.add(sub);
        };

        list.setCellFactory(lv -> buildDraggableCell(list, syncOrderToDef, onDirty));

        // A drop that lands on the ListView's own empty space below the last real cell (a short
        // list with room left in its fixed 140px height, or dragging past the last row) never
        // reaches any cell's own drag handlers - see buildDraggableCell()'s own doc on why cells
        // alone aren't enough. Treated as "move to the very end" - the one place a drop there could
        // sensibly mean.
        list.setOnDragOver(event -> {
            if (event.getGestureSource() != list && event.getDragboard().hasString()) event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        list.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                int draggedIndex = Integer.parseInt(db.getString());
                if (draggedIndex >= 0 && draggedIndex < list.getItems().size() - 1) {
                    FiringPatternDef dragged = list.getItems().remove(draggedIndex);
                    list.getItems().add(dragged);
                    syncOrderToDef.run();
                    list.getSelectionModel().select(dragged);
                    onDirty.run();
                    list.refresh();
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // Scrolls internally rather than growing - a nested sub-pattern's own tabs (General/
        // Pattern/Bullet, each already a ScrollPane) can still want more height than this dialog
        // has room for; without this wrapper that demand pushed straight up through subEditorHolder
        // onto the whole Sub-Patterns tab, which is what shrank `list` above in the first place.
        VBox subEditorHolder = new VBox();
        ScrollPane subEditorScroll = new ScrollPane(subEditorHolder);
        subEditorScroll.setFitToWidth(true);
        VBox.setVgrow(subEditorScroll, javafx.scene.layout.Priority.ALWAYS);
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, sub) -> {
            subEditorHolder.getChildren().clear();
            if (sub != null) {
                TabPane nested = build(sub, () -> { onDirty.run(); list.refresh(); });
                subEditorHolder.getChildren().add(nested);
            }
        });

        Button add = new Button("Add Sub-Pattern");
        add.setOnAction(e -> {
            if (def.patterns == null) def.patterns = new Array<>();
            FiringPatternDef sub = new FiringPatternDef();
            sub.type = "Aimed";
            def.patterns.add(sub);
            list.getItems().add(sub);
            list.getSelectionModel().select(sub);
            onDirty.run();
        });
        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> {
            FiringPatternDef sub = list.getSelectionModel().getSelectedItem();
            if (sub == null || def.patterns == null) return;
            def.patterns.removeValue(sub, true);
            list.getItems().remove(sub);
            subEditorHolder.getChildren().clear();
            onDirty.run();
        });

        root.getChildren().addAll(new HBox(8, add, remove), list, new Separator(), subEditorScroll);
        VBox.setVgrow(root, javafx.scene.layout.Priority.ALWAYS);
        return root;
    }

    /** One drag-reorderable row of the "Sub-Patterns" list - keeps buildSubPatterns()'s own
     *  updateItem/index-prefix display exactly as before, plus the actual drag machinery: press-drag
     *  a row and drop it on another to move it there (before the drop target if dragging upward,
     *  after it if dragging downward - see the index-shift math below), reordering `list`'s own
     *  items AND (via `syncOrderToDef`) def.patterns together so the persisted file order actually
     *  changes, not just the display. Only a NON-empty cell can start a drag or accept a drop -
     *  dropping past the last real row lands in the ListView's own empty space below every cell
     *  instead, which is why buildSubPatterns() ALSO wires a drop handler on `list` itself (append
     *  to the end) rather than relying on cells alone to cover that case. */
    private static ListCell<FiringPatternDef> buildDraggableCell(ListView<FiringPatternDef> list, Runnable syncOrderToDef, Runnable onDirty) {
        ListCell<FiringPatternDef> cell = new ListCell<>() {
            @Override
            protected void updateItem(FiringPatternDef item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int i = getListView().getItems().indexOf(item);
                    setText(i + ": " + item.type + (item.bulletId != null ? " (" + item.bulletId + ")" : ""));
                }
            }
        };

        cell.setOnDragDetected(event -> {
            if (cell.isEmpty()) return;
            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(cell.getIndex()));
            db.setContent(content);
            event.consume();
        });

        cell.setOnDragOver((DragEvent event) -> {
            if (!cell.isEmpty() && event.getGestureSource() != cell && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        cell.setOnDragEntered(event -> {
            if (!cell.isEmpty() && event.getGestureSource() != cell && event.getDragboard().hasString()) {
                cell.setStyle("-fx-border-color: #6fd6ff; -fx-border-width: 0 0 2 0;");
            }
        });
        cell.setOnDragExited(event -> cell.setStyle(""));

        cell.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (!cell.isEmpty() && db.hasString()) {
                int draggedIndex = Integer.parseInt(db.getString());
                int dropIndex = cell.getIndex();
                if (draggedIndex != dropIndex) {
                    FiringPatternDef dragged = list.getItems().remove(draggedIndex);
                    // Removing the dragged row shifts every LATER index down by one - if the drop
                    // target was after it, its own index needs the same correction before inserting.
                    int insertIndex = dropIndex > draggedIndex ? dropIndex - 1 : dropIndex;
                    list.getItems().add(insertIndex, dragged);
                    syncOrderToDef.run();
                    list.getSelectionModel().select(insertIndex);
                    onDirty.run();
                    list.refresh();
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        cell.setOnDragDone(event -> cell.setStyle(""));

        return cell;
    }

    private static String formatIntArray(int[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static int[] parseIntArray(String text) {
        text = text.trim();
        if (text.isEmpty()) return null;
        String[] parts = text.split(",");
        int[] result = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                result[count++] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                // leave malformed entries out rather than crashing on a stray comma/typo
            }
        }
        if (count == 0) return null;
        int[] trimmed = new int[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }
}
