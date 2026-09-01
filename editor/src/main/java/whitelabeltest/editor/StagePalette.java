package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import java.util.function.Consumer;

/** Click-to-select source for editing a StageDefinition's own metadata (background layers, music,
 *  shader/video background, kaleidoscope/ground-scroll overrides, etc.) - one row per
 *  data/stages.json entry, routed to StageDefinitionPanel the same way EnemyPalette routes a click
 *  to EnemyDefinitionPanel - see EditorApp.
 *
 * Deliberately separate from "Stage > Open Stage..." (OpenStageDialog), which loads a stage's
 * TRIGGER file into the main canvas for placing/editing spawns - this palette is purely about
 * editing the stage DEFINITION itself, not navigating to its placed content, so selecting a row
 * here does not also swap which trigger file the canvas has open. */
public class StagePalette extends ScrollPane {
    private VBox selectedRow;
    private Consumer<StageDefinition> selectionListener;

    public StagePalette(StageLibrary library) {
        VBox rows = new VBox(4);
        rows.setPadding(new Insets(6));
        for (StageDefinition stage : library.getStages()) {
            rows.getChildren().add(buildRow(stage));
        }
        setContent(rows);
        setFitToWidth(true);
    }

    public void setSelectionListener(Consumer<StageDefinition> listener) { this.selectionListener = listener; }

    private VBox buildRow(StageDefinition stage) {
        VBox row = new VBox(2);
        row.setStyle(rowStyle(false));

        Label name = new Label(stage.name != null ? stage.name : stage.id);
        name.setTextFill(Color.WHITE);
        name.setStyle("-fx-font-size: 11px;");
        name.setWrapText(true);
        Label id = new Label(stage.id);
        id.setTextFill(Color.LIGHTGRAY);
        id.setStyle("-fx-font-size: 9px;");
        row.getChildren().addAll(name, id);

        row.setOnMouseClicked(event -> {
            if (selectedRow != null) selectedRow.setStyle(rowStyle(false));
            selectedRow = row;
            row.setStyle(rowStyle(true));
            if (selectionListener != null) selectionListener.accept(stage);
        });

        return row;
    }

    private static String rowStyle(boolean selected) {
        String border = selected ? "-fx-border-color: #ffd54a; -fx-border-width: 2; -fx-border-radius: 6;" : "";
        return "-fx-background-color: #2a2b30; -fx-padding: 6; -fx-background-radius: 6;" + border;
    }
}
