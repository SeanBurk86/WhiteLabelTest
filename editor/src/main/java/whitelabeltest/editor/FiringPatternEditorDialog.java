package whitelabeltest.editor;

import com.badlogic.gdx.utils.Json;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import whitelabeltest.enemy.FiringPatternDef;

import java.util.Optional;

/** The Firing Pattern Editor window: pattern list (left), FiringPatternFieldsEditor (center) and
 *  FiringPatternPreviewCanvas (right). Saves data/firing_patterns/&lt;id&gt;.json. */
final class FiringPatternEditorDialog {
    private FiringPatternEditorDialog() {}

    static void show(Stage owner) {
        FiringPatternLibrary library = new FiringPatternLibrary();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.setTitle("Firing Pattern Editor");

        ListView<String> list = new ListView<>(FXCollections.observableArrayList(PatternIds.firingPatternIds()));
        list.setPrefWidth(200);

        BorderPane centerHolder = new BorderPane();
        FiringPatternPreviewCanvas preview = new FiringPatternPreviewCanvas();
        Button playButton = new Button("▶ Play");
        Button pauseButton = new Button("⏸ Pause");
        Button resetButton = new Button("↺ Reset");
        playButton.setOnAction(e -> preview.play());
        pauseButton.setOnAction(e -> preview.pause());
        resetButton.setOnAction(e -> preview.reset());
        HBox previewControls = new HBox(6, playButton, pauseButton, resetButton);
        VBox previewBox = new VBox(6, FormControls.sectionLabel("Preview (live)"), previewControls, preview);
        previewBox.setPadding(new Insets(8));

        FiringPatternDef[] current = { null };
        boolean[] dirty = { false };
        Label statusLabel = new Label("No pattern selected.");

        TextField idField = new TextField();
        idField.setPromptText("pattern id");
        idField.setPrefWidth(160);

        Runnable markDirty = () -> {
            dirty[0] = true;
            // The preview must be rebuilt, not just redrawn, to pick up the edit.
            preview.onFieldChanged();
            statusLabel.setText((current[0] != null ? current[0].id : "") + " - unsaved changes");
        };

        Runnable loadSelected = () -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                current[0] = null;
                centerHolder.setCenter(new Label("Select a pattern on the left, or click New."));
                preview.setPattern(null);
                idField.setText("");
                statusLabel.setText("No pattern selected.");
                return;
            }
            // New/Duplicate select an unsaved def; only load from disk when a file exists.
            boolean unsaved = current[0] != null && id.equals(current[0].id) && !library.exists(id);
            current[0] = unsaved ? current[0] : library.load(id);
            idField.setText(id);
            centerHolder.setCenter(FiringPatternFieldsEditor.build(current[0], markDirty));
            preview.setPattern(current[0]);
            dirty[0] = unsaved;
            statusLabel.setText(id + (unsaved ? " - new, unsaved" : " - loaded"));
        };
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, val) -> loadSelected.run());

        Button newButton = new Button("New");
        newButton.setOnAction(e -> {
            Optional<String> id = promptForId(dialog, "New Firing Pattern", "");
            if (id.isEmpty()) return;
            if (library.exists(id.get()) || list.getItems().contains(id.get())) {
                new Alert(Alert.AlertType.WARNING, "A pattern named '" + id.get() + "' already exists.").showAndWait();
                return;
            }
            current[0] = library.createNew(id.get());
            list.getItems().add(id.get());
            list.getSelectionModel().select(id.get());
        });

        Button duplicateButton = new Button("Duplicate");
        duplicateButton.setOnAction(e -> {
            if (current[0] == null) return;
            Optional<String> id = promptForId(dialog, "Duplicate Firing Pattern", current[0].id + "_copy");
            if (id.isEmpty()) return;
            if (library.exists(id.get()) || list.getItems().contains(id.get())) {
                new Alert(Alert.AlertType.WARNING, "A pattern named '" + id.get() + "' already exists.").showAndWait();
                return;
            }
            Json json = new Json();
            FiringPatternDef copy = json.fromJson(FiringPatternDef.class, json.toJson(current[0], FiringPatternDef.class));
            copy.id = id.get();
            current[0] = copy;
            list.getItems().add(id.get());
            list.getSelectionModel().select(id.get());
        });

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete firing pattern '" + id + "'? This removes its JSON file.", ButtonType.OK, ButtonType.CANCEL);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            library.delete(id);
            list.getItems().remove(id);
            list.getSelectionModel().clearSelection();
        });

        // Saving under a new id writes a new file and leaves the old one (Save As).
        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            if (current[0] == null) return;
            String newId = idField.getText().trim();
            if (newId.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Pattern id can't be blank.").showAndWait();
                return;
            }
            current[0].id = newId;
            library.save(current[0]);
            if (!list.getItems().contains(newId)) list.getItems().add(newId);
            list.getSelectionModel().select(newId);
            dirty[0] = false;
            statusLabel.setText(newId + " - saved");
        });

        HBox idRow = new HBox(6, FormControls.fieldLabel("ID"), idField);
        idRow.setStyle("-fx-alignment: center-left;");
        HBox toolbar = new HBox(8, newButton, duplicateButton, deleteButton, idRow, saveButton);
        toolbar.setPadding(new Insets(8));
        toolbar.setStyle("-fx-alignment: center-left;");

        VBox listBox = new VBox(6, FormControls.sectionLabel("Firing Patterns"), list);
        listBox.setPadding(new Insets(8));
        VBox.setVgrow(list, Priority.ALWAYS);

        SplitPane split = new SplitPane(listBox, centerHolder, previewBox);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.18, 0.72);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(split);
        root.setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(4, 8, 4, 8));

        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(FiringPatternEditorDialog.class.getResource("/dark-theme.css").toExternalForm());
        dialog.setScene(scene);

        dialog.setOnCloseRequest(e -> {
            if (dirty[0]) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Discard unsaved changes to '" + (current[0] != null ? current[0].id : "") + "'?", ButtonType.OK, ButtonType.CANCEL);
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) e.consume();
            }
        });

        dialog.show();
    }

    private static Optional<String> promptForId(Stage owner, String title, String initial) {
        TextInputDialog prompt = new TextInputDialog(initial);
        prompt.initOwner(owner);
        prompt.setTitle(title);
        prompt.setHeaderText(null);
        prompt.setContentText("Pattern id:");
        return prompt.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }
}
