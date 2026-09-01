package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.gamemanagers.trigger.Condition;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.numberRow;
import static whitelabeltest.editor.FormControls.numberRowNullable;
import static whitelabeltest.editor.FormControls.sectionLabel;
import static whitelabeltest.editor.FormControls.textRow;
import static whitelabeltest.editor.FormControls.withBlank;

/** Right-hand editing form for whichever Trigger is currently selected on the StageCanvas.
 *
 * A trigger is edited in two independent parts, deliberately in this order: **Conditions** first -
 * the same gate vocabulary the tutorial stage's SpawnScheduler.GateCue already uses (shoot/bomb/
 * moved/enemiesDestroyed/etc. - see CONDITION_TYPES), which decide WHEN this trigger fires once the
 * camera reaches its distance - then the **Action** it's linked to - what actually happens once
 * those conditions are met: an enemy spawn, sound cue, sprite cue, camera-speed change, or one of
 * the scripted enemy-list actions (despawn/silence/waypoint gem/weapon swap). The Action combo lets
 * you change (or start with none - see ActionPalette's "Trigger Event" tile) which of those a
 * trigger is linked to at any time, clearing whichever fields the previous action used - so a
 * trigger's gating logic and its effect are edited/authored as separable concerns, matching how
 * TriggerManager.fire() itself already treats them (arm on distance+conditions, dispatch on action
 * fields - see that class).
 *
 * Every field commits straight back onto the live Trigger object, then tells the canvas to
 * reposition/relabel that one node and mark the document dirty - see StageCanvas.refreshTrigger().
 * See FormControls for the shared field-row builders, and EnemyDefinitionPanel for the sibling panel
 * that edits an enemy's own template stats instead of a placed instance - EditorApp swaps whichever
 * of the two is relevant into the same dock slot, depending on whether you clicked a canvas trigger
 * or a palette entry. */
public class PropertiesPanel extends ScrollPane {
    private static final String[] CONDITION_TYPES = {
        "shoot", "bomb", "weaponSwitch", "moved", "movedLeft", "movedRight",
        "hyperAttack", "hyperAttackReleased", "enemiesDestroyed", "enemyTypeDestroyed",
        "gemsCollected", "grazed"
    };

    // Ordered key -> display label for the Action combo - see applyActionKind()/actionKindKey().
    // LinkedHashMap so the combo's option order matches declaration order here.
    private static final Map<String, String> ACTION_KINDS = new LinkedHashMap<>();
    static {
        ACTION_KINDS.put("none", "(unlinked)");
        ACTION_KINDS.put("enemy", "Enemy Spawn");
        ACTION_KINDS.put("sound", "Sound Cue");
        ACTION_KINDS.put("sprite", "Sprite Cue");
        ACTION_KINDS.put("speed", "Set Camera Speed");
        ACTION_KINDS.put("text", "Text Cue");
        ACTION_KINDS.put("bossVideo", "Trigger Boss Video");
        ACTION_KINDS.put("fadeMusic", "Fade Out Music");
        ACTION_KINDS.put("despawn", "Despawn Enemies");
        ACTION_KINDS.put("silence", "Silence Enemies");
        ACTION_KINDS.put("waypointGem", "Waypoint Gem");
        ACTION_KINDS.put("swapWeapon", "Swap Weapon");
    }

    private final StageLibrary library;
    private final StageCanvas canvas;
    private final VBox root = new VBox(8);
    // Persistent (not rebuilt from scratch by showTrigger()'s root.getChildren().clear()) so that
    // canvas.setPathPointSelectionListener()/setPathEditChangeListener() can refresh just this
    // section - see refreshMovementPathBox() - when a waypoint is selected/edited directly on the
    // canvas, without blowing away the rest of this panel's scroll position/fields.
    private final VBox movementPathBox = new VBox(6);
    private Trigger trigger;

    public PropertiesPanel(StageLibrary library, StageCanvas canvas) {
        this.library = library;
        this.canvas = canvas;
        root.setPadding(new Insets(8));
        setContent(root);
        setFitToWidth(true);
        setPrefWidth(260);
        canvas.setPathPointSelectionListener(waypoint -> refreshMovementPathBox());
        canvas.setPathEditChangeListener(() -> refreshMovementPathBox());
        showTrigger(null);
    }

