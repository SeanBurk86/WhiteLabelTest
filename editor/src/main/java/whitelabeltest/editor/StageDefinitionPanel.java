package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import java.util.List;

import static whitelabeltest.editor.FormControls.checkBox;
import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.numberRowNullable;
import static whitelabeltest.editor.FormControls.sectionLabel;
import static whitelabeltest.editor.FormControls.textRow;
import static whitelabeltest.editor.FormControls.withBlank;

/** Right-hand editing form for a StageDefinition's own metadata - content file paths, music,
 *  background (video/shader/layers), kaleidoscope/ground-scroll overrides - selected from
 *  StagePalette. Distinct from PropertiesPanel (a placed Trigger's per-spawn settings) and
 *  EnemyDefinitionPanel (an enemy type's own template stats); EditorApp swaps whichever of the
 *  three is relevant into the same dock slot.
 *
 * `id` is shown as a label, not an editable field - same "don't let this get out of sync with
 * whatever else on disk already references it" reasoning EnemyDefinitionPanel already applies to
 * EnemyDefinition.id (stage_sequences.json/trigger-file `triggerFile` paths and the id itself would
 * silently desync from a rename otherwise).
 *
 * Edits commit straight onto the live StageDefinition (shared by reference with whatever
 * EditorDocument.setStageDefinition() already pointed the open document at, and with StagePalette's
 * own list), and "Save stages.json" persists the whole list - see StageLibrary.saveStages(). */
public class StageDefinitionPanel extends ScrollPane {
    private static final List<String> SHADER_IDS = List.of("boxTunnel", "kaleidoscope");

    private final StageLibrary library;
    private final VBox root = new VBox(8);
    private StageDefinition stage;
    private Label statusLabel;

    public StageDefinitionPanel(StageLibrary library) {
        this.library = library;
        root.setPadding(new Insets(8));
        setContent(root);
        setFitToWidth(true);
        setPrefWidth(300);
        showStage(null);
    }

    public void showStage(StageDefinition stage) {
        this.stage = stage;

        root.getChildren().clear();
        if (stage == null) {
            root.getChildren().add(sectionLabel("No stage selected - click one in the Stages palette."));
            return;
        }

        root.getChildren().add(sectionLabel("Stage Definition: " + stage.id));
        root.getChildren().add(textRow("Name", stage.name, v -> stage.name = v));
        root.getChildren().add(textRow("Music", stage.music, v -> stage.music = v.isEmpty() ? null : v));

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Content Files"));
        root.getChildren().add(textRow("Trigger file", stage.triggerFile, v -> stage.triggerFile = v.isEmpty() ? null : v));
        root.getChildren().add(textRow("Spawn schedule (legacy - see StageDefinition.spawnSchedule)",
            stage.spawnSchedule, v -> stage.spawnSchedule = v.isEmpty() ? null : v));

        root.getChildren().add(new Separator());
        root.getChildren().add(sectionLabel("Background"));
        root.getChildren().add(textRow("Boss video", stage.bossVideo, v -> stage.bossVideo = v.isEmpty() ? null : v));
        root.getChildren().add(textRow("Background video", stage.backgroundVideo, v -> stage.backgroundVideo = v.isEmpty() ? null : v));
        root.getChildren().add(comboRow("Shader background", withBlank(SHADER_IDS), stage.shaderBackground == null ? "" : stage.shaderBackground,
            v -> stage.shaderBackground = v.isEmpty() ? null : v));
        root.getChildren().add(checkBox("Hue cycle background", stage.hueCycleBackground, v -> stage.hueCycleBackground = v));
        root.getChildren().add(checkBox("Player feedback background", stage.playerFeedbackBackground, v -> stage.playerFeedbackBackground = v));
        root.getChildren().add(numberRowNullable("Kaleidoscope transition time (blank = engine default)",
            stage.kaleidoscopeTransitionTime != null ? stage.kaleidoscopeTransitionTime : Float.NaN,
            v -> stage.kaleidoscopeTransitionTime = Float.isNaN(v) ? null : v));
        root.getChildren().add(numberRowNullable("Ground scroll speed (blank = engine default)",
            stage.groundScrollSpeed != null ? stage.groundScrollSpeed : Float.NaN,
            v -> stage.groundScrollSpeed = Float.isNaN(v) ? null : v));

        root.getChildren().add(new Separator());
        root.getChildren().add(buildBackgroundLayersSection());

        root.getChildren().add(new Separator());
        Button save = new Button("Save stages.json");
        statusLabel = new Label();
        statusLabel.setTextFill(Color.LIGHTGREEN);
        statusLabel.setStyle("-fx-font-size: 10px;");
        save.setOnAction(e -> {
            library.saveStages();
            statusLabel.setText("Saved.");
        });
        root.getChildren().add(save);
        root.getChildren().add(statusLabel);
    }

