package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import whitelabeltest.gamemanagers.trigger.Condition;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.List;

/** Right-hand editing form for whichever Trigger is currently selected on the StageCanvas - shown
 *  fields depend on which action the trigger performs (mirrors TriggerManager.fire()'s own dispatch
 *  order - see that method). Every field commits straight back onto the live Trigger object, then
 *  tells the canvas to reposition/relabel that one node and mark the document dirty - see
 *  StageCanvas.refreshTrigger(). */
public class PropertiesPanel extends ScrollPane {
    private static final String[] CONDITION_TYPES = {
        "shoot", "bomb", "weaponSwitch", "moved", "movedLeft", "movedRight",
        "hyperAttack", "hyperAttackReleased", "enemiesDestroyed", "enemyTypeDestroyed",
        "gemsCollected", "grazed"
    };

    private final EditorDocument document;
    private final StageLibrary library;
    private final StageCanvas canvas;
    private final VBox root = new VBox(8);
    private Trigger trigger;

    public PropertiesPanel(EditorDocument document, StageLibrary library, StageCanvas canvas) {
        this.document = document;
        this.library = library;
        this.canvas = canvas;
        root.setPadding(new Insets(8));
        setContent(root);
        setFitToWidth(true);
        setPrefWidth(260);
        showTrigger(null);
    }

    public void showTrigger(Trigger trigger) {
        this.trigger = trigger;
        root.getChildren().clear();
        if (trigger == null) {
            root.getChildren().add(themedLabel("No trigger selected - drag one from the palette, or click a placed trigger."));
            return;
        }

        root.getChildren().add(themedLabel(actionKindLabel(trigger)));
        root.getChildren().add(numberRow("Distance", trigger.distance, v -> { trigger.distance = v; onEdited(); }));

        if (trigger.sound != null) {
            buildSoundFields();
        } else if (trigger.spriteTexture != null) {
            buildSpriteFields();
        } else if (trigger.setSpeed != null) {
            root.getChildren().add(numberRow("New camera speed", trigger.setSpeed, v -> { trigger.setSpeed = v; onEdited(); }));
        } else if (trigger.silence || trigger.despawn) {
            root.getChildren().add(textRow("Enemy type", trigger.type, v -> { trigger.type = v; onEdited(); }));
        } else if (trigger.waypointGem) {
            buildWaypointGemFields();
        } else if (trigger.swapWeaponId != null) {
            root.getChildren().add(textRow("Weapon id", trigger.swapWeaponId, v -> { trigger.swapWeaponId = v; onEdited(); }));
            root.getChildren().add(numberRow("Weapon slot", trigger.weaponSlot, v -> { trigger.weaponSlot = v.intValue(); onEdited(); }));
        } else {
            buildEnemySpawnFields();
        }

        root.getChildren().add(new Separator());
        buildConditionsSection();

        root.getChildren().add(new Separator());
        Button delete = new Button("Delete Trigger");
        delete.setOnAction(e -> {
            canvas.deleteTrigger(trigger);
            showTrigger(null);
        });
        root.getChildren().add(delete);
    }

    private void onEdited() {
        canvas.refreshTrigger(trigger);
    }

