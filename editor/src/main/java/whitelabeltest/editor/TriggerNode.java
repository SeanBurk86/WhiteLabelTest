package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.function.Consumer;

import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.gamemanagers.trigger.Trigger;

/** One placed Trigger's visual on the StageCanvas - a small pill (thumbnail-or-icon + short label)
 *  positioned at (trigger.x, trigger.distance) * StageCanvas.PIXELS_PER_UNIT_{X,Y}. Draggable within
 *  the canvas to reposition (writes trigger.x/distance back live), click to select - see
 *  StageCanvas's drag-drop (creation) vs this class's plain mouse drag (repositioning an existing
 *  node) for why these are two different mechanisms. */
public class TriggerNode extends HBox {
    private final Trigger trigger;
    private final StageLibrary library;
    private final Runnable onMoved;
    private final Consumer<TriggerNode> onSelect;
    private final Label label = new Label();

    private double dragStartMouseX, dragStartMouseY;
    private double dragStartLayoutX, dragStartLayoutY;
    private boolean dragged;

    public TriggerNode(Trigger trigger, StageLibrary library, Runnable onMoved, Consumer<TriggerNode> onSelect) {
        this.trigger = trigger;
        this.library = library;
        this.onMoved = onMoved;
        this.onSelect = onSelect;

        setSpacing(4);
        setPadding(new Insets(2, 6, 2, 2));
        setBackground(new Background(new BackgroundFill(Color.web("#2f3138"), new CornerRadii(10), Insets.EMPTY)));
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-size: 10px;");

        ImageView icon = buildIcon();
        if (icon != null) getChildren().add(icon);
        getChildren().add(label);

        refresh();
        updatePosition();
        setUnselected();

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::handlePressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleReleased);
    }

    public Trigger getTrigger() { return trigger; }

    /** Re-syncs this node's on-screen position from the trigger's current x/distance - call after a
     *  properties-panel edit changes either field directly (a drag calls this too, from
     *  handleDragged(), to keep the math in one place). */
    public void updatePosition() {
        setLayoutX(trigger.x * StageCanvas.PIXELS_PER_UNIT_X - getBoundsInLocal().getWidth() / 2);
        setLayoutY(trigger.distance * StageCanvas.PIXELS_PER_UNIT_Y - getBoundsInLocal().getHeight() / 2);
    }

    /** Re-reads the trigger's action fields to refresh the icon/label - call after a properties-
     *  panel edit changes which action this trigger performs (rare - only "type" for an enemy spawn
     *  is likely to change post-creation) or its key identifying fields (sound path, enemy type). */
    public void refresh() {
        label.setText(describe());
    }

    private String describe() {
        if (trigger.sound != null) return "🔔 " + shortName(trigger.sound);
        if (trigger.spriteTexture != null) return "🎬 " + shortName(trigger.spriteTexture);
        if (trigger.setSpeed != null) return "⏱ speed=" + trigger.setSpeed;
        if (trigger.silence) return "🔇 " + trigger.type;
        if (trigger.despawn) return "✖ " + trigger.type;
        if (trigger.waypointGem) return "💎 gem";
        if (trigger.swapWeaponId != null) return "🔫 " + trigger.swapWeaponId;
        return trigger.type != null ? trigger.type : "(enemy?)";
    }

    private static String shortName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private ImageView buildIcon() {
        if (trigger.type == null || trigger.sound != null || trigger.spriteTexture != null
            || trigger.setSpeed != null || trigger.silence || trigger.despawn || trigger.waypointGem
            || trigger.swapWeaponId != null) {
            return null;
        }
        EnemyDefinition def = library.findEnemy(trigger.type);
        if (def == null || def.texture == null) return null;
        File file = new File("images", def.texture.startsWith("images/") ? def.texture.substring("images/".length()) : def.texture);
        if (!file.exists()) return null;
        Image sheet = new Image(file.toURI().toString());
        int columns = Math.max(def.columns, 1);
        int rows = Math.max(def.rows, 1);
        double frameW = sheet.getWidth() / columns;
        double frameH = sheet.getHeight() / rows;
        ImageView view = new ImageView(sheet);
        view.setViewport(new javafx.geometry.Rectangle2D(0, 0, frameW, frameH));
        view.setFitWidth(18);
        view.setFitHeight(18);
        return view;
    }

    public void setSelected() {
        setBorder(new Border(new BorderStroke(Color.web("#ffd54a"), BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(2))));
    }

    public void setUnselected() {
        setBorder(new Border(new BorderStroke(Color.web("#54565e"), BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1))));
    }

    private void handlePressed(MouseEvent event) {
        dragStartMouseX = event.getSceneX();
        dragStartMouseY = event.getSceneY();
        dragStartLayoutX = getLayoutX();
        dragStartLayoutY = getLayoutY();
        dragged = false;
        onSelect.accept(this);
        event.consume();
    }

    private void handleDragged(MouseEvent event) {
        dragged = true;
        double dx = event.getSceneX() - dragStartMouseX;
        double dy = event.getSceneY() - dragStartMouseY;
        double newLayoutX = Math.max(0, dragStartLayoutX + dx);
        double newLayoutY = Math.max(0, dragStartLayoutY + dy);
        setLayoutX(newLayoutX);
        setLayoutY(newLayoutY);
        trigger.x = (float) ((newLayoutX + getBoundsInLocal().getWidth() / 2) / StageCanvas.PIXELS_PER_UNIT_X);
        trigger.distance = (float) ((newLayoutY + getBoundsInLocal().getHeight() / 2) / StageCanvas.PIXELS_PER_UNIT_Y);
        event.consume();
    }

    private void handleReleased(MouseEvent event) {
        if (dragged) onMoved.run();
        event.consume();
    }
}
