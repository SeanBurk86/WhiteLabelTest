package whitelabeltest.editor;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Consumer;

/** Small field-row builders shared by PropertiesPanel (edits a placed Trigger) and
 *  EnemyDefinitionPanel (edits an EnemyDefinition's own template stats) - same "label + control,
 *  commit on Enter/focus-lost" style for both, so the two panels read as one consistent editor
 *  instead of two differently-behaved forms. */
public final class FormControls {
    private FormControls() {}

    public static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setWrapText(true);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    public static HBox textRow(String label, String initial, Consumer<String> onCommit) {
        Label l = fieldLabel(label);
        TextField field = new TextField(initial != null ? initial : "");
        field.setOnAction(e -> onCommit.accept(field.getText()));
        field.focusedProperty().addListener((obs, was, is) -> { if (!is) onCommit.accept(field.getText()); });
        HBox row = new HBox(6, l, field);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    public static HBox numberRow(String label, float initial, Consumer<Float> onCommit) {
        return numberRowNullable(label, initial, onCommit);
    }

    public static HBox numberRow(String label, int initial, Consumer<Float> onCommit) {
        return numberRow(label, (float) initial, onCommit);
    }

    /** Same as numberRow but treats a non-numeric/empty entry as NaN (used for fields whose NaN
     *  default carries meaning, e.g. Trigger.offsetX/offsetY) rather than refusing the edit. */
    public static HBox numberRowNullable(String label, float initial, Consumer<Float> onCommit) {
        Label l = fieldLabel(label);
        TextField field = new TextField(Float.isNaN(initial) ? "" : formatFloat(initial));
        Runnable commit = () -> {
            String text = field.getText().trim();
            try {
                onCommit.accept(text.isEmpty() ? Float.NaN : Float.parseFloat(text));
            } catch (NumberFormatException ignored) {
                // leave the field as typed - no crash on a stray non-numeric edit mid-keystroke
            }
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, was, is) -> { if (!is) commit.run(); });
        HBox row = new HBox(6, l, field);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    public static String formatFloat(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    public static HBox comboRow(String label, List<String> options, String initial, Consumer<String> onCommit) {
        Label l = fieldLabel(label);
        ComboBox<String> combo = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(options));
        combo.setEditable(false);
        combo.setValue(initial != null ? initial : "");
        combo.setOnAction(e -> onCommit.accept(combo.getValue() == null ? "" : combo.getValue()));
        HBox row = new HBox(6, l, combo);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    public static CheckBox checkBox(String label, boolean initial, Consumer<Boolean> onCommit) {
        CheckBox box = new CheckBox(label);
        box.setSelected(initial);
        box.setTextFill(Color.WHITE);
        box.setOnAction(e -> onCommit.accept(box.isSelected()));
        return box;
    }

    public static Label fieldLabel(String text) {
        Label label = new Label(text + ":");
        label.setTextFill(Color.LIGHTGRAY);
        label.setStyle("-fx-font-size: 10px;");
        label.setMinWidth(90);
        label.setWrapText(true);
        return label;
    }

    public static List<String> withBlank(List<String> options) {
        List<String> result = new java.util.ArrayList<>();
        result.add("");
        result.addAll(options);
        return result;
    }
}
