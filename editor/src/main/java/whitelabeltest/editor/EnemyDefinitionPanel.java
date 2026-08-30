package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import whitelabeltest.enemy.EnemyDefinition;

import java.io.File;

import static whitelabeltest.editor.FormControls.checkBox;
import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.numberRow;
import static whitelabeltest.editor.FormControls.sectionLabel;
import static whitelabeltest.editor.FormControls.textRow;
import static whitelabeltest.editor.FormControls.withBlank;

/** Right-hand editing form for an EnemyDefinition's own template stats - health, size, textures,
 *  default patterns, behavior flags - selected from EnemyPalette (a base definition, not a placed
 *  instance). Distinct from PropertiesPanel, which edits one placed Trigger's per-spawn overrides;
 *  EditorApp swaps whichever of the two is relevant into the same dock slot. Edits commit straight
 *  onto the live EnemyDefinition (shared by reference with whatever TriggerNode.buildEnemyImage()
 *  already looked up via StageLibrary.findEnemy()), and "Save enemies.json" persists the whole list
 *  - see StageLibrary.saveEnemies(). */
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
        setPrefWidth(260);
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
        root.getChildren().add(comboRow("Movement pattern", withBlank(PatternIds.movementPatternIds()), def.movementPattern,
            v -> def.movementPattern = v.isEmpty() ? null : v));
        root.getChildren().add(comboRow("Firing pattern", withBlank(PatternIds.firingPatternIds()), def.firingPattern,
            v -> def.firingPattern = v.isEmpty() ? null : v));
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
