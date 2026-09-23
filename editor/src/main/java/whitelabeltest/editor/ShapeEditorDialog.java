package whitelabeltest.editor;

import com.badlogic.gdx.utils.Json;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;
import whitelabeltest.enemy.BulletDef;
import whitelabeltest.enemy.FiringPatternDef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static whitelabeltest.editor.FormControls.fieldLabel;
import static whitelabeltest.editor.FormControls.sectionLabel;

/** "Shape Editor" for a Shape firing pattern's picture (FiringPatternDef.shapePoints) - a standalone
 *  window (same separate-Stage convention as HitboxEditorDialog) for drawing the dots instead of typing
 *  their coordinates.
 *
 *  The canvas is the picture's own frame: world units at scale 1, +y up, centered on the point the
 *  picture flies around - drawn the way the shape looks while travelling straight DOWN the screen, so its
 *  top points back at the boss (see ShapeFiring). Each dot is drawn at the pattern's real bullet size, so
 *  the gaps a player would have to thread are visible while drawing.
 *
 *  Edits happen on a working copy; Apply writes it into the pattern (and refreshes the pattern editor's
 *  preview through onApplied), Cancel/closing discards whatever wasn't applied. */
final class ShapeEditorDialog {
    private static final double CANVAS_W = 620;
    private static final double CANVAS_H = 620;
    private static final double GRID_FINE = 0.1;
    private static final double SNAP = 0.05;
    private static final double PICK_PX = 8;

    private ShapeEditorDialog() {}

    /** @param onApplied called with the new points (null = no dots) every time Apply is pressed */
    static void show(Window owner, FiringPatternDef def, java.util.function.Consumer<float[]> onApplied) {
        new Editor(def, onApplied).show(owner);
    }

    private static final class Dot {
        double x, y;
        boolean selected;
        Dot(double x, double y) { this.x = x; this.y = y; }
    }

    private enum Tool { SELECT, DOT, FREEHAND, LINE, CIRCLE, ERASE }

    private static final class Editor {
        private final FiringPatternDef def;
        private final java.util.function.Consumer<float[]> onApplied;
        private final double bulletSize;

        private final List<Dot> dots = new ArrayList<>();
        private final Deque<double[]> undo = new ArrayDeque<>();
        private final Deque<double[]> redo = new ArrayDeque<>();

        private final Canvas canvas = new Canvas(CANVAS_W, CANVAS_H);
        private final Slider zoomSlider = new Slider(30, 400, 120);
        private final Slider spacingSlider = new Slider(0.05, 0.6, 0.15);
        private final Label spacingValue = new Label();
        private final CheckBox snap = new CheckBox("Snap to grid");
        private final CheckBox mirrorDraw = new CheckBox("Mirror left/right while drawing");
        private final CheckBox showFormation = new CheckBox("Preview formation");
        private final Label status = new Label();
        private final AnimationTimer timer;

        private Tool tool = Tool.FREEHAND;
        private double pxPerUnit = 120;
        private boolean dirty;

        // Gesture state (canvas pixels unless noted).
        private boolean dragging;
        private double pressX, pressY, lastX, lastY;
        private double strokeX, strokeY;     // world position of the last dot a freehand stroke placed
        private boolean movingSelection;     // SELECT: dragging picked dots rather than box-selecting
        private double formationTime;

        Editor(FiringPatternDef def, java.util.function.Consumer<float[]> onApplied) {
            this.def = def;
            this.onApplied = onApplied;
            this.bulletSize = resolveBulletSize(def);
            if (def.shapePoints != null) {
                for (int i = 0; i + 1 < def.shapePoints.length; i += 2) dots.add(new Dot(def.shapePoints[i], def.shapePoints[i + 1]));
            }
            fitZoom();
            timer = new AnimationTimer() {
                private long last = -1;
                @Override public void handle(long now) {
                    if (last >= 0 && showFormation.isSelected()) {
                        formationTime += (now - last) / 1e9;
                        redraw();
                    }
                    last = now;
                }
            };
        }

