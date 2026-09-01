package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.function.BiConsumer;

import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.gamemanagers.trigger.Trigger;

/** One placed Trigger's visual on the StageCanvas - positioned via
 *  StageCanvas.worldXToCanvasX(effectiveX(trigger))/distanceToCanvasY(trigger.distance) - see that
 *  class's doc for why the vertical axis goes through a conversion method rather than a flat
 *  multiply, and effectiveX()'s own doc for why X goes through one too (a text cue/condition-only
 *  gate has no real x of its own to convert).
 *  Draggable within the canvas to reposition (writes trigger.x/distance back live), click to select
 *  - see StageCanvas's drag-drop (creation) vs this class's plain mouse drag (repositioning an
 *  existing node) for why these are two different mechanisms.
 *
 * An enemy spawn or sprite cue shows its ACTUAL sprite (cropped from the real texture, sized to
 * match the real in-game footprint - see buildIcon()) as the node's whole visible body, with just a
 * small id caption overlaid at the bottom, so the canvas reads as a real layout preview rather than
 * a diagram of abstract markers - the whole point being to judge spacing/overlap the way it'll
 * actually look on screen. A trigger with nothing pictorial to show (sound/despawn/silence/etc.)
 * falls back to a plain labeled chip.
 *
 * An enemy spawn's actual movement path (real scale, not a thumbnail) is drawn by StageCanvas
 * itself - see StageCanvas.drawPathPreviews()/MovementPathPreview - rather than by this node, since
 * a real-scale path can extend well beyond this node's own small bounding box and needs to draw
 * behind every trigger, not clipped to one. */
public class TriggerNode extends StackPane {
    private final Trigger trigger;
    private final StageCanvas canvas;
    // (this node, whether Ctrl/Cmd was held) - see handlePressed()/StageCanvas.select()'s own doc
    // on what the boolean does (multi-select toggle vs. plain "replace the selection").
    private final BiConsumer<TriggerNode, Boolean> onSelect;
    private final Label label = new Label();
    private boolean hasSprite;

    private double dragStartMouseX, dragStartMouseY;
    private double dragStartLayoutX, dragStartLayoutY;
    private boolean dragged;
    // See handlePressed()'s own doc on why a press on an already-selected node defers its usual
    // "replace the selection with just this node" collapse until handleReleased() confirms no drag
    // actually happened.
    private boolean deferSelectionCollapse;