    public void showTrigger(Trigger trigger) {
        this.trigger = trigger;
        root.getChildren().clear();
        if (trigger == null) {
            root.getChildren().add(sectionLabel("No trigger selected - drag one from the palette, or click a placed "
                + "trigger (Ctrl/Cmd-click to select several at once)."));
            return;
        }

        root.getChildren().add(sectionLabel("Trigger Event"));
        root.getChildren().add(numberRow("Distance", trigger.distance, v -> { trigger.distance = v; onEdited(); }));

        root.getChildren().add(new Separator());
        buildConditionsSection();

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Action"));
        String currentKey = actionKindKey(trigger);
        root.getChildren().add(comboRow("Linked to", new ArrayList<>(ACTION_KINDS.values()), ACTION_KINDS.get(currentKey),
            label -> {
                applyActionKind(trigger, keyForLabel(label));
                onEdited();
                showTrigger(trigger);
            }));
        buildActionFields(currentKey);

        root.getChildren().add(new Separator());
        Button delete = new Button("Delete Trigger");
        delete.setOnAction(e -> {
            canvas.deleteTrigger(trigger);
            showTrigger(null);
        });
        root.getChildren().add(delete);
    }

    /** Shown instead of showTrigger()'s usual per-field form whenever 2+ triggers are selected at
     *  once on the canvas (Ctrl/Cmd-click - see StageCanvas.select()'s own doc) - editing several
     *  triggers' individual fields at once isn't supported, just bulk deletion, which is the actual
     *  point of multi-select here. */
    public void showMultiSelection(List<Trigger> triggers) {
        this.trigger = null;
        root.getChildren().clear();
        root.getChildren().add(sectionLabel(triggers.size() + " triggers selected"));
        root.getChildren().add(sectionLabel("Ctrl/Cmd-click a trigger to add or remove it from the selection, "
            + "or click empty canvas space to clear it."));
        Button delete = new Button("Delete " + triggers.size() + " Triggers");
        delete.setOnAction(e -> canvas.deleteSelectedTriggers());
        root.getChildren().add(delete);
    }

    private void onEdited() {
        canvas.refreshTrigger(trigger);
    }

    /** Which action-kind key a trigger's current field state represents - see ACTION_KINDS. Mirrors
     *  TriggerManager.fire()'s own dispatch order. */
    private static String actionKindKey(Trigger trigger) {
        if (trigger.sound != null) return "sound";
        if (trigger.spriteTexture != null) return "sprite";
        if (trigger.setSpeed != null) return "speed";
        if (trigger.text != null) return "text";
        if (trigger.triggerBossVideo) return "bossVideo";
        if (trigger.fadeOutMusic) return "fadeMusic";
        if (trigger.silence) return "silence";
        if (trigger.despawn) return "despawn";
        if (trigger.waypointGem) return "waypointGem";
        if (trigger.swapWeaponId != null) return "swapWeapon";
        if (trigger.type != null) return "enemy";
        return "none";
    }

    private static String keyForLabel(String label) {
        for (Map.Entry<String, String> entry : ACTION_KINDS.entrySet()) {
            if (entry.getValue().equals(label)) return entry.getKey();
        }
        return "none";
    }

