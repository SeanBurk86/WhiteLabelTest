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

/** Entry point for the stage editor - a JavaFX app that drags enemies/trigger-actions onto a
 *  scrolling canvas and reads/writes the exact same *_triggers.json format
 *  whitelabeltest.gamemanagers.trigger.TriggerManager parses in-game, so a stage edited here can be
 *  tested by just running the game against the saved file - see StageLibrary/EditorDocument.
 *
 * Movement-pattern (path/waypoint) editing happens directly on the StageCanvas itself, scoped to
 * whichever placed enemy-spawn trigger is selected - see StageCanvas.setPathEditTrigger() and
 * PropertiesPanel's "Movement Path" section, which is what turns it on/off. Not a separate
 * top-level view or a small standalone canvas - a placed spawn already has the real stage context
 * (nearby triggers, background art) a standalone editor never had, and every trigger's resolved
 * path is always visible as a true-scale preview on the canvas regardless of edit mode.
 *
 * Two center tabs: "Edit" (the interactive canvas above - also serves as the zoomable stage
 * overview, so there's no separate "camera view" tab) and "Player View" (PlayerPreviewView - a true
 * simulated render of what the player actually sees at the TimelineBar's scrubbed distance, which
 * sits below the tabs and is an inert no-op while the Edit tab is active). */
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

        // The right-hand dock shows exactly one of these three panels at a time: a placed trigger's
        // per-instance settings, an enemy's own template stats, or a stage's own metadata -
        // whichever the user selected most recently (canvas node vs. palette tile/row). See
        // EnemyPalette/StagePalette/StageCanvas's own selection listeners below for the triggers
        // that flip this.
        StackPane rightDock = new StackPane(propertiesPanel, enemyDefinitionPanel, stageDefinitionPanel);
        showRightDock(propertiesPanel);

        // 0 selected -> showTrigger(null)'s "nothing selected" message; exactly 1 -> the normal
        // per-field form; 2+ (Ctrl/Cmd-click - see StageCanvas.select()) -> the bulk-delete view.
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
        // Distance 0 (stage start) is anchored to the BOTTOM of the canvas - see StageCanvas's own
        // doc - so a freshly-opened stage should scroll all the way down, not show the far end of
        // the level first.
        canvasScroll.setVvalue(1.0);

        TimelineBar timelineBar = new TimelineBar(document);
        timelineBar.addListener(playerPreviewView::setPreviewDistance);
        // Show something the instant the app opens rather than leaving Player View blank until the
        // user first touches the slider.
        playerPreviewView.setPreviewDistance(timelineBar.getValue());
        HBox.setHgrow(timelineBar, Priority.ALWAYS);

        // Launches the real game starting exactly where this timeline is scrubbed to - see
        // QuickPlayDialog's own doc. Sits right next to the timeline it reads from, rather than
        // buried in a menu, since it's meant for fast edit/test iteration.
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

    /** Player View isn't zoomable/pannable (see PlayerPreviewView's own doc - it's meant to read as
     *  the game's own fixed viewport, not an editing surface) - just centered in whatever space the
     *  tab gives it. */
    private Node wrapPlayerView(PlayerPreviewView view) {
        StackPane holder = new StackPane(view);
        holder.setStyle("-fx-background-color: #17181c;");
        return holder;
    }

    /** Shows exactly `active` in the right-hand dock, hiding (and un-managing, so it doesn't still
     *  claim layout space) the others. */
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
            // Scroll to the stage's start (the bottom - see StageCanvas's doc) rather than leaving
            // whatever scroll position the previously-open file happened to be at.
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
