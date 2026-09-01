package whitelabeltest.editor;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** "Stage > Open Stage..." - picks a stage from data/stages.json by name and loads its
 *  triggerFile. A stage with no triggerFile yet (every stage but stage1, as of this session) is
 *  offered a fresh one via StageLibrary.createTriggerFileForStage() rather than just failing. */
public class OpenStageDialog {
    private final StageLibrary library;

    public OpenStageDialog(StageLibrary library) {
        this.library = library;
    }

    public void showAndLoad(Stage owner, EditorDocument document) {
        List<String> names = new ArrayList<>();
        library.getStages().forEach(s -> names.add(s.name + "  (" + s.id + ")"));
        if (names.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "data/stages.json has no stages defined.").showAndWait();
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Open Stage");
        dialog.setHeaderText("Choose a stage to edit its triggers");
        Optional<String> choice = dialog.showAndWait();
        if (choice.isEmpty()) return;

        int index = names.indexOf(choice.get());
        StageDefinition stage = library.getStages().get(index);

        if (stage.triggerFile == null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                stage.name + " has no trigger file yet. Create data/stages/" + stage.id + "_triggers.json?",
                ButtonType.YES, ButtonType.NO);
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) return;
            Path created = library.createTriggerFileForStage(stage);
            document.load(created);
            document.setStageDefinition(stage);
            return;
        }

        document.load(Path.of(stage.triggerFile));
        document.setStageDefinition(stage);
    }
}
