package whitelabeltest.editor;

import javafx.geometry.Rectangle2D;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import whitelabeltest.enemy.EnemyDefinition;

import java.io.File;

/** Drag source for enemy-spawn triggers - one tile per data/enemies.json entry, thumbnail cropped
 *  to the sheet's first frame (same columns/rows math EnemyDefinition already encodes for the
 *  game's own renderer). Dragging a tile onto StageCanvas puts "enemy:&lt;id&gt;" on the Dragboard -
 *  see StageCanvas.createTrigger(). */
public class EnemyPalette extends ScrollPane {
    public EnemyPalette(StageLibrary library) {
        TilePane tiles = new TilePane();
        tiles.setHgap(6);
        tiles.setVgap(6);
        tiles.setPadding(new javafx.geometry.Insets(6));
        tiles.setPrefColumns(2);

        for (EnemyDefinition def : library.getEnemies()) {
            tiles.getChildren().add(buildTile(def));
        }

        setContent(tiles);
        setFitToWidth(true);
    }

    private VBox buildTile(EnemyDefinition def) {
        VBox tile = new VBox(2);
        tile.setStyle("-fx-background-color: #2a2b30; -fx-padding: 4; -fx-background-radius: 6;");
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

        return tile;
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
