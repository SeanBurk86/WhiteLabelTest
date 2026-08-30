package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import whitelabeltest.gamemanagers.trigger.Condition;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        ACTION_KINDS.put("despawn", "Despawn Enemies");
        ACTION_KINDS.put("silence", "Silence Enemies");
        ACTION_KINDS.put("waypointGem", "Waypoint Gem");
        ACTION_KINDS.put("swapWeapon", "Swap Weapon");
    }

    private final StageLibrary library;
    private final StageCanvas canvas;
    private final VBox root = new VBox(8);
    private Trigger trigger;

    public PropertiesPanel(StageLibrary library, StageCanvas canvas) {
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
            root.getChildren().add(sectionLabel("No trigger selected - drag one from the palette, or click a placed trigger."));
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

    private void onEdited() {
        canvas.refreshTrigger(trigger);
    }

    /** Which action-kind key a trigger's current field state represents - see ACTION_KINDS. Mirrors
     *  TriggerManager.fire()'s own dispatch order. */
    private static String actionKindKey(Trigger trigger) {
        if (trigger.sound != null) return "sound";
        if (trigger.spriteTexture != null) return "sprite";
        if (trigger.setSpeed != null) return "speed";
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
        trigger.silence = false;
        trigger.despawn = false;
        trigger.waypointGem = false;
        trigger.swapWeaponId = null;

        switch (key) {
            case "enemy" -> {
                trigger.type = library.getEnemies().size > 0 ? library.getEnemies().first().id : null;
                if (Float.isNaN(trigger.y)) trigger.y = StageCanvas.DEFAULT_SPAWN_Y;
            }
            case "sound" -> trigger.sound = "audio/sfx/CHANGE_ME.mp3";
            case "sprite" -> {
                trigger.spriteTexture = "images/ui/CHANGE_ME.png";
                if (Float.isNaN(trigger.y)) trigger.y = StageCanvas.DEFAULT_SPAWN_Y;
            }
            case "speed" -> trigger.setSpeed = 1f;
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
        root.getChildren().add(FormControls.checkBox("Inverse movement", trigger.inverseMovement, v -> { trigger.inverseMovement = v; onEdited(); }));
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