    private static String actionKindLabel(Trigger trigger) {
        if (trigger.sound != null) return "Sound Cue";
        if (trigger.spriteTexture != null) return "Sprite Cue";
        if (trigger.setSpeed != null) return "Set Camera Speed";
        if (trigger.silence) return "Silence Enemies";
        if (trigger.despawn) return "Despawn Enemies";
        if (trigger.waypointGem) return "Waypoint Gem";
        if (trigger.swapWeaponId != null) return "Swap Weapon";
        return "Enemy Spawn: " + trigger.type;
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
        root.getChildren().add(numberRow("Spawn Y (entrance position, not stage progress)", trigger.y, v -> { trigger.y = v; onEdited(); }));
        root.getChildren().add(numberRowNullable("Offset X (formation slot)", trigger.offsetX, v -> { trigger.offsetX = v; onEdited(); }));
        root.getChildren().add(numberRowNullable("Offset Y (formation slot)", trigger.offsetY, v -> { trigger.offsetY = v; onEdited(); }));
        root.getChildren().add(comboRow("Movement pattern override", withBlank(PatternIds.movementPatternIds()), trigger.movementPattern,
            v -> { trigger.movementPattern = v.isEmpty() ? null : v; onEdited(); }));
        root.getChildren().add(comboRow("Firing pattern override", withBlank(PatternIds.firingPatternIds()), trigger.firingPattern,
            v -> { trigger.firingPattern = v.isEmpty() ? null : v; onEdited(); }));
        root.getChildren().add(comboRow("Guaranteed powerup", List.of("", "1", "2", "3"),
            trigger.powerup == null ? "" : String.valueOf(trigger.powerup),
            v -> { trigger.powerup = v.isEmpty() ? null : Integer.valueOf(v); onEdited(); }));
        CheckBox inverseMovement = new CheckBox("Inverse movement");
        inverseMovement.setSelected(trigger.inverseMovement);
        inverseMovement.setTextFill(Color.WHITE);
        inverseMovement.setOnAction(e -> { trigger.inverseMovement = inverseMovement.isSelected(); onEdited(); });
        root.getChildren().add(inverseMovement);
    }

    private List<String> enemyIdOptions() {
        List<String> ids = new ArrayList<>();
        library.getEnemies().forEach(def -> ids.add(def.id));
        return ids;
    }

    private static List<String> withBlank(List<String> options) {
        List<String> result = new ArrayList<>();
        result.add("");
        result.addAll(options);
        return result;
    }

    private void buildConditionsSection() {
        root.getChildren().add(themedLabel("Conditions"));
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

    // --- small field-row builders -------------------------------------------------------------

    private Label themedLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setWrapText(true);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private HBox textRow(String label, String initial, java.util.function.Consumer<String> onCommit) {
        Label l = fieldLabel(label);
        TextField field = new TextField(initial != null ? initial : "");
        field.setOnAction(e -> onCommit.accept(field.getText()));
        field.focusedProperty().addListener((obs, was, is) -> { if (!is) onCommit.accept(field.getText()); });
        HBox row = new HBox(6, l, field);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    private HBox numberRow(String label, float initial, java.util.function.Consumer<Float> onCommit) {
        return numberRowNullable(label, initial, onCommit);
    }

    private HBox numberRow(String label, int initial, java.util.function.Consumer<Float> onCommit) {
        return numberRow(label, (float) initial, onCommit);
    }

    /** Same as numberRow but treats a non-numeric/empty entry as NaN (used for offsetX/offsetY,
     *  whose NaN default means "not a formation member" - see Trigger's own field doc) rather than
     *  refusing the edit. */
    private HBox numberRowNullable(String label, float initial, java.util.function.Consumer<Float> onCommit) {
        Label l = fieldLabel(label);
        TextField field = new TextField(Float.isNaN(initial) ? "" : formatFloat(initial));
        Runnable commit = () -> {
            String text = field.getText().trim();
            try {
                onCommit.accept(text.isEmpty() ? Float.NaN : Float.parseFloat(text));
            } catch (NumberFormatException ignored) {
                // leave the field as typed - no crash on a stray non-numeric edit mid-keystroke
            }
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, was, is) -> { if (!is) commit.run(); });
        HBox row = new HBox(6, l, field);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    private static String formatFloat(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private HBox comboRow(String label, List<String> options, String initial, java.util.function.Consumer<String> onCommit) {
        Label l = fieldLabel(label);
        ComboBox<String> combo = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(options));
        combo.setEditable(false);
        combo.setValue(initial != null ? initial : "");
        combo.setOnAction(e -> onCommit.accept(combo.getValue() == null ? "" : combo.getValue()));
        HBox row = new HBox(6, l, combo);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text + ":");
        label.setTextFill(Color.LIGHTGRAY);
        label.setStyle("-fx-font-size: 10px;");
        label.setMinWidth(90);
        label.setWrapText(true);
        return label;
    }
}
