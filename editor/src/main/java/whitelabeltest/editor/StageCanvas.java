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
import com.badlogic.gdx.utils.Json;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
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
 * internally). Also draws every enemy spawn's real, true-scale movement path (see
 * drawPathPreviews()/MovementPathPreview) directly on the canvas, anchored at each trigger's own
 * position - and, for whichever ONE enemy-spawn trigger is currently in path-edit mode (see
 * setPathEditTrigger(), driven by PropertiesPanel's "Movement Path" section), that same path is
 * also editable right here as draggable PathWaypointNode handles plus click-to-add on empty canvas
 * space (see handleCanvasClicked()) - deliberately in-place rather than in a separate small canvas,
 * since a placed spawn already has the real stage context (nearby triggers, background art) that a
 * standalone editor never had.
 *
 * Once EditorDocument.getStageDefinition() resolves a stage, this canvas also draws the stage's own
 * background art - a sequence of true-scale, real-formula snapshots stacked down the whole canvas
 * (see drawBackgroundArt()/BackgroundWindowRenderer), each one exactly what Player View would show
 * at that snapshot's own distance - so spacing can be judged against the real backdrop instead of a
 * flat fill, at the SAME scale sprites/paths already render at. */
public class StageCanvas extends Pane {
    public static final double PIXELS_PER_UNIT_X = 60;
    // The distance axis's own pixels-per-unit scale - grid/TriggerNode/path-preview placement (see
    // distanceToCanvasY()/canvasYToDistance()) - a single fixed constant for every stage, with NO tie
    // to any background layer's own scroll rate (unlike this class's earlier "guide layer" approach -
    // see drawBackgroundArt()'s own doc for why that had to go: background art no longer shares this
    // axis's scale at all, so this is free to be whatever reads best on screen. Matching
    // PIXELS_PER_UNIT_X exactly keeps the canvas isotropic (1 canvas pixel = 1 distance-unit = 1
    // world-unit) and gives a reasonable ~12-distance-unit granularity per background snapshot band
    // (see drawBackgroundArt()) - purely a UX tuning knob now, not a correctness requirement.
    private static final double PIXELS_PER_UNIT_Y = 60;
    // Matches Main.PLAY_AREA_WIDTH - the fixed on-screen world width every stage's spawn
    // coordinates are authored against (see GameController(worldWidth, ...)). Not read from the
    // game module since it's a private constant there; keep this in sync if that ever changes.
    private static final double WORLD_WIDTH = 9.0;
    // Matches Main.PLAY_AREA_HEIGHT/PlayerPreviewView.WORLD_HEIGHT - the fixed viewport height every
    // background layer's own scroll math (BackgroundWindowRenderer/ScrollingBackground.Layer) is
    // computed against - see drawBackgroundArt()'s own doc. Not read from the game module, same
    // reasoning as WORLD_WIDTH's own doc above.
    private static final double WORLD_HEIGHT = 12.0;
    // Extra world-units of margin drawn on either side of the actual play area, so an enemy
    // authored to enter from off-screen (e.g. stage1's x: -0.7 / x: 9.7 entrance spawns, or a
    // formation's offsetX pushing a member further out still) has real canvas space to sit in,
    // rather than landing outside the drawable/scrollable area or right at its edge. Covers every
    // off-screen x value already in stage1_triggers.json with room to spare.
    private static final double MARGIN_UNITS = 2.5;
    private static final double TOTAL_WIDTH_UNITS = WORLD_WIDTH + 2 * MARGIN_UNITS;
    private static final double MIN_DISTANCE_UNITS = 160;
    private static final double GRID_STEP_UNITS = 10;
    // How far into the stage a paste lands relative to what was copied - see pasteTriggers()'s own
    // doc on why this scales by how many consecutive pastes have happened since the last copy.
    private static final float PASTE_DISTANCE_OFFSET = 2f;
    // Default spawn-entrance position (see EnemyDefinition/Trigger's own x/y doc) for a
    // freshly-dropped enemy - deliberately NOT tied to the canvas's own vertical axis (that's
    // `distance`, a different quantity - see the drop handler's own comment), just a reasonable
    // mid-screen starting point the properties panel can then be adjusted from.
    // Also used by PropertiesPanel when linking a blank Trigger Event to an enemy/sprite action.
    public static final float DEFAULT_SPAWN_Y = 8f;
    // Same reasoning as DEFAULT_SPAWN_Y, just for X: also used by effectiveX() below as the center
    // point a positionless trigger (see that method's own doc) is displayed at.
    public static final float DEFAULT_SPAWN_X = (float) (WORLD_WIDTH / 2.0);
    // World-x gap between synthesized markers when more than one positionless trigger (see
    // effectiveX()) shares the exact same distance - just enough that they render as visibly
    // separate, individually-clickable nodes side by side instead of stacked exactly on top of each
    // other (which would leave every one but the topmost unselectable again).
    private static final float NO_POSITION_X_SPACING = 0.6f;

    private final EditorDocument document;
    private final StageLibrary library;
    // Used to resolve a placed enemy spawn's movement pattern for drawPathPreviews() below.
    private final MovementPatternLibrary patternLibrary = new MovementPatternLibrary();
    private final Group backgroundLayer = new Group();
    private final Group playAreaLayer = new Group();
    private final Group pathPreviewLayer = new Group();
    private final Group gridLayer = new Group();
    private final Group pathEditLayer = new Group();
    private final Group triggerLayer = new Group();
    // Topmost - just the rubber-band rectangle while a select-drag is in progress (see
    // handleSelectionPressed()/handleSelectionDragged()/handleSelectionReleased()), empty otherwise.
    private final Group selectionRectLayer = new Group();
    private final Map<Trigger, TriggerNode> nodesByTrigger = new IdentityHashMap<>();
    // Synthesized world-x for a trigger with no real x of its own (see effectiveX()'s own doc) -
    // recomputed every rebuild(), keyed by identity since two triggers can otherwise be
    // field-for-field equal.
    private final Map<Trigger, Float> noPositionFallbackX = new IdentityHashMap<>();
    private final List<PathWaypointNode> pathEditNodes = new ArrayList<>();
    // Every currently-selected node - LinkedHashSet so iteration order matches click/toggle order
    // (nothing depends on this beyond visual/debugging consistency). A plain click replaces this
    // whole set with just the clicked node; Ctrl/Cmd-click (see TriggerNode.handlePressed()) toggles
    // one node in/out of an existing multi-selection instead - see select()'s own doc.
    private final Set<TriggerNode> selectedNodes = new LinkedHashSet<>();
    // Fired with the full current selection on every change (including empty, on deselect) - never
    // null. PropertiesPanel's own doc covers how EditorApp routes 0/1/many through to the right
    // right-dock view.
    private Consumer<List<Trigger>> selectionListener;
    // Non-null only while a rubber-band select-drag is in progress - see
    // handleSelectionPressed()/handleSelectionDragged()/handleSelectionReleased().
    private Rectangle rubberBandRect;
    private double rubberBandStartX, rubberBandStartY;
    // Non-empty only while a multi-node drag-move is in progress - each selected node's own
    // layoutX/Y at the moment the drag started, so every OTHER selected node (the one actually
    // under the cursor moves/updates itself the normal single-node way - see TriggerNode.
    // handleDragged()) can be moved by the same delta the dragged node itself just was, regardless
    // of when THEY were last individually pressed. See beginGroupDrag()/applyGroupDrag()/
    // endGroupDrag().
    private final Map<TriggerNode, double[]> groupDragStartPositions = new IdentityHashMap<>();
    // Detached Trigger clones from the most recent copySelectedTriggers() - see that method's own
    // doc. Empty until the first copy; survives a stage switch (this canvas instance is reused
    // across "Open Stage" - see EditorApp), so copying from one stage and pasting into another works.
    private final List<Trigger> clipboard = new ArrayList<>();
    // How many consecutive pasteTriggers() calls have happened since clipboard was last replaced -
    // see that method's own doc on why this fans repeated pastes out instead of stacking them.
    private int pasteCount = 0;
    // The one enemy-spawn Trigger (if any) currently being edited point-by-point directly on this
    // canvas - see setPathEditTrigger()/PropertiesPanel's "Movement Path" section, which is what
    // turns this on/off. pathEditPattern is the actual MovementPatternDef loaded for it, cached
    // rather than re-resolved on every interaction - see setPathEditTrigger()'s own doc on why
    // re-resolving mid-edit would silently discard unsaved changes.
    private Trigger pathEditTrigger;
    private MovementPatternDef pathEditPattern;
    private MovementPatternDef selectedPathPoint;
    private Consumer<MovementPatternDef> pathPointSelectionListener;
    // Fired on any unsaved pattern-content edit (add/move/delete a point) - deliberately separate
    // from EditorDocument.markDirty(): a movement pattern is its own file
    // (data/movement_patterns/<id>.json), not part of the *_triggers.json this document owns, so
    // marking the TRIGGER file dirty over a pattern-only edit would be misleading. PropertiesPanel
    // uses this purely to drive its own "unsaved" status text next to the "Save Path" button.
    private Runnable pathEditChangeListener;
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
        // Background art sits at the very back; path previews sit above the play-area fill but
        // below the grid/sprites, so the grid stays legible and a trigger's own sprite is never
        // hidden behind its own path line. pathEditLayer's draggable handles sit just below the
        // triggers themselves - same prominence as a TriggerNode - so they read as "part of the
        // active editing", above the grid but not fighting a trigger's own icon for the top spot.
        getChildren().addAll(backgroundLayer, playAreaLayer, pathPreviewLayer, gridLayer, pathEditLayer, triggerLayer, selectionRectLayer);

        setOnDragOver(this::handleDragOver);
        setOnDragDropped(this::handleDragDropped);
        setOnMouseClicked(this::handleCanvasClicked);
        // Rubber-band select - a press+drag that STARTS on empty canvas space (a press landing on a
        // TriggerNode/PathWaypointNode never reaches here at all, since both already consume their
        // own MOUSE_PRESSED for the same reason handleCanvasClicked()'s own doc covers). Separate
        // from the MOUSE_CLICKED handler above - JavaFX only synthesizes a click for a press+release
        // pair with no real drag between them, so a genuine rubber-band drag never also triggers
        // handleCanvasClicked()'s own empty-space "clear selection" - it's handled once, here.
        setOnMousePressed(this::handleSelectionPressed);
        setOnMouseDragged(this::handleSelectionDragged);
        setOnMouseReleased(this::handleSelectionReleased);
        // Delete/Backspace deletes the current selection (see deleteSelectedTriggers()) - only
        // reaches this handler while the canvas itself holds focus, which select() already
        // requests on every selection change, so a click-then-Delete flow just works without the
        // user needing to click some OTHER neutral part of the canvas first.
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

    /** trigger.x if it has one, otherwise a synthesized position purely so this trigger has SOME
     *  clickable spot on the canvas - see the class doc on TriggerNode's own use of this. A text cue
     *  or a condition-only gate (see Trigger.x's own doc) is never authored with a real x at all,
     *  since neither actually places anything at a location; loading one straight into
     *  worldXToCanvasX(trigger.x) used to produce NaN, which renders (and hit-tests) nowhere -
     *  invisible and unselectable, the tutorial stage's own trigger file being full of exactly this
     *  (text/gate triggers vastly outnumber real spawns there, unlike stage1). Deliberately never
     *  written back onto trigger.x here - see TriggerNode.updatePosition()'s own doc for why that's
     *  the node's job, only once the user actually drags it. */
    public float effectiveX(Trigger trigger) {
        if (!Float.isNaN(trigger.x)) return trigger.x;
        Float fallback = noPositionFallbackX.get(trigger);
        return fallback != null ? fallback : DEFAULT_SPAWN_X;
    }

    /** Populates noPositionFallbackX for every trigger with no real x - see effectiveX()'s own doc -
     *  spreading ones that land on the exact same distance (the tutorial stage's gem-gate gauntlet
     *  alone has one run of 4) symmetrically around DEFAULT_SPAWN_X rather than piling them all onto
     *  that one point, where only the topmost would ever receive a click. */
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

    // --- copy/paste - Ctrl/Cmd+C, Ctrl/Cmd+V (see handleKeyPressed() above), matching the same
    // platform-primary-modifier convention Ctrl/Cmd-click multi-select already uses -------------

    /** Snapshots the current selection into `clipboard` as detached clones (a Json round-trip - see
     *  cloneTrigger() - rather than hand-copying every field, so this can never silently drop a field
     *  Trigger gains in the future) - independent of the live selected Triggers from this point on,
     *  so editing/deleting/moving the originals afterward has no effect on what a later paste
     *  produces. A no-op with nothing selected (leaves whatever was previously copied intact, rather
     *  than clearing the clipboard on an accidental empty-selection Ctrl+C). */
    private void copySelectedTriggers() {
        if (selectedNodes.isEmpty()) return;
        clipboard.clear();
        for (TriggerNode node : selectedNodes) clipboard.add(cloneTrigger(node.getTrigger()));
        pasteCount = 0;
    }

    /** Adds a fresh clone of every clipboard trigger (see copySelectedTriggers()) to the document,
     *  offset further into the stage (see PASTE_DISTANCE_OFFSET) so a paste never lands exactly on
     *  top of what it was copied from, and selects the newly-pasted set so it's immediately ready to
     *  drag into place. Each consecutive paste (without an intervening copy) offsets one step further
     *  than the last - pasteCount, reset by copySelectedTriggers() - so mashing Ctrl+V fans pasted
     *  copies out instead of stacking them all on the exact same spot. A no-op with nothing copied
     *  yet. */
    private void pasteTriggers() {
        if (clipboard.isEmpty()) return;
        pasteCount++;
        float offset = PASTE_DISTANCE_OFFSET * pasteCount;

        List<Trigger> pasted = new ArrayList<>();
        for (Trigger source : clipboard) {
            Trigger copy = cloneTrigger(source);
            copy.distance += offset;
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

    /** Deep-copies `source` via a Json round trip (same String-based technique EditorDocument/
     *  MovementPatternLibrary already use - see either's own doc) rather than copying fields by hand,
     *  so this can never drift out of sync with Trigger as new fields are added. Runtime-only fields
     *  (armed/fired/confirmed/actionFired/liveTextCue) always read back at their untouched defaults
     *  regardless, since nothing in the editor itself ever sets them on a Trigger sitting in a
     *  document - only a live TriggerManager (never constructed here) does. */
    private static Trigger cloneTrigger(Trigger source) {
        Json json = new Json();
        return json.fromJson(Trigger.class, json.toJson(source, Trigger.class));
    }

    /** Starts a rubber-band select-drag - only reachable for a press that lands on genuinely empty
     *  canvas space (see the constructor's own doc on why a TriggerNode/PathWaypointNode press never
     *  gets here), and only outside path-edit mode (that mode's own empty-space interaction is
     *  MOUSE_CLICKED's click-to-add-a-waypoint - see handleCanvasClicked() - conflating the two would
     *  be confusing, so a drag started while editing a path simply does nothing here). */
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

    /** Selects every TriggerNode the band actually overlaps - both Groups the two bounds are read
     *  from (triggerLayer/selectionRectLayer) are direct, untransformed children of this Pane, so
     *  getBoundsInParent() on a node from either one is already expressed in the same coordinate
     *  space as the other with no extra reconciliation needed. Plain drag REPLACES the selection
     *  (matching a plain click's own behavior - see select()); a Ctrl/Cmd-held drag ADDS the
     *  enclosed nodes to whatever's already selected instead - a union, not select()'s own true
     *  per-node toggle, since flipping each individually would be a much less predictable result for
     *  a band that can enclose many nodes at once. */
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
        computeNoPositionFallbackX();

        triggerLayer.getChildren().clear();
        nodesByTrigger.clear();
        // No unselect()/notify needed - every node (and its old selected border) is about to be
        // discarded and rebuilt fresh anyway; a caller that just deleted the whole selection (see
        // deleteSelectedTriggers()) already sends its own empty-selection notification afterward.
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

        // A trigger being edited can be removed out from under us (Delete Trigger, or an Open Stage
        // switching documents entirely) - drop the stale reference before rebuildPathEdit() runs
        // rather than rendering handles for a Trigger no longer in the document.
        if (pathEditTrigger != null && !document.getTriggers().contains(pathEditTrigger, true)) {
            pathEditTrigger = null;
            pathEditPattern = null;
            selectedPathPoint = null;
        }
        rebuildPathEdit();
    }

    /** Draws the stage's own background-layer art as a sequence of true-scale, real-formula
     *  snapshots (see BackgroundWindowRenderer) stacked bottom-up from distance 0, one WORLD_HEIGHT-
     *  tall "band" at a time - each band is exactly what Player View would show at that band's own
     *  distance, so background art always renders at the SAME scale sprites/paths already do.
     *
     * This replaced an earlier "one stretched panorama per layer" approach that picked a single
     * "guide" background layer and stretched every OTHER layer's texture height by
     * guideSpeed/thisLayerSpeed so all layers could share one canvas-wide distance axis. That was
     * mathematically self-consistent for the guide layer alone, but any OTHER layer (a different
     * scrollSpeed - the whole point of parallax) ended up rendered at the WRONG visual scale relative
     * to enemy sprites and movement-path previews, which always render at true PIXELS_PER_UNIT_X
     * scale - stage1's own second background layer, at 2x the guide's scroll speed, was drawn at HALF
     * true size. There's no scale-tweak fix for this: one shared "distance -> canvas pixel" axis can
     * only be true-scale for ONE layer's own scroll rate at a time. The only way to keep both a
     * distance-navigable canvas AND every layer at its real, gameplay-matching scale is what this
     * method does now - resample the exact per-instant Player-View formula repeatedly instead of
     * drawing one static, stretched image per layer. This also fixes a second, smaller bug for free:
     * the old approach re-tiled a layer's last texture indefinitely once its sequence was exhausted,
     * where the real game just freezes on the final frame (see ScrollingBackground.Layer) - each band
     * here independently computes its own correctly-clamped snapshot, so bands past a layer's own
     * exhaustion point all show that identical frozen frame rather than repeating.
     *
     * Returns true if anything was actually drawn (no stage/backgroundLayers resolved yet draws
     * nothing), so drawPlayArea() knows whether to fall back to its own flat fill. */
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

            // Declaration order is far-to-near (see ScrollingBackground.drawBaseContent()'s own doc)
            // - drawing in that same order here means a nearer layer naturally paints over a farther
            // one, same as real gameplay.
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
        // bandCount is rounded UP to fully cover heightPixels, so the topmost band can overhang past
        // canvas-Y 0 - clip it off rather than let it draw above the canvas's own top.
        backgroundLayer.setClip(new Rectangle(0, 0, TOTAL_WIDTH_UNITS * PIXELS_PER_UNIT_X, heightPixels));
        return drewAny;
    }

    /** Draws every enemy spawn's actual movement path at TRUE scale (PIXELS_PER_UNIT_X - the same
     *  real-world-unit conversion a sprite's own width/height already uses, so the path's length and
     *  shape genuinely match how far/which way the enemy travels in-game, not a normalized
     *  thumbnail) - see MovementPathPreview. Anchored at the trigger's own canvas position
     *  (worldXToCanvasX(trigger.x), distanceToCanvasY(trigger.distance)): the horizontal axis is
     *  exactly right since world-x is shared with the rest of this canvas, but the vertical
     *  placement is necessarily an anchor of convenience, not a literal mapping - `distance` (this
     *  canvas's real vertical axis) and a movement pattern's real world-y are unrelated quantities,
     *  so "one for one" here means true scale/shape relative to the spawn point, not that the whole
     *  canvas becomes spatially accurate in both axes at once. */
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
    }

    /** Re-draws just the static path-preview lines/dots (see drawPathPreviews()) without touching
     *  trigger nodes, background art, or the grid - called live from PathWaypointNode's own drag
     *  handler so the preview line follows a dragged point immediately, the same
     *  dirty-on-release-only pattern TriggerNode's own drag already uses. */
    public void refreshPathPreviews() {
        drawPathPreviews();
    }

    // --- movement-path editing directly on this canvas - see PropertiesPanel's "Movement Path"
    // section, which is what actually turns this on/off per selected trigger --------------------

    /** Scopes in-place waypoint editing to `trigger` (null to turn it off) - see the class-level
     *  fields' own doc. Resolves and caches that trigger's pattern HERE, once, rather than in every
     *  interaction that follows: MovementPatternLibrary.resolveForTrigger()/load() always re-reads
     *  the file from disk, so calling it again mid-edit (e.g. from handleCanvasClicked() or a drag)
     *  would silently throw away whatever points had already been added/moved but not yet saved via
     *  savePathEditPattern(). Only a pattern that's a clean waypoint sequence (see
     *  MovementPatternLibrary.isWaypointSequence()) is ever entered into edit mode - anything else
     *  resolves to null here, same as "nothing to edit" - PropertiesPanel is responsible for
     *  offering "Convert to Waypoints" first in that case. */
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

    /** Call after a PropertiesPanel field edit changes the selected point's targetX/targetY/speed/
     *  duration directly - re-renders the handle's position and the preview line from the (already
     *  mutated) cached pattern, without re-resolving it from disk. */
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

    /** Rebuilds the draggable handles for pathEditTrigger's cached pattern - called whenever that
     *  pattern's point list or a point's own position changes, and from rebuild() so a trigger drag
     *  (which recomputes this trigger's own canvas anchor) keeps its handles in sync. */
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

    /** Click-to-add for whichever trigger is currently in path-edit mode (see setPathEditTrigger()) -
     *  a no-op otherwise, so this coexists harmlessly with the normal palette drag-drop/trigger-drag
     *  interactions when nothing is being edited. A click that lands on an existing TriggerNode or
     *  PathWaypointNode never reaches here - both consume their own MOUSE_CLICKED for exactly this
     *  reason (see their own docs) - so this only ever fires for a genuine click on empty canvas
     *  space. Inverts the same anchor+offset transform drawPathPreviews()/PathWaypointNode use to
     *  turn the click back into a world point, appends a fresh "MoveToPoint" leg (matching the old
     *  WaypointCanvas's own click-to-add defaults), and selects it immediately so it's one click away
     *  from being deleted if it was a mis-click. Outside path-edit mode, this same "genuine click on
     *  empty canvas space" case instead just clears the current trigger selection (a plain click on
     *  a TriggerNode already replaces the selection itself - see select() - so this only ever needs
     *  to handle the "clicked nothing" case). */
    private void handleCanvasClicked(MouseEvent event) {
        // Selection itself - including "clicked empty space -> nothing selected" - is now fully
        // owned by handleSelectionPressed()/handleSelectionDragged()/handleSelectionReleased() above,
        // which already run for this same gesture before this synthesized MOUSE_CLICKED fires.
        // Consuming MOUSE_RELEASED there does NOT suppress this event (same reason TriggerNode itself
        // needs its own explicit MOUSE_CLICKED consumer - see its constructor's doc), so this used to
        // also call clearSelection() here - which, on a plain click, ran AFTER handleSelectionReleased
        // had already set the real selection and immediately wiped it back out again. This only still
        // has a job in path-edit mode (adding a waypoint at the click location).
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

    /** Marks the actual on-screen play area (world x in [0, WORLD_WIDTH]) with a border, so it reads
     *  as "the screen" with off-screen approach lanes around it, the same distinction the game
     *  itself draws (see Main.drawGame()'s side panels either side of the scissored play area),
     *  rather than one undifferentiated canvas. Filled with a plain lighter shade when there's no
     *  background art to show through (hasBackgroundArt - see drawBackgroundArt()); otherwise left
     *  transparent so that art is what actually reads as "the screen". */
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

    /** @param toggle true for a Ctrl/Cmd-click (see TriggerNode.handlePressed()'s own
     *  event.isShortcutDown()) - adds/removes just this ONE node to/from whatever's already
     *  selected, instead of a plain click's usual "replace the whole selection with just this node"
     *  - the standard multi-select convention most editors use, so several triggers can be
     *  selected and bulk-deleted together (see deleteSelectedTriggers()/PropertiesPanel's
     *  multi-selection view). */
    /** Whether `node` is currently part of the selection - see TriggerNode.handlePressed()'s own use
     *  (deferring a plain press's usual "replace selection with just this node" collapse when the
     *  pressed node is already selected, so an existing multi-selection survives long enough to be
     *  group-dragged). */
    public boolean isSelected(TriggerNode node) {
        return selectedNodes.contains(node);
    }

    private void select(TriggerNode node, boolean toggle) {
        // So an immediate Delete/Backspace keypress right after clicking a trigger reaches
        // handleKeyPressed() below without the user needing to click some neutral part of the
        // canvas first - a click on a TriggerNode itself doesn't grant focus to anything on its own,
        // since TriggerNode isn't focusTraversable (there'd be no point - selection state already
        // lives here on the canvas, not per-node).
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

        // Path-edit mode is scoped to exactly one trigger - any selection change that leaves it not
        // exactly that same single trigger selected exits it (re-selecting/dragging the SAME lone
        // trigger, e.g. repositioning its spawn point while editing its path, deliberately leaves
        // edit mode alone).
        if (pathEditTrigger != null && !(selectedNodes.size() == 1 && pathEditTrigger == node.getTrigger() && selectedNodes.contains(node))) {
            setPathEditTrigger(null);
        }
        notifySelectionChanged();
    }

    /** Deselects everything - a plain click on empty canvas space (see handleCanvasClicked()) or
     *  after deleteSelectedTriggers() removes the whole current selection. */
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

    /** Deletes every currently-selected trigger - the Delete/Backspace key (see the constructor's
     *  own key-handler wiring) and PropertiesPanel's multi-selection "Delete N Triggers" button both
     *  just call this. Each EditorDocument.removeTrigger() call fires a full StageCanvas.rebuild()
     *  (see EditorDocument.markDirty()'s own doc), which already clears selectedNodes as a side
     *  effect - re-notifying with the now-empty selection afterward is still this method's own job
     *  (rebuild() deliberately doesn't, since a rebuild triggered by something else entirely, e.g.
     *  loading a different stage, shouldn't imply "selection was just cleared by a delete"). */
    public void deleteSelectedTriggers() {
        if (selectedNodes.isEmpty()) return;
        List<Trigger> toDelete = new ArrayList<>();
        for (TriggerNode n : selectedNodes) toDelete.add(n.getTrigger());
        for (Trigger t : toDelete) document.removeTrigger(t);
        notifySelectionChanged();
    }

    // --- multi-node drag-move - see TriggerNode.handlePressed()/handleDragged()/handleReleased(),
    // which call these three in lockstep with its own single-node drag handling --------------------

    /** Called from `pressedNode`'s own handlePressed() - snapshots every OTHER selected node's
     *  current layout position (pressedNode keeps tracking its own via its existing dragStartLayoutX/
     *  Y fields, unchanged) so applyGroupDrag() below can move them by the same delta later, without
     *  needing their own MOUSE_PRESSED to have just fired (it didn't - only the actually-clicked
     *  node gets one for this gesture). A no-op (empty map) when pressedNode isn't part of a 2+
     *  multi-selection, so a plain single-node drag stays exactly as cheap as it already was. */
    public void beginGroupDrag(TriggerNode pressedNode) {
        groupDragStartPositions.clear();
        if (selectedNodes.size() > 1 && selectedNodes.contains(pressedNode)) {
            for (TriggerNode n : selectedNodes) {
                if (n != pressedNode) groupDragStartPositions.put(n, new double[] { n.getLayoutX(), n.getLayoutY() });
            }
        }
    }

    /** Moves every OTHER selected node by the same (dx, dy) scene-pixel delta `pressedNode` itself
     *  just moved by - called from its handleDragged() right after it repositions itself the normal
     *  single-node way. Same "clamp to 0, sync trigger.x/distance from the new layout" logic
     *  TriggerNode.handleDragged() already applies to itself - see TriggerNode.syncTriggerFromLayout(). */
    public void applyGroupDrag(double dx, double dy) {
        for (Map.Entry<TriggerNode, double[]> entry : groupDragStartPositions.entrySet()) {
            TriggerNode n = entry.getKey();
            double[] start = entry.getValue();
            n.setLayoutX(Math.max(0, start[0] + dx));
            n.setLayoutY(Math.max(0, start[1] + dy));
            n.syncTriggerFromLayout();
        }
    }

    /** Called from handleReleased() - no markDirty() needed here specifically: every OTHER selected
     *  node's trigger.x/distance was already kept live-synced during the drag (see
     *  applyGroupDrag()'s own syncTriggerFromLayout() call), same as the pressed node's own fields
     *  already are by the time ITS handleReleased() fires its own (single) markDirty() - one
     *  document-wide dirty+rebuild already covers the whole group. */
    public void endGroupDrag() {
        groupDragStartPositions.clear();
    }

    /** Called by PropertiesPanel after a field edit or delete - repositions/relabels the affected
     *  node without disturbing everything else (a full rebuild() would also work but drops the
     *  current scroll position/selection unnecessarily). refresh() runs FIRST - see its own doc - so
     *  a label-text edit that resizes the node's box is already reflected in it before
     *  updatePosition() reads that size to re-center; the other order around would center on the
     *  box's size from BEFORE this edit. */
    public void refreshTrigger(Trigger trigger) {
        TriggerNode node = nodesByTrigger.get(trigger);
        if (node != null) {
            node.refresh();
            node.updatePosition();
        }
        document.markDirty();
    }

    public void deleteTrigger(Trigger trigger) {
        document.removeTrigger(trigger);
    }
}