        void show(Window owner) {
            Stage stage = new Stage();
            if (owner != null) stage.initOwner(owner);
            stage.setTitle("Shape Editor - " + (def.id != null ? def.id : "Shape pattern"));

            canvas.setFocusTraversable(true);
            canvas.setOnMousePressed(this::onPressed);
            canvas.setOnMouseDragged(this::onDragged);
            canvas.setOnMouseReleased(this::onReleased);
            canvas.setOnMouseMoved(e -> { lastX = e.getX(); lastY = e.getY(); if (tool == Tool.ERASE) redraw(); });
            canvas.setOnScroll(this::onScroll);
            canvas.setOnKeyPressed(e -> {
                boolean ctrl = e.isShortcutDown();
                double step = e.isShiftDown() ? 0.1 : 0.02;
                switch (e.getCode()) {
                    case DELETE, BACK_SPACE -> deleteSelected();
                    case Z -> { if (ctrl) { if (e.isShiftDown()) redoStep(); else undoStep(); } }
                    case Y -> { if (ctrl) redoStep(); }
                    case A -> { if (ctrl) { dots.forEach(d -> d.selected = true); redraw(); } }
                    case LEFT -> nudge(-step, 0);
                    case RIGHT -> nudge(step, 0);
                    case UP -> nudge(0, step);
                    case DOWN -> nudge(0, -step);
                    case ESCAPE -> { dots.forEach(d -> d.selected = false); redraw(); }
                    default -> { return; }
                }
                e.consume();
            });

            zoomSlider.setValue(pxPerUnit);
            zoomSlider.valueProperty().addListener((o, a, b) -> { pxPerUnit = b.doubleValue(); redraw(); });
            spacingSlider.valueProperty().addListener((o, a, b) -> { spacingValue.setText(String.format("%.2f", b.doubleValue())); });
            spacingValue.setText(String.format("%.2f", spacingSlider.getValue()));
            spacingValue.setTextFill(Color.LIGHTGRAY);
            showFormation.selectedProperty().addListener((o, a, b) -> { formationTime = 0; redraw(); });
            snap.setSelected(true);

            ToggleGroup tools = new ToggleGroup();
            FlowPane toolRow = new FlowPane(6, 6);
            addTool(toolRow, tools, "Select / move", Tool.SELECT);
            addTool(toolRow, tools, "Dot", Tool.DOT);
            addTool(toolRow, tools, "Freehand", Tool.FREEHAND);
            addTool(toolRow, tools, "Line", Tool.LINE);
            addTool(toolRow, tools, "Circle", Tool.CIRCLE);
            addTool(toolRow, tools, "Erase", Tool.ERASE);

            FlowPane editRow = new FlowPane(6, 6,
                button("Undo", this::undoStep),
                button("Redo", this::redoStep),
                button("Select all", () -> { dots.forEach(d -> d.selected = true); redraw(); }),
                button("Delete selected", this::deleteSelected),
                button("Clear", () -> { if (!dots.isEmpty()) { pushUndo(); dots.clear(); changed(); } }));
            FlowPane transformRow = new FlowPane(6, 6,
                button("Center", this::centerShape),
                button("Mirror ↔", () -> transform(-1, 0, 0, 1)),
                button("Flip ↕", () -> transform(1, 0, 0, -1)),
                button("Rotate -15°", () -> rotate(-15)),
                button("Rotate +15°", () -> rotate(15)),
                button("Scale 90%", () -> transform(0.9, 0, 0, 0.9)),
                button("Scale 110%", () -> transform(1.1, 0, 0, 1.1)),
                button("Remove overlaps", this::removeOverlaps));

            Button apply = new Button("Apply");
            apply.setDefaultButton(true);
            apply.setOnAction(e -> applyChanges());
            Button applyClose = new Button("Apply & close");
            applyClose.setOnAction(e -> { applyChanges(); stage.close(); });
            Button cancel = new Button("Close");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> { if (confirmDiscard(stage)) stage.close(); });
            status.setTextFill(Color.LIGHTGREEN);
            status.setStyle("-fx-font-size: 10px;");

            Label hint = new Label("Draw the picture as it should look flying straight DOWN the screen - its top faces the boss. "
                + "Freehand, Line and Circle place dots every 'Dot spacing' units. Select: click a dot (Shift adds), drag dots to "
                + "move them, drag on empty space to box-select. Arrow keys nudge (Shift = bigger), Delete removes, Ctrl+Z / "
                + "Ctrl+Y undo/redo. Scroll to zoom. The cross marks the point the picture flies around; transforms act on the "
                + "selection, or on every dot when nothing is selected.");
            hint.setWrapText(true);
            hint.setTextFill(Color.LIGHTGRAY);
            hint.setStyle("-fx-font-size: 10px;");

