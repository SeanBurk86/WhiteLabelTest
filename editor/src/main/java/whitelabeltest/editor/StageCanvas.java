package whitelabeltest.editor;

import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** The stage's editable surface - a vertical scrolling strip, per this session's plan: world-x maps
 *  left-right, a trigger's `distance` maps top-to-bottom. Accepts drags from EnemyPalette/
 *  ActionPalette (creating a new Trigger at the drop point) and hosts one TriggerNode per placed
 *  Trigger (which handle their own repositioning-by-drag internally). */
public class StageCanvas extends Pane {
    public static final double PIXELS_PER_UNIT_X = 60;
    public static final double PIXELS_PER_UNIT_Y = 14;
    // Matches Main.PLAY_AREA_WIDTH - the fixed on-screen world width every stage's spawn
    // coordinates are authored against (see GameController(worldWidth, ...)). Not read from the
    // game module since it's a private constant there; keep this in sync if that ever changes.
    private static final double WORLD_WIDTH = 9.0;
    private static final double MIN_DISTANCE_UNITS = 160;
    private static final double GRID_STEP_UNITS = 10;
    // Default spawn-entrance position (see EnemyDefinition/Trigger's own x/y doc) for a
    // freshly-dropped enemy - deliberately NOT tied to the canvas's own vertical axis (that's
    // `distance`, a different quantity - see the drop handler's own comment), just a reasonable
    // mid-screen starting point the properties panel can then be adjusted from.
    private static final float DEFAULT_SPAWN_Y = 8f;

    private final EditorDocument document;
    private final StageLibrary library;
    private final Group gridLayer = new Group();
    private final Group triggerLayer = new Group();
    private final Map<Trigger, TriggerNode> nodesByTrigger = new IdentityHashMap<>();
    private TriggerNode selectedNode;
    private Consumer<Trigger> selectionListener;

    public StageCanvas(EditorDocument document, StageLibrary library) {
        this.document = document;
        this.library = library;
        setPrefWidth(WORLD_WIDTH * PIXELS_PER_UNIT_X);
        setStyle("-fx-background-color: #1b1b1f;");
        getChildren().addAll(gridLayer, triggerLayer);

        setOnDragOver(this::handleDragOver);
        setOnDragDropped(this::handleDragDropped);

        document.addChangeListener(this::rebuild);
        rebuild();
    }

    public void setSelectionListener(Consumer<Trigger> listener) { this.selectionListener = listener; }

    private void handleDragOver(DragEvent event) {
        if (event.getGestureSource() != this && event.getDragboard().hasString()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        String content = event.getDragboard().getString();
        boolean accepted = content != null && (content.startsWith("enemy:") || content.startsWith("action:"));
        if (accepted) {
            float x = round((float) (event.getX() / PIXELS_PER_UNIT_X));
            float distance = round((float) (event.getY() / PIXELS_PER_UNIT_Y));
            Trigger trigger = createTrigger(content, x, distance);
            document.addTrigger(trigger);
            TriggerNode node = nodesByTrigger.get(trigger);
            if (node != null) select(node);
        }
        event.setDropCompleted(accepted);
        event.consume();
    }

    private Trigger createTrigger(String paletteContent, float x, float distance) {
        Trigger trigger = new Trigger();
        trigger.distance = distance;
        String payload = paletteContent.substring(paletteContent.indexOf(':') + 1);
        switch (paletteContent.substring(0, paletteContent.indexOf(':'))) {
            case "enemy" -> {
                trigger.type = payload;
                trigger.x = x;
                trigger.y = DEFAULT_SPAWN_Y;
            }
            case "action" -> {
                switch (payload) {
                    case "sound" -> trigger.sound = "audio/sfx/CHANGE_ME.mp3";
                    case "sprite" -> {
                        trigger.spriteTexture = "images/ui/CHANGE_ME.png";
                        trigger.x = x;
                        trigger.y = DEFAULT_SPAWN_Y;
                    }
                    case "speed" -> trigger.setSpeed = 1f;
                    default -> { }
                }
            }
            default -> { }
        }
        return trigger;
    }

    private static float round(float value) {
        return Math.round(value * 100f) / 100f;
    }

    /** Rebuilds every TriggerNode from the document's current trigger list - called on load/
     *  add/remove (see EditorDocument.addChangeListener()). Property-panel edits to an already-
     *  placed trigger's own fields go through TriggerNode.updatePosition()/refresh() instead of a
     *  full rebuild, so editing doesn't fight with in-progress drags. */
    private void rebuild() {
        triggerLayer.getChildren().clear();
        nodesByTrigger.clear();
        selectedNode = null;

        double maxDistance = MIN_DISTANCE_UNITS;
        for (Trigger trigger : document.getTriggers()) {
            TriggerNode node = new TriggerNode(trigger, library, () -> document.markDirty(), this::select);
            nodesByTrigger.put(trigger, node);
            triggerLayer.getChildren().add(node);
            maxDistance = Math.max(maxDistance, trigger.distance + 10);
        }
        setPrefHeight(maxDistance * PIXELS_PER_UNIT_Y);
        drawGrid(maxDistance);
    }

    private void drawGrid(double maxDistanceUnits) {
        gridLayer.getChildren().clear();
        double width = WORLD_WIDTH * PIXELS_PER_UNIT_X;
        for (double d = 0; d <= maxDistanceUnits; d += GRID_STEP_UNITS) {
            double y = d * PIXELS_PER_UNIT_Y;
            Line line = new Line(0, y, width, y);
            line.setStroke(Color.web("#33343b"));
            gridLayer.getChildren().add(line);

            Label label = new Label(String.valueOf((int) d));
            label.setFont(Font.font(9));
            label.setTextFill(Color.web("#6d6f78"));
            label.setLayoutX(2);
            label.setLayoutY(y + 1);
            gridLayer.getChildren().add(label);
        }
    }

    private void select(TriggerNode node) {
        if (selectedNode != null) selectedNode.setUnselected();
        selectedNode = node;
        node.setSelected();
        if (selectionListener != null) selectionListener.accept(node.getTrigger());
    }

    /** Called by PropertiesPanel after a field edit or delete - repositions/relabels the affected
     *  node without disturbing everything else (a full rebuild() would also work but drops the
     *  current scroll position/selection unnecessarily). */
    public void refreshTrigger(Trigger trigger) {
        TriggerNode node = nodesByTrigger.get(trigger);
        if (node != null) {
            node.updatePosition();
            node.refresh();
        }
        document.markDirty();
    }

    public void deleteTrigger(Trigger trigger) {
        document.removeTrigger(trigger);
    }
}
