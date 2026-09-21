package whitelabeltest.editor;

import com.badlogic.gdx.utils.Array;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.stage.Window;
import whitelabeltest.enemy.EnemyDefinition;
import whitelabeltest.enemy.HitboxDef;

import java.util.ArrayList;
import java.util.List;

import static whitelabeltest.editor.FormControls.comboRow;
import static whitelabeltest.editor.FormControls.numberRow;
import static whitelabeltest.editor.FormControls.sectionLabel;

/** "Hitbox Editor" for one EnemyDefinition - a standalone window (same separate-Stage convention as
 *  FiringPatternEditorDialog) for placing the collision shapes described by EnemyDefinition.hitboxes /
 *  HitboxDef: any number of rectangles and circles over the enemy's sprite.
 *
 *  The sprite is drawn exactly as the game draws it - the real texture frame, sized by EnemyDefinition.size
 *  along its longer axis (the same rule as GenericEnemy.initWithDefinition() and EnemySpriteImages), the
 *  sheet sliced the way the game slices it, and the animation playable - at an adjustable scale, over a grid
 *  in world units, so a hitbox is placed against the art as it actually appears in play. A rotation slider
 *  turns the whole thing the way a rotating enemy turns (hitboxes rotate with the sprite around its centre,
 *  see EnemyHitboxes), and every drag/edit still works while it's turned.
 *
 *  Edits go straight onto the live EnemyDefinition (shared with the rest of the editor, like every other
 *  field in EnemyDefinitionPanel); "Save enemies.json" persists them. With no hitboxes the enemy keeps its
 *  default - one box the size of the whole sprite - drawn here as a dashed outline. */
final class HitboxEditorDialog {
    private static final double CANVAS_W = 640;
    private static final double CANVAS_H = 560;
    private static final double HANDLE = 5;
    private static final double MIN_SIZE = 0.02;
    private static final double SNAP = 0.025;
    // How far (pixels) a rectangle's rotate handle sits beyond its top edge, and the angle step it snaps to
    // with "Snap to grid" ticked or Shift held.
    private static final double ROTATE_OFFSET = 26;
    private static final double ROTATE_SNAP = 15;
    // Most pixels per world unit the zoom slider allows - enough to place a hitbox on a small sprite precisely.
    private static final double MAX_ZOOM = 700;

    private HitboxEditorDialog() {}

    static void show(Window owner, EnemyDefinition def, StageLibrary library, Runnable onChanged) {
        new Editor(def, library, onChanged).show(owner);
    }

    private static final class Editor {
        private final EnemyDefinition def;
        private final StageLibrary library;
        private final Runnable onChanged;

        private final Image sheet;
        private final int columns, rows, frameCount;
        private final double frameW, frameH;
        // The sprite's size in world units - GenericEnemy.initWithDefinition()'s sizing rule.
        private final double worldW, worldH;

        private final Canvas canvas = new Canvas(CANVAS_W, CANVAS_H);
        private final ListView<String> list = new ListView<>();
        private final VBox inspector = new VBox(6);
        private final Slider zoomSlider;
        private final Slider rotationSlider = new Slider(-180, 180, 0);
        private final Slider frameSlider;
        private final CheckBox animate = new CheckBox("Animate");
        private final CheckBox snap = new CheckBox("Snap to grid");
        private final Label status = new Label();
        private final AnimationTimer timer;

        private int selected = -1;
        private double pxPerUnit;
        private double animationTime;
        private boolean updatingList;

        // Drag state.
        private enum Drag { NONE, MOVE, RESIZE_RECT, RESIZE_CIRCLE, ROTATE }
        private Drag drag = Drag.NONE;
        private double pressFx, pressFy, pressBoxX, pressBoxY;
        private int handleX, handleY; // -1/0/1: which edge(s) of the rectangle are being dragged

