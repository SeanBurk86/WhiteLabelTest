package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Drag source for the non-enemy trigger actions this pass supports - see
 *  whitelabeltest.gamemanagers.trigger.Trigger's sound/spriteTexture/setSpeed fields and
 *  TriggerManager.fire()'s dispatch. Dragging an entry onto StageCanvas puts "action:&lt;kind&gt;"
 *  on the Dragboard - see StageCanvas.createTrigger(). */
public class ActionPalette extends VBox {
    private record Entry(String label, String icon, String payload) {}

    private static final Entry[] ENTRIES = {
        new Entry("Sound Cue", "🔔", "sound"),
        new Entry("Sprite Cue", "🎬", "sprite"),
        new Entry("Set Camera Speed", "⏱", "speed"),
    };

    public ActionPalette() {
        setSpacing(6);
        setPadding(new Insets(6));
        for (Entry entry : ENTRIES) {
            getChildren().add(buildTile(entry));
        }
    }

    private Label buildTile(Entry entry) {
        Label label = new Label(entry.icon() + "  " + entry.label());
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-background-color: #2a2b30; -fx-padding: 8; -fx-background-radius: 6;");
        label.setMaxWidth(Double.MAX_VALUE);

        label.setOnDragDetected(event -> {
            Dragboard db = label.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("action:" + entry.payload());
            db.setContent(content);
            event.consume();
        });

        return label;
    }
}
