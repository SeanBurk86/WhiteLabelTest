package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import whitelabeltest.enemy.EnemyDefinition;

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;

/** Drag source for enemy-spawn triggers, and click-to-select source for editing an enemy's own
 *  definition - one tile per data/enemies.json entry, thumbnail cropped to the sheet's first frame
 *  (same columns/rows math EnemyDefinition already encodes for the game's own renderer). Dragging a
 *  tile onto StageCanvas puts "enemy:&lt;id&gt;" on the Dragboard - see StageCanvas.createTrigger().
 *  Clicking (not dragging) a tile instead notifies the selection listener - see EditorApp, which
 *  routes that to EnemyDefinitionPanel.
 *
 * "+ New Enemy" (top, always visible - outside the scrollable tile area, unlike the tiles
 * themselves) prompts for an id, adds a fresh blank EnemyDefinition to the library, and selects it
 * exactly as if its own tile had just been clicked - so it opens straight into EnemyDefinitionPanel
 * ready to fill in, the same "new, unsaved until Save" flow FiringPatternEditorDialog's own New
 * button already uses for firing patterns. */
public class EnemyPalette extends BorderPane {
    private final StageLibrary library;
    private final TilePane tiles = new TilePane();
    private VBox selectedTile;
    private Consumer<EnemyDefinition> selectionListener;

    public EnemyPalette(StageLibrary library) {
        this.library = library;
        tiles.setHgap(6);
        tiles.setVgap(6);
        tiles.setPadding(new Insets(6));
        tiles.setPrefColumns(2);
        rebuildTiles();

        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);

        Button newButton = new Button("+ New Enemy");
        newButton.setMaxWidth(Double.MAX_VALUE);
        newButton.setOnAction(e -> createNewEnemy());
        HBox toolbar = new HBox(newButton);
        toolbar.setPadding(new Insets(6, 6, 0, 6));
        HBox.setHgrow(newButton, Priority.ALWAYS);

        setTop(toolbar);
        setCenter(scroll);
    }

    public void setSelectionListener(Consumer<EnemyDefinition> listener) { this.selectionListener = listener; }

    /** Rebuilds every tile from the library's current enemy list - the only way this palette's
     *  tiles ever change, since (unlike EnemyDefinitionPanel's own refresh()) there was previously
     *  no rebuild path at all: a freshly-added enemy needed a whole editor restart to show up. */
    private void rebuildTiles() {
        tiles.getChildren().clear();
        selectedTile = null;
        for (EnemyDefinition def : library.getEnemies()) {
            tiles.getChildren().add(buildTile(def));
        }
    }

    private void createNewEnemy() {
        TextInputDialog prompt = new TextInputDialog("");
        prompt.setTitle("New Enemy");
        prompt.setHeaderText(null);
        prompt.setContentText("Enemy id:");
        Optional<String> result = prompt.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
        if (result.isEmpty()) return;

        String id = result.get();
        if (library.findEnemy(id) != null) {
            new Alert(Alert.AlertType.WARNING, "An enemy named '" + id + "' already exists.").showAndWait();
            return;
        }

        EnemyDefinition def = library.createEnemy(id);
        rebuildTiles();
        if (!tiles.getChildren().isEmpty()) {
            VBox tile = (VBox) tiles.getChildren().get(tiles.getChildren().size() - 1);
            selectedTile = tile;
            tile.setStyle(tileStyle(true));
        }
        if (selectionListener != null) selectionListener.accept(def);
    }

    private VBox buildTile(EnemyDefinition def) {
        VBox tile = new VBox(2);
        tile.setStyle(tileStyle(false));
        tile.setPrefWidth(96);

        ImageView view = buildThumbnail(def);
        if (view != null) tile.getChildren().add(view);

        Label label = new Label(def.id);
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-size: 9px;");
        label.setWrapText(true);
        tile.getChildren().add(label);

        tile.setOnDragDetected(event -> {
            javafx.scene.input.Dragboard db = tile.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("enemy:" + def.id);
            db.setContent(content);
            event.consume();
        });

        tile.setOnMouseClicked(event -> {
            if (selectedTile != null) selectedTile.setStyle(tileStyle(false));
            selectedTile = tile;
            tile.setStyle(tileStyle(true));
            if (selectionListener != null) selectionListener.accept(def);
        });

        return tile;
    }

    private static String tileStyle(boolean selected) {
        String border = selected ? "-fx-border-color: #ffd54a; -fx-border-width: 2; -fx-border-radius: 6;" : "";
        return "-fx-background-color: #2a2b30; -fx-padding: 4; -fx-background-radius: 6;" + border;
    }

    private ImageView buildThumbnail(EnemyDefinition def) {
        if (def.texture == null) return null;
        String relative = def.texture.startsWith("images/") ? def.texture.substring("images/".length()) : def.texture;
        File file = new File("images", relative);
        if (!file.exists()) return null;
        try {
            Image sheet = new Image(file.toURI().toString());
            int columns = Math.max(def.columns, 1);
            int rows = Math.max(def.rows, 1);
            double frameW = sheet.getWidth() / columns;
            double frameH = sheet.getHeight() / rows;
            ImageView view = new ImageView(sheet);
            view.setViewport(new Rectangle2D(0, 0, frameW, frameH));
            view.setFitWidth(48);
            view.setFitHeight(48);
            view.setPreserveRatio(true);
            return view;
        } catch (Exception e) {
            return null;
        }
    }
}
