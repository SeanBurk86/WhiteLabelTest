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

import static whitelabeltest.editor.FormControls.bulletTextureRow;
import static whitelabeltest.editor.FormControls.checkBox;
import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.enemyTextureRow;
import static whitelabeltest.editor.FormControls.numberRow;
import static whitelabeltest.editor.FormControls.sectionLabel;
import static whitelabeltest.editor.FormControls.textRow;
import static whitelabeltest.editor.FormControls.withBlank;

/** Form for an enemy type (EnemyDefinition), opened from EnemyPalette: stats, textures, firing
 *  pattern, weapon sets, hitboxes, flags. There's no movement here; movement is set per placed
 *  spawn. Edits apply to the shared definition; "Save enemies.json" persists them. */
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
        root.getChildren().add(enemyTextureRow("Texture", def.texture, v -> { def.texture = v.isEmpty() ? null : v; refresh(); }));
        root.getChildren().add(bulletTextureRow("Bullet texture", def.bulletTexture, v -> def.bulletTexture = v.isEmpty() ? null : v));
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
        root.getChildren().add(buildHitboxSection());
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
        root.getChildren().add(checkBox("Face player", def.facePlayer, v -> def.facePlayer = v));
        root.getChildren().add(checkBox("Sealable", def.sealable, v -> def.sealable = v));
        root.getChildren().add(checkBox("Ignore ceasefire zone", def.ignoreCeasefireZone, v -> def.ignoreCeasefireZone = v));
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

    /** Rebuilds the form (after texture/size or list edits). */
    private void refresh() {
        showDefinition(def);
    }

    /** Weapon sets: named firing patterns that waypoints can switch to. Names can't be renamed
     *  (waypoints refer to them); remove and re-add instead. */
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

    /** A hitbox summary and a button to open HitboxEditorDialog. */
    private VBox buildHitboxSection() {
        VBox box = new VBox(4);
        int count = def.hitboxes == null ? 0 : def.hitboxes.size;
        Label summary = new Label(count == 0 ? "Hitbox: default (one box, the whole sprite)"
            : "Hitbox: " + count + " custom shape" + (count == 1 ? "" : "s"));
        summary.setTextFill(Color.LIGHTGRAY);
        summary.setStyle("-fx-font-size: 10px;");
        Button edit = new Button("Edit Hitboxes...");
        edit.setOnAction(e -> HitboxEditorDialog.show(getScene() != null ? getScene().getWindow() : null, def, library, this::refresh));
        box.getChildren().addAll(summary, edit);
        return box;
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
