package whitelabeltest.editor;

import com.badlogic.gdx.utils.ObjectMap;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import whitelabeltest.enemy.EnemyDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static whitelabeltest.editor.FormControls.checkBox;
import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.numberRow;
import static whitelabeltest.editor.FormControls.sectionLabel;
import static whitelabeltest.editor.FormControls.textRow;
import static whitelabeltest.editor.FormControls.withBlank;

/** Right-hand editing form for an EnemyDefinition's own template stats - health, size, textures,
 *  default patterns, behavior flags - selected from EnemyPalette (a base definition, not a placed
 *  instance). Distinct from PropertiesPanel, which edits one placed Trigger's per-spawn overrides;
 *  EditorApp swaps whichever of the two is relevant into the same dock slot.
 *
 * No movement pattern field here at all, deliberately - see EnemyDefinition.java's own doc. Every
 * placed enemy spawn gets its own movement assigned individually via PropertiesPanel's "Movement
 * Path" section once it's on the Stage canvas, scoped to that one placement - a dedicated small
 * canvas docked in this narrow sidebar made editing a path needlessly cramped anyway, and a placed
 * spawn already has real stage context (where other triggers/background art sit) a standalone
 * canvas never had.
 *
 * Edits commit straight onto the live EnemyDefinition (shared by reference with whatever
 * TriggerNode.buildEnemyImage() already looked up via StageLibrary.findEnemy()), and "Save
 * enemies.json" persists the whole list - see StageLibrary.saveEnemies(). */
public class EnemyDefinitionPanel extends ScrollPane {
    private final StageLibrary library;
    private final VBox root = new VBox(8);
    private EnemyDefinition def;
    private Label statusLabel;

    public EnemyDefinitionPanel(StageLibrary library) {
        this.library = library;
        root.setPadding(new Insets(8));
        setContent(root);
        setFitToWidth(true);
        setPrefWidth(280);
        showDefinition(null);
    }

    public void showDefinition(EnemyDefinition def) {
        this.def = def;

        root.getChildren().clear();
        if (def == null) {
            root.getChildren().add(sectionLabel("No enemy selected - click one in the Enemies palette."));
            return;
        }

        root.getChildren().add(sectionLabel("Enemy Definition: " + def.id));
        ImageView preview = buildPreview();
        if (preview != null) root.getChildren().add(preview);

        root.getChildren().add(sectionLabel("Identity"));
        root.getChildren().add(textRow("Texture", def.texture, v -> { def.texture = v; refresh(); }));
        root.getChildren().add(textRow("Bullet texture", def.bulletTexture, v -> { def.bulletTexture = v; }));
        root.getChildren().add(numberRow("Frame count", def.frameCount, v -> def.frameCount = v.intValue()));
        root.getChildren().add(numberRow("Columns", def.columns, v -> def.columns = v.intValue()));
        root.getChildren().add(numberRow("Rows", def.rows, v -> def.rows = v.intValue()));
        root.getChildren().add(numberRow("Frame duration", def.frameDuration, v -> def.frameDuration = v));
        root.getChildren().add(numberRow("Size", def.size, v -> { def.size = v; refresh(); }));

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Combat"));
        root.getChildren().add(numberRow("Health", def.health, v -> def.health = v.intValue()));
        root.getChildren().add(numberRow("Score", def.score, v -> def.score = v.intValue()));
        root.getChildren().add(numberRow("Health regen/sec", def.healthRegenPerSecond, v -> def.healthRegenPerSecond = v));
        // Movement is deliberately NOT configured here - see EnemyDefinition.java's own doc. Every
        // placed spawn gets its own movement (a WaypointPath, usually) assigned individually via
        // PropertiesPanel's "Movement Path" section once it's on the Stage canvas, with no
        // type-level default to fall back to.
        root.getChildren().add(comboRow("Firing pattern", withBlank(PatternIds.firingPatternIds()), def.firingPattern,
            v -> def.firingPattern = v.isEmpty() ? null : v));
        root.getChildren().add(buildWeaponSetsSection());
        root.getChildren().add(comboRow("Explosion pattern", withBlank(PatternIds.explosionPatternIds()), def.explosionPattern,
            v -> def.explosionPattern = v.isEmpty() ? null : v));
        root.getChildren().add(textRow("Pair id", def.pairId, v -> def.pairId = v.isEmpty() ? null : v));

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Behavior Flags"));
        root.getChildren().add(checkBox("Is boss", def.isBoss, v -> def.isBoss = v));
        root.getChildren().add(checkBox("Is ground", def.isGround, v -> def.isGround = v));
        root.getChildren().add(checkBox("Inverse movement", def.inverseMovement, v -> def.inverseMovement = v));
        root.getChildren().add(checkBox("Rotate with movement", def.rotateWithMovement, v -> def.rotateWithMovement = v));
        root.getChildren().add(checkBox("Sealable", def.sealable, v -> def.sealable = v));
        root.getChildren().add(checkBox("Defiant", def.defiant, v -> def.defiant = v));
        root.getChildren().add(checkBox("Damageable by enemy bullets", def.damageableByEnemyBullets, v -> def.damageableByEnemyBullets = v));
        root.getChildren().add(checkBox("Show health bar", def.showHealthBar, v -> def.showHealthBar = v));
        root.getChildren().add(checkBox("Targetable by homing", def.targetableByHoming, v -> def.targetableByHoming = v));
        root.getChildren().add(checkBox("Bullet cancel on death", def.bulletCancel, v -> def.bulletCancel = v));
        root.getChildren().add(numberRow("Background layer (-1 = none)", def.backgroundLayer, v -> def.backgroundLayer = v.intValue()));

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Spawn-in Animation"));
        root.getChildren().add(textRow("Spawn texture", def.spawnTexture, v -> def.spawnTexture = v.isEmpty() ? null : v));
        root.getChildren().add(numberRow("Frame count", def.spawnFrameCount, v -> def.spawnFrameCount = v.intValue()));
        root.getChildren().add(numberRow("Columns", def.spawnColumns, v -> def.spawnColumns = v.intValue()));
        root.getChildren().add(numberRow("Rows", def.spawnRows, v -> def.spawnRows = v.intValue()));
        root.getChildren().add(numberRow("Duration", def.spawnDuration, v -> def.spawnDuration = v));

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Death Animation"));
        root.getChildren().add(textRow("Death texture", def.deathTexture, v -> def.deathTexture = v.isEmpty() ? null : v));
        root.getChildren().add(numberRow("Frame count", def.deathFrameCount, v -> def.deathFrameCount = v.intValue()));
        root.getChildren().add(numberRow("Columns", def.deathColumns, v -> def.deathColumns = v.intValue()));
        root.getChildren().add(numberRow("Rows", def.deathRows, v -> def.deathRows = v.intValue()));
        root.getChildren().add(numberRow("Duration", def.deathDuration, v -> def.deathDuration = v));

        root.getChildren().add(new Separator());
        Button save = new Button("Save enemies.json");
        statusLabel = new Label();
        statusLabel.setTextFill(Color.LIGHTGREEN);
        statusLabel.setStyle("-fx-font-size: 10px;");
        save.setOnAction(e -> {
            library.saveEnemies();
            statusLabel.setText("Saved.");
        });
        root.getChildren().add(save);
        root.getChildren().add(statusLabel);
    }