    /** Re-shows this same stage - used after an add/remove edit to a background layer, whose row
     *  list needs to be rebuilt from scratch (same reasoning EnemyDefinitionPanel.refresh() already
     *  applies to its own weapon-sets list). */
    private void refresh() {
        showStage(stage);
    }

    /** ScrollingBackground.drawBaseContent() draws these far-to-near in declaration order (see
     *  StageDefinition.BackgroundLayerDef's own doc) - so index 0 here is the farthest-back layer,
     *  same as every existing stages.json entry already authors them. */
    private VBox buildBackgroundLayersSection() {
        VBox box = new VBox(6);
        box.getChildren().add(sectionLabel("Background Layers (declared far-to-near)"));
        if (stage.backgroundLayers == null) stage.backgroundLayers = new Array<>();

        for (int i = 0; i < stage.backgroundLayers.size; i++) {
            box.getChildren().add(buildLayerRow(stage.backgroundLayers.get(i), i));
        }

        Button add = new Button("+ Add Layer");
        add.setOnAction(e -> {
            stage.backgroundLayers.add(new StageDefinition.BackgroundLayerDef());
            refresh();
        });
        box.getChildren().add(add);
        return box;
    }

    private VBox buildLayerRow(StageDefinition.BackgroundLayerDef layer, int index) {
        VBox row = new VBox(4);
        row.setStyle("-fx-background-color: #26272c; -fx-padding: 6; -fx-background-radius: 6;");

        HBox header = new HBox(6);
        header.setStyle("-fx-alignment: center-left;");
        Label title = new Label("Layer " + (index + 1));
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-weight: bold;");
        Button remove = new Button("✕");
        remove.setOnAction(e -> {
            stage.backgroundLayers.removeIndex(index);
            refresh();
        });
        header.getChildren().addAll(title, remove);
        row.getChildren().add(header);

        row.getChildren().add(textRow("Texture", layer.texture, v -> layer.texture = v.isEmpty() ? null : v));

        TextArea sequenceArea = new TextArea(layer.textureSequence != null ? String.join("\n", layer.textureSequence) : "");
        sequenceArea.setPromptText("Texture sequence, one path per line - overrides Texture above when non-empty");
        sequenceArea.setPrefRowCount(3);
        sequenceArea.setWrapText(true);
        sequenceArea.focusedProperty().addListener((obs, was, is) -> {
            if (is) return;
            String text = sequenceArea.getText().trim();
            if (text.isEmpty()) {
                layer.textureSequence = null;
                return;
            }
            Array<String> lines = new Array<>();
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
            layer.textureSequence = lines.size > 0 ? lines : null;
        });
        row.getChildren().add(sequenceArea);

        // BackgroundLayerDef.scrollSpeed is already NaN-as-"unset" (see its own doc), the exact
        // convention numberRowNullable already speaks - no null<->NaN boxing needed here, unlike
        // kaleidoscopeTransitionTime/groundScrollSpeed above.
        row.getChildren().add(numberRowNullable("Scroll speed (blank = engine default)", layer.scrollSpeed, v -> layer.scrollSpeed = v));

        return row;
    }
}
