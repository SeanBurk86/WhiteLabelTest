package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.function.Consumer;

import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.gamemanagers.trigger.Trigger;

/** One placed Trigger's visual on the StageCanvas - positioned via
 *  StageCanvas.worldXToCanvasX(trigger.x)/distanceToCanvasY(trigger.distance) - see that class's doc
 *  for why the vertical axis goes through a conversion method rather than a flat multiply.
 *  Draggable within the canvas to reposition (writes trigger.x/distance back live), click to select
 *  - see StageCanvas's drag-drop (creation) vs this class's plain mouse drag (repositioning an
 *  existing node) for why these are two different mechanisms.
 *
 * An enemy spawn or sprite cue shows its ACTUAL sprite (cropped from the real texture, sized to
 * match the real in-game footprint - see buildIcon()) as the node's whole visible body, with just a
 * small id caption overlaid at the bottom, so the canvas reads as a real layout preview rather than
 * a diagram of abstract markers - the whole point being to judge spacing/overlap the way it'll
 * actually look on screen. A trigger with nothing pictorial to show (sound/despawn/silence/etc.)
 * falls back to a plain labeled chip. */
public class TriggerNode extends StackPane {
    // Sanity ceiling on the sprite's rendered size, in pixels - guards against a pathological
    // EnemyDefinition.size/Trigger.size value blowing up the layout; every real boss in
    // data/enemies.json today (size up to ~3.5) renders well under this at
    // StageCanvas.PIXELS_PER_UNIT_X scale.
    private static final double MAX_SPRITE_PIXELS = 320;
    private static final double MIN_SPRITE_PIXELS = 4;

    private final Trigger trigger;
    private final StageCanvas canvas;
    private final Consumer<TriggerNode> onSelect;
    private final Label label = new Label();
    private boolean hasSprite;

    private double dragStartMouseX, dragStartMouseY;
    private double dragStartLayoutX, dragStartLayoutY;
    private boolean dragged;

    public TriggerNode(Trigger trigger, StageCanvas canvas, Consumer<TriggerNode> onSelect) {
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
        setLayoutX(canvas.worldXToCanvasX(trigger.x) - getBoundsInLocal().getWidth() / 2);
        setLayoutY(canvas.distanceToCanvasY(trigger.distance) - getBoundsInLocal().getHeight() / 2);
    }

    /** Re-reads the trigger's action fields to refresh the label - call after a properties-panel
     *  edit changes which action this trigger performs or its key identifying fields (sound path,
     *  enemy type). Doesn't rebuild the sprite itself (see buildSprite()'s own doc on that) - the
     *  next full canvas rebuild (reload/save round trip) picks up a changed sprite. */
    public void refresh() {
        label.setText(describe());
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
     *  (sound/despawn/silence/waypointGem/swapWeaponId), which falls back to a plain label chip. */
    private ImageView buildSprite() {
        if (trigger.spriteTexture != null) return buildSpriteCueImage();
        if (trigger.type != null && trigger.sound == null && trigger.setSpeed == null && !trigger.silence
            && !trigger.despawn && !trigger.waypointGem && trigger.swapWeaponId == null) {
            return buildEnemyImage();
        }
        return null;
    }

    /** Same sizing rule GenericEnemy.initWithDefinition() uses in-game: def.size along the frame's
     *  longer axis, scaled down the shorter axis by its aspect ratio - converted to pixels at
     *  StageCanvas.PIXELS_PER_UNIT_X, the same scale the canvas's world-x axis uses, since
     *  width/height here are both real spatial world units unlike the distance axis. So a size=1
     *  grunt and a size=3.5 boss end up as visibly different on the canvas as they are on screen. */
    private ImageView buildEnemyImage() {
        EnemyDefinition def = canvas.getLibrary().findEnemy(trigger.type);
        if (def == null || def.texture == null) return null;
        Image sheet = loadImage(def.texture);
        if (sheet == null) return null;
        int columns = Math.max(def.columns, 1);
        int rows = Math.max(def.rows, 1);
        double frameW = sheet.getWidth() / columns;
        double frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return null;

        double aspect = frameW / frameH;
        double worldW = aspect >= 1f ? def.size : def.size * aspect;
        double worldH = aspect >= 1f ? def.size / aspect : def.size;
        return cropFrame(sheet, frameW, frameH, worldW * StageCanvas.PIXELS_PER_UNIT_X, worldH * StageCanvas.PIXELS_PER_UNIT_X);
    }

    /** Same sizing rule EnemySpawnOps.spawnSpriteCue() uses in-game: trigger.size is the drawn
     *  HEIGHT directly (not the longer-axis convention EnemyDefinition.size uses), width derived
     *  from the frame's own aspect ratio - see that method's own doc. */
    private ImageView buildSpriteCueImage() {
        Image sheet = loadImage(trigger.spriteTexture);
        if (sheet == null) return null;
        int columns = Math.max(trigger.columns, 1);
        int rows = Math.max(trigger.rows, 1);
        double frameW = sheet.getWidth() / columns;
        double frameH = sheet.getHeight() / rows;
        if (frameW <= 0 || frameH <= 0) return null;

        double worldH = trigger.size;
        double worldW = worldH * (frameW / frameH);
        return cropFrame(sheet, frameW, frameH, worldW * StageCanvas.PIXELS_PER_UNIT_X, worldH * StageCanvas.PIXELS_PER_UNIT_X);
    }

    private static Image loadImage(String texturePath) {
        String relative = texturePath.startsWith("images/") ? texturePath.substring("images/".length()) : texturePath;
        File file = new File("images", relative);
        if (!file.exists()) return null;
        return new Image(file.toURI().toString());
    }

    private static ImageView cropFrame(Image sheet, double frameW, double frameH, double fitWidth, double fitHeight) {
        ImageView view = new ImageView(sheet);
        view.setViewport(new javafx.geometry.Rectangle2D(0, 0, frameW, frameH));
        view.setFitWidth(clamp(fitWidth, MIN_SPRITE_PIXELS, MAX_SPRITE_PIXELS));
        view.setFitHeight(clamp(fitHeight, MIN_SPRITE_PIXELS, MAX_SPRITE_PIXELS));
        return view;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
        trigger.x = canvas.canvasXToWorldX(newLayoutX + getBoundsInLocal().getWidth() / 2);
        trigger.distance = canvas.canvasYToDistance(newLayoutY + getBoundsInLocal().getHeight() / 2);
        event.consume();
    }

    private void handleReleased(MouseEvent event) {
        if (dragged) canvas.getDocument().markDirty();
        event.consume();
    }
}
