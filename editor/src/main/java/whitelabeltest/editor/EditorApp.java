package whitelabeltest.editor;

import javafx.geometry.Orientation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Entry point for the stage editor - a JavaFX app that drags enemies/trigger-actions onto a
 *  scrolling canvas and reads/writes the exact same *_triggers.json format
 *  whitelabeltest.gamemanagers.trigger.TriggerManager parses in-game, so a stage edited here can be
 *  tested by just running the game against the saved file - see StageLibrary/EditorDocument. */
public class EditorApp extends Application {
    private EditorDocument document;
    private StageLibrary library;
    private StageCanvas canvas;
    private PropertiesPanel propertiesPanel;
    private EnemyDefinitionPanel enemyDefinitionPanel;
    private ScrollPane canvasScroll;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        library = new StageLibrary();
        document = new EditorDocument();
        canvas = new StageCanvas(document, library);
        propertiesPanel = new PropertiesPanel(library, canvas);
        enemyDefinitionPanel = new EnemyDefinitionPanel(library);

        // The right-hand dock shows exactly one of these two panels at a time: a placed trigger's
        // per-instance settings, or an enemy's own template stats - whichever the user selected
        // most recently (canvas node vs. palette tile). See EnemyPalette/StageCanvas's own
        // selection listeners below for the two triggers that flip this.
        StackPane rightDock = new StackPane(propertiesPanel, enemyDefinitionPanel);
        showRightDock(propertiesPanel);

        canvas.setSelectionListener(trigger -> {
            showRightDock(propertiesPanel);
            propertiesPanel.showTrigger(trigger);
        });

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));

        EnemyPalette enemyPalette = new EnemyPalette(library);
        enemyPalette.setSelectionListener(def -> {
            showRightDock(enemyDefinitionPanel);
            enemyDefinitionPanel.showDefinition(def);
        });
        ActionPalette actionPalette = new ActionPalette();
        TabPane paletteTabs = new TabPane();
        paletteTabs.getTabs().add(nonClosableTab("Enemies", enemyPalette));
        paletteTabs.getTabs().add(nonClosableTab("Actions", actionPalette));
        paletteTabs.setPrefWidth(220);

        canvasScroll = new ScrollPane(canvas);
        canvasScroll.setPannable(false);
        canvasScroll.setFitToWidth(true);
        // Distance 0 (stage start) is anchored to the BOTTOM of the canvas - see StageCanvas's own
        // doc - so a freshly-opened stage should scroll all the way down, not show the far end of
        // the level first.
        canvasScroll.setVvalue(1.0);

        SplitPane split = new SplitPane(paletteTabs, canvasScroll, rightDock);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.16, 0.82);
        root.setCenter(split);

        statusLabel = new Label("No file loaded - use Stage > Open Stage...");
        document.addChangeListener(() -> statusLabel.setText(document.describe()));
        root.setBottom(statusLabel);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        stage.setTitle("WhiteLabelTest Stage Editor");
        stage.setScene(scene);
        stage.show();
    }

    /** Shows exactly `active` in the right-hand dock, hiding (and un-managing, so it doesn't still
     *  claim layout space) the other. */
    private void showRightDock(javafx.scene.Node active) {
        for (javafx.scene.Node node : new javafx.scene.Node[] { propertiesPanel, enemyDefinitionPanel }) {
            boolean isActive = node == active;
            node.setVisible(isActive);
            node.setManaged(isActive);
        }
    }

    private Tab nonClosableTab(String name, javafx.scene.Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }

    private MenuBar buildMenuBar(Stage stage) {
        MenuItem openStage = new MenuItem("Open Stage...");
        openStage.setOnAction(e -> {
            new OpenStageDialog(library).showAndLoad(stage, document);
            // Scroll to the stage's start (the bottom - see StageCanvas's doc) rather than leaving
            // whatever scroll position the previously-open file happened to be at.
            canvasScroll.setVvalue(1.0);
        });

        MenuItem save = new MenuItem("Save");
        save.setOnAction(e -> document.save());

        MenuItem saveAs = new MenuItem("Save As...");
        saveAs.setOnAction(e -> document.saveAsDialog(stage));

        Menu stageMenu = new Menu("Stage", null, openStage, save, saveAs);
        return new MenuBar(stageMenu);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
