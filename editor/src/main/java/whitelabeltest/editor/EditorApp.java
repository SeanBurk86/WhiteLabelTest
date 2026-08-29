package whitelabeltest.editor;

import javafx.application.Application;
import javafx.geometry.Orientation;
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
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        library = new StageLibrary();
        document = new EditorDocument();
        canvas = new StageCanvas(document, library);
        propertiesPanel = new PropertiesPanel(document, library, canvas);
        canvas.setSelectionListener(propertiesPanel::showTrigger);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));

        EnemyPalette enemyPalette = new EnemyPalette(library);
        ActionPalette actionPalette = new ActionPalette();
        TabPane paletteTabs = new TabPane();
        paletteTabs.getTabs().add(nonClosableTab("Enemies", enemyPalette));
        paletteTabs.getTabs().add(nonClosableTab("Actions", actionPalette));
        paletteTabs.setPrefWidth(220);

        ScrollPane canvasScroll = new ScrollPane(canvas);
        canvasScroll.setPannable(false);
        canvasScroll.setFitToWidth(true);

        SplitPane split = new SplitPane(paletteTabs, canvasScroll, propertiesPanel);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.16, 0.82);
        root.setCenter(split);

        statusLabel = new Label("No file loaded - use Stage > Open Stage...");
        document.addChangeListener(() -> statusLabel.setText(document.describe()));
        root.setBottom(statusLabel);

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("WhiteLabelTest Stage Editor");
        stage.setScene(scene);
        stage.show();
    }

    private Tab nonClosableTab(String name, javafx.scene.Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }

    private MenuBar buildMenuBar(Stage stage) {
        MenuItem openStage = new MenuItem("Open Stage...");
        openStage.setOnAction(e -> new OpenStageDialog(library).showAndLoad(stage, document));

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
