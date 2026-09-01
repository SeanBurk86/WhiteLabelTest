package whitelabeltest.editor;

import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import whitelabeltest.enemy.MovementPatternDef;

import java.util.function.Consumer;

/** One "MoveToPoint" leg of an enemy spawn's movement pattern, shown directly on StageCanvas while
 *  that spawn's Trigger is in path-edit mode - see StageCanvas.setPathEditTrigger(). Positioned via
 *  the SAME anchor+offset transform StageCanvas.drawPathPreviews() already uses for the static
 *  preview line (true scale - PIXELS_PER_UNIT_X - anchored at the trigger's own canvas position; see
 *  that method's doc for why the anchor is a point of convenience rather than a literal distance-axis
 *  mapping), so this handle sits exactly on the vertex it edits. Draggable to reposition (writes back
 *  MovementPatternDef.targetX/targetY live), click to select for PropertiesPanel's "Delete Selected
 *  Point" action - mirrors TriggerNode's own interaction model, including consuming MOUSE_CLICKED so
 *  a click here doesn't bubble up to StageCanvas.handleCanvasClicked() as "add a new point" (the same
 *  fix TriggerNode itself just needed for the same reason). */
public class PathWaypointNode extends StackPane {
    private final MovementPatternDef waypoint;
    private final StageCanvas canvas;
    private final double anchorCanvasX;
    private final double anchorCanvasY;
    private final float spawnWorldX;
    private final float spawnWorldY;
    private final Consumer<PathWaypointNode> onSelect;
    private final Circle circle = new Circle(7);
    private final Label indexLabel = new Label();

    private double dragStartMouseX, dragStartMouseY;
    private double dragStartLayoutX, dragStartLayoutY;
    private boolean dragged;

    public PathWaypointNode(MovementPatternDef waypoint, StageCanvas canvas, double anchorCanvasX, double anchorCanvasY,
                             float spawnWorldX, float spawnWorldY, Consumer<PathWaypointNode> onSelect) {
        this.waypoint = waypoint;
        this.canvas = canvas;
        this.anchorCanvasX = anchorCanvasX;
        this.anchorCanvasY = anchorCanvasY;
        this.spawnWorldX = spawnWorldX;
        this.spawnWorldY = spawnWorldY;
        this.onSelect = onSelect;

        circle.setFill(Color.web("#ff8a4a"));
        indexLabel.setTextFill(Color.web("#0c0d10"));
        indexLabel.setStyle("-fx-font-size: 9px; -fx-font-weight: bold;");
        getChildren().addAll(circle, indexLabel);
        setUnselected();
        // See TriggerNode's own doc on this exact pattern - same fix, same reason: this StackPane
        // sits in pathEditLayer (a Group), which never resizes it, so without the listener,
        // updatePosition() below can center it on a stale (0,0) - or merely too-small - box instead
        // of its real one, and never get another chance to correct itself.
        boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> updatePosition());
        autosize();
        updatePosition();

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::handlePressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleReleased);
        addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
    }

    public MovementPatternDef getWaypoint() { return waypoint; }
    public void setIndex(int oneBasedIndex) { indexLabel.setText(String.valueOf(oneBasedIndex)); }

    private void updatePosition() {
        double x = anchorCanvasX + (waypoint.targetX - spawnWorldX) * StageCanvas.PIXELS_PER_UNIT_X;
        double y = anchorCanvasY - (waypoint.targetY - spawnWorldY) * StageCanvas.PIXELS_PER_UNIT_X;
        setLayoutX(x - getBoundsInLocal().getWidth() / 2);
        setLayoutY(y - getBoundsInLocal().getHeight() / 2);
    }

    public void setSelected() {
        circle.setStroke(Color.web("#ffd54a"));
        circle.setStrokeWidth(2.5);
    }

    public void setUnselected() {
        circle.setStroke(Color.web("#1e1f24"));
        circle.setStrokeWidth(1.5);
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
        double newLayoutX = dragStartLayoutX + dx;
        double newLayoutY = dragStartLayoutY + dy;
        setLayoutX(newLayoutX);
        setLayoutY(newLayoutY);
        double centerX = newLayoutX + getBoundsInLocal().getWidth() / 2;
        double centerY = newLayoutY + getBoundsInLocal().getHeight() / 2;
        waypoint.targetX = spawnWorldX + (float) ((centerX - anchorCanvasX) / StageCanvas.PIXELS_PER_UNIT_X);
        waypoint.targetY = spawnWorldY - (float) ((centerY - anchorCanvasY) / StageCanvas.PIXELS_PER_UNIT_X);
        canvas.refreshPathPreviews();
        event.consume();
    }

    private void handleReleased(MouseEvent event) {
        if (dragged) canvas.notifyPathEditChanged();
        event.consume();
    }
}