        Editor(EnemyDefinition def, StageLibrary library, Runnable onChanged) {
            this.def = def;
            this.library = library;
            this.onChanged = onChanged;

            sheet = def.texture != null ? EnemySpriteImages.loadImage(def.texture) : null;
            frameCount = Math.max(def.frameCount, 1);
            columns = def.columns > 0 ? def.columns : frameCount;
            rows = Math.max(def.rows, 1);
            if (sheet != null) {
                frameW = sheet.getWidth() / columns;
                frameH = sheet.getHeight() / rows;
            } else {
                frameW = frameH = 1;
            }
            double aspect = frameW / frameH;
            worldW = aspect >= 1 ? def.size : def.size * aspect;
            worldH = aspect >= 1 ? def.size / aspect : def.size;

            // Fit the sprite to about 60% of the canvas to start with.
            double fit = Math.min(CANVAS_W * 0.6 / Math.max(worldW, 0.01), CANVAS_H * 0.6 / Math.max(worldH, 0.01));
            pxPerUnit = Math.max(10, Math.min(MAX_ZOOM, fit));
            zoomSlider = new Slider(10, MAX_ZOOM, pxPerUnit);
            frameSlider = new Slider(0, Math.max(frameCount - 1, 0), 0);
            frameSlider.setBlockIncrement(1);
            frameSlider.setMajorTickUnit(1);
            frameSlider.setMinorTickCount(0);
            frameSlider.setSnapToTicks(true);
            frameSlider.setDisable(frameCount <= 1);
            animate.setDisable(frameCount <= 1);

            timer = new AnimationTimer() {
                private long last = -1;
                @Override public void handle(long now) {
                    if (last >= 0) animationTime += (now - last) / 1e9;
                    last = now;
                    if (animate.isSelected()) redraw();
                }
            };
        }