    /** Re-shows this same definition - used after editing texture/size, whose preview/canvas icons
     *  need to reflect the change; a fresh EnemyPalette entry still needs a reload to pick up a
     *  texture swap (same "next rebuild" limitation TriggerNode's own doc already covers for a
     *  placed instance). */
    private void refresh() {
        showDefinition(def);
    }

    /** Named alternates this enemy's WaypointPath waypoints can switch its live firing pattern to
     *  mid-flight - see EnemyDefinition.weaponSets' own doc and PropertiesPanel's "Weapon Set"
     *  combo (Movement Path panel), which is where a name defined here actually gets referenced
     *  from a placed spawn's path. Each row's own combo commits straight to the map on change (no
     *  full refresh() needed, unlike a row add/remove); only the name is fixed once created - this
     *  editor doesn't offer a rename, just remove-and-recreate, since nothing on disk indexes these
     *  names except each MovementPatternDef.weaponSet string authored against them, and silently
     *  updating those from here would be more surprising than requiring a deliberate re-link. */
    private VBox buildWeaponSetsSection() {
        VBox box = new VBox(4);
        box.getChildren().add(sectionLabel("Weapon Sets (named firing-pattern alternates for Movement Path waypoints)"));
        if (def.weaponSets == null) def.weaponSets = new ObjectMap<>();

        List<String> names = new ArrayList<>();
        def.weaponSets.keys().forEach(names::add);
        Collections.sort(names);
        for (String name : names) {
            box.getChildren().add(buildWeaponSetRow(name));
        }

        HBox addRow = new HBox(6);
        TextField nameField = new TextField();
        nameField.setPromptText("SetName");
        Button add = new Button("+ Add");
        add.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty() || def.weaponSets.containsKey(name)) return;
            def.weaponSets.put(name, "");
            refresh();
        });
        addRow.getChildren().addAll(nameField, add);
        box.getChildren().add(addRow);
        return box;
    }

    private HBox buildWeaponSetRow(String name) {
        HBox row = new HBox(6);
        row.setStyle("-fx-alignment: center-left;");
        Label label = new Label(name + ":");
        label.setTextFill(Color.LIGHTGRAY);
        label.setStyle("-fx-font-size: 10px;");
        label.setMinWidth(90);
        label.setWrapText(true);

        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(withBlank(PatternIds.firingPatternIds())));
        combo.setEditable(false);
        combo.setValue(def.weaponSets.get(name, ""));
        combo.setOnAction(e -> def.weaponSets.put(name, combo.getValue() == null ? "" : combo.getValue()));

        Button remove = new Button("✕");
        remove.setOnAction(e -> { def.weaponSets.remove(name); refresh(); });

        row.getChildren().addAll(label, combo, remove);
        return row;
    }

    private ImageView buildPreview() {
        if (def.texture == null) return null;
        String relative = def.texture.startsWith("images/") ? def.texture.substring("images/".length()) : def.texture;
        File file = new File("images", relative);
        if (!file.exists()) return null;
        Image sheet = new Image(file.toURI().toString());
        int columns = Math.max(def.columns, 1);
        int rows = Math.max(def.rows, 1);
        double frameW = sheet.getWidth() / columns;
        double frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return null;
        ImageView view = new ImageView(sheet);
        view.setViewport(new javafx.geometry.Rectangle2D(0, 0, frameW, frameH));
        double maxDim = 120;
        double scale = Math.min(maxDim / frameW, maxDim / frameH);
        view.setFitWidth(frameW * scale);
        view.setFitHeight(frameH * scale);
        return view;
    }
}
