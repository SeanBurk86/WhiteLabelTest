package whitelabeltest.editor;

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
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.player.WeaponLoadout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** "Quick Play" - launches the real game (a separate LWJGL3/GL process; it cannot run inside this
 *  JavaFX process) directly into gameplay on whichever stage is currently open in the editor,
 *  starting at the TimelineBar's current scrub distance, with a player-chosen starting weapon
 *  loadout - skipping the start menu/weapon-select screens entirely. See
 *  GameController.quickStartAtStage()/Main.QuickPlayConfig/Lwjgl3Launcher.readQuickPlayConfig() for
 *  how the launched process actually receives these three things.
 *
 * Two tabs: "Launch" (a read-only summary of what's about to start, plus the actual Launch button)
 * and "Weapons" (the two starting-slot combos) - the four real weapon ids
 * (Player.weaponById()'s own list), not just the three curated WeaponLoadout presets
 * (WaveBlastWeapon is deliberately powerup-only there) - this is a dev testing tool, not real
 * progression, so the extra freedom is fine here. */
public final class QuickPlayDialog {
    private static final List<String> WEAPON_IDS = List.of("BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt");

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
            launchProcess(stageDef.id, timelineBar.getValue(), slotACombo.getValue(), slotBCombo.getValue());
            dialog.close();
        });

        VBox launchTab = new VBox(10, summary, launch);
        launchTab.setPadding(new Insets(12));

        VBox weaponsTab = new VBox(10,
            FormControls.fieldLabel("Slot A"), slotACombo,
            FormControls.fieldLabel("Slot B"), slotBCombo);
        weaponsTab.setPadding(new Insets(12));

        TabPane tabs = new TabPane();
        Tab launchTabWrapper = new Tab("Launch", launchTab);
        launchTabWrapper.setClosable(false);
        launchTabWrapper.setOnSelectionChanged(e -> { if (launchTabWrapper.isSelected()) refreshSummary.run(); });
        Tab weaponsTabWrapper = new Tab("Weapons", weaponsTab);
        weaponsTabWrapper.setClosable(false);
        tabs.getTabs().addAll(launchTabWrapper, weaponsTabWrapper);

        Scene scene = new Scene(tabs, 320, 220);
        scene.getStylesheets().add(QuickPlayDialog.class.getResource("/dark-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /** Forks the real game via `gradlew(.bat) :lwjgl3:run` (reusing that task's own already-working
     *  classpath/natives resolution rather than hand-assembling one here - the editor module doesn't
     *  even depend on :lwjgl3) with quickPlay* Gradle project properties - see lwjgl3/build.gradle's
     *  run task, which translates them into the "-DquickPlay.*" system properties
     *  Lwjgl3Launcher.readQuickPlayConfig() reads. Fire-and-forget (no waitFor()) so the editor's own
     *  UI thread never blocks on the forked game's lifetime; inheritIO() surfaces Gradle/game output
     *  in the editor's own console for troubleshooting a failed launch. */
    private static void launchProcess(String stageId, float distance, String slotA, String slotB) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String gradlew = isWindows ? "gradlew.bat" : "./gradlew";
        // The editor's own run task sets its working directory to assets/ (see editor/build.gradle) -
        // the same convention lwjgl3's own run task relies on for its asset paths - so the repo root
        // where gradlew(.bat) actually lives is this process's cwd's PARENT, not the cwd itself.
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                repoRoot.resolve(gradlew).toString(),
                ":lwjgl3:run",
                "-PquickPlayStage=" + stageId,
                "-PquickPlayDistance=" + distance,
                "-PquickPlaySlotA=" + slotA,
                "-PquickPlaySlotB=" + slotB
            );
            pb.directory(repoRoot.toFile());
            pb.inheritIO();
            pb.start();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to launch Quick Play: " + e.getMessage()).showAndWait();
        }
    }
}