        void show(Window owner) {
            Stage dialog = new Stage();
            if (owner != null) dialog.initOwner(owner);
            dialog.setTitle("Hitbox Editor - " + def.id);

            canvas.setFocusTraversable(true);
            canvas.setOnMousePressed(this::onPressed);
            canvas.setOnMouseDragged(this::onDragged);
            canvas.setOnMouseReleased(e -> onReleased());
            canvas.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) { deleteSelected(); e.consume(); }
                HitboxDef box = selectedBox();
                if (box == null) return;
                double step = e.isShiftDown() ? 0.05 : 0.01;
                boolean moved = true;
                switch (e.getCode()) {
                    case LEFT -> box.x -= step;
                    case RIGHT -> box.x += step;
                    case UP -> box.y += step;
                    case DOWN -> box.y -= step;
                    // Q / E turn a rectangle a degree at a time (Shift = 5).
                    case Q -> { if (!box.isCircle()) box.rotation = (float) normalizeDegrees(box.rotation + (e.isShiftDown() ? 5 : 1)); }
                    case E -> { if (!box.isCircle()) box.rotation = (float) normalizeDegrees(box.rotation - (e.isShiftDown() ? 5 : 1)); }
                    default -> moved = false;
                }
                if (moved) { changed(); rebuildInspector(); redraw(); e.consume(); }
            });

            zoomSlider.valueProperty().addListener((o, a, b) -> { pxPerUnit = b.doubleValue(); redraw(); });
            rotationSlider.valueProperty().addListener((o, a, b) -> redraw());
            frameSlider.valueProperty().addListener((o, a, b) -> redraw());
            animate.selectedProperty().addListener((o, a, b) -> { frameSlider.setDisable(b || frameCount <= 1); redraw(); });
            snap.setSelected(false);

            list.setPrefHeight(150);
            list.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
                if (updatingList) return;
                selected = b.intValue();
                rebuildInspector();
                redraw();
            });

            Button addRect = new Button("+ Rectangle");
            addRect.setOnAction(e -> addBox(HitboxDef.RECT));
            Button addCircle = new Button("+ Circle");
            addCircle.setOnAction(e -> addBox(HitboxDef.CIRCLE));
            Button duplicate = new Button("Duplicate");
            duplicate.setOnAction(e -> duplicateSelected(false));
            Button mirror = new Button("Mirror copy ↔");
            mirror.setOnAction(e -> duplicateSelected(true));
            Button delete = new Button("Delete");
            delete.setOnAction(e -> deleteSelected());
            Button reset = new Button("Reset to default (whole sprite)");
            reset.setOnAction(e -> { def.hitboxes = null; selected = -1; changed(); refreshAll(); });
            HBox addRow = new HBox(6, addRect, addCircle);
            HBox editRow = new HBox(6, duplicate, mirror, delete);

            Button save = new Button("Save enemies.json");
            save.setOnAction(e -> { library.saveEnemies(); status.setText("Saved."); });
            status.setTextFill(Color.LIGHTGREEN);
            status.setStyle("-fx-font-size: 10px;");

            Label hint = new Label("Click a shape to select it. Drag it to move it, drag its handles to resize. "
                + "Drag the yellow dot above a rectangle to rotate it (Shift or Snap = 15 degree steps; Q/E turn it a degree). "
                + "Arrow keys nudge (Shift = bigger steps), Delete removes. The dashed box is the sprite itself.");
            hint.setWrapText(true);
            hint.setTextFill(Color.LIGHTGRAY);
            hint.setStyle("-fx-font-size: 10px;");

            VBox view = new VBox(4,
                sectionLabel("View"),
                labeled("Zoom", zoomSlider),
                labeled("Rotation preview", rotationSlider),
                labeled("Frame", frameSlider),
                new HBox(10, animate, snap));

            VBox side = new VBox(8,
                sectionLabel("Hitboxes"),
                list, addRow, editRow, reset,
                sectionLabel("Selected"),
                inspector,
                view,
                hint,
                new HBox(8, save, status));
            side.setPadding(new Insets(8));
            ScrollPane sideScroll = new ScrollPane(side);
            sideScroll.setFitToWidth(true);
            sideScroll.setPrefWidth(310);

            BorderPane root = new BorderPane();
            root.setCenter(canvas);
            root.setRight(sideScroll);
            // Dark like the rest of the editor - the shared FormControls labels are light-on-dark.
            root.setStyle("-fx-base: #303136; -fx-background: #303136;");
            dialog.setScene(new Scene(root));
            dialog.setOnHidden(e -> { timer.stop(); onChanged.run(); });

            refreshAll();
            timer.start();
            dialog.show();
        }

        private static HBox labeled(String label, javafx.scene.Node control) {
            Label l = FormControls.fieldLabel(label);
            HBox row = new HBox(6, l, control);
            row.setStyle("-fx-alignment: center-left;");
            if (control instanceof Slider s) s.setPrefWidth(170);
            return row;
        }

        // ---------------------------------------------------------------- data

        private Array<HitboxDef> boxes() { return def.hitboxes; }

        private int boxCount() { return def.hitboxes == null ? 0 : def.hitboxes.size; }

        private HitboxDef selectedBox() {
            return selected >= 0 && selected < boxCount() ? def.hitboxes.get(selected) : null;
        }

        private void changed() {
            status.setText("");
        }

        private void addBox(String shape) {
            if (def.hitboxes == null) def.hitboxes = new Array<>();
            HitboxDef box = new HitboxDef();
            box.shape = shape;
            if (HitboxDef.CIRCLE.equals(shape)) box.radius = 0.25f; else { box.width = 0.5f; box.height = 0.5f; }
            def.hitboxes.add(box);
            selected = def.hitboxes.size - 1;
            changed();
            refreshAll();
        }

        private void duplicateSelected(boolean mirrored) {
            HitboxDef box = selectedBox();
            if (box == null) return;
            HitboxDef copy = box.copy();
            // Mirroring across the vertical axis flips a turn's direction too.
            if (mirrored) { copy.x = -copy.x; copy.rotation = -copy.rotation; }
            else { copy.x += 0.05f; copy.y -= 0.05f; }
            def.hitboxes.add(copy);
            selected = def.hitboxes.size - 1;
            changed();
            refreshAll();
        }

        private void deleteSelected() {
            if (selectedBox() == null) return;
            def.hitboxes.removeIndex(selected);
            if (def.hitboxes.size == 0) def.hitboxes = null; // back to the default whole-sprite box
            selected = Math.min(selected, boxCount() - 1);
            changed();
            refreshAll();
        }

        private void refreshAll() {
            updatingList = true;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < boxCount(); i++) {
                HitboxDef b = def.hitboxes.get(i);
                names.add((i + 1) + ". " + (b.isCircle() ? "Circle" : "Rectangle"));
            }
            list.getItems().setAll(names);
            if (selected >= 0 && selected < names.size()) list.getSelectionModel().select(selected);
            else list.getSelectionModel().clearSelection();
            updatingList = false;
            rebuildInspector();
            redraw();
        }

        private void rebuildInspector() {
            inspector.getChildren().clear();
            HitboxDef box = selectedBox();
            if (box == null) {
                inspector.getChildren().add(new Label(boxCount() == 0
                    ? "No custom hitboxes - this enemy uses one box the size of its whole sprite. Add one to start."
                    : "Nothing selected."));
                return;
            }
            inspector.getChildren().add(comboRow("Shape", List.of("rect", "circle"), box.shape, v -> {
                if (v.equals(box.shape)) return;
                if (HitboxDef.CIRCLE.equals(v)) box.radius = Math.min(box.width, box.height) / 2f;
                else { box.width = box.radius * 2f; box.height = box.radius * 2f; box.rotation = 0f; }
                box.shape = v;
                changed();
                refreshAll();
            }));
            inspector.getChildren().add(numberRow("X (fraction of width)", box.x, v -> { box.x = v; changed(); redraw(); }));
            inspector.getChildren().add(numberRow("Y (fraction of height, up)", box.y, v -> { box.y = v; changed(); redraw(); }));
            if (box.isCircle()) {
                inspector.getChildren().add(numberRow("Radius (fraction of shorter side)", box.radius, v -> {
                    box.radius = Math.max(v, (float) MIN_SIZE); changed(); redraw(); updateSizeInfo(box);
                }));
            } else {
                inspector.getChildren().add(numberRow("Rotation (degrees, counter-clockwise)", box.rotation, v -> {
                    box.rotation = (float) normalizeDegrees(v); changed(); redraw();
                }));
                inspector.getChildren().add(numberRow("Width (fraction)", box.width, v -> {
                    box.width = Math.max(v, (float) MIN_SIZE); changed(); redraw(); updateSizeInfo(box);
                }));
                inspector.getChildren().add(numberRow("Height (fraction)", box.height, v -> {
                    box.height = Math.max(v, (float) MIN_SIZE); changed(); redraw(); updateSizeInfo(box);
                }));
            }
            sizeInfo.setTextFill(Color.LIGHTGRAY);
            sizeInfo.setStyle("-fx-font-size: 10px;");
            inspector.getChildren().add(sizeInfo);
            updateSizeInfo(box);
        }

        private final Label sizeInfo = new Label();

        private void updateSizeInfo(HitboxDef box) {
            if (box.isCircle()) {
                double r = box.radius * Math.min(worldW, worldH);
                sizeInfo.setText(String.format("= radius %.2f world units (sprite is %.2f x %.2f)", r, worldW, worldH));
            } else {
                sizeInfo.setText(String.format("= %.2f x %.2f world units (sprite is %.2f x %.2f)",
                    box.width * worldW, box.height * worldH, worldW, worldH));
            }
        }

        // ------------------------------------------------------------- drawing

        private double spriteWPx() { return worldW * pxPerUnit; }
        private double spriteHPx() { return worldH * pxPerUnit; }

        private int currentFrame() {
            if (animate.isSelected() && frameCount > 1) {
                return (int) (animationTime / Math.max(def.frameDuration, 0.01f)) % frameCount;
            }
            return (int) Math.round(frameSlider.getValue());
        }

        private void redraw() {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setTransform(1, 0, 0, 1, 0, 0);
            gc.setFill(Color.rgb(28, 29, 34));
            gc.fillRect(0, 0, CANVAS_W, CANVAS_H);

            double cx = CANVAS_W / 2, cy = CANVAS_H / 2;
            drawGrid(gc, cx, cy);

            gc.save();
            gc.translate(cx, cy);
            gc.rotate(-rotationSlider.getValue()); // libGDX rotation is counter-clockwise; screen y points down.

            double w = spriteWPx(), h = spriteHPx();
            if (sheet != null) {
                int frame = Math.min(currentFrame(), frameCount - 1);
                double sx = (frame % columns) * frameW;
                double sy = (frame / columns) * frameH;
                gc.drawImage(sheet, sx, sy, frameW, frameH, -w / 2, -h / 2, w, h);
            } else {
                gc.setFill(Color.rgb(60, 60, 70));
                gc.fillRect(-w / 2, -h / 2, w, h);
                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(Font.font(11));
                gc.fillText("(no texture)", -30, 4);
            }

            // The sprite's own bounds.
            gc.setStroke(Color.rgb(150, 150, 160));
            gc.setLineWidth(1);
            gc.setLineDashes(5, 4);
            gc.strokeRect(-w / 2, -h / 2, w, h);
            gc.setLineDashes((double[]) null);

            if (boxCount() == 0) {
                // The default hitbox - one box exactly the sprite's size.
                gc.setStroke(Color.rgb(255, 90, 90, 0.9));
                gc.setLineWidth(2);
                gc.strokeRect(-w / 2, -h / 2, w, h);
                gc.setFill(Color.rgb(255, 90, 90, 0.10));
                gc.fillRect(-w / 2, -h / 2, w, h);
            }
            for (int i = 0; i < boxCount(); i++) drawBox(gc, def.hitboxes.get(i), i == selected);
            if (selectedBox() != null) drawHandles(gc, selectedBox());

            // Centre mark - the point a rotating enemy turns around.
            gc.setStroke(Color.rgb(255, 255, 255, 0.5));
            gc.setLineWidth(1);
            gc.strokeLine(-5, 0, 5, 0);
            gc.strokeLine(0, -5, 0, 5);
            gc.restore();
        }

        private void drawGrid(GraphicsContext gc, double cx, double cy) {
            // One line per world unit, centred on the sprite - a ruler for how big things really are in play.
            gc.setLineWidth(1);
            gc.setStroke(Color.rgb(50, 52, 60));
            for (double x = cx % pxPerUnit; x < CANVAS_W; x += pxPerUnit) gc.strokeLine(x, 0, x, CANVAS_H);
            for (double y = cy % pxPerUnit; y < CANVAS_H; y += pxPerUnit) gc.strokeLine(0, y, CANVAS_W, y);
            gc.setStroke(Color.rgb(75, 78, 90));
            gc.strokeLine(cx, 0, cx, CANVAS_H);
            gc.strokeLine(0, cy, CANVAS_W, cy);
            gc.setFill(Color.rgb(120, 124, 138));
            gc.setFont(Font.font(10));
            gc.fillText("grid = 1 world unit", 8, CANVAS_H - 8);
        }

        private void drawBox(GraphicsContext gc, HitboxDef box, boolean isSelected) {
            double w = spriteWPx(), h = spriteHPx();
            double cx = box.x * w, cy = -box.y * h;
            Color stroke = isSelected ? Color.rgb(255, 220, 60) : Color.rgb(255, 90, 90);
            gc.setStroke(stroke);
            gc.setLineWidth(isSelected ? 2.5 : 2);
            gc.setFill(isSelected ? Color.rgb(255, 220, 60, 0.20) : Color.rgb(255, 90, 90, 0.16));
            if (box.isCircle()) {
                double r = box.radius * Math.min(w, h);
                gc.fillOval(cx - r, cy - r, r * 2, r * 2);
                gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            } else {
                double bw = box.width * w, bh = box.height * h;
                // Turned about its own centre - libGDX rotation is counter-clockwise, the screen's is clockwise.
                gc.save();
                gc.translate(cx, cy);
                gc.rotate(-box.rotation);
                gc.fillRect(-bw / 2, -bh / 2, bw, bh);
                gc.strokeRect(-bw / 2, -bh / 2, bw, bh);
                gc.restore();
            }
        }

        /** Rotates the screen-space vector (vx, vy) - x right, y DOWN - by `deg` degrees counter-clockwise as the
         *  eye sees it. rotPx(v, box.rotation) takes a vector from the box's own frame into the sprite's; the
         *  negative angle takes it back. */
        private static double[] rotPx(double vx, double vy, double deg) {
            double a = Math.toRadians(deg);
            double cos = Math.cos(a), sin = Math.sin(a);
            return new double[] { vx * cos + vy * sin, -vx * sin + vy * cos };
        }

        /** The handle positions of `box` in the sprite's local pixel frame: for a rectangle its 4 corners and 4
         *  edge midpoints, turned with the box (with the (hx, hy) each one drags), for a circle one on its right
         *  edge. */
        private double[][] handles(HitboxDef box) {
            double w = spriteWPx(), h = spriteHPx();
            double cx = box.x * w, cy = -box.y * h;
            if (box.isCircle()) {
                double r = box.radius * Math.min(w, h);
                return new double[][] { { cx + r, cy, 1, 0 } };
            }
            double hw = box.width * w / 2, hh = box.height * h / 2;
            List<double[]> out = new ArrayList<>();
            for (int hx = -1; hx <= 1; hx++) {
                for (int hy = -1; hy <= 1; hy++) {
                    if (hx == 0 && hy == 0) continue;
                    // hy = +1 is the TOP edge (y up in sprite space) = smaller screen y.
                    double[] p = rotPx(hx * hw, -hy * hh, box.rotation);
                    out.add(new double[] { cx + p[0], cy + p[1], hx, hy });
                }
            }
            return out.toArray(new double[0][]);
        }

        /** Where the rotate handle of a rectangle sits: ROTATE_OFFSET pixels beyond the middle of its top edge, in
         *  the box's own frame, so it turns with the box. */
        private double[] rotateHandle(HitboxDef box) {
            double w = spriteWPx(), h = spriteHPx();
            double cx = box.x * w, cy = -box.y * h;
            double[] p = rotPx(0, -(box.height * h / 2 + ROTATE_OFFSET), box.rotation);
            return new double[] { cx + p[0], cy + p[1] };
        }

        private void drawHandles(GraphicsContext gc, HitboxDef box) {
            gc.setStroke(Color.rgb(20, 20, 20));
            gc.setLineWidth(1);
            if (!box.isCircle()) {
                // The rotate handle, joined to the top edge by a line.
                double w = spriteWPx(), h = spriteHPx();
                double cx = box.x * w, cy = -box.y * h;
                double[] top = rotPx(0, -box.height * h / 2, box.rotation);
                double[] rh = rotateHandle(box);
                gc.setStroke(Color.rgb(255, 220, 60));
                gc.strokeLine(cx + top[0], cy + top[1], rh[0], rh[1]);
                gc.setFill(Color.rgb(255, 220, 60));
                gc.fillOval(rh[0] - HANDLE - 1, rh[1] - HANDLE - 1, (HANDLE + 1) * 2, (HANDLE + 1) * 2);
                gc.setStroke(Color.rgb(20, 20, 20));
                gc.strokeOval(rh[0] - HANDLE - 1, rh[1] - HANDLE - 1, (HANDLE + 1) * 2, (HANDLE + 1) * 2);
            }
            gc.setFill(Color.WHITE);
            for (double[] hp : handles(box)) {
                gc.fillRect(hp[0] - HANDLE, hp[1] - HANDLE, HANDLE * 2, HANDLE * 2);
                gc.strokeRect(hp[0] - HANDLE, hp[1] - HANDLE, HANDLE * 2, HANDLE * 2);
            }
        }

        // ---------------------------------------------------------- interaction

        /** Mouse position in the sprite's local pixel frame (x right, y down, origin at the sprite's centre),
         *  undoing the preview rotation - so every edit below works in the sprite's own frame however far the
         *  preview is turned. */
        private Point2D toLocal(MouseEvent e) {
            double dx = e.getX() - CANVAS_W / 2, dy = e.getY() - CANVAS_H / 2;
            // A rotation is always invertible, so this can't fail - unlike the general Transform case.
            return new Rotate(-rotationSlider.getValue()).inverseTransform(dx, dy);
        }

        private double fractionX(Point2D local) { return local.getX() / spriteWPx(); }
        private double fractionY(Point2D local) { return -local.getY() / spriteHPx(); }

        private double snapped(double v) {
            return snap.isSelected() ? Math.round(v / SNAP) * SNAP : v;
        }

        private boolean contains(HitboxDef box, Point2D p) {
            double w = spriteWPx(), h = spriteHPx();
            double cx = box.x * w, cy = -box.y * h;
            if (box.isCircle()) {
                double r = box.radius * Math.min(w, h);
                return Math.hypot(p.getX() - cx, p.getY() - cy) <= r;
            }
            // In the box's own frame it's an axis-aligned rectangle again.
            double[] m = rotPx(p.getX() - cx, p.getY() - cy, -box.rotation);
            return Math.abs(m[0]) <= box.width * w / 2 && Math.abs(m[1]) <= box.height * h / 2;
        }

        private void onPressed(MouseEvent e) {
            canvas.requestFocus();
            Point2D p = toLocal(e);
            drag = Drag.NONE;

            // A handle of the selected shape wins over everything.
            HitboxDef sel = selectedBox();
            if (sel != null) {
                if (!sel.isCircle()) {
                    double[] rh = rotateHandle(sel);
                    if (Math.hypot(p.getX() - rh[0], p.getY() - rh[1]) <= HANDLE + 4) {
                        drag = Drag.ROTATE;
                        return;
                    }
                }
                for (double[] hp : handles(sel)) {
                    if (Math.abs(p.getX() - hp[0]) <= HANDLE + 2 && Math.abs(p.getY() - hp[1]) <= HANDLE + 2) {
                        drag = sel.isCircle() ? Drag.RESIZE_CIRCLE : Drag.RESIZE_RECT;
                        handleX = (int) hp[2];
                        handleY = (int) hp[3];
                        return;
                    }
                }
            }
            // Otherwise the topmost shape under the cursor (later ones draw over earlier ones).
            for (int i = boxCount() - 1; i >= 0; i--) {
                if (contains(def.hitboxes.get(i), p)) {
                    selected = i;
                    HitboxDef box = def.hitboxes.get(i);
                    pressFx = fractionX(p);
                    pressFy = fractionY(p);
                    pressBoxX = box.x;
                    pressBoxY = box.y;
                    drag = Drag.MOVE;
                    updatingList = true;
                    list.getSelectionModel().select(i);
                    updatingList = false;
                    rebuildInspector();
                    redraw();
                    return;
                }
            }
            selected = -1;
            updatingList = true;
            list.getSelectionModel().clearSelection();
            updatingList = false;
            rebuildInspector();
            redraw();
        }

        private void onDragged(MouseEvent e) {
            HitboxDef box = selectedBox();
            if (box == null || drag == Drag.NONE) return;
            Point2D p = toLocal(e);
            double fx = fractionX(p), fy = fractionY(p);
            double w = spriteWPx(), h = spriteHPx();

            switch (drag) {
                case MOVE -> {
                    box.x = (float) snapped(pressBoxX + (fx - pressFx));
                    box.y = (float) snapped(pressBoxY + (fy - pressFy));
                }
                case ROTATE -> {
                    // The handle sits straight "up" from the box's centre at rotation 0, so the box's rotation is
                    // the direction of the mouse from the centre (counter-clockwise, y up) minus 90 degrees.
                    double dx = p.getX() - box.x * w;
                    double dy = p.getY() + box.y * h;
                    double angle = Math.toDegrees(Math.atan2(-dy, dx)) - 90;
                    if (snap.isSelected() || e.isShiftDown()) angle = Math.round(angle / ROTATE_SNAP) * ROTATE_SNAP;
                    box.rotation = (float) normalizeDegrees(angle);
                }
                case RESIZE_CIRCLE -> {
                    double dxPx = p.getX() - box.x * w;
                    double dyPx = p.getY() + box.y * h;
                    double r = Math.hypot(dxPx, dyPx) / Math.min(w, h);
                    box.radius = (float) Math.max(MIN_SIZE, snapped(r));
                }
                case RESIZE_RECT -> {
                    // Work in the box's own frame (x right, y down, origin at its centre), where it's an
                    // axis-aligned rectangle again: move the grabbed edge(s) to the mouse, leave the opposite
                    // one(s) where they are, then put the new centre back in the sprite's frame.
                    double cx = box.x * w, cy = -box.y * h;
                    double[] m = rotPx(p.getX() - cx, p.getY() - cy, -box.rotation);
                    double left = -box.width * w / 2, right = box.width * w / 2;
                    double top = -box.height * h / 2, bottom = box.height * h / 2;
                    double minW = MIN_SIZE * w, minH = MIN_SIZE * h;
                    if (handleX < 0) left = Math.min(m[0], right - minW);
                    if (handleX > 0) right = Math.max(m[0], left + minW);
                    if (handleY > 0) top = Math.min(m[1], bottom - minH);   // hy = +1 is the top edge
                    if (handleY < 0) bottom = Math.max(m[1], top + minH);
                    double[] shift = rotPx((left + right) / 2, (top + bottom) / 2, box.rotation);
                    box.width = (float) ((right - left) / w);
                    box.height = (float) ((bottom - top) / h);
                    box.x = (float) ((cx + shift[0]) / w);
                    box.y = (float) (-(cy + shift[1]) / h);
                }
                default -> { }
            }
            changed();
            redraw();
        }

        /** Wraps an angle into (-180, 180]. */
        private static double normalizeDegrees(double deg) {
            double d = deg % 360;
            if (d > 180) d -= 360;
            if (d <= -180) d += 360;
            return d;
        }

        private void onReleased() {
            if (drag != Drag.NONE) rebuildInspector(); // the drag changed the numbers the fields show
            drag = Drag.NONE;
        }
    }
}
