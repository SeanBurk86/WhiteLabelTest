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

/** "Quick Play" - launches the real game (a separate LWJGL3/GL process; it cannot run inside this
 *  JavaFX process) directly into gameplay on whichever stage is currently open in the editor,
 *  starting at the TimelineBar's current scrub distance, with a player-chosen starting weapon
 *  loadout - skipping the start menu/weapon-select screens entirely. See
 *  GameController.quickStartAtStage()/Main.QuickPlayConfig/Lwjgl3Launcher.readQuickPlayConfig() for
 *  how the launched process actually receives these three things.
 *
 * Two tabs: "Launch" (a read-only summary of what's about to start, the Input combo, and the actual
 * Launch button) and "Weapons" (the two starting-slot combos, each paired with its own starting
 * LEVEL combo) - the four real weapon ids (Player.weaponById()'s own list), not just the three
 * curated WeaponLoadout presets (WaveBlastWeapon is deliberately powerup-only there) - this is a dev
 * testing tool, not real progression, so the extra freedom is fine here. Level range is read from
 * player.json's own maxWeaponLevel (see loadMaxWeaponLevel()) rather than hardcoded, so it never
 * drifts out of sync with what Player.setWeaponLevel() itself actually allows.
 *
 * The Input combo (Keyboard/Gamepad) exists because Quick Play skips StartScreen entirely - see
 * Main.transitionToQuickPlay()'s own doc - so there's no "press any key/button" step to auto-detect
 * the device from the way an ordinary run does; without this a Quick Play session was always stuck
 * polling keyboard regardless of what the tester actually wanted to play with. */
public final class QuickPlayDialog {
    private static final List<String> WEAPON_IDS = List.of("BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt");
    // Matches player.json's own current maxWeaponLevel - used only if that file can't be read for
    // some reason (see loadMaxWeaponLevel()), so the level combos always have SOME sane range.
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

    /** Forks the real game via `gradlew(.bat) :lwjgl3:run` (reusing that task's own already-working
     *  classpath/natives resolution rather than hand-assembling one here - the editor module doesn't
     *  even depend on :lwjgl3) with quickPlay* Gradle project properties - see lwjgl3/build.gradle's
     *  run task, which translates them into the "-DquickPlay.*" system properties
     *  Lwjgl3Launcher.readQuickPlayConfig() reads. Fire-and-forget (no waitFor()) so the editor's own
     *  UI thread never blocks on the forked game's lifetime; inheritIO() surfaces Gradle/game output
     *  in the editor's own console for troubleshooting a failed launch. */
    private static void launchProcess(String stageId, float distance, String slotA, String slotB,
                                       int slotALevel, int slotBLevel, InputType inputType) {
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

    /** player.json's own maxWeaponLevel - the same ceiling Player.setWeaponLevel() itself clamps
     *  to - read fresh every time this dialog opens (a plain, uncached Files.readString() + Json
     *  parse, same technique EditorDocument/StageLibrary already use for their own JSON reads) so a
     *  tuning change to that file shows up here without an editor restart. Falls back to
     *  FALLBACK_MAX_WEAPON_LEVEL if the file is missing/unparseable, rather than failing to open
     *  this dialog at all over what's ultimately just a cosmetic range on a dev testing tool. */
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
