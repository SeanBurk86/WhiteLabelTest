package whitelabeltest.editor;

import javafx.geometry.Orientation;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** The stage editor (JavaFX). Palettes on the left, the StageCanvas ("Edit" tab) and
 *  PlayerPreviewView ("Player View" tab, driven by the TimelineBar) in the center, and one
 *  properties panel on the right (trigger, enemy or stage). It reads and writes the same
 *  *_triggers.json the game loads. */
public class EditorApp extends Application {
    private EditorDocument document;
    private StageLibrary library;
    private StageCanvas canvas;
    private PlayerPreviewView playerPreviewView;
    private PropertiesPanel propertiesPanel;
    private EnemyDefinitionPanel enemyDefinitionPanel;
    private StageDefinitionPanel stageDefinitionPanel;
    private ScrollPane canvasScroll;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        library = new StageLibrary();
        document = new EditorDocument();
        canvas = new StageCanvas(document, library);
        playerPreviewView = new PlayerPreviewView(document, library);
        propertiesPanel = new PropertiesPanel(library, canvas);
        enemyDefinitionPanel = new EnemyDefinitionPanel(library);
        stageDefinitionPanel = new StageDefinitionPanel(library);

        // Shows whichever panel matches the most recent selection (trigger, enemy or stage).
        StackPane rightDock = new StackPane(propertiesPanel, enemyDefinitionPanel, stageDefinitionPanel);
        showRightDock(propertiesPanel);

        // 0: nothing selected, 1: the trigger form, 2+: bulk delete.
        canvas.setSelectionListener(triggers -> {
            showRightDock(propertiesPanel);
            if (triggers.size() == 1) {
                propertiesPanel.showTrigger(triggers.get(0));
            } else if (triggers.isEmpty()) {
                propertiesPanel.showTrigger(null);
            } else {
                propertiesPanel.showMultiSelection(triggers);
            }
        });

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));

        EnemyPalette enemyPalette = new EnemyPalette(library);
        enemyPalette.setSelectionListener(def -> {
            showRightDock(enemyDefinitionPanel);
            enemyDefinitionPanel.showDefinition(def);
        });
        ActionPalette actionPalette = new ActionPalette();
        StagePalette stagePalette = new StagePalette(library);
        stagePalette.setSelectionListener(stageDef -> {
            showRightDock(stageDefinitionPanel);
            stageDefinitionPanel.showStage(stageDef);
        });
        TabPane paletteTabs = new TabPane();
        paletteTabs.getTabs().add(nonClosableTab("Enemies", enemyPalette));
        paletteTabs.getTabs().add(nonClosableTab("Actions", actionPalette));
        paletteTabs.getTabs().add(nonClosableTab("Stages", stagePalette));
        paletteTabs.setPrefWidth(220);

        canvasScroll = new ScrollPane(canvas);
        canvasScroll.setPannable(false);
        canvasScroll.setFitToWidth(true);
        // Start scrolled to the bottom (distance 0).
        canvasScroll.setVvalue(1.0);

        TimelineBar timelineBar = new TimelineBar(document);
        timelineBar.addListener(playerPreviewView::setPreviewDistance);
        // Render Player View once at startup.
        playerPreviewView.setPreviewDistance(timelineBar.getValue());
        HBox.setHgrow(timelineBar, Priority.ALWAYS);

        // Launches the game at the timeline's distance.
        Button quickPlay = new Button("Quick Play");
        quickPlay.setOnAction(e -> QuickPlayDialog.show(stage, document, library, timelineBar));
        HBox timelineRow = new HBox(8, timelineBar, quickPlay);
        timelineRow.setStyle("-fx-alignment: center-left;");

        TabPane centerTabs = new TabPane();
        centerTabs.getTabs().add(nonClosableTab("Edit", canvasScroll));
        centerTabs.getTabs().add(nonClosableTab("Player View", wrapPlayerView(playerPreviewView)));

        VBox centerArea = new VBox(centerTabs, timelineRow);
        VBox.setVgrow(centerTabs, Priority.ALWAYS);

        SplitPane split = new SplitPane(paletteTabs, centerArea, rightDock);
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

    /** Centers Player View (it doesn't zoom or pan). */
    private Node wrapPlayerView(PlayerPreviewView view) {
        StackPane holder = new StackPane(view);
        holder.setStyle("-fx-background-color: #17181c;");
        return holder;
    }

    /** Shows only `active` (the others are hidden and unmanaged). */
    private void showRightDock(javafx.scene.Node active) {
        for (javafx.scene.Node node : new javafx.scene.Node[] { propertiesPanel, enemyDefinitionPanel, stageDefinitionPanel }) {
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
            // Scroll to the stage start.
            canvasScroll.setVvalue(1.0);
        });

        MenuItem save = new MenuItem("Save");
        save.setOnAction(e -> document.save());

        MenuItem saveAs = new MenuItem("Save As...");
        saveAs.setOnAction(e -> document.saveAsDialog(stage));

        Menu stageMenu = new Menu("Stage", null, openStage, save, saveAs);

        MenuItem editFiringPatterns = new MenuItem("Edit Firing Patterns...");
        editFiringPatterns.setOnAction(e -> FiringPatternEditorDialog.show(stage));
        Menu firingPatternsMenu = new Menu("Firing Patterns", null, editFiringPatterns);

        return new MenuBar(stageMenu, firingPatternsMenu);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
