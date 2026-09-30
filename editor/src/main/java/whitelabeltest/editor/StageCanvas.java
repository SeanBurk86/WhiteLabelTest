package whitelabeltest.editor;

import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.trigger.Trigger;
import whitelabeltest.gamemanagers.trigger.WaveSpawnPlanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The stage editing surface. x is world x (with off-screen margins either side of the play
 *  area); the vertical axis is trigger distance, with 0 at the bottom. All coordinate mapping goes
 *  through distanceToCanvasY()/canvasYToDistance()/worldXToCanvasX()/canvasXToWorldX().
 *
 *  Hosts one TriggerNode per trigger and accepts palette drops. Handles selection (click,
 *  Ctrl/Cmd-click, rubber band), group drags, copy/paste and delete. Draws true-scale movement path
 *  previews, wave member previews and the stage's background art. For one trigger at a time, its
 *  waypoint path can be edited in place (drag handles, click to add). */
public class StageCanvas extends Pane {
    public static final double PIXELS_PER_UNIT_X = 60;
    // Distance-axis scale. Equal to X so the canvas is isotropic; background art doesn't depend on it.
    private static final double PIXELS_PER_UNIT_Y = 60;
    // Keep in sync with Main.PLAY_AREA_WIDTH / PLAY_AREA_HEIGHT.
    private static final double WORLD_WIDTH = 9.0;
    private static final double WORLD_HEIGHT = 12.0;
    // Room either side of the play area for off-screen entrance spawns.
    private static final double MARGIN_UNITS = 2.5;
    private static final double TOTAL_WIDTH_UNITS = WORLD_WIDTH + 2 * MARGIN_UNITS;
    private static final double MIN_DISTANCE_UNITS = 160;
    private static final double GRID_STEP_UNITS = 10;
    // Each consecutive paste lands this much further into the stage.
    private static final float PASTE_DISTANCE_OFFSET = 2f;
    // Default spawn position for a dropped enemy/sprite (y is world y, not distance).
    public static final float DEFAULT_SPAWN_Y = 8f;
    public static final float DEFAULT_SPAWN_X = (float) (WORLD_WIDTH / 2.0);
    // Spacing between position-less triggers at the same distance, so each stays clickable.
    private static final float NO_POSITION_X_SPACING = 0.6f;

    private final EditorDocument document;
    private final StageLibrary library;
    private final MovementPatternLibrary patternLibrary = new MovementPatternLibrary();
    private final Group backgroundLayer = new Group();
    private final Group playAreaLayer = new Group();
    private final Group pathPreviewLayer = new Group();
    private final Group waveSpawnPreviewLayer = new Group();
    private final Group gridLayer = new Group();
    private final Group pathEditLayer = new Group();
    private final Group triggerLayer = new Group();
    private final Group selectionRectLayer = new Group();
    private final Map<Trigger, TriggerNode> nodesByTrigger = new IdentityHashMap<>();
    // Display x for triggers without an x (see effectiveX()), recomputed on rebuild().
    private final Map<Trigger, Float> noPositionFallbackX = new IdentityHashMap<>();
    private final List<PathWaypointNode> pathEditNodes = new ArrayList<>();
    private final Set<TriggerNode> selectedNodes = new LinkedHashSet<>();
    // Called with the full selection (possibly empty) on every change.
    private Consumer<List<Trigger>> selectionListener;
    // Non-null only during a rubber-band drag.
    private Rectangle rubberBandRect;
    private double rubberBandStartX, rubberBandStartY;
    // During a group drag: the start position of every selected node except the one under the cursor.
    private final Map<TriggerNode, double[]> groupDragStartPositions = new IdentityHashMap<>();
    // Detached clones; survives opening another stage, so you can paste across stages.
    private final List<Trigger> clipboard = new ArrayList<>();
    private int pasteCount = 0;
    // The trigger whose path is being edited, and its pattern, cached because reloading from
    // disk would discard unsaved edits.
    private Trigger pathEditTrigger;
    private MovementPatternDef pathEditPattern;
    private MovementPatternDef selectedPathPoint;
    private Consumer<MovementPatternDef> pathPointSelectionListener;
    // Unsaved path edits. Separate from markDirty(): patterns are their own files, not part of the
    // trigger file.
    private Runnable pathEditChangeListener;
    // Canvas height; distance 0 maps here (the bottom edge).
    private double heightPixels = MIN_DISTANCE_UNITS * PIXELS_PER_UNIT_Y;

