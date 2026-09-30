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

/** A trigger on the StageCanvas, centered on (effectiveX, distance). Drag to move (writes
 *  x/distance back), click to select. Enemy spawns and sprite cues show their real sprite at
 *  in-game size with a small caption; other triggers show a labeled chip. Paths are drawn by the
 *  canvas. */
public class TriggerNode extends StackPane {
    private final Trigger trigger;
    private final StageCanvas canvas;
    // (node, Ctrl/Cmd held = toggle instead of replace).
    private final BiConsumer<TriggerNode, Boolean> onSelect;
    private final Label label = new Label();
    private boolean hasSprite;

    private double dragStartMouseX, dragStartMouseY;
    private double dragStartLayoutX, dragStartLayoutY;
    private boolean dragged;
    // A plain press on a selected node narrows the selection only on release without a drag.
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
        // Inside a Group the node's real size may only settle after its first layout pass (label
        // skins), so re-center whenever the bounds change.
        boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> updatePosition());
        autosize();
        updatePosition();
        setUnselected();

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::handlePressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleReleased);
        // Keep clicks from reaching the canvas, which would add a waypoint in path-edit mode.
        addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
    }

    public Trigger getTrigger() { return trigger; }

    /** Centers the node on the trigger's effectiveX/distance. */
    public void updatePosition() {
        setLayoutX(canvas.worldXToCanvasX(canvas.effectiveX(trigger)) - getBoundsInLocal().getWidth() / 2);
        setLayoutY(canvas.distanceToCanvasY(trigger.distance) - getBoundsInLocal().getHeight() / 2);
    }

    /** Updates the label (not the sprite) and re-autosizes, since the label sets the box size. */
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

    /** First frame of the enemy/sprite at in-game size, or null (label chip) for other actions. */
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
        // Ctrl/Cmd toggles. A plain press on an already-selected node keeps the selection so the
        // group can be dragged; it narrows to this node on release if there was no drag.
        boolean shortcutDown = event.isShortcutDown();
        deferSelectionCollapse = !shortcutDown && canvas.isSelected(this);
        if (!deferSelectionCollapse) {
            onSelect.accept(this, shortcutDown);
        } else {
            // select() is deferred, so grab focus for keyboard shortcuts here.
            canvas.requestFocus();
        }
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
        canvas.applyGroupDrag(dx, dy);
        event.consume();
    }

    private void handleReleased(MouseEvent event) {
        // markDirty() rebuilds the canvas (and previews) for the whole dragged group.
        if (dragged) {
            canvas.getDocument().markDirty();
        } else if (deferSelectionCollapse) {
            // The deferred press wasn't a drag: select just this node.
            onSelect.accept(this, false);
        }
        canvas.endGroupDrag();
        event.consume();
    }

    /** Writes the node's position back to trigger.x/distance (inverse of updatePosition()). */
    public void syncTriggerFromLayout() {
        trigger.x = canvas.canvasXToWorldX(getLayoutX() + getBoundsInLocal().getWidth() / 2);
        trigger.distance = canvas.canvasYToDistance(getLayoutY() + getBoundsInLocal().getHeight() / 2);
    }
}