    /** Resets every action-defining field to "unset" then applies sensible defaults for `key` - see
     *  the Action combo in showTrigger(). Always starts from a clean slate so switching, say, Sound
     *  Cue -> Enemy Spawn can't leave a stale `sound` value the game would never read but that would
     *  otherwise still win TriggerManager.fire()'s dispatch (sound is checked first). */
    private void applyActionKind(Trigger trigger, String key) {
        trigger.type = null;
        trigger.sound = null;
        trigger.spriteTexture = null;
        trigger.setSpeed = null;
        trigger.text = null;
        trigger.triggerBossVideo = false;
        trigger.fadeOutMusic = false;
        trigger.silence = false;
        trigger.despawn = false;
        trigger.waypointGem = false;
        trigger.swapWeaponId = null;

        switch (key) {
            case "enemy" -> {
                trigger.type = library.getEnemies().size > 0 ? library.getEnemies().first().id : null;
                if (Float.isNaN(trigger.x)) trigger.x = StageCanvas.DEFAULT_SPAWN_X;
                if (Float.isNaN(trigger.y)) trigger.y = StageCanvas.DEFAULT_SPAWN_Y;
            }
            case "sound" -> trigger.sound = "audio/sfx/CHANGE_ME.mp3";
            case "sprite" -> {
                trigger.spriteTexture = "images/ui/CHANGE_ME.png";
                if (Float.isNaN(trigger.x)) trigger.x = StageCanvas.DEFAULT_SPAWN_X;
                if (Float.isNaN(trigger.y)) trigger.y = StageCanvas.DEFAULT_SPAWN_Y;
            }
            case "speed" -> trigger.setSpeed = 1f;
            case "text" -> {
                trigger.text = "New text cue";
                trigger.textEffect = "static";
                trigger.textDuration = 3.5f;
            }
            case "bossVideo" -> trigger.triggerBossVideo = true;
            case "fadeMusic" -> trigger.fadeOutMusic = true;
            case "despawn" -> { trigger.despawn = true; trigger.type = firstEnemyIdOrNull(); }
            case "silence" -> { trigger.silence = true; trigger.type = firstEnemyIdOrNull(); }
            case "waypointGem" -> trigger.waypointGem = true;
            case "swapWeapon" -> trigger.swapWeaponId = "BasicWeapon";
            default -> { } // "none" - stays unlinked
        }
    }

    private String firstEnemyIdOrNull() {
        return library.getEnemies().size > 0 ? library.getEnemies().first().id : null;
    }

    private void buildActionFields(String key) {
        switch (key) {
            case "enemy" -> buildEnemySpawnFields();
            case "sound" -> buildSoundFields();
            case "sprite" -> buildSpriteFields();
            case "speed" -> root.getChildren().add(numberRow("New camera speed", trigger.setSpeed, v -> { trigger.setSpeed = v; onEdited(); }));
            case "text" -> buildTextCueFields();
            case "bossVideo" -> root.getChildren().add(sectionLabel(
                "Fires the stage's boss-intro video (see ScrollingBackground.triggerBossVideo()) once."));
            case "fadeMusic" -> root.getChildren().add(sectionLabel(
                "Fades out the stage's music (see AudioManager.fadeOutStageMusic()) once."));
            case "despawn", "silence" -> root.getChildren().add(
                comboRow("Enemy type", enemyIdOptions(), trigger.type, v -> { trigger.type = v; onEdited(); }));
            case "waypointGem" -> buildWaypointGemFields();
            case "swapWeapon" -> {
                root.getChildren().add(textRow("Weapon id", trigger.swapWeaponId, v -> { trigger.swapWeaponId = v; onEdited(); }));
                root.getChildren().add(numberRow("Weapon slot", trigger.weaponSlot, v -> { trigger.weaponSlot = v.intValue(); onEdited(); }));
            }
            default -> root.getChildren().add(sectionLabel("Pick an action above to link this event to an enemy spawn, sound, sprite, etc."));
        }
    }

    private void buildSoundFields() {
        root.getChildren().add(textRow("Sound path", trigger.sound, v -> { trigger.sound = v; onEdited(); }));
    }

    private void buildSpriteFields() {
        root.getChildren().add(textRow("Texture path", trigger.spriteTexture, v -> { trigger.spriteTexture = v; onEdited(); }));
        root.getChildren().add(numberRow("X", trigger.x, v -> { trigger.x = v; onEdited(); }));
        root.getChildren().add(numberRow("Y", trigger.y, v -> { trigger.y = v; onEdited(); }));
        root.getChildren().add(numberRow("Size", trigger.size, v -> { trigger.size = v; onEdited(); }));
        root.getChildren().add(numberRow("Columns", trigger.columns, v -> { trigger.columns = v.intValue(); onEdited(); }));
        root.getChildren().add(numberRow("Rows", trigger.rows, v -> { trigger.rows = v.intValue(); onEdited(); }));
        root.getChildren().add(numberRow("Frame count", trigger.frameCount, v -> { trigger.frameCount = v.intValue(); onEdited(); }));
        root.getChildren().add(numberRow("Frame duration", trigger.frameDuration, v -> { trigger.frameDuration = v; onEdited(); }));
    }

