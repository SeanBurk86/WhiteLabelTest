package whitelabeltest.editor;

import com.badlogic.gdx.utils.Json;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import whitelabeltest.gamemanagers.input.InputType;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.player.PlayerDefinition;
import whitelabeltest.player.WeaponLoadout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Quick Play: launches the game (a separate process) straight into the open stage at the
 *  timeline distance. The Launch tab has a summary and the input device (the start screen's
 *  detection is skipped). The Weapons tab has any weapon and level (up to player.json's
 *  maxWeaponLevel) per slot. */
public final class QuickPlayDialog {
    private static final List<String> WEAPON_IDS = List.of("BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt");
    // Used if player.json can't be read.
    private static final int FALLBACK_MAX_WEAPON_LEVEL = 4;

    private QuickPlayDialog() {}

    public static void show(Stage owner, EditorDocument document, StageLibrary library, TimelineBar timelineBar) {
        StageDefinition stageDef = document.getStageDefinition();
        if (stageDef == null) {
            new Alert(Alert.AlertType.WARNING, "No stage open - use Stage > Open Stage... first.").showAndWait();
            return;
        }

        ComboBox<String> slotACombo = new ComboBox<>(FXCollections.observableArrayList(WEAPON_IDS));
        slotACombo.setValue(WeaponLoadout.BASIC_THUNDERBOLT.slotAWeaponId);
        ComboBox<String> slotBCombo = new ComboBox<>(FXCollections.observableArrayList(WEAPON_IDS));
        slotBCombo.setValue(WeaponLoadout.BASIC_THUNDERBOLT.slotBWeaponId);

        List<String> levelOptions = new ArrayList<>();
        for (int level = 1; level <= loadMaxWeaponLevel(); level++) levelOptions.add(String.valueOf(level));
        ComboBox<String> slotALevelCombo = new ComboBox<>(FXCollections.observableArrayList(levelOptions));
        slotALevelCombo.setValue("1");
        ComboBox<String> slotBLevelCombo = new ComboBox<>(FXCollections.observableArrayList(levelOptions));
        slotBLevelCombo.setValue("1");

        ComboBox<InputType> inputCombo = new ComboBox<>(FXCollections.observableArrayList(InputType.values()));
        inputCombo.setValue(InputType.KEYBOARD);

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Quick Play");

        Label summary = new Label();
        summary.setTextFill(Color.WHITE);
        summary.setWrapText(true);
        Runnable refreshSummary = () -> summary.setText(
            "Stage: " + (stageDef.name != null ? stageDef.name : stageDef.id) + "  (" + stageDef.id + ")\n"
                + "Starting distance: " + String.format("%.1f", timelineBar.getValue()));
        refreshSummary.run();

        Button launch = new Button("Launch Quick Play");
        launch.setOnAction(e -> {
            document.save();
            library.saveStages();
            launchProcess(stageDef.id, timelineBar.getValue(), slotACombo.getValue(), slotBCombo.getValue(),
                Integer.parseInt(slotALevelCombo.getValue()), Integer.parseInt(slotBLevelCombo.getValue()), inputCombo.getValue());
            dialog.close();
        });

        VBox launchTab = new VBox(10, summary, FormControls.fieldLabel("Input"), inputCombo, launch);
        launchTab.setPadding(new Insets(12));

        VBox weaponsTab = new VBox(10,
            FormControls.fieldLabel("Slot A"), slotACombo,
            FormControls.fieldLabel("Slot A Level"), slotALevelCombo,
            FormControls.fieldLabel("Slot B"), slotBCombo,
            FormControls.fieldLabel("Slot B Level"), slotBLevelCombo);
        weaponsTab.setPadding(new Insets(12));

        TabPane tabs = new TabPane();
        Tab launchTabWrapper = new Tab("Launch", launchTab);
        launchTabWrapper.setClosable(false);
        launchTabWrapper.setOnSelectionChanged(e -> { if (launchTabWrapper.isSelected()) refreshSummary.run(); });
        Tab weaponsTabWrapper = new Tab("Weapons", weaponsTab);
        weaponsTabWrapper.setClosable(false);
        tabs.getTabs().addAll(launchTabWrapper, weaponsTabWrapper);

        Scene scene = new Scene(tabs, 320, 300);
        scene.getStylesheets().add(QuickPlayDialog.class.getResource("/dark-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /** Runs `gradlew :lwjgl3:run -PquickPlay...` (turned into -DquickPlay.* system properties by
     *  lwjgl3/build.gradle) without waiting; output goes to the editor's console. */
    private static void launchProcess(String stageId, float distance, String slotA, String slotB,
                                       int slotALevel, int slotBLevel, InputType inputType) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String gradlew = isWindows ? "gradlew.bat" : "./gradlew";
        // The working directory is assets/, so the repo root is its parent.
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                repoRoot.resolve(gradlew).toString(),
                ":lwjgl3:run",
                "-PquickPlayStage=" + stageId,
                "-PquickPlayDistance=" + distance,
                "-PquickPlaySlotA=" + slotA,
                "-PquickPlaySlotB=" + slotB,
                "-PquickPlaySlotALevel=" + slotALevel,
                "-PquickPlaySlotBLevel=" + slotBLevel,
                "-PquickPlayInput=" + inputType.name()
            );
            pb.directory(repoRoot.toFile());
            pb.inheritIO();
            pb.start();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to launch Quick Play: " + e.getMessage()).showAndWait();
        }
    }

    /** player.json's maxWeaponLevel, read each time the dialog opens. */
    private static int loadMaxWeaponLevel() {
        try {
            String text = Files.readString(Path.of("data/player.json"));
            PlayerDefinition def = new Json().fromJson(PlayerDefinition.class, text);
            return def != null && def.maxWeaponLevel > 0 ? def.maxWeaponLevel : FALLBACK_MAX_WEAPON_LEVEL;
        } catch (IOException | UncheckedIOException e) {
            return FALLBACK_MAX_WEAPON_LEVEL;
        }
    }
}
