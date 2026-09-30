package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.HealthPhase;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
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
import static whitelabeltest.editor.FormControls.soundRow;
import static whitelabeltest.editor.FormControls.textRow;
import static whitelabeltest.editor.FormControls.withBlank;

/** Form for the selected trigger: distance and flags, then Conditions (when it fires), then the
 *  Action (what it does: spawn, sound, sprite, text, speed change or a scripted action). Changing
 *  the action clears the previous action's fields. Edits write straight to the Trigger and call
 *  StageCanvas.refreshTrigger(). With 2+ triggers selected it shows a bulk delete instead. */
public class PropertiesPanel extends ScrollPane {
    private static final String[] CONDITION_TYPES = {
        "shoot", "bomb", "weaponSwitch", "moved", "movedLeft", "movedRight",
        "hyperAttack", "hyperAttackReleased", "enemiesDestroyed", "enemyTypeDestroyed",
        "spawnDestroyed", "gemsCollected", "grazed"
    };

    // Action combo: key -> label, in display order.
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
        ACTION_KINDS.put("music", "Switch Music");
        ACTION_KINDS.put("despawn", "Despawn Enemies");
        ACTION_KINDS.put("silence", "Silence Enemies");
        ACTION_KINDS.put("waypointGem", "Waypoint Gem");
        ACTION_KINDS.put("swapWeapon", "Swap Weapon");
    }

    private final StageLibrary library;
    private final StageCanvas canvas;
    private final VBox root = new VBox(8);
    // Kept across showTrigger() so they can be refreshed alone (e.g. on canvas waypoint edits)
    // without resetting the scroll position.
    private final VBox movementPathBox = new VBox(6);
    private final VBox waveBox = new VBox(6);
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
        root.getChildren().add(FormControls.checkBox("Gate (freezes the camera until resolved)", trigger.gate,
            v -> { trigger.gate = v; onEdited(); }));
        root.getChildren().add(FormControls.checkBox("Require confirm (waits for a FIRE press)", trigger.requireConfirm,
            v -> { trigger.requireConfirm = v; onEdited(); }));
        // Mutually exclusive; the form is rebuilt so the other box unchecks.
        root.getChildren().add(FormControls.checkBox("First attempt only (skipped on a practice retry)", trigger.firstAttemptOnly, v -> {
            trigger.firstAttemptOnly = v;
            if (v) trigger.retryOnly = false;
            onEdited();
            showTrigger(trigger);
        }));
        root.getChildren().add(FormControls.checkBox("Retry only (plays after failing a practice section)", trigger.retryOnly, v -> {
            trigger.retryOnly = v;
            if (v) trigger.firstAttemptOnly = false;
            onEdited();
            showTrigger(trigger);
        }));

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

    /** 2+ triggers selected: only bulk delete is offered. */
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

    /** The trigger's action kind, checked in TriggerManager.fire()'s dispatch order. */
    private static String actionKindKey(Trigger trigger) {
        if (trigger.sound != null) return "sound";
        if (trigger.spriteTexture != null) return "sprite";
        if (trigger.setSpeed != null) return "speed";
        if (trigger.text != null) return "text";
        if (trigger.triggerBossVideo) return "bossVideo";
        if (trigger.fadeOutMusic) return "fadeMusic";
        if (trigger.music != null) return "music";
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

    /** Clears every action field, then sets defaults for the new kind. A leftover field could
     *  otherwise win TriggerManager.fire()'s dispatch. */
    private void applyActionKind(Trigger trigger, String key) {
        trigger.type = null;
        trigger.sound = null;
        trigger.spriteTexture = null;
        trigger.setSpeed = null;
        trigger.text = null;
        trigger.triggerBossVideo = false;
        trigger.fadeOutMusic = false;
        trigger.music = null;
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
            case "music" -> trigger.music = "audio/music/CHANGE_ME.mp3";
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
            case "music" -> {
                root.getChildren().add(textRow("Music path", trigger.music, v -> { trigger.music = v.isBlank() ? null : v.trim(); onEdited(); }));
                root.getChildren().add(sectionLabel("Switches the stage music to this track (looping) from here on - usually placed just after a Fade Out Music."));
            }
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
        root.getChildren().add(soundRow("Sound path", trigger.sound, v -> { trigger.sound = v; onEdited(); }));
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

    /** TextCue's authored fields. */
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

    /** A TextArea, since cue text needs real newlines. Commits on focus lost. */
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
        // Only relabels the node; its icon updates on the next full canvas rebuild.
        root.getChildren().add(comboRow("Enemy type", enemyIdOptions(), trigger.type, v -> { trigger.type = v; onEdited(); }));
        // Lets a spawnDestroyed condition wait for this spawn (or whole wave) to be destroyed.
        root.getChildren().add(textRow("Trigger ID (for \"spawnDestroyed\" conditions)", trigger.id,
            v -> { trigger.id = v.isBlank() ? null : v.trim(); onEdited(); }));
        root.getChildren().add(numberRow("Spawn X", trigger.x, v -> { trigger.x = v; onEdited(); }));
        // With "Enters from above", this is the arrival point instead.
        root.getChildren().add(numberRow("Spawn Y (entrance position, not stage progress)", trigger.y, v -> { trigger.y = v; onEdited(); }));
        // Spawns this much earlier than the trigger's distance, so the enemy is in place by then.
        root.getChildren().add(numberRow("Spawn lead (distance-units early)", trigger.spawnLead, v -> { trigger.spawnLead = v; onEdited(); }));
        // Spawns off-screen and flies down to Spawn Y over the lead time (needs a spawn lead > 0).
        root.getChildren().add(FormControls.checkBox("Enters from above (travels down to Spawn Y)", trigger.enterFromAbove, v -> { trigger.enterFromAbove = v; onEdited(); }));
        root.getChildren().add(numberRowNullable("Offset X (formation slot)", trigger.offsetX, v -> { trigger.offsetX = v; onEdited(); }));
        root.getChildren().add(numberRowNullable("Offset Y (formation slot)", trigger.offsetY, v -> { trigger.offsetY = v; onEdited(); }));
        // Movement belongs to the trigger, not the enemy type. Usually authored via Movement Path below.
        root.getChildren().add(comboRow("Movement pattern", withBlank(PatternIds.movementPatternIds()), trigger.movementPattern,
            v -> { trigger.movementPattern = v.isEmpty() ? null : v; onEdited(); refreshWaveBox(); }));
        root.getChildren().add(comboRow("Firing pattern override", withBlank(PatternIds.firingPatternIds()), trigger.firingPattern,
            v -> { trigger.firingPattern = v.isEmpty() ? null : v; onEdited(); }));
        root.getChildren().add(comboRow("Guaranteed powerup", List.of("", "1", "2", "3"),
            trigger.powerup == null ? "" : String.valueOf(trigger.powerup),
            v -> { trigger.powerup = v.isEmpty() ? null : Integer.valueOf(v); onEdited(); }));
        root.getChildren().add(FormControls.checkBox("Inverse movement", trigger.inverseMovement, v -> { trigger.inverseMovement = v; onEdited(); }));

        root.getChildren().add(new Separator());
        buildHealthPhasesSection();

        root.getChildren().add(new Separator());
        root.getChildren().add(movementPathBox);
        refreshMovementPathBox();

        root.getChildren().add(new Separator());
        root.getChildren().add(waveBox);
        refreshWaveBox();
    }

    /** The Movement Path section: offers to create a pattern if there's none, to convert one that
     *  isn't a waypoint list, or else the "Edit Path on Stage" toggle with the waypoint fields and
     *  Save. Also refreshed alone from the canvas's path listeners. */
    private void refreshMovementPathBox() {
        movementPathBox.getChildren().clear();
        if (trigger == null || !"enemy".equals(actionKindKey(trigger))) return;

        movementPathBox.getChildren().add(sectionLabel("Movement Path"));
        MovementPatternDef pattern = canvas.getPatternLibrary().resolveForTrigger(trigger);

        if (pattern == null) {
            movementPathBox.getChildren().add(sectionLabel("No movement pattern resolved for this spawn."));
            Button create = new Button("Create Path");
            create.setOnAction(e -> {
                StageDefinition stageDef = canvas.getDocument().getStageDefinition();
                String hint = (stageDef != null && stageDef.id != null ? stageDef.id + "_" : "")
                    + (trigger.type != null ? trigger.type : "path");
                String id = canvas.getPatternLibrary().uniqueId(hint);
                canvas.getPatternLibrary().save(canvas.getPatternLibrary().createNew(id));
                trigger.movementPattern = id;
                onEdited();
                canvas.setPathEditTrigger(trigger);
                refreshMovementPathBox();
                refreshWaveBox();
            });
            movementPathBox.getChildren().add(create);
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

        // Edit the canvas's cached pattern, not the copy loaded above (edits to that would be lost).
        MovementPatternDef live = canvas.getPathEditPattern();
        final MovementPatternDef livePattern = live != null ? live : pattern;

        movementPathBox.getChildren().add(sectionLabel(
            "Click the stage to add a point. Drag a point to move it. Click a point to select it."));

        movementPathBox.getChildren().add(new Separator());
        movementPathBox.getChildren().add(sectionLabel("Path Options"));
        movementPathBox.getChildren().add(numberRow("Global speed", livePattern.globalSpeed, v -> {
            livePattern.globalSpeed = v; canvas.notifyPathEditChanged();
        }));
        movementPathBox.getChildren().add(FormControls.checkBox("Close path (loop)", livePattern.closePath, v -> {
            livePattern.closePath = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
        }));
        movementPathBox.getChildren().add(FormControls.checkBox("Flip X", livePattern.flipX, v -> {
            livePattern.flipX = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
        }));
        movementPathBox.getChildren().add(FormControls.checkBox("Flip Y", livePattern.flipY, v -> {
            livePattern.flipY = v; canvas.refreshPathPreviews(); canvas.notifyPathEditChanged();
        }));

        movementPathBox.getChildren().add(new Separator());
        movementPathBox.getChildren().add(sectionLabel("Waypoints"));
        if (livePattern.patterns != null) {
            int index = 1;
            for (MovementPatternDef waypoint : livePattern.patterns) {
                if (!"MoveToPoint".equals(waypoint.type)) continue;
                movementPathBox.getChildren().add(buildWaypointListRow(waypoint, index++));
            }
        }

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
            movementPathBox.getChildren().add(soundRow("File (blank = none)", selected.soundName, v -> {
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
            movementPathBox.getChildren().add(sectionLabel("Saved " + livePattern.id + ".json"));
        });
        movementPathBox.getChildren().add(save);
    }

    /** The Wave section. A blank shape = a single spawn; picking a shape shows the wave fields.
     *  Each member still uses the trigger's movement pattern. */
    private void refreshWaveBox() {
        waveBox.getChildren().clear();
        if (trigger == null || !"enemy".equals(actionKindKey(trigger))) return;

        waveBox.getChildren().add(sectionLabel("Wave"));
        waveBox.getChildren().add(comboRow("shape", withBlank(List.of("point", "circle", "plane", "triangle")),
            trigger.waveShape == null ? "" : trigger.waveShape,
            v -> {
                trigger.waveShape = v.isEmpty() ? null : v;
                onEdited();
                refreshWaveBox();
            }));

        if (trigger.waveShape == null) {
            waveBox.getChildren().add(sectionLabel("No wave - this trigger just spawns its one enemy normally."));
            return;
        }

        VBox shapeFields = new VBox(6);
        rebuildWaveShapeFields(shapeFields);
        waveBox.getChildren().add(shapeFields);

        // Rotates the layout about the anchor (orientation only affects facing).
        waveBox.getChildren().add(numberRow("rotation (deg)", trigger.waveRotation, v -> { trigger.waveRotation = v; onEdited(); }));

        boolean hasOwnMovement = trigger.movementPattern != null && !trigger.movementPattern.isBlank();
        waveBox.getChildren().add(sectionLabel(hasOwnMovement
            ? "Every member flies its own copy of \"" + trigger.movementPattern + "\" (see Movement Path above) - orientation below is unused."
            : "No movement pattern set above, so orientation below picks each member's straight-line direction instead."));
        waveBox.getChildren().add(comboRow("orientation",
            List.of("in front", "to the center", "to the player", "to the exterior"), trigger.waveOrientation,
            v -> { trigger.waveOrientation = v; onEdited(); }));

        waveBox.getChildren().add(new Separator());
        waveBox.getChildren().add(sectionLabel("Timing"));
        waveBox.getChildren().add(numberRow("start delay (sec)", trigger.waveStartDelay, v -> { trigger.waveStartDelay = v; onEdited(); }));
        waveBox.getChildren().add(numberRow("spawn interval (sec)", trigger.waveSpawnInterval, v -> { trigger.waveSpawnInterval = v; onEdited(); }));
        waveBox.getChildren().add(FormControls.checkBox("keep formation", trigger.waveKeepFormation, v -> { trigger.waveKeepFormation = v; onEdited(); }));

        if (!hasOwnMovement) {
            waveBox.getChildren().add(new Separator());
            waveBox.getChildren().add(numberRow("speed", trigger.waveSpeed, v -> { trigger.waveSpeed = v; onEdited(); }));
        }
    }

    /** Fields for the selected wave shape, rebuilt on every shape change. */
    private void rebuildWaveShapeFields(VBox shapeFields) {
        shapeFields.getChildren().clear();
        switch (trigger.waveShape) {
            case "circle" -> {
                shapeFields.getChildren().add(numberRow("width", trigger.waveWidth, v -> { trigger.waveWidth = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("height", trigger.waveHeight, v -> { trigger.waveHeight = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("start angle", trigger.waveStartAngle, v -> { trigger.waveStartAngle = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("end angle", trigger.waveEndAngle, v -> { trigger.waveEndAngle = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("circle offset", trigger.waveCircleOffset, v -> { trigger.waveCircleOffset = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("number of spawns", trigger.waveNumberOfSpawns, v -> { trigger.waveNumberOfSpawns = Math.round(v); onEdited(); }));
            }
            case "plane" -> {
                shapeFields.getChildren().add(numberRow("width", trigger.waveWidth, v -> { trigger.waveWidth = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("height", trigger.waveHeight, v -> { trigger.waveHeight = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("lines", trigger.waveLines, v -> { trigger.waveLines = Math.round(v); onEdited(); }));
                shapeFields.getChildren().add(numberRow("columns", trigger.waveColumns, v -> { trigger.waveColumns = Math.round(v); onEdited(); }));
            }
            case "triangle" -> {
                shapeFields.getChildren().add(numberRow("width", trigger.waveWidth, v -> { trigger.waveWidth = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("height", trigger.waveHeight, v -> { trigger.waveHeight = v; onEdited(); }));
                shapeFields.getChildren().add(numberRow("columns", trigger.waveColumns, v -> { trigger.waveColumns = Math.round(v); onEdited(); }));
            }
            default -> // "point"
                shapeFields.getChildren().add(numberRow("number of spawns", trigger.waveNumberOfSpawns, v -> { trigger.waveNumberOfSpawns = Math.round(v); onEdited(); }));
        }
    }

    /** A Waypoints list row: X/Y plus a Select button. The other fields live under Selected Waypoint. */
    private VBox buildWaypointListRow(MovementPatternDef waypoint, int index) {
        boolean isSelected = waypoint == canvas.getSelectedPathPoint();
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: " + (isSelected ? "#33342f" : "#26272c") + "; -fx-padding: 6; -fx-background-radius: 6;");

        HBox header = new HBox(6, sectionLabel("Point " + index));
        header.setStyle("-fx-alignment: center-left;");
        Button select = new Button(isSelected ? "Selected" : "Select");
        select.setDisable(isSelected);
        select.setOnAction(e -> { canvas.selectPathPoint(waypoint); refreshMovementPathBox(); });
        Button delete = new Button("Delete");
        delete.setOnAction(e -> {
            canvas.selectPathPoint(waypoint);
            canvas.deleteSelectedPathPoint();
            refreshMovementPathBox();
        });
        header.getChildren().addAll(select, delete);

        box.getChildren().add(header);
        box.getChildren().add(numberRow("X", waypoint.targetX, v -> {
            waypoint.targetX = v; canvas.refreshPathEditPositions(); canvas.notifyPathEditChanged();
        }));
        box.getChildren().add(numberRow("Y", waypoint.targetY, v -> {
            waypoint.targetY = v; canvas.refreshPathEditPositions(); canvas.notifyPathEditChanged();
        }));
        return box;
    }

    private static final List<String> ORIENTATION_MODES = List.of("path", "player", "fixed");

    /** The spawned enemy's weaponSets names (empty if none). */
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

    /** Other spawn triggers, by distance: what a spawnDestroyed condition can wait on. */
    private List<Trigger> spawnTriggerCandidates() {
        List<Trigger> candidates = new ArrayList<>();
        for (Trigger t : canvas.getDocument().getTriggers()) {
            if (t != trigger && "enemy".equals(actionKindKey(t))) candidates.add(t);
        }
        candidates.sort(java.util.Comparator.comparingDouble(t -> t.distance));
        return candidates;
    }

    /** e.g. "waveA - IceKnight @ 12.5 (wave)". */
    private static String describeSpawn(Trigger t) {
        StringBuilder sb = new StringBuilder();
        if (t.id != null && !t.id.isBlank()) sb.append(t.id).append(" - ");
        sb.append(t.type).append(" @ ").append(FormControls.formatFloat(t.distance));
        if (t.waveShape != null) sb.append(" (wave)");
        return sb.toString();
    }

    /** The spawnDestroyed picker. Picking a trigger without an id gives it a unique one. */
    private HBox spawnDestroyedRow(Condition condition) {
        List<Trigger> candidates = spawnTriggerCandidates();
        List<String> labels = new ArrayList<>();
        String currentLabel = "";
        for (Trigger t : candidates) {
            String label = describeSpawn(t);
            // Two spawns can read identically (same type, same distance) - keep the entries distinguishable.
            for (int n = 2; labels.contains(label); n++) label = describeSpawn(t) + " #" + n;
            labels.add(label);
            if (condition.triggerId != null && condition.triggerId.equals(t.id)) currentLabel = label;
        }
        // Show a dangling id rather than a blank.
        if (condition.triggerId != null && currentLabel.isEmpty()) {
            currentLabel = "(missing) " + condition.triggerId;
            labels.add(currentLabel);
        }
        return comboRow("Spawn trigger", withBlank(labels), currentLabel, picked -> {
            int index = labels.indexOf(picked);
            if (picked.isEmpty() || index < 0 || index >= candidates.size()) {
                if (picked.isEmpty()) condition.triggerId = null;
                onEdited();
                return;
            }
            Trigger chosen = candidates.get(index);
            if (chosen.id == null || chosen.id.isBlank()) chosen.id = uniqueTriggerId(chosen.type);
            condition.triggerId = chosen.id;
            canvas.getDocument().markDirty();
            onEdited();
        });
    }

    /** A Trigger.id not used by any trigger in the open stage: `base_1`, `base_2`, ... */
    private String uniqueTriggerId(String base) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Trigger t : canvas.getDocument().getTriggers()) if (t.id != null) used.add(t.id);
        int n = 1;
        while (used.contains(base + "_" + n)) n++;
        return base + "_" + n;
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
        if ("spawnDestroyed".equals(condition.type)) {
            box.getChildren().add(spawnDestroyedRow(condition));
            box.getChildren().add(sectionLabel("Satisfied once every enemy that spawn produced is destroyed (a whole wave, if it's one)."));
        }
        if (List.of("enemiesDestroyed", "enemyTypeDestroyed", "gemsCollected", "grazed").contains(condition.type)) {
            box.getChildren().add(numberRow("Count", condition.count, v -> { condition.count = v.intValue(); onEdited(); }));
        }
        return box;
    }

    /** Health phases: pattern swaps at health percentages (blank = unchanged). The list stays null
     *  until one is added. */
    private void buildHealthPhasesSection() {
        root.getChildren().add(sectionLabel("Health phases (change patterns as it takes damage)"));
        if (trigger.healthPhases != null) {
            for (HealthPhase phase : trigger.healthPhases) root.getChildren().add(buildHealthPhaseRow(phase));
        }
        Button add = new Button("+ Add Health Phase");
        add.setOnAction(e -> {
            if (trigger.healthPhases == null) trigger.healthPhases = new com.badlogic.gdx.utils.Array<>();
            trigger.healthPhases.add(new HealthPhase(50f, null, null));
            onEdited();
            showTrigger(trigger);
        });
        root.getChildren().add(add);
    }

    private VBox buildHealthPhaseRow(HealthPhase phase) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: #26272c; -fx-padding: 6; -fx-background-radius: 6;");

        Button remove = new Button("✕");
        remove.setOnAction(e -> {
            trigger.healthPhases.removeValue(phase, true);
            if (trigger.healthPhases.size == 0) trigger.healthPhases = null;
            onEdited();
            showTrigger(trigger);
        });
        HBox header = new HBox(6, numberRow("At health %", phase.healthPercent, v -> { phase.healthPercent = v; onEdited(); }), remove);
        box.getChildren().add(header);
        box.getChildren().add(comboRow("Movement pattern", withBlank(PatternIds.movementPatternIds()), phase.movementPattern,
            v -> { phase.movementPattern = v.isEmpty() ? null : v; onEdited(); }));
        box.getChildren().add(comboRow("Firing pattern", withBlank(PatternIds.firingPatternIds()), phase.firingPattern,
            v -> { phase.firingPattern = v.isEmpty() ? null : v; onEdited(); }));
        return box;
    }
}