    /** See Trigger.text's own doc - these fields mirror whitelabeltest.gamemanagers.TextCue's
     *  authored (non-runtime) fields exactly; TriggerManager.fireTextCue() builds a live TextCue
     *  from them the moment this trigger fires. */
    private void buildTextCueFields() {
        root.getChildren().add(sectionLabel("Text"));
        root.getChildren().add(buildTextArea());
        root.getChildren().add(comboRow("Effect", List.of("static", "typewriter", "blinking"), trigger.textEffect,
            v -> { trigger.textEffect = v; onEdited(); }));
        root.getChildren().add(numberRow("Duration (seconds on screen)", trigger.textDuration, v -> { trigger.textDuration = v; onEdited(); }));
        root.getChildren().add(numberRow("X", trigger.textX, v -> { trigger.textX = v; onEdited(); }));
        root.getChildren().add(numberRow("Y", trigger.textY, v -> { trigger.textY = v; onEdited(); }));
        root.getChildren().add(FormControls.checkBox("Centered on (X, Y)", trigger.textCentered, v -> { trigger.textCentered = v; onEdited(); }));
        root.getChildren().add(numberRow("Font size (multiplier)", trigger.textFontSize, v -> { trigger.textFontSize = v; onEdited(); }));
        root.getChildren().add(numberRow("Chars/sec (typewriter only)", trigger.textCharsPerSecond, v -> { trigger.textCharsPerSecond = v; onEdited(); }));
        root.getChildren().add(numberRow("Blinks/sec (blinking only)", trigger.textBlinksPerSecond, v -> { trigger.textBlinksPerSecond = v; onEdited(); }));
    }

    /** A real multi-line TextArea rather than FormControls.textRow's single-line TextField - a
     *  plain TextField can't produce an actual newline character (typing the two characters "\"
     *  and "n" would just store that literal substring, not a line break), and Trigger.text needs
     *  real newlines the same way the migrated stage1_schedule.json text cues always did (JSON's own
     *  "\n" escape decodes to a real newline on load - see stage1_triggers.json's own text triggers
     *  for the multi-line ones). Commits on focus-lost, same as every other field here. */
    private TextArea buildTextArea() {
        TextArea area = new TextArea(trigger.text != null ? trigger.text : "");
        area.setPrefRowCount(3);
        area.setWrapText(true);
        area.focusedProperty().addListener((obs, was, is) -> {
            if (!is) { trigger.text = area.getText(); onEdited(); }
        });
        return area;
    }

    private void buildWaypointGemFields() {
        root.getChildren().add(numberRow("X", trigger.x, v -> { trigger.x = v; onEdited(); }));
        root.getChildren().add(numberRow("Y", trigger.y, v -> { trigger.y = v; onEdited(); }));
    }

