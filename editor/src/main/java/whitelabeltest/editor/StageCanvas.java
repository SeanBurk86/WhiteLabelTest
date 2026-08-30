package whitelabeltest.editor;

import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** The stage's editable surface - a vertical scrolling strip, per this session's plan: world-x maps
 *  left-right. The vertical axis is a trigger's `distance`, oriented bottom-to-top - distance 0
 *  (stage start) anchors to the BOTTOM of the canvas and later distances sit higher up, matching how
 *  the camera is meant to "scan up" through the level in-game (see this session's very first
 *  design discussion) rather than the arbitrary top-to-bottom reading order a plain data table
 *  would suggest. distanceToCanvasY()/canvasYToDistance() are the one place that mapping lives -
 *  TriggerNode goes through these rather than doing its own pixel math, so the flip only has to be
 *  right in one place.
 *
 * The canvas is wider than just the play area - see MARGIN_UNITS - with the actual on-screen
 * WORLD_WIDTH strip drawn distinctly (drawPlayArea()) in the middle, so an enemy authored to enter
 * from off-screen (negative x, or x beyond WORLD_WIDTH - see stage1_triggers.json's own entrance
 * spawns) has real, visible canvas space on either side to sit in and can be dropped there directly.
 *
 * Accepts drags from EnemyPalette/ActionPalette (creating a new Trigger at the drop point) and
 * hosts one TriggerNode per placed Trigger (which handle their own repositioning-by-drag
 * internally). */
public class StageCanvas extends Pane {
    public static final double PIXELS_PER_UNIT_X = 60;
    public static final double PIXELS_PER_UNIT_Y = 14;
    // Matches Main.PLAY_AREA_WIDTH - the fixed on-screen world width every stage's spawn
    // coordinates are authored against (see GameController(worldWidth, ...)). Not read from the
    // game module since it's a private constant there; keep this in sync if that ever changes.
    private static final double WORLD_WIDTH = 9.0;
    // Extra world-units of margin drawn on either side of the actual play area, so an enemy
    // authored to enter from off-screen (e.g. stage1's x: -0.7 / x: 9.7 entrance spawns, or a
    // formation's offsetX pushing a member further out still) has real canvas space to sit in,
    // rather than landing outside the drawable/scrollable area or right at its edge. Covers every
    // off-screen x value already in stage1_triggers.json with room to spare.
    private static final double MARGIN_UNITS = 2.5;
    private static final double TOTAL_WIDTH_UNITS = WORLD_WIDTH + 2 * MARGIN_UNITS;
    private static final double MIN_DISTANCE_UNITS = 160;
    private static final double GRID_STEP_UNITS = 10;
    // Default spawn-entrance position (see EnemyDefinition/Trigger's own x/y doc) for a
    // freshly-dropped enemy - deliberately NOT tied to the canvas's own vertical axis (that's
    // `distance`, a different quantity - see the drop handler's own comment), just a reasonable
    // mid-screen starting point the properties panel can then be adjusted from.
    // Also used by PropertiesPanel when linking a blank Trigger Event to an enemy/sprite action.
    public static final float DEFAULT_SPAWN_Y = 8f;

    private final EditorDocument document;
    private final StageLibrary library;
    private final Group playAreaLayer = new Group();
    private final Group gridLayer = new Group();
    private final Group triggerLayer = new Group();
    private final Map<Trigger, TriggerNode> nodesByTrigger = new IdentityHashMap<>();
    private TriggerNode selectedNode;
    private Consumer<Trigger> selectionListener;
    // The canvas's current total height in pixels - distance 0 always maps to this value (the
    // bottom edge); see distanceToCanvasY(). Recomputed in rebuild() from the furthest trigger, so
    // it only changes when a trigger further into the stage than anything seen so far is added.
    private double heightPixels = MIN_DISTANCE_UNITS * PIXELS_PER_UNIT_Y;

    public StageCanvas(EditorDocument document, StageLibrary library) {
        this.document = document;
        this.library = library;
        setPrefWidth(TOTAL_WIDTH_UNITS * PIXELS_PER_UNIT_X);
        // The MARGIN_UNITS strips either side of the play area (see playAreaLayer's fill in
        // drawPlayArea()) - this base color only shows through there.
        setStyle("-fx-background-color: #17181c;");
        getChildren().addAll(playAreaLayer, gridLayer, triggerLayer);

        setOnDragOver(this::handleDragOver);
        setOnDragDropped(this::handleDragDropped);

        document.addChangeListener(this::rebuild);
        rebuild();
    }

    public EditorDocument getDocument() { return document; }
    public StageLibrary getLibrary() { return library; }
    public void setSelectionListener(Consumer<Trigger> listener) { this.selectionListener = listener; }

