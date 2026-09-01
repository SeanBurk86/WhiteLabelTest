package whitelabeltest.editor;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import whitelabeltest.gamemanagers.trigger.Trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The draggable scrub bar shared by the Camera View and Player View tabs (see EditorApp) - one
 *  "current distance" drives both, so scrubbing keeps working no matter which of the two is
 *  currently showing. Sits below the tab area, always visible - an inert no-op while the Edit tab
 *  (which isn't distance-scoped) is the active one. Range auto-extends the same way
 *  StageCanvas.rebuild() derives its own max distance from the furthest trigger, so a newly-added
 *  trigger past the current end of the stage is always reachable. */
public class TimelineBar extends HBox {
    private static final double MIN_RANGE_UNITS = 160;

    private final Slider slider = new Slider();
    private final Label valueLabel = new Label();
    private final List<Consumer<Float>> listeners = new ArrayList<>();

    public TimelineBar(EditorDocument document) {
        setSpacing(8);
        setPadding(new Insets(6, 10, 6, 10));
        setStyle("-fx-background-color: #202126;");

        Label title = new Label("Camera distance:");
        title.setTextFill(Color.LIGHTGRAY);

        slider.setMin(0);
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.valueProperty().addListener((obs, was, value) -> {
            float v = value.floatValue();
            valueLabel.setText(String.format("%.1f", v));
            for (Consumer<Float> listener : listeners) listener.accept(v);
        });

        valueLabel.setTextFill(Color.WHITE);
        valueLabel.setMinWidth(48);

        getChildren().addAll(title, slider, valueLabel);

        document.addChangeListener(() -> refreshRange(document));
        refreshRange(document);
    }

    public void addListener(Consumer<Float> listener) {
        listeners.add(listener);
    }

    public float getValue() { return (float) slider.getValue(); }

    private void refreshRange(EditorDocument document) {
        double maxDistance = MIN_RANGE_UNITS;
        for (Trigger trigger : document.getTriggers()) {
            maxDistance = Math.max(maxDistance, trigger.distance + 10);
        }
        slider.setMax(maxDistance);
    }
}