    private void buildEnemySpawnFields() {
        // Changing type here only relabels the node (TriggerNode.refresh()) - its icon is built once
        // at node-creation time, so it stays stale until the next full canvas rebuild (reload/save
        // round trip). Acceptable for this pass: retyping an already-placed enemy is rare.
        root.getChildren().add(comboRow("Enemy type", enemyIdOptions(), trigger.type, v -> { trigger.type = v; onEdited(); }));
        root.getChildren().add(numberRow("Spawn X", trigger.x, v -> { trigger.x = v; onEdited(); }));
        // "Spawn Y" doubles as the ARRIVAL point once "Enters from above" below is checked - see
        // that checkbox's own doc - rather than the actual spawn position in that case.
        root.getChildren().add(numberRow("Spawn Y (entrance position, not stage progress)", trigger.y, v -> { trigger.y = v; onEdited(); }));
        // See Trigger.spawnLead's own doc - this trigger still shows/drags at its own `distance`
        // above (where it should matter/engage) - a nonzero lead just makes it actually spawn that
        // many distance-units earlier, so it's already present (or already arrived, if it has a
        // movement path) by the time the camera reaches this trigger's own distance.
        root.getChildren().add(numberRow("Spawn lead (distance-units early)", trigger.spawnLead, v -> { trigger.spawnLead = v; onEdited(); }));
        // See Trigger.enterFromAbove/EnemyEntranceMovement's own docs - together with a nonzero
        // Spawn lead above, this is what actually stops the enemy from popping into an
        // already-visible spot: it spawns off-screen and travels down to Spawn Y on its own, timed to
        // land there exactly when the camera reaches this trigger's own distance. A no-op with
        // Spawn lead at 0 (nowhere for the travel time to come from).
        root.getChildren().add(FormControls.checkBox("Enters from above (travels down to Spawn Y)", trigger.enterFromAbove, v -> { trigger.enterFromAbove = v; onEdited(); }));
        root.getChildren().add(numberRowNullable("Offset X (formation slot)", trigger.offsetX, v -> { trigger.offsetX = v; onEdited(); }));
        root.getChildren().add(numberRowNullable("Offset Y (formation slot)", trigger.offsetY, v -> { trigger.offsetY = v; onEdited(); }));
        // Not an "override" - see EnemyDefinition.java's own doc: movement isn't part of the enemy
        // type at all, so this trigger's own movementPattern is the sole source, usually authored
        // via the "Movement Path" section below rather than picked from this combo directly.
        root.getChildren().add(comboRow("Movement pattern", withBlank(PatternIds.movementPatternIds()), trigger.movementPattern,
            v -> { trigger.movementPattern = v.isEmpty() ? null : v; onEdited(); }));
        root.getChildren().add(comboRow("Firing pattern override", withBlank(PatternIds.firingPatternIds()), trigger.firingPattern,
            v -> { trigger.firingPattern = v.isEmpty() ? null : v; onEdited(); }));
        root.getChildren().add(comboRow("Guaranteed powerup", List.of("", "1", "2", "3"),
            trigger.powerup == null ? "" : String.valueOf(trigger.powerup),
            v -> { trigger.powerup = v.isEmpty() ? null : Integer.valueOf(v); onEdited(); }));
        root.getChildren().add(FormControls.checkBox("Inverse movement", trigger.inverseMovement, v -> { trigger.inverseMovement = v; onEdited(); }));

        root.getChildren().add(new Separator());
        root.getChildren().add(movementPathBox);
        refreshMovementPathBox();
    }