    public StageCanvas(EditorDocument document, StageLibrary library) {
        this.document = document;
        this.library = library;
        setPrefWidth(TOTAL_WIDTH_UNITS * PIXELS_PER_UNIT_X);
        // Shows only in the off-screen margins.
        setStyle("-fx-background-color: #17181c;");
        // Back to front: art, play area, path/wave previews, grid, path handles, triggers, rubber band.
        getChildren().addAll(backgroundLayer, playAreaLayer, pathPreviewLayer, waveSpawnPreviewLayer, gridLayer, pathEditLayer, triggerLayer, selectionRectLayer);

        setOnDragOver(this::handleDragOver);
        setOnDragDropped(this::handleDragDropped);
        setOnMouseClicked(this::handleCanvasClicked);
        // Presses on nodes are consumed by the nodes, so these only see empty-space presses.
        setOnMousePressed(this::handleSelectionPressed);
        setOnMouseDragged(this::handleSelectionDragged);
        setOnMouseReleased(this::handleSelectionReleased);
        // Delete/Backspace, Ctrl+C, Ctrl+V. select() requests focus so these work right after a click.
        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPressed);

        document.addChangeListener(this::rebuild);
        rebuild();
    }

    public EditorDocument getDocument() { return document; }
    public StageLibrary getLibrary() { return library; }
    public MovementPatternLibrary getPatternLibrary() { return patternLibrary; }
    public void setSelectionListener(Consumer<List<Trigger>> listener) { this.selectionListener = listener; }
    public void setPathPointSelectionListener(Consumer<MovementPatternDef> listener) { this.pathPointSelectionListener = listener; }
    public void setPathEditChangeListener(Runnable listener) { this.pathEditChangeListener = listener; }
    public void notifyPathEditChanged() { if (pathEditChangeListener != null) pathEditChangeListener.run(); }

    // --- coordinate conversion ---

    public double distanceToCanvasY(float distance) {
        return heightPixels - distance * PIXELS_PER_UNIT_Y;
    }

    public float canvasYToDistance(double canvasY) {
        return (float) ((heightPixels - canvasY) / PIXELS_PER_UNIT_Y);
    }

    /** World x = 0 is MARGIN_UNITS in from the canvas's left edge. */
    public double worldXToCanvasX(float x) {
        return (x + MARGIN_UNITS) * PIXELS_PER_UNIT_X;
    }

    public float canvasXToWorldX(double canvasX) {
        return (float) (canvasX / PIXELS_PER_UNIT_X - MARGIN_UNITS);
    }

    /** trigger.x, or a display-only x for triggers without one (text cues, gates). It is never
     *  written back; dragging the node sets a real x. */
    public float effectiveX(Trigger trigger) {
        if (!Float.isNaN(trigger.x)) return trigger.x;
        Float fallback = noPositionFallbackX.get(trigger);
        return fallback != null ? fallback : DEFAULT_SPAWN_X;
    }

    /** Spreads position-less triggers that share a distance around DEFAULT_SPAWN_X. */
    private void computeNoPositionFallbackX() {
        noPositionFallbackX.clear();
        Map<Float, List<Trigger>> byDistance = new HashMap<>();
        for (Trigger trigger : document.getTriggers()) {
            if (!Float.isNaN(trigger.x)) continue;
            byDistance.computeIfAbsent(trigger.distance, d -> new ArrayList<>()).add(trigger);
        }
        for (List<Trigger> group : byDistance.values()) {
            int count = group.size();
            for (int i = 0; i < count; i++) {
                float offset = (i - (count - 1) / 2f) * NO_POSITION_X_SPACING;
                noPositionFallbackX.put(group.get(i), DEFAULT_SPAWN_X + offset);
            }
        }
    }

    private void handleKeyPressed(javafx.scene.input.KeyEvent event) {
        javafx.scene.input.KeyCode code = event.getCode();
        if (code == javafx.scene.input.KeyCode.DELETE || code == javafx.scene.input.KeyCode.BACK_SPACE) {
            deleteSelectedTriggers();
            event.consume();
        } else if (event.isShortcutDown() && code == javafx.scene.input.KeyCode.C) {
            copySelectedTriggers();
            event.consume();
        } else if (event.isShortcutDown() && code == javafx.scene.input.KeyCode.V) {
            pasteTriggers();
            event.consume();
        }
    }

    // --- copy/paste ---

    /** Copies detached clones of the selection. An empty selection leaves the clipboard alone. */
    private void copySelectedTriggers() {
        if (selectedNodes.isEmpty()) return;
        clipboard.clear();
        for (TriggerNode node : selectedNodes) clipboard.add(cloneTrigger(node.getTrigger()));
        pasteCount = 0;
    }

    /** Pastes clones offset further into the stage (one more step per consecutive paste) and
     *  selects them. */
    private void pasteTriggers() {
        if (clipboard.isEmpty()) return;
        pasteCount++;
        float offset = PASTE_DISTANCE_OFFSET * pasteCount;

        List<Trigger> pasted = new ArrayList<>();
        for (Trigger source : clipboard) {
            Trigger copy = cloneTrigger(source);
            copy.distance += offset;
            forkMovementPattern(copy);
            pasted.add(copy);
            document.addTrigger(copy);
        }

        for (TriggerNode n : selectedNodes) n.setUnselected();
        selectedNodes.clear();
        for (Trigger trigger : pasted) {
            TriggerNode node = nodesByTrigger.get(trigger);
            if (node == null) continue;
            selectedNodes.add(node);
            node.setSelected();
        }
        requestFocus();
        notifySelectionChanged();
    }

    /** Deep copy via a Json round trip, so new Trigger fields are always included. */
    private static Trigger cloneTrigger(Trigger source) {
        Json json = new Json();
        return json.fromJson(Trigger.class, json.toJson(source, Trigger.class));
    }

    /** Gives a pasted trigger its own copy of its movement pattern file, so editing the copy's path
     *  doesn't change the original's. */
    private void forkMovementPattern(Trigger copy) {
        String id = copy.movementPattern;
        if (id == null || id.isBlank() || !patternLibrary.exists(id)) return;
        MovementPatternDef forked = patternLibrary.load(id);
        forked.id = patternLibrary.uniqueId(id);
        patternLibrary.save(forked);
        copy.movementPattern = forked.id;
    }

    /** Starts a rubber band on empty space (disabled in path-edit mode, where clicks add waypoints). */
    private void handleSelectionPressed(MouseEvent event) {
        if (pathEditTrigger != null) return;
        rubberBandStartX = event.getX();
        rubberBandStartY = event.getY();
        rubberBandRect = new Rectangle(rubberBandStartX, rubberBandStartY, 0, 0);
        rubberBandRect.setFill(Color.web("#6fd6ff", 0.15));
        rubberBandRect.setStroke(Color.web("#6fd6ff"));
        rubberBandRect.setStrokeWidth(1);
        rubberBandRect.setMouseTransparent(true);
        selectionRectLayer.getChildren().add(rubberBandRect);
    }

    private void handleSelectionDragged(MouseEvent event) {
        if (rubberBandRect == null) return;
        double x = Math.min(rubberBandStartX, event.getX());
        double y = Math.min(rubberBandStartY, event.getY());
        rubberBandRect.setX(x);
        rubberBandRect.setY(y);
        rubberBandRect.setWidth(Math.abs(event.getX() - rubberBandStartX));
        rubberBandRect.setHeight(Math.abs(event.getY() - rubberBandStartY));
    }

    /** Selects the nodes the band overlaps: replaces the selection, or adds to it with Ctrl/Cmd. */
    private void handleSelectionReleased(MouseEvent event) {
        if (rubberBandRect == null) return;
        boolean add = event.isShortcutDown();
        Bounds band = rubberBandRect.getBoundsInParent();
        selectionRectLayer.getChildren().remove(rubberBandRect);
        rubberBandRect = null;

        if (!add) {
            for (TriggerNode n : selectedNodes) n.setUnselected();
            selectedNodes.clear();
        }
        for (TriggerNode node : nodesByTrigger.values()) {
            if (!band.intersects(node.getBoundsInParent())) continue;
            if (selectedNodes.add(node)) node.setSelected();
        }
        requestFocus();
        notifySelectionChanged();
        event.consume();
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
            if (node != null) select(node, false);
        }
        event.setDropCompleted(accepted);
        event.consume();
    }

    private Trigger createTrigger(String paletteContent, float x, float distance) {
        Trigger trigger = new Trigger();
        trigger.distance = distance;
        // Always set x (even where the game ignores it) so the node has a stable canvas position.
        trigger.x = x;
        String payload = paletteContent.substring(paletteContent.indexOf(':') + 1);
        switch (paletteContent.substring(0, paletteContent.indexOf(':'))) {
            case "enemy" -> {
                trigger.type = payload;
                trigger.y = DEFAULT_SPAWN_Y;
            }
            case "action" -> {
                switch (payload) {
                    // Blank trigger: no action until one is picked in the properties panel.
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

    /** Full rebuild on document changes (load/add/remove/markDirty). Field edits use
     *  refreshTrigger() instead. The height is recomputed first because every Y depends on it. */
    private void rebuild() {
        double maxDistance = MIN_DISTANCE_UNITS;
        for (Trigger trigger : document.getTriggers()) {
            maxDistance = Math.max(maxDistance, trigger.distance + 10);
        }
        heightPixels = maxDistance * PIXELS_PER_UNIT_Y;
        setPrefHeight(heightPixels);
        computeNoPositionFallbackX();

        triggerLayer.getChildren().clear();
        nodesByTrigger.clear();
        // The nodes are discarded; callers that need it notify the selection change themselves.
        selectedNodes.clear();
        for (Trigger trigger : document.getTriggers()) {
            TriggerNode node = new TriggerNode(trigger, this, this::select);
            nodesByTrigger.put(trigger, node);
            triggerLayer.getChildren().add(node);
        }
        boolean hasBackgroundArt = drawBackgroundArt();
        drawPlayArea(hasBackgroundArt);
        drawGrid(maxDistance);
        drawPathPreviews();

        // Drop path-edit state if its trigger was deleted or another stage was opened.
        if (pathEditTrigger != null && !document.getTriggers().contains(pathEditTrigger, true)) {
            pathEditTrigger = null;
            pathEditPattern = null;
            selectedPathPoint = null;
        }
        rebuildPathEdit();
    }

    /** Draws the background as a stack of screen-height bands, each a snapshot of what the game
     *  shows at that band's distance. Layers scroll at different rates, so one stretched image per
     *  layer can't be true scale for all of them; sampling per band keeps every layer true scale.
     *  Returns whether anything was drawn. */
    private boolean drawBackgroundArt() {
        backgroundLayer.getChildren().clear();
        StageDefinition stageDef = document.getStageDefinition();
        if (stageDef == null || stageDef.backgroundLayers == null || stageDef.backgroundLayers.size == 0) return false;

        float cameraSpeed = document.getFile().cameraSpeed > 0 ? document.getFile().cameraSpeed : 1f;
        double left = worldXToCanvasX(0f);
        double bandPixels = WORLD_HEIGHT * PIXELS_PER_UNIT_X;
        int bandCount = (int) Math.ceil(heightPixels / bandPixels);

        boolean drewAny = false;
        for (int band = 0; band < bandCount; band++) {
            double bottomCanvasY = heightPixels - band * bandPixels;
            double topCanvasY = bottomCanvasY - bandPixels;
            float elapsedSinceStart = canvasYToDistance(bottomCanvasY) / cameraSpeed;

            // Layers are declared far to near.
            for (StageDefinition.BackgroundLayerDef layerDef : stageDef.backgroundLayers) {
                List<ImageView> views = BackgroundWindowRenderer.buildWindow(layerDef, elapsedSinceStart, WORLD_WIDTH, WORLD_HEIGHT, PIXELS_PER_UNIT_X);
                for (ImageView view : views) {
                    view.setLayoutX(view.getLayoutX() + left);
                    view.setLayoutY(view.getLayoutY() + topCanvasY);
                    backgroundLayer.getChildren().add(view);
                }
                if (!views.isEmpty()) drewAny = true;
            }
        }
        // Clip the top band's overhang.
        backgroundLayer.setClip(new Rectangle(0, 0, TOTAL_WIDTH_UNITS * PIXELS_PER_UNIT_X, heightPixels));
        return drewAny;
    }

    /** Draws each spawn's movement path at true scale, anchored at its trigger node. World y and
     *  distance are different axes, so only the path's shape relative to the spawn is literal. */
    private void drawPathPreviews() {
        pathPreviewLayer.getChildren().clear();
        for (Trigger trigger : document.getTriggers()) {
            List<double[]> points = MovementPathPreview.resolve(trigger, patternLibrary);
            if (points == null) continue;

            double anchorCanvasX = worldXToCanvasX(trigger.x);
            double anchorCanvasY = distanceToCanvasY(trigger.distance);
            double[] spawn = points.get(0);

            Polyline line = new Polyline();
            for (double[] p : points) {
                double px = anchorCanvasX + (p[0] - spawn[0]) * PIXELS_PER_UNIT_X;
                double py = anchorCanvasY - (p[1] - spawn[1]) * PIXELS_PER_UNIT_X;
                line.getPoints().addAll(px, py);
            }
            line.setStroke(Color.web("#6fd6ff"));
            line.setStrokeWidth(1.5);
            line.setOpacity(0.8);
            pathPreviewLayer.getChildren().add(line);

            double[] end = points.get(points.size() - 1);
            double endPx = anchorCanvasX + (end[0] - spawn[0]) * PIXELS_PER_UNIT_X;
            double endPy = anchorCanvasY - (end[1] - spawn[1]) * PIXELS_PER_UNIT_X;
            Circle endDot = new Circle(endPx, endPy, 3, Color.web("#ffd54a"));
            endDot.setOpacity(0.9);
            pathPreviewLayer.getChildren().add(endDot);
        }
        drawWaveSpawnPreviews();
    }

    /** A dot and direction arrow for each wave member (WaveSpawnPlanner), anchored like the path
     *  previews. Uses a nominal player at (worldWidth / 2, 1), as the game's wave preview does. */
    private void drawWaveSpawnPreviews() {
        waveSpawnPreviewLayer.getChildren().clear();
        for (Trigger trigger : document.getTriggers()) {
            if (trigger.waveShape == null) continue;
            Array<WaveSpawnPlanner.Slot> slots = WaveSpawnPlanner.plan(trigger, DEFAULT_SPAWN_X, 1f);

            double anchorCanvasX = worldXToCanvasX(trigger.x);
            double anchorCanvasY = distanceToCanvasY(trigger.distance);
            for (WaveSpawnPlanner.Slot slot : slots) {
                double px = anchorCanvasX + (slot.x - trigger.x) * PIXELS_PER_UNIT_X;
                double py = anchorCanvasY - (slot.y - trigger.y) * PIXELS_PER_UNIT_X;
                Circle dot = new Circle(px, py, 4, Color.web("#ff9a3c"));
                dot.setOpacity(0.85);
                waveSpawnPreviewLayer.getChildren().add(dot);

                double dirRad = Math.toRadians(slot.angleDeg);
                Line arrow = new Line(px, py, px + Math.cos(dirRad) * 12, py - Math.sin(dirRad) * 12);
                arrow.setStroke(Color.web("#ff9a3c"));
                arrow.setStrokeWidth(1.5);
                arrow.setOpacity(0.85);
                waveSpawnPreviewLayer.getChildren().add(arrow);
            }
        }
    }

    /** Redraws only the previews (live during waypoint drags). */
    public void refreshPathPreviews() {
        drawPathPreviews();
    }

    // --- in-place path editing (toggled from PropertiesPanel's Movement Path section) ---

    /** Starts (or with null, stops) path editing for a trigger. Loads and caches its pattern once;
     *  only pure waypoint sequences can be edited. */
    public void setPathEditTrigger(Trigger trigger) {
        this.pathEditTrigger = trigger;
        MovementPatternDef resolved = trigger != null ? patternLibrary.resolveForTrigger(trigger) : null;
        this.pathEditPattern = MovementPatternLibrary.isWaypointSequence(resolved) ? resolved : null;
        this.selectedPathPoint = null;
        rebuildPathEdit();
        drawPathPreviews();
        if (pathPointSelectionListener != null) pathPointSelectionListener.accept(null);
    }

    public boolean isPathEditActive(Trigger trigger) {
        return trigger != null && trigger == pathEditTrigger;
    }

    public MovementPatternDef getSelectedPathPoint() { return selectedPathPoint; }

    /** The cached pattern being edited. The panel must edit this object, not a fresh load from
     *  disk, or its edits would be lost. */
    public MovementPatternDef getPathEditPattern() { return pathEditPattern; }

    /** Redraws handles and previews after a panel edit to the cached pattern. */
    public void refreshPathEditPositions() {
        rebuildPathEdit();
        drawPathPreviews();
    }

    public void deleteSelectedPathPoint() {
        if (pathEditPattern == null || pathEditPattern.patterns == null || selectedPathPoint == null) return;
        pathEditPattern.patterns.removeValue(selectedPathPoint, true);
        selectedPathPoint = null;
        rebuildPathEdit();
        drawPathPreviews();
        if (pathPointSelectionListener != null) pathPointSelectionListener.accept(null);
        notifyPathEditChanged();
    }

    public void savePathEditPattern() {
        if (pathEditPattern != null) patternLibrary.save(pathEditPattern);
    }

    /** Rebuilds the waypoint handles (after point changes, and on rebuild() since the anchor may move). */
    private void rebuildPathEdit() {
        pathEditLayer.getChildren().clear();
        pathEditNodes.clear();
        if (pathEditTrigger == null || pathEditPattern == null || pathEditPattern.patterns == null) return;

        double anchorCanvasX = worldXToCanvasX(pathEditTrigger.x);
        double anchorCanvasY = distanceToCanvasY(pathEditTrigger.distance);
        int index = 1;
        for (MovementPatternDef waypoint : pathEditPattern.patterns) {
            if (!"MoveToPoint".equals(waypoint.type)) continue;
            PathWaypointNode node = new PathWaypointNode(waypoint, this, anchorCanvasX, anchorCanvasY,
                pathEditTrigger.x, pathEditTrigger.y, this::selectPathPoint);
            node.setIndex(index++);
            if (waypoint == selectedPathPoint) node.setSelected();
            pathEditNodes.add(node);
            pathEditLayer.getChildren().add(node);
        }
    }

    private void selectPathPoint(PathWaypointNode node) {
        for (PathWaypointNode n : pathEditNodes) n.setUnselected();
        selectedPathPoint = node.getWaypoint();
        node.setSelected();
        if (pathPointSelectionListener != null) pathPointSelectionListener.accept(selectedPathPoint);
    }

    /** Selects a waypoint as if its handle were clicked (used by the panel's waypoint list). */
    public void selectPathPoint(MovementPatternDef waypoint) {
        for (PathWaypointNode node : pathEditNodes) {
            if (node.getWaypoint() == waypoint) { selectPathPoint(node); return; }
        }
    }

    /** In path-edit mode, a click on empty space appends a MoveToPoint waypoint there and selects
     *  it. Selection clicks are handled by the press/release handlers. */
    private void handleCanvasClicked(MouseEvent event) {
        if (pathEditTrigger == null || pathEditPattern == null || pathEditPattern.patterns == null) {
            return;
        }

        double anchorCanvasX = worldXToCanvasX(pathEditTrigger.x);
        double anchorCanvasY = distanceToCanvasY(pathEditTrigger.distance);
        float worldX = round(pathEditTrigger.x + (float) ((event.getX() - anchorCanvasX) / PIXELS_PER_UNIT_X));
        float worldY = round(pathEditTrigger.y - (float) ((event.getY() - anchorCanvasY) / PIXELS_PER_UNIT_X));

        MovementPatternDef waypoint = new MovementPatternDef();
        waypoint.type = "MoveToPoint";
        waypoint.targetX = worldX;
        waypoint.targetY = worldY;
        waypoint.speed = 5f;
        waypoint.duration = 2f;
        pathEditPattern.patterns.add(waypoint);

        rebuildPathEdit();
        drawPathPreviews();
        for (PathWaypointNode node : pathEditNodes) {
            if (node.getWaypoint() == waypoint) selectPathPoint(node);
        }
        notifyPathEditChanged();
        event.consume();
    }

    /** Outlines the play area; filled only when there's no background art. */
    private void drawPlayArea(boolean hasBackgroundArt) {
        playAreaLayer.getChildren().clear();
        double left = worldXToCanvasX(0f);
        double width = WORLD_WIDTH * PIXELS_PER_UNIT_X;

        Rectangle fill = new Rectangle(left, 0, width, heightPixels);
        fill.setFill(hasBackgroundArt ? Color.TRANSPARENT : Color.web("#232429"));
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

    public boolean isSelected(TriggerNode node) {
        return selectedNodes.contains(node);
    }

    /** @param toggle Ctrl/Cmd-click: add/remove this node instead of replacing the selection */
    private void select(TriggerNode node, boolean toggle) {
        // So keyboard shortcuts work right after clicking a node.
        requestFocus();
        if (toggle) {
            if (selectedNodes.remove(node)) {
                node.setUnselected();
            } else {
                selectedNodes.add(node);
                node.setSelected();
            }
        } else if (!(selectedNodes.size() == 1 && selectedNodes.contains(node))) {
            for (TriggerNode n : selectedNodes) n.setUnselected();
            selectedNodes.clear();
            selectedNodes.add(node);
            node.setSelected();
        }

        // Leave path-edit mode unless its trigger is still the only one selected.
        if (pathEditTrigger != null && !(selectedNodes.size() == 1 && pathEditTrigger == node.getTrigger() && selectedNodes.contains(node))) {
            setPathEditTrigger(null);
        }
        notifySelectionChanged();
    }

    private void clearSelection() {
        if (selectedNodes.isEmpty()) return;
        for (TriggerNode n : selectedNodes) n.setUnselected();
        selectedNodes.clear();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener == null) return;
        List<Trigger> triggers = new ArrayList<>();
        for (TriggerNode n : selectedNodes) triggers.add(n.getTrigger());
        selectionListener.accept(triggers);
    }

    /** Delete key / the panel's "Delete N Triggers" button. */
    public void deleteSelectedTriggers() {
        if (selectedNodes.isEmpty()) return;
        List<Trigger> toDelete = new ArrayList<>();
        for (TriggerNode n : selectedNodes) toDelete.add(n.getTrigger());
        for (Trigger t : toDelete) document.removeTrigger(t);
        notifySelectionChanged();
    }

    // --- group drag (driven by TriggerNode's press/drag/release handlers) ---

    /** Records the start positions of the other selected nodes (if 2+ are selected). */
    public void beginGroupDrag(TriggerNode pressedNode) {
        groupDragStartPositions.clear();
        if (selectedNodes.size() > 1 && selectedNodes.contains(pressedNode)) {
            for (TriggerNode n : selectedNodes) {
                if (n != pressedNode) groupDragStartPositions.put(n, new double[] { n.getLayoutX(), n.getLayoutY() });
            }
        }
    }

    /** Moves the other selected nodes by the dragged node's delta and syncs their triggers. */
    public void applyGroupDrag(double dx, double dy) {
        for (Map.Entry<TriggerNode, double[]> entry : groupDragStartPositions.entrySet()) {
            TriggerNode n = entry.getKey();
            double[] start = entry.getValue();
            n.setLayoutX(Math.max(0, start[0] + dx));
            n.setLayoutY(Math.max(0, start[1] + dy));
            n.syncTriggerFromLayout();
        }
    }

    /** The dragged node's own markDirty() covers the whole group. */
    public void endGroupDrag() {
        groupDragStartPositions.clear();
    }

    /** After a panel field edit: relabels then repositions one node (refresh() first, since the
     *  label changes the box size used for centering). */
    public void refreshTrigger(Trigger trigger) {
        TriggerNode node = nodesByTrigger.get(trigger);
        if (node != null) {
            node.refresh();
            node.updatePosition();
        }
        document.markDirty();
        // Keeps path/wave previews live while editing fields.
        drawPathPreviews();
    }

    public void deleteTrigger(Trigger trigger) {
        document.removeTrigger(trigger);
    }
}