            VBox side = new VBox(8,
                sectionLabel("Tools"), toolRow,
                labeled("Dot spacing", new HBox(6, spacingSlider, spacingValue)),
                snap, mirrorDraw,
                sectionLabel("Edit"), editRow,
                sectionLabel("Transform"), transformRow,
                sectionLabel("View"),
                labeled("Zoom", zoomSlider),
                showFormation,
                hint,
                new HBox(8, apply, applyClose, cancel),
                status);
            side.setPadding(new Insets(8));
            ScrollPane sideScroll = new ScrollPane(side);
            sideScroll.setFitToWidth(true);
            sideScroll.setPrefWidth(320);

            BorderPane root = new BorderPane();
            root.setCenter(canvas);
            root.setRight(sideScroll);
            root.setStyle("-fx-base: #303136; -fx-background: #303136;");
            stage.setScene(new Scene(root));
            stage.setOnCloseRequest(e -> { if (!confirmDiscard(stage)) e.consume(); });
            stage.setOnHidden(e -> timer.stop());

            redraw();
            timer.start();
            stage.show();
            canvas.requestFocus();
        }

        private void addTool(FlowPane row, ToggleGroup group, String label, Tool value) {
            ToggleButton b = new ToggleButton(label);
            b.setToggleGroup(group);
            b.setSelected(value == tool);
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true); // one tool is always active
                tool = value;
                redraw();
                canvas.requestFocus();
            });
            row.getChildren().add(b);
        }

        private Button button(String label, Runnable action) {
            Button b = new Button(label);
            b.setOnAction(e -> { action.run(); canvas.requestFocus(); });
            return b;
        }

        private static HBox labeled(String label, javafx.scene.Node control) {
            HBox row = new HBox(6, fieldLabel(label), control);
            row.setStyle("-fx-alignment: center-left;");
            if (control instanceof Slider s) s.setPrefWidth(170);
            return row;
        }

        // ------------------------------------------------------------ coordinates

        private double toScreenX(double x) { return CANVAS_W / 2 + x * pxPerUnit; }
        private double toScreenY(double y) { return CANVAS_H / 2 - y * pxPerUnit; }
        private double toWorldX(double sx) { return (sx - CANVAS_W / 2) / pxPerUnit; }
        private double toWorldY(double sy) { return (CANVAS_H / 2 - sy) / pxPerUnit; }
        private double snapped(double v) { return snap.isSelected() ? Math.round(v / SNAP) * SNAP : v; }
        private double spacing() { return spacingSlider.getValue(); }

        /** Zooms so the existing picture fills about 70% of the canvas. */
        private void fitZoom() {
            double extent = 1.0;
            for (Dot d : dots) extent = Math.max(extent, Math.max(Math.abs(d.x), Math.abs(d.y)));
            pxPerUnit = Math.max(30, Math.min(400, CANVAS_W * 0.35 / extent));
        }

        private Dot dotAt(double sx, double sy) {
            Dot best = null;
            double bestDist = Math.max(PICK_PX, bulletSize * pxPerUnit / 2);
            for (Dot d : dots) {
                double dist = Math.hypot(toScreenX(d.x) - sx, toScreenY(d.y) - sy);
                if (dist <= bestDist) { best = d; bestDist = dist; }
            }
            return best;
        }

        // ------------------------------------------------------------ mouse

        private void onPressed(MouseEvent e) {
            canvas.requestFocus();
            if (e.getButton() != MouseButton.PRIMARY) return;
            dragging = true;
            pressX = lastX = e.getX();
            pressY = lastY = e.getY();
            double wx = snapped(toWorldX(pressX)), wy = snapped(toWorldY(pressY));
            switch (tool) {
                case SELECT -> {
                    Dot hit = dotAt(pressX, pressY);
                    if (hit != null) {
                        if (e.isShiftDown() || e.isShortcutDown()) hit.selected = !hit.selected;
                        else if (!hit.selected) { dots.forEach(d -> d.selected = false); hit.selected = true; }
                        movingSelection = hit.selected;
                        if (movingSelection) pushUndo();
                    } else {
                        movingSelection = false;
                        if (!e.isShiftDown() && !e.isShortcutDown()) dots.forEach(d -> d.selected = false);
                    }
                }
                case DOT -> { pushUndo(); addDot(wx, wy); }
                case FREEHAND -> {
                    pushUndo();
                    addDot(wx, wy);
                    strokeX = wx;
                    strokeY = wy;
                }
                case ERASE -> { pushUndo(); eraseAt(pressX, pressY); }
                default -> {}
            }
            redraw();
        }

        private void onDragged(MouseEvent e) {
            if (!dragging) return;
            double x = e.getX(), y = e.getY();
            switch (tool) {
                case SELECT -> {
                    if (movingSelection) {
                        double dx = (x - lastX) / pxPerUnit, dy = -(y - lastY) / pxPerUnit;
                        for (Dot d : dots) if (d.selected) { d.x += dx; d.y += dy; }
                        dirty = true;
                    }
                }
                case FREEHAND -> {
                    // Walk the pointer's path and drop a dot every `spacing` world units along it.
                    double wx = toWorldX(x), wy = toWorldY(y);
                    double segX = wx - strokeX, segY = wy - strokeY;
                    double len = Math.hypot(segX, segY);
                    double s = spacing();
                    while (len >= s) {
                        strokeX += segX / len * s;
                        strokeY += segY / len * s;
                        addDot(snapped(strokeX), snapped(strokeY));
                        segX = wx - strokeX;
                        segY = wy - strokeY;
                        len = Math.hypot(segX, segY);
                    }
                }
                case ERASE -> eraseAt(x, y);
                default -> {}
            }
            lastX = x;
            lastY = y;
            redraw();
        }

        private void onReleased(MouseEvent e) {
            if (!dragging) return;
            dragging = false;
            double x = e.getX(), y = e.getY();
            switch (tool) {
                case SELECT -> {
                    if (!movingSelection && Math.hypot(x - pressX, y - pressY) > 3) {
                        double minX = Math.min(pressX, x), maxX = Math.max(pressX, x);
                        double minY = Math.min(pressY, y), maxY = Math.max(pressY, y);
                        for (Dot d : dots) {
                            double sx = toScreenX(d.x), sy = toScreenY(d.y);
                            if (sx >= minX && sx <= maxX && sy >= minY && sy <= maxY) d.selected = true;
                        }
                    }
                    if (movingSelection && snap.isSelected()) for (Dot d : dots) if (d.selected) { d.x = snapped(d.x); d.y = snapped(d.y); }
                    if (movingSelection) changed();
                }
                case LINE -> {
                    double ax = snapped(toWorldX(pressX)), ay = snapped(toWorldY(pressY));
                    double bx = snapped(toWorldX(x)), by = snapped(toWorldY(y));
                    pushUndo();
                    int n = Math.max(1, (int) Math.round(Math.hypot(bx - ax, by - ay) / spacing()));
                    for (int i = 0; i <= n; i++) addDot(ax + (bx - ax) * i / n, ay + (by - ay) * i / n);
                }
                case CIRCLE -> {
                    double cx = snapped(toWorldX(pressX)), cy = snapped(toWorldY(pressY));
                    double r = Math.hypot(toWorldX(x) - toWorldX(pressX), toWorldY(y) - toWorldY(pressY));
                    if (r > 0.02) {
                        pushUndo();
                        int n = Math.max(3, (int) Math.round(2 * Math.PI * r / spacing()));
                        for (int i = 0; i < n; i++) {
                            double a = Math.PI / 2 + 2 * Math.PI * i / n;
                            addDot(cx + r * Math.cos(a), cy + r * Math.sin(a));
                        }
                    }
                }
                default -> {}
            }
            changed();
        }

        private void onScroll(ScrollEvent e) {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            zoomSlider.setValue(Math.max(zoomSlider.getMin(), Math.min(zoomSlider.getMax(), pxPerUnit * factor)));
            e.consume();
        }

        // ------------------------------------------------------------ edits

        /** Adds a dot, plus its left/right mirror image when "Mirror while drawing" is on (unless the dot
         *  sits on the center line, where the mirror would just double it up). */
        private void addDot(double x, double y) {
            dots.add(new Dot(x, y));
            if (mirrorDraw.isSelected() && Math.abs(x) > spacing() / 4) dots.add(new Dot(-x, y));
            dirty = true;
        }

        private void eraseAt(double sx, double sy) {
            double radius = eraseRadiusPx();
            if (dots.removeIf(d -> Math.hypot(toScreenX(d.x) - sx, toScreenY(d.y) - sy) <= radius)) dirty = true;
        }

        private double eraseRadiusPx() { return Math.max(10, spacing() * pxPerUnit * 0.6); }

        private void deleteSelected() {
            if (dots.stream().noneMatch(d -> d.selected)) return;
            pushUndo();
            dots.removeIf(d -> d.selected);
            changed();
        }

        private void nudge(double dx, double dy) {
            List<Dot> targets = targets();
            if (targets.isEmpty()) return;
            pushUndo();
            for (Dot d : targets) { d.x += dx; d.y += dy; }
            changed();
        }

        /** The selection, or every dot when nothing is selected. */
        private List<Dot> targets() {
            List<Dot> selected = dots.stream().filter(d -> d.selected).toList();
            return selected.isEmpty() ? dots : selected;
        }

        /** Applies a 2x2 linear transform around the targets' own center (their bounding-box middle). */
        private void transform(double a, double b, double c, double d) {
            List<Dot> targets = targets();
            if (targets.isEmpty()) return;
            pushUndo();
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Dot p : targets) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
            // The whole picture turns/scales around the flight center; a partial selection around its own middle.
            double cx = targets == dots ? 0 : (minX + maxX) / 2, cy = targets == dots ? 0 : (minY + maxY) / 2;
            for (Dot p : targets) {
                double x = p.x - cx, y = p.y - cy;
                p.x = cx + a * x + b * y;
                p.y = cy + c * x + d * y;
            }
            changed();
        }

        private void rotate(double degrees) {
            double r = Math.toRadians(degrees);
            transform(Math.cos(r), -Math.sin(r), Math.sin(r), Math.cos(r));
        }

        /** Moves the whole picture so its bounding-box middle sits on the flight center. */
        private void centerShape() {
            if (dots.isEmpty()) return;
            pushUndo();
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Dot p : dots) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y); }
            double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
            for (Dot p : dots) { p.x -= cx; p.y -= cy; }
            changed();
        }

        /** Drops any dot closer than half the dot spacing to one kept before it. */
        private void removeOverlaps() {
            double min = spacing() / 2;
            List<Dot> kept = new ArrayList<>();
            for (Dot d : dots) {
                boolean clash = false;
                for (Dot k : kept) if (Math.hypot(k.x - d.x, k.y - d.y) < min) { clash = true; break; }
                if (!clash) kept.add(d);
            }
            if (kept.size() == dots.size()) { status.setText("No overlapping dots."); return; }
            pushUndo();
            int removed = dots.size() - kept.size();
            dots.clear();
            dots.addAll(kept);
            changed();
            status.setText("Removed " + removed + " overlapping dot" + (removed == 1 ? "" : "s") + ".");
        }

        // ------------------------------------------------------------ undo / apply

        private double[] snapshot() {
            double[] s = new double[dots.size() * 2];
            for (int i = 0; i < dots.size(); i++) { s[i * 2] = dots.get(i).x; s[i * 2 + 1] = dots.get(i).y; }
            return s;
        }

        private void restore(double[] s) {
            dots.clear();
            for (int i = 0; i + 1 < s.length; i += 2) dots.add(new Dot(s[i], s[i + 1]));
        }

        private void pushUndo() {
            undo.push(snapshot());
            if (undo.size() > 200) undo.removeLast();
            redo.clear();
        }

        private void undoStep() {
            if (undo.isEmpty()) return;
            redo.push(snapshot());
            restore(undo.pop());
            changed();
        }

        private void redoStep() {
            if (redo.isEmpty()) return;
            undo.push(snapshot());
            restore(redo.pop());
            changed();
        }

        private void changed() {
            dirty = true;
            status.setText(dots.size() + " dots - not applied yet");
            redraw();
        }

        private void applyChanges() {
            float[] points = null;
            if (!dots.isEmpty()) {
                points = new float[dots.size() * 2];
                for (int i = 0; i < dots.size(); i++) {
                    points[i * 2] = round3(dots.get(i).x);
                    points[i * 2 + 1] = round3(dots.get(i).y);
                }
            }
            onApplied.accept(points);
            dirty = false;
            status.setText("Applied " + dots.size() + " dots.");
        }

        private static float round3(double v) { return Math.round(v * 1000) / 1000f; }

        /** True if it's fine to close: nothing unapplied, or the user agreed to throw it away. */
        private boolean confirmDiscard(Stage stage) {
            if (!dirty) return true;
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Close without applying your changes to the shape?", javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
            alert.initOwner(stage);
            alert.setHeaderText("Unapplied changes");
            return alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK;
        }

        // ------------------------------------------------------------ drawing

        private void redraw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setFill(Color.rgb(18, 18, 24));
            g.fillRect(0, 0, CANVAS_W, CANVAS_H);
            drawGrid(g);
            drawGuides(g);

            double r = Math.max(2, bulletSize * pxPerUnit / 2);
            if (showFormation.isSelected()) {
                drawFormation(g, r);
            } else {
                for (Dot d : dots) {
                    double sx = toScreenX(d.x), sy = toScreenY(d.y);
                    g.setFill(d.selected ? Color.rgb(255, 210, 80) : Color.rgb(90, 120, 255));
                    g.fillOval(sx - r, sy - r, r * 2, r * 2);
                    if (d.selected) {
                        g.setStroke(Color.WHITE);
                        g.setLineWidth(1);
                        g.strokeOval(sx - r - 2, sy - r - 2, r * 2 + 4, r * 2 + 4);
                    }
                }
            }
            drawGesture(g);

            g.setFill(Color.LIGHTGRAY);
            g.setFont(Font.font(11));
            g.fillText(dots.size() + " dots   dot size " + String.format("%.2f", bulletSize) + "   grid 0.1 / 1.0 units", 8, CANVAS_H - 8);
        }

        private void drawGrid(GraphicsContext g) {
            double left = toWorldX(0), right = toWorldX(CANVAS_W), top = toWorldY(0), bottom = toWorldY(CANVAS_H);
            for (double x = Math.floor(left / GRID_FINE) * GRID_FINE; x <= right; x += GRID_FINE) {
                boolean major = Math.abs(x - Math.round(x)) < 1e-6;
                g.setStroke(major ? Color.rgb(70, 72, 84) : Color.rgb(34, 35, 44));
                g.setLineWidth(1);
                double sx = Math.round(toScreenX(x)) + 0.5;
                g.strokeLine(sx, 0, sx, CANVAS_H);
            }
            for (double y = Math.floor(bottom / GRID_FINE) * GRID_FINE; y <= top; y += GRID_FINE) {
                boolean major = Math.abs(y - Math.round(y)) < 1e-6;
                g.setStroke(major ? Color.rgb(70, 72, 84) : Color.rgb(34, 35, 44));
                double sy = Math.round(toScreenY(y)) + 0.5;
                g.strokeLine(0, sy, CANVAS_W, sy);
            }
        }

        /** The flight center cross plus "toward the boss" / "direction of travel" arrows. */
        private void drawGuides(GraphicsContext g) {
            double cx = toScreenX(0), cy = toScreenY(0);
            g.setStroke(Color.ORANGE);
            g.setLineWidth(2);
            g.strokeLine(cx - 8, cy, cx + 8, cy);
            g.strokeLine(cx, cy - 8, cx, cy + 8);

            g.setStroke(Color.rgb(255, 150, 60, 0.8));
            g.setLineCap(StrokeLineCap.ROUND);
            g.strokeLine(CANVAS_W - 26, 60, CANVAS_W - 26, 18);
            g.strokeLine(CANVAS_W - 26, 18, CANVAS_W - 32, 26);
            g.strokeLine(CANVAS_W - 26, 18, CANVAS_W - 20, 26);
            g.setStroke(Color.rgb(120, 220, 140, 0.8));
            g.strokeLine(CANVAS_W - 26, CANVAS_H - 60, CANVAS_W - 26, CANVAS_H - 18);
            g.strokeLine(CANVAS_W - 26, CANVAS_H - 18, CANVAS_W - 32, CANVAS_H - 26);
            g.strokeLine(CANVAS_W - 26, CANVAS_H - 18, CANVAS_W - 20, CANVAS_H - 26);
            g.setFont(Font.font(11));
            g.setFill(Color.rgb(255, 150, 60));
            g.fillText("toward the boss", CANVAS_W - 118, 16);
            g.setFill(Color.rgb(120, 220, 140));
            g.fillText("direction of travel", CANVAS_W - 130, CANVAS_H - 30);
        }

        /** Rubber band / line / circle / eraser feedback for the gesture in progress. */
        private void drawGesture(GraphicsContext g) {
            g.setLineWidth(1);
            if (tool == Tool.ERASE) {
                double r = eraseRadiusPx();
                g.setStroke(Color.rgb(255, 90, 90, 0.8));
                g.strokeOval(lastX - r, lastY - r, r * 2, r * 2);
            }
            if (!dragging) return;
            switch (tool) {
                case SELECT -> {
                    if (movingSelection) return;
                    g.setStroke(Color.rgb(255, 210, 80, 0.9));
                    g.setLineDashes(4);
                    g.strokeRect(Math.min(pressX, lastX), Math.min(pressY, lastY), Math.abs(lastX - pressX), Math.abs(lastY - pressY));
                    g.setLineDashes((double[]) null);
                }
                case LINE -> {
                    g.setStroke(Color.rgb(255, 255, 255, 0.6));
                    g.strokeLine(toScreenX(snapped(toWorldX(pressX))), toScreenY(snapped(toWorldY(pressY))), lastX, lastY);
                }
                case CIRCLE -> {
                    double r = Math.hypot(lastX - pressX, lastY - pressY);
                    double cx = toScreenX(snapped(toWorldX(pressX))), cy = toScreenY(snapped(toWorldY(pressY)));
                    g.setStroke(Color.rgb(255, 255, 255, 0.6));
                    g.strokeOval(cx - r, cy - r, r * 2, r * 2);
                }
                default -> {}
            }
        }

        /** Plays one volley's spread the way ShapeBullet does it (travel along the flight path left out, so
         *  the picture stays centered): every dot starts on the center, lands on its place at the form time
         *  and scale, then drifts on. The drawn dots are ghosted behind the animation for reference. */
        private void drawFormation(GraphicsContext g, double r) {
            double formScale = def.shapeScale > 0 ? def.shapeScale : 1;
            double formTime = def.shapeFormTime;
            double loop = formTime > 0 ? formTime * 2.2 + 0.6 : 2;
            double t = formationTime % loop;
            double spread;
            if (formTime <= 0) {
                spread = formScale;
            } else {
                double ratio = Math.max(0, Math.min(2, def.shapeDriftRatio));
                double u = (2 - ratio) * formScale / formTime, a = 2 * (ratio - 1) * formScale / (formTime * formTime);
                spread = t < formTime ? u * t + 0.5 * a * t * t : formScale + ratio * formScale / formTime * (t - formTime);
            }
            g.setFill(Color.rgb(90, 120, 255, 0.18));
            for (Dot d : dots) g.fillOval(toScreenX(d.x * formScale) - r, toScreenY(d.y * formScale) - r, r * 2, r * 2);
            boolean formed = formTime <= 0 || Math.abs(t - formTime) < 0.08;
            g.setFill(formed ? Color.rgb(120, 220, 140) : Color.rgb(90, 160, 255));
            for (Dot d : dots) g.fillOval(toScreenX(d.x * spread) - r, toScreenY(d.y * spread) - r, r * 2, r * 2);
            g.setFill(Color.LIGHTGRAY);
            g.setFont(Font.font(11));
            g.fillText(String.format("t = %.2fs   form time %.2fs   scale when formed %.2f", t, formTime, formScale), 8, 16);
        }

        // ------------------------------------------------------------ helpers

        /** The bullet size ShapeFiring will actually draw - the pattern's own bulletSize, else its bulletId's
         *  (read straight from data/bullets.json the way FiringPatternPreviewCanvas does), else 0.25. */
        private static double resolveBulletSize(FiringPatternDef def) {
            if (def.bulletSize > 0) return def.bulletSize;
            if (def.bulletId != null) {
                try {
                    Path path = Path.of("data/bullets.json");
                    if (Files.exists(path)) {
                        @SuppressWarnings("unchecked")
                        com.badlogic.gdx.utils.Array<BulletDef> defs = new Json().fromJson(com.badlogic.gdx.utils.Array.class, BulletDef.class, Files.readString(path));
                        for (BulletDef b : defs) if (def.bulletId.equals(b.id) && b.bulletSize > 0) return b.bulletSize;
                    }
                } catch (Exception ignored) {
                    // fall through to the default rather than refusing to open the editor
                }
            }
            return 0.25;
        }
    }
}