    // --- coordinate conversion - the one place the bottom-up flip (and the off-screen margin
    // offset) lives ------------------------------------------------------------------------------

    public double distanceToCanvasY(float distance) {
        return heightPixels - distance * PIXELS_PER_UNIT_Y;
    }

    public float canvasYToDistance(double canvasY) {
        return (float) ((heightPixels - canvasY) / PIXELS_PER_UNIT_Y);
    }

    /** World x=0 (the play area's left edge) sits MARGIN_UNITS in from the canvas's own left edge,
     *  leaving room to the left for an off-screen entrance spawn (negative x) - see MARGIN_UNITS. */
    public double worldXToCanvasX(float x) {
        return (x + MARGIN_UNITS) * PIXELS_PER_UNIT_X;
    }

    public float canvasXToWorldX(double canvasX) {
        return (float) (canvasX / PIXELS_PER_UNIT_X - MARGIN_UNITS);
    }

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
            float x = round(canvasXToWorldX(event.getX()));
            float distance = round(canvasYToDistance(event.getY()));
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
        // Every action kind gets a real x (the drop point) even where the game itself never reads
        // x/y for that action (sound/speed/event) - purely so this node has a stable, visible canvas
        // position instead of TriggerNode.updatePosition() computing off Trigger's NaN default.
        trigger.x = x;
        String payload = paletteContent.substring(paletteContent.indexOf(':') + 1);
        switch (paletteContent.substring(0, paletteContent.indexOf(':'))) {
            case "enemy" -> {
                trigger.type = payload;
                trigger.y = DEFAULT_SPAWN_Y;
            }
            case "action" -> {
                switch (payload) {
                    // A blank trigger - see ActionPalette's own doc - left with no action field set
                    // at all, gated purely by whatever conditions get added in the properties panel,
                    // which also carries the "Action" combo that links this to an actual effect
                    // (enemy spawn, sound, sprite, camera speed, ...) once you're ready to configure
                    // one.
                    case "event" -> { }
                    case "sound" -> trigger.sound = "audio/sfx/CHANGE_ME.mp3";
                    case "sprite" -> {
                        trigger.spriteTexture = "images/ui/CHANGE_ME.png";
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
     *  full rebuild, so editing doesn't fight with in-progress drags. heightPixels (and therefore
     *  every node's flipped Y - see distanceToCanvasY()) is recomputed before any node is built, so
     *  a rebuild triggered by a further-out trigger being added correctly repositions everything
     *  already placed, not just the new one. */
    private void rebuild() {
        double maxDistance = MIN_DISTANCE_UNITS;
        for (Trigger trigger : document.getTriggers()) {
            maxDistance = Math.max(maxDistance, trigger.distance + 10);
        }
        heightPixels = maxDistance * PIXELS_PER_UNIT_Y;
        setPrefHeight(heightPixels);

        triggerLayer.getChildren().clear();
        nodesByTrigger.clear();
        selectedNode = null;
        for (Trigger trigger : document.getTriggers()) {
            TriggerNode node = new TriggerNode(trigger, this, this::select);
            nodesByTrigger.put(trigger, node);
            triggerLayer.getChildren().add(node);
        }
        drawPlayArea();
        drawGrid(maxDistance);
    }

    /** Fills the actual on-screen play area (world x in [0, WORLD_WIDTH]) in a lighter shade than
     *  the MARGIN_UNITS strips either side of it, with a border marking exactly where its edges
     *  are - so it reads as "the screen" with off-screen approach lanes around it, the same
     *  distinction the game itself draws (see Main.drawGame()'s side panels either side of the
     *  scissored play area), rather than one undifferentiated canvas. */
    private void drawPlayArea() {
        playAreaLayer.getChildren().clear();
        double left = worldXToCanvasX(0f);
        double width = WORLD_WIDTH * PIXELS_PER_UNIT_X;

        Rectangle fill = new Rectangle(left, 0, width, heightPixels);
        fill.setFill(Color.web("#232429"));
        fill.setStroke(Color.web("#4a4b54"));
        fill.setStrokeWidth(1.5);
        playAreaLayer.getChildren().add(fill);
    }

    private void drawGrid(double maxDistanceUnits) {
        gridLayer.getChildren().clear();
        double width = TOTAL_WIDTH_UNITS * PIXELS_PER_UNIT_X;
        for (double d = 0; d <= maxDistanceUnits; d += GRID_STEP_UNITS) {
            double y = distanceToCanvasY((float) d);
            Line line = new Line(0, y, width, y);
            line.setStroke(Color.web("#2c2d33"));
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