    public TriggerNode(Trigger trigger, StageCanvas canvas, BiConsumer<TriggerNode, Boolean> onSelect) {
        this.trigger = trigger;
        this.canvas = canvas;
        this.onSelect = onSelect;

        ImageView sprite = buildSprite();
        hasSprite = sprite != null;
        if (hasSprite) {
            getChildren().add(sprite);
            label.setStyle("-fx-font-size: 9px; -fx-background-color: rgba(0,0,0,0.6); -fx-background-radius: 3; -fx-padding: 0 3 0 3;");
            setAlignment(Pos.BOTTOM_CENTER);
        } else {
            setPadding(new Insets(2, 6, 2, 2));
            setBackground(new Background(new BackgroundFill(Color.web("#2f3138"), new CornerRadii(10), Insets.EMPTY)));
            label.setStyle("-fx-font-size: 10px;");
        }
        label.setTextFill(Color.WHITE);
        getChildren().add(label);

        refresh();
        // A Region placed in a Group (triggerLayer, here) is never resized by its parent the way one
        // inside an actual layout Pane would be - see Node.autosize()'s own doc - so getBoundsInLocal()
        // read right here, synchronously in the constructor, can still be the (0,0) box every Region
        // starts at: autosize() forces an immediate resize to the computed preferred size, but that
        // computation itself can depend on a Label child's Skin, which JavaFX may not have created yet
        // this early (before this node has ever been part of a live Scene) - so even right after
        // autosize(), the box can still measure smaller than its REAL eventual size, and
        // updatePosition() below would center on that too-small box instead. The bounds listener is
        // the actual fix: it re-centers EVERY time this node's true size changes, whenever that
        // settles - immediately if autosize() already got it right, or on the first real layout pass
        // once this node is actually part of the scene otherwise - so the node can never end up stuck
        // showing at a stale, wrong-sized box's position, which is what "placed to the right of where
        // the click is released" actually was.
        boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> updatePosition());
        autosize();
        updatePosition();
        setUnselected();

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::handlePressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleReleased);
        // Selection itself already happens in handlePressed() - this just stops the click JavaFX
        // synthesizes after press+release from bubbling up to StageCanvas's own path-edit click
        // handler (see StageCanvas.handleCanvasClicked()), which would otherwise read a click on an
        // already-placed trigger as "click empty canvas space, add a waypoint" - the same bug this
        // pattern already fixed for WaypointNode/the movement-pattern canvas.
        addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
    }

    public Trigger getTrigger() { return trigger; }

    /** Re-syncs this node's on-screen position from the trigger's current x/distance - call after a
     *  properties-panel edit changes either field directly (a drag calls this too, from
     *  handleDragged(), to keep the math in one place). Goes through canvas.effectiveX() rather than
     *  trigger.x directly - see that method's own doc - so a text cue/condition-only gate (never
     *  authored with a real x) still lands somewhere real and clickable instead of at NaN. */
    public void updatePosition() {
        setLayoutX(canvas.worldXToCanvasX(canvas.effectiveX(trigger)) - getBoundsInLocal().getWidth() / 2);
        setLayoutY(canvas.distanceToCanvasY(trigger.distance) - getBoundsInLocal().getHeight() / 2);
    }

    /** Re-reads the trigger's action fields to refresh the label - call after a properties-panel
     *  edit changes which action this trigger performs or its key identifying fields (sound path,
     *  enemy type). Doesn't rebuild the sprite itself (see buildSprite()'s own doc on that) - the
     *  next full canvas rebuild (reload/save round trip) picks up a changed sprite. Re-autosizes
     *  afterward (see the constructor's own doc on why that's needed at all) since a chip-style
     *  node's whole box is sized to fit the label text - without this, a text change that grows/
     *  shrinks the label would leave the box (and so updatePosition()'s centering) sized for the OLD
     *  text, the same stale-bounds drift the constructor fix addresses for the very first layout. */
    public void refresh() {
        label.setText(describe());
        autosize();
    }

    private String describe() {
        if (trigger.sound != null) return "🔔 " + shortName(trigger.sound);
        if (trigger.spriteTexture != null) return shortName(trigger.spriteTexture);
        if (trigger.setSpeed != null) return "⏱ speed=" + trigger.setSpeed;
        if (trigger.silence) return "🔇 " + trigger.type;
        if (trigger.despawn) return "✖ " + trigger.type;
        if (trigger.waypointGem) return "💎 gem";
        if (trigger.swapWeaponId != null) return "🔫 " + trigger.swapWeaponId;
        return trigger.type != null ? trigger.type : "🧩 (unlinked)";
    }

    private static String shortName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** The real sprite for whatever this trigger places, cropped to its first frame and sized to
     *  match its actual in-game footprint - null for an action with nothing pictorial to show
     *  (sound/despawn/silence/waypointGem/swapWeaponId), which falls back to a plain label chip.
     *  See EnemySpriteImages, shared with PlayerPreviewView's own enemy rendering. */
    private ImageView buildSprite() {
        if (trigger.spriteTexture != null) {
            return EnemySpriteImages.buildSpriteCueImage(trigger.spriteTexture, trigger.columns, trigger.rows, trigger.size);
        }
        if (trigger.type != null && trigger.sound == null && trigger.setSpeed == null && !trigger.silence
            && !trigger.despawn && !trigger.waypointGem && trigger.swapWeaponId == null) {
            return EnemySpriteImages.buildEnemyImage(canvas.getLibrary().findEnemy(trigger.type));
        }
        return null;
    }

    public void setSelected() {
        setBorder(new Border(new BorderStroke(Color.web("#ffd54a"), BorderStrokeStyle.SOLID,
            new CornerRadii(hasSprite ? 0 : 10), new BorderWidths(2))));
    }

    public void setUnselected() {
        setBorder(hasSprite ? null
            : new Border(new BorderStroke(Color.web("#54565e"), BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1))));
    }

    private void handlePressed(MouseEvent event) {
        dragStartMouseX = event.getSceneX();
        dragStartMouseY = event.getSceneY();
        dragStartLayoutX = getLayoutX();
        dragStartLayoutY = getLayoutY();
        dragged = false;
        // isShortcutDown() is the platform primary modifier (Ctrl on Windows/Linux, Cmd on macOS) -
        // held down toggles this node in/out of a multi-selection instead of replacing it, matching
        // the "Hold Ctrl to add/remove objects to the selection" convention this session's waypoint-
        // editor reference tool already used.
        //
        // A PLAIN (non-Ctrl) press on a node that's already part of the current selection is
        // deliberately NOT resolved here - selecting immediately would collapse a multi-selection down
        // to just this one node before beginGroupDrag() below ever gets a chance to see it as a group,
        // making a plain drag-to-move-the-group impossible (matches the standard file-explorer/
        // Photoshop-style convention: pressing an already-selected item preserves the whole selection
        // in case a drag follows; it only narrows to just this item on a plain click that turns out
        // NOT to be a drag - see handleReleased() below).
        boolean shortcutDown = event.isShortcutDown();
        deferSelectionCollapse = !shortcutDown && canvas.isSelected(this);
        if (!deferSelectionCollapse) {
            onSelect.accept(this, shortcutDown);
        } else {
            // select() itself grants focus as part of resolving a click (needed so an immediate
            // Delete/Backspace keypress works - see StageCanvas.select()'s own doc) - since resolving
            // is deferred here, grant it directly instead so a group-drag that skips select()
            // entirely (see handleReleased()) still leaves the canvas focused afterward.
            canvas.requestFocus();
        }
        // Snapshots every OTHER currently-selected node's layout position, if this press landed on a
        // node that's already part of a multi-selection - see StageCanvas.beginGroupDrag()'s own doc.
        // A no-op (clears to empty) otherwise, so plain single-node dragging below is unaffected.
        canvas.beginGroupDrag(this);
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
        syncTriggerFromLayout();
        // Moves every OTHER selected node by this same scene-pixel delta, if this was a group drag -
        // see StageCanvas.applyGroupDrag()'s own doc. A no-op otherwise.
        canvas.applyGroupDrag(dx, dy);
        event.consume();
    }

    private void handleReleased(MouseEvent event) {
        // markDirty() fires EditorDocument's change listeners, which includes
        // StageCanvas.rebuild() (registered in its constructor) - that already redraws every path
        // preview (see StageCanvas.drawPathPreviews()) against this trigger's new position, so
        // there's nothing extra to trigger here. Covers the whole group, not just this node - every
        // OTHER selected node's trigger.x/distance was already kept live-synced during the drag (see
        // canvas.applyGroupDrag()), so this one markDirty()/rebuild() picks up all of them at once.
        if (dragged) {
            canvas.getDocument().markDirty();
        } else if (deferSelectionCollapse) {
            // The deferred press from handlePressed() turned out NOT to be a drag - now it resolves
            // the same way an immediate plain click always has: replace the selection with just this
            // node.
            onSelect.accept(this, false);
        }
        canvas.endGroupDrag();
        event.consume();
    }

    /** Writes this node's current on-screen layout position back to trigger.x/distance - the inverse
     *  of updatePosition(). Shared by this node's own single-drag handling (handleDragged(), above)
     *  and StageCanvas.applyGroupDrag() (which calls this once per OTHER selected node it moves, via
     *  setLayoutX/Y first, so the math here is identical either way). */
    public void syncTriggerFromLayout() {
        trigger.x = canvas.canvasXToWorldX(getLayoutX() + getBoundsInLocal().getWidth() / 2);
        trigger.distance = canvas.canvasYToDistance(getLayoutY() + getBoundsInLocal().getHeight() / 2);
    }
}