    /** Rebuilds the "Movement Path" section for the currently-shown enemy-spawn trigger - resolves
     *  which pattern it actually uses (purely trigger.movementPattern - see
     *  MovementPatternLibrary.resolveForTrigger()'s own doc on why there's no enemy-level fallback
     *  anymore) and shows whichever of three states applies: no pattern resolved yet (offer to
     *  create one), a pattern that isn't a clean waypoint list (offer to convert it), or an editable
     *  one (the "Edit Path on Stage"
     *  toggle plus, once active, the selected point's own fields and delete/save controls). Called
     *  standalone (not through the full showTrigger() rebuild) from StageCanvas's own path-point-
     *  selection/change listeners, so clicking/dragging a point on the canvas updates just this
     *  section instead of scrolling the whole panel back to the top. */
    private void refreshMovementPathBox() {
        movementPathBox.getChildren().clear();
        if (trigger == null || !"enemy".equals(actionKindKey(trigger))) return;

        movementPathBox.getChildren().add(sectionLabel("Movement Path"));
        MovementPatternDef pattern = canvas.getPatternLibrary().resolveForTrigger(trigger);

        if (pattern == null) {
            movementPathBox.getChildren().add(sectionLabel("No movement pattern resolved for this spawn."));
            HBox row = new HBox(6);
            TextField idField = new TextField();
            idField.setPromptText("NewPatternId");
            Button create = new Button("Create Path");
            create.setOnAction(e -> {
                String id = idField.getText().trim();
                if (id.isEmpty() || canvas.getPatternLibrary().exists(id)) return;
                canvas.getPatternLibrary().save(canvas.getPatternLibrary().createNew(id));
                trigger.movementPattern = id;
                onEdited();
                canvas.setPathEditTrigger(trigger);
                refreshMovementPathBox();
            });
            row.getChildren().addAll(idField, create);
            movementPathBox.getChildren().add(row);
            return;
        }

        movementPathBox.getChildren().add(sectionLabel("Pattern: " + pattern.id + "  (type=" + pattern.type + ")"));

        if (!MovementPatternLibrary.isWaypointSequence(pattern)) {
            movementPathBox.getChildren().add(sectionLabel(
                "This pattern isn't a simple waypoint list, so it can't be edited as points here."));
            Button convert = new Button("Convert to Waypoints...");
            convert.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Replace \"" + pattern.id + "\" (type=" + pattern.type + ") with a fresh, empty waypoint "
                        + "path? Any other spawn using this same pattern id will see the change too, and "
                        + "the file is only overwritten on disk once you click \"Save Path\" afterward.",
                    ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.YES) return;
                canvas.getPatternLibrary().save(canvas.getPatternLibrary().createNew(pattern.id));
                canvas.setPathEditTrigger(trigger);
                refreshMovementPathBox();
            });
            movementPathBox.getChildren().add(convert);
            return;
        }

        boolean editingThis = canvas.isPathEditActive(trigger);
        Button toggle = new Button(editingThis ? "Stop Editing Path" : "Edit Path on Stage");
        toggle.setOnAction(e -> {
            canvas.setPathEditTrigger(editingThis ? null : trigger);
            refreshMovementPathBox();
        });
        movementPathBox.getChildren().add(toggle);
        if (!editingThis) return;

        movementPathBox.getChildren().add(sectionLabel(
            "Click the stage to add a point. Drag a point to move it. Click a point to select it."));

        movementPathBox.getChildren().add(new Separator());
        movementPathBox.getChildren().add(sectionLabel("Path Options"));
        movementPathBox.getChildren().add(numberRow("Global speed", pattern.globalSpeed, v -> {
            pattern.globalSpeed = v; canvas.notifyPathEditChanged();
        }));
        movementPathBox.getChildren().add(FormControls.checkBox("Close path (loop)", pattern.closePath, v -> {
            pattern.closePath = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
        }));
        movementPathBox.getChildren().add(FormControls.checkBox("Flip X", pattern.flipX, v -> {
            pattern.flipX = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
        }));
        movementPathBox.getChildren().add(FormControls.checkBox("Flip Y", pattern.flipY, v -> {
            pattern.flipY = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
        }));

        MovementPatternDef selected = canvas.getSelectedPathPoint();
        if (selected != null) {
            movementPathBox.getChildren().add(new Separator());
            movementPathBox.getChildren().add(sectionLabel("Selected Waypoint"));
            movementPathBox.getChildren().add(numberRow("Point X", selected.targetX, v -> {
                selected.targetX = v; canvas.refreshPathEditPositions(); canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Point Y", selected.targetY, v -> {
                selected.targetY = v; canvas.refreshPathEditPositions(); canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Speed at waypoint", selected.speed, v -> {
                selected.speed = v; canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Path tension (0=curved, 1=straight corner)", selected.tension, v -> {
                selected.tension = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Wait at waypoint (seconds)", selected.waitSeconds, v -> {
                selected.waitSeconds = v; canvas.notifyPathEditChanged();
            }));

            movementPathBox.getChildren().add(sectionLabel("Orientation"));
            movementPathBox.getChildren().add(comboRow("Mode", ORIENTATION_MODES, selected.orientation, v -> {
                selected.orientation = v; canvas.notifyPathEditChanged(); refreshMovementPathBox();
            }));
            if ("player".equals(selected.orientation)) {
                movementPathBox.getChildren().add(numberRow("Aim speed (deg/sec)", selected.aimSpeed, v -> {
                    selected.aimSpeed = v; canvas.notifyPathEditChanged();
                }));
            } else if ("fixed".equals(selected.orientation)) {
                movementPathBox.getChildren().add(numberRow("Fixed angle (deg)", selected.fixedAngle, v -> {
                    selected.fixedAngle = v; canvas.notifyPathEditChanged();
                }));
            }

            movementPathBox.getChildren().add(sectionLabel("Sound"));
            movementPathBox.getChildren().add(textRow("Name (blank = none)", selected.soundName, v -> {
                selected.soundName = v.isBlank() ? null : v; canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Volume", selected.soundVolume, v -> {
                selected.soundVolume = v; canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Pitch", selected.soundPitch, v -> {
                selected.soundPitch = v; canvas.notifyPathEditChanged();
            }));
            movementPathBox.getChildren().add(numberRow("Pitch variation", selected.soundPitchVariation, v -> {
                selected.soundPitchVariation = v; canvas.notifyPathEditChanged();
            }));

            movementPathBox.getChildren().add(sectionLabel("Weapon Set"));
            movementPathBox.getChildren().add(FormControls.checkBox("Change weapon set here", selected.changeWeaponSet, v -> {
                selected.changeWeaponSet = v; canvas.notifyPathEditChanged(); refreshMovementPathBox();
            }));
            if (selected.changeWeaponSet) {
                movementPathBox.getChildren().add(comboRow("Set", withBlank(weaponSetNames()), selected.weaponSet, v -> {
                    selected.weaponSet = v.isEmpty() ? null : v; canvas.notifyPathEditChanged();
                }));
            }

            Button delete = new Button("Delete Selected Point");
            delete.setOnAction(e -> canvas.deleteSelectedPathPoint());
            movementPathBox.getChildren().add(delete);
        }

        Button save = new Button("Save Path");
        save.setOnAction(e -> {
            canvas.savePathEditPattern();
            movementPathBox.getChildren().add(sectionLabel("Saved " + pattern.id + ".json"));
        });
        movementPathBox.getChildren().add(save);
    }

    // "path"/"player"/"fixed" - see MovementPatternDef.orientation's own doc.
    private static final List<String> ORIENTATION_MODES = List.of("path", "player", "fixed");

    /** The spawning enemy's own EnemyDefinition.weaponSets keys - see PropertiesPanel's "Weapon
     *  Set" combo above and EnemyDefinitionPanel's "Weapon Sets" section, which is where these
     *  names are actually defined. Empty (not null) when the enemy has none defined yet, so
     *  withBlank() still produces a valid (if pointless) combo instead of throwing. */
    private List<String> weaponSetNames() {
        EnemyDefinition def = library.findEnemy(trigger.type);
        if (def == null || def.weaponSets == null) return new ArrayList<>();
        List<String> names = new ArrayList<>();
        def.weaponSets.keys().forEach(names::add);
        java.util.Collections.sort(names);
        return names;
    }

    private List<String> enemyIdOptions() {
        List<String> ids = new ArrayList<>();
        library.getEnemies().forEach(def -> ids.add(def.id));
        return ids;
    }

    private void buildConditionsSection() {
        root.getChildren().add(sectionLabel("Conditions (when this fires)"));
        if (trigger.conditions == null) trigger.conditions = new com.badlogic.gdx.utils.Array<>();

        root.getChildren().add(comboRow("Match", List.of("ALL", "ANY"),
            trigger.conditionMode == null ? "ALL" : trigger.conditionMode,
            v -> { trigger.conditionMode = v; onEdited(); }));

        for (Condition condition : trigger.conditions) {
            root.getChildren().add(buildConditionRow(condition));
        }

        Button add = new Button("+ Add Condition");
        add.setOnAction(e -> {
            Condition condition = new Condition();
            condition.type = CONDITION_TYPES[0];
            trigger.conditions.add(condition);
            showTrigger(trigger);
            onEdited();
        });
        root.getChildren().add(add);
    }

    private VBox buildConditionRow(Condition condition) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: #26272c; -fx-padding: 6; -fx-background-radius: 6;");

        HBox header = new HBox(6);
        ComboBox<String> typeCombo = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(CONDITION_TYPES));
        typeCombo.setValue(condition.type);
        typeCombo.setOnAction(e -> { condition.type = typeCombo.getValue(); onEdited(); showTrigger(trigger); });
        Button remove = new Button("✕");
        remove.setOnAction(e -> {
            trigger.conditions.removeValue(condition, true);
            onEdited();
            showTrigger(trigger);
        });
        header.getChildren().addAll(typeCombo, remove);
        box.getChildren().add(header);

        if ("enemyTypeDestroyed".equals(condition.type) || "weaponSwitch".equals(condition.type)) {
            String label = "enemyTypeDestroyed".equals(condition.type) ? "Enemy type" : "Weapon id";
            box.getChildren().add(textRow(label,
                "enemyTypeDestroyed".equals(condition.type) ? condition.enemyType : condition.weaponId,
                v -> {
                    if ("enemyTypeDestroyed".equals(condition.type)) condition.enemyType = v; else condition.weaponId = v;
                    onEdited();
                }));
        }
        if (List.of("enemiesDestroyed", "enemyTypeDestroyed", "gemsCollected", "grazed").contains(condition.type)) {
            box.getChildren().add(numberRow("Count", condition.count, v -> { condition.count = v.intValue(); onEdited(); }));
        }
        return box;
    }
}
