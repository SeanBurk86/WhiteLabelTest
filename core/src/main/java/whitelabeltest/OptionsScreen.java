package whitelabeltest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import whitelabeltest.gamemanagers.AudioSettings;
import whitelabeltest.gamemanagers.KeyBindings;
import whitelabeltest.gamemanagers.KeyBindings.Action;
import whitelabeltest.gamemanagers.KeyBindings.GamepadButton;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Options is a small menu tree: a top-level page with entries that open the Key Bindings and
 *  Audio pages, each of which returns to the top level on Back rather than leaving Options
 *  outright. All three pages share one row/column focus-navigation system (keyboard click,
 *  mouse click and gamepad D-Pad+A all resolve to the same per-cell Runnable - see
 *  {@link #rowActivators}) so moveRow/moveCol/activateFocused/handleControllerNavigation don't
 *  need to know which page is showing. */
public class OptionsScreen implements Disposable {
    // ScrollPane.updateActorPosition() (private, can't be overridden) truncates the scrolled
    // widget's position to the nearest *whole stage unit* - a pixel-snapping optimization that's
    // invisible when 1 stage unit is roughly 1 screen pixel, but this screen's stage otherwise
    // used the game's own 9x12 world-unit space, where a whole unit is nearly two entire row
    // heights. That truncation - not any bug in the Table layout itself - was the real source of
    // the "phantom blank row"/misaligned-focus symptoms: depending on the exact fractional part
    // of wherever a row's scroll target landed, up to a full unit of vertical position (~2 rows)
    // could vanish. Every dimension below is scaled up by this factor from its originally-designed
    // 9x12-space value so that truncating to a whole stage unit becomes sub-pixel and negligible,
    // exactly like ScrollPane assumes - the on-screen appearance is unchanged.
    private static final float UI_SCALE = 100f;
    private static final float VOLUME_STEP = 0.1f;

    private enum Page { MENU, KEY_BINDINGS, AUDIO }

    private final Stage stage;
    private final KeyBindings keyBindings;
    private final AudioSettings audioSettings;
    private final BitmapFont font;
    private final Texture pixel;
    private final Skin skin;
    // Menu SFX: backSound on any back input (Escape, the dedicated gamepad Back/Select button, or
    // the new gamepad B - see triggerBack()), confirmSound on any confirm input (mouse click or
    // gamepad A - see onClick()/activateFocused()), selectSound whenever focus actually moves to a
    // different row/column (see moveRow()/moveCol()).
    private final Sound backSound;
    private final Sound confirmSound;
    private final Sound selectSound;
    private final Map<Action, TextButton> keyButtons = new EnumMap<>(Action.class);
    private final Map<Action, TextButton> gamepadBindingButtons = new EnumMap<>(Action.class);

    private Label subtitleLabel;
    private Table menuTable;
    private ScrollPane bindingsScroll;
    private Table audioTable;
    private Label musicValueLabel;
    private Label sfxValueLabel;

    // Every page's row/activator lists, built once in the constructor. rows/rowActivators/
    // activeScroll are simply re-pointed at one of these three pairs on switchPage() - see the
    // class doc.
    private final Array<TextButton[]> menuRows = new Array<>();
    private final Array<Runnable[]> menuActivators = new Array<>();
    private final Array<TextButton[]> keyBindingRows = new Array<>();
    private final Array<Runnable[]> keyBindingActivators = new Array<>();
    private final Array<TextButton[]> audioRows = new Array<>();
    private final Array<Runnable[]> audioActivators = new Array<>();

    private Page page = Page.MENU;
    private Array<TextButton[]> rows = menuRows;
    private Array<Runnable[]> rowActivators = menuActivators;
    private ScrollPane activeScroll;
    private int focusedRow = -1;
    private int focusedCol = 0;
    private final boolean[] prevGamepadButtonDown = new boolean[GamepadButton.values().length];

    private Action listeningFor;
    private TextButton listeningButton;
    private Action gamepadListeningFor;
    private TextButton gamepadListeningButton;
    private boolean backRequested;

    public OptionsScreen(KeyBindings keyBindings, AudioSettings audioSettings, float worldWidth, float worldHeight) {
        this.keyBindings = keyBindings;
        this.audioSettings = audioSettings;
        this.stage = new Stage(new ExtendViewport(worldWidth * UI_SCALE, worldHeight * UI_SCALE));

        backSound = Gdx.audio.newSound(Gdx.files.internal("backsoundmenu.mp3"));
        confirmSound = Gdx.audio.newSound(Gdx.files.internal("confirmsoundmenu.mp3"));
        selectSound = Gdx.audio.newSound(Gdx.files.internal("selectsoundmenu.mp3"));

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        generator.dispose();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.01171875f * UI_SCALE);

        Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        skin = buildSkin();

        Table root = buildLayout();
        stage.addActor(root);
        stage.setKeyboardFocus(root);
        switchPage(Page.MENU);
    }

    private Skin buildSkin() {
        Skin skin = new Skin();
        TextureRegionDrawable base = new TextureRegionDrawable(new TextureRegion(pixel));

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);

        Label.LabelStyle headerStyle = new Label.LabelStyle(font, Color.YELLOW);
        skin.add("header", headerStyle);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.up = base.tint(new Color(0.22f, 0.22f, 0.22f, 1f));
        buttonStyle.down = base.tint(new Color(0.4f, 0.4f, 0.12f, 1f));
        buttonStyle.over = base.tint(new Color(0.32f, 0.32f, 0.32f, 1f));
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.downFontColor = Color.YELLOW;
        buttonStyle.overFontColor = Color.LIGHT_GRAY;
        skin.add("default", buttonStyle);

        TextButton.TextButtonStyle listeningStyle = new TextButton.TextButtonStyle(buttonStyle);
        listeningStyle.up = base.tint(new Color(0.5f, 0.15f, 0.15f, 1f));
        listeningStyle.fontColor = Color.YELLOW;
        skin.add("listening", listeningStyle);

        TextButton.TextButtonStyle focusedStyle = new TextButton.TextButtonStyle(buttonStyle);
        focusedStyle.up = base.tint(new Color(0.2f, 0.32f, 0.5f, 1f));
        focusedStyle.fontColor = Color.CYAN;
        skin.add("focused", focusedStyle);

        return skin;
    }

    private Table buildLayout() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(0.6f * UI_SCALE);

        Label title = new Label("OPTIONS", skin, "header");
        root.add(title).padBottom(0.5f * UI_SCALE).row();

        subtitleLabel = new Label("", skin);
        root.add(subtitleLabel).padBottom(0.2f * UI_SCALE).row();

        menuTable = buildMenuTable();
        buildKeyBindingsTable();
        audioTable = buildAudioTable();

        Stack contentStack = new Stack();
        contentStack.add(menuTable);
        contentStack.add(bindingsScroll);
        contentStack.add(audioTable);
        root.add(contentStack).expand().fill().row();

        Label backHint = new Label("Esc/Back - Back   Arrows/D-Pad - Move   Enter/A - Select", skin);
        root.add(backHint).padTop(0.35f * UI_SCALE);

        root.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleKeyDown(keycode);
            }
        });

        return root;
    }

    private Table buildMenuTable() {
        Table table = new Table();

        TextButton keyBindingsButton = new TextButton("Key Bindings", skin);
        TextButton audioButton = new TextButton("Audio", skin);
        Runnable openKeyBindings = () -> switchPage(Page.KEY_BINDINGS);
        Runnable openAudio = () -> switchPage(Page.AUDIO);
        onClick(keyBindingsButton, openKeyBindings);
        onClick(audioButton, openAudio);

        table.add(keyBindingsButton).width(3f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.08f * UI_SCALE).row();
        table.add(audioButton).width(3f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.08f * UI_SCALE).row();

        menuRows.add(new TextButton[]{keyBindingsButton, null});
        menuActivators.add(new Runnable[]{openKeyBindings, null});
        menuRows.add(new TextButton[]{audioButton, null});
        menuActivators.add(new Runnable[]{openAudio, null});

        return table;
    }

    private void buildKeyBindingsTable() {
        Table bindingsTable = new Table();

        bindingsTable.add();
        bindingsTable.add(new Label("Keyboard", skin)).width(1.9f * UI_SCALE);
        bindingsTable.add(new Label("Gamepad", skin)).width(1.9f * UI_SCALE).row();

        for (Action action : Action.values()) {
            Label nameLabel = new Label(action.label, skin);
            TextButton keyButton = new TextButton(Input.Keys.toString(keyBindings.getKey(action)), skin);
            Runnable listenKey = () -> startListening(action, keyButton);
            onClick(keyButton, listenKey);

            bindingsTable.add(nameLabel).left().width(2.6f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE);
            bindingsTable.add(keyButton).width(1.9f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE);
            keyButtons.put(action, keyButton);

            TextButton gamepadButton = null;
            Runnable listenGamepad = null;
            if (action.hasGamepadBinding()) {
                gamepadButton = new TextButton(keyBindings.getGamepadButton(action).displayName, skin);
                TextButton finalGamepadButton = gamepadButton;
                listenGamepad = () -> startGamepadListening(action, finalGamepadButton);
                onClick(gamepadButton, listenGamepad);
                bindingsTable.add(gamepadButton).width(1.9f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE).row();
                gamepadBindingButtons.put(action, gamepadButton);
            } else {
                bindingsTable.add().width(1.9f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE).row();
            }
            keyBindingRows.add(new TextButton[]{keyButton, gamepadButton});
            keyBindingActivators.add(new Runnable[]{listenKey, listenGamepad});
        }

        TextButton resetButton = new TextButton("Reset to Defaults", skin);
        Runnable resetKeyBindings = this::resetKeyBindingsToDefaults;
        onClick(resetButton, resetKeyBindings);
        bindingsTable.add(resetButton).colspan(3).padTop(0.4f * UI_SCALE).height(0.4f * UI_SCALE).row();
        keyBindingRows.add(new TextButton[]{resetButton, null});
        keyBindingActivators.add(new Runnable[]{resetKeyBindings, null});

        bindingsScroll = new ScrollPane(bindingsTable);
        bindingsScroll.setScrollingDisabled(true, false);
        bindingsScroll.setFadeScrollBars(false);
        // Smooth scrolling animates the visible scroll position toward its target over several
        // frames, but setFocus()'s highlight restyle is instant - so right after a D-pad press,
        // the newly-focused button is already shown highlighted while the still-catching-up
        // scroll position renders the rest of the list as if it belongs to the previous target,
        // making a focused row look detached/misaligned from its neighbors until the animation
        // settles a few frames later. Disabled so scrollTo() takes effect immediately instead.
        bindingsScroll.setSmoothScrolling(false);
    }

    private Table buildAudioTable() {
        Table table = new Table();

        musicValueLabel = new Label(volumeText(audioSettings.getMusicVolume()), skin);
        sfxValueLabel = new Label(volumeText(audioSettings.getSfxVolume()), skin);

        addVolumeRow(table, "Music Volume", musicValueLabel, audioSettings::getMusicVolume, audioSettings::setMusicVolume);
        addVolumeRow(table, "Sound Effects", sfxValueLabel, audioSettings::getSfxVolume, audioSettings::setSfxVolume);

        TextButton resetButton = new TextButton("Reset to Defaults", skin);
        Runnable resetAudio = this::resetAudioToDefaults;
        onClick(resetButton, resetAudio);
        table.add(resetButton).colspan(4).padTop(0.4f * UI_SCALE).height(0.4f * UI_SCALE).row();
        audioRows.add(new TextButton[]{resetButton, null});
        audioActivators.add(new Runnable[]{resetAudio, null});

        return table;
    }

    /** One "Name   -   80%   +" row, with the "-"/"+" buttons taking the same two focus columns
     *  the Key Bindings page's Keyboard/Gamepad buttons occupy - so moveCol/moveRow work on this
     *  page without any page-specific navigation logic. */
    private void addVolumeRow(Table table, String name, Label valueLabel, Supplier<Float> get, Consumer<Float> set) {
        TextButton minusButton = new TextButton("-", skin);
        TextButton plusButton = new TextButton("+", skin);
        Runnable decrease = () -> { set.accept(get.get() - VOLUME_STEP); valueLabel.setText(volumeText(get.get())); };
        Runnable increase = () -> { set.accept(get.get() + VOLUME_STEP); valueLabel.setText(volumeText(get.get())); };
        onClick(minusButton, decrease);
        onClick(plusButton, increase);

        table.add(new Label(name, skin)).left().width(2.6f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE);
        table.add(minusButton).width(0.7f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE);
        table.add(valueLabel).width(1.2f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE);
        table.add(plusButton).width(0.7f * UI_SCALE).height(0.4f * UI_SCALE).pad(0.06f * UI_SCALE).row();

        audioRows.add(new TextButton[]{minusButton, plusButton});
        audioActivators.add(new Runnable[]{decrease, increase});
    }

    private static String volumeText(float volume) {
        return Math.round(volume * 100) + "%";
    }

    private void onClick(TextButton button, Runnable action) {
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                confirmSound.play();
                action.run();
            }
        });
    }

    private void switchPage(Page newPage) {
        cancelListening();
        cancelGamepadListening();
        page = newPage;

        boolean isMenu = newPage == Page.MENU;
        boolean isBindings = newPage == Page.KEY_BINDINGS;
        boolean isAudio = newPage == Page.AUDIO;

        switch (newPage) {
            case MENU:
                subtitleLabel.setText("");
                rows = menuRows;
                rowActivators = menuActivators;
                activeScroll = null;
                break;
            case KEY_BINDINGS:
                subtitleLabel.setText("KEY BINDINGS");
                rows = keyBindingRows;
                rowActivators = keyBindingActivators;
                activeScroll = bindingsScroll;
                break;
            case AUDIO:
                subtitleLabel.setText("AUDIO");
                rows = audioRows;
                rowActivators = audioActivators;
                activeScroll = null;
                break;
        }

        menuTableVisibility(isMenu);
        bindingsScroll.setVisible(isBindings);
        bindingsScroll.setTouchable(isBindings ? Touchable.enabled : Touchable.disabled);
        audioTableVisibility(isAudio);

        focusedRow = -1;
        focusedCol = 0;
        setFocus(0, 0);
    }

    private void menuTableVisibility(boolean visible) {
        menuTable.setVisible(visible);
        menuTable.setTouchable(visible ? Touchable.enabled : Touchable.disabled);
    }

    private void audioTableVisibility(boolean visible) {
        audioTable.setVisible(visible);
        audioTable.setTouchable(visible ? Touchable.enabled : Touchable.disabled);
    }

    private void startListening(Action action, TextButton button) {
        cancelListening();
        cancelGamepadListening();
        listeningFor = action;
        listeningButton = button;
        button.setStyle(skin.get("listening", TextButton.TextButtonStyle.class));
        button.setText("Press a key...");
    }

    private void cancelListening() {
        if (listeningButton != null) {
            applyIdleStyle(listeningButton);
            listeningButton.setText(Input.Keys.toString(keyBindings.getKey(listeningFor)));
        }
        listeningFor = null;
        listeningButton = null;
    }

    private void startGamepadListening(Action action, TextButton button) {
        cancelListening();
        cancelGamepadListening();
        gamepadListeningFor = action;
        gamepadListeningButton = button;
        button.setStyle(skin.get("listening", TextButton.TextButtonStyle.class));
        button.setText("Press a button...");
    }

    private void cancelGamepadListening() {
        if (gamepadListeningButton != null) {
            applyIdleStyle(gamepadListeningButton);
            gamepadListeningButton.setText(keyBindings.getGamepadButton(gamepadListeningFor).displayName);
        }
        gamepadListeningFor = null;
        gamepadListeningButton = null;
    }

    private boolean handleKeyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            triggerBack();
            return true;
        }

        if (listeningFor != null) {
            confirmSound.play();
            keyBindings.setKey(listeningFor, keycode);
            applyIdleStyle(listeningButton);
            listeningButton.setText(Input.Keys.toString(keycode));
            listeningFor = null;
            listeningButton = null;
            return true;
        }

        if (gamepadListeningFor != null) {
            return true; // swallow keyboard input while capturing a gamepad button
        }

        // Plain menu navigation - mirrors handleControllerNavigation()'s D-Pad/A handling (moveRow/
        // moveCol/activateFocused already play selectSound/confirmSound themselves) so keyboard-only
        // players can actually reach every page/row/column, not just Back.
        switch (keycode) {
            case Input.Keys.UP:
            case Input.Keys.W:
                moveRow(-1);
                return true;
            case Input.Keys.DOWN:
            case Input.Keys.S:
                moveRow(1);
                return true;
            case Input.Keys.LEFT:
                moveCol(-1);
                return true;
            case Input.Keys.RIGHT:
                moveCol(1);
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.SPACE:
            case Input.Keys.Z:
                activateFocused();
                return true;
            default:
                return false;
        }
    }

    private void applyIdleStyle(TextButton button) {
        boolean isFocused = focusedRow >= 0 && rows.get(focusedRow)[focusedCol] == button;
        button.setStyle(skin.get(isFocused ? "focused" : "default", TextButton.TextButtonStyle.class));
    }

    private boolean isBeingListenedOn(TextButton button) {
        return button == listeningButton || button == gamepadListeningButton;
    }

    private void setFocus(int row, int col) {
        if (rows.size == 0) return;
        if (focusedRow >= 0) {
            TextButton oldButton = rows.get(focusedRow)[focusedCol];
            if (!isBeingListenedOn(oldButton)) {
                oldButton.setStyle(skin.get("default", TextButton.TextButtonStyle.class));
            }
        }
        focusedRow = row;
        focusedCol = col;
        TextButton newButton = rows.get(focusedRow)[focusedCol];
        if (!isBeingListenedOn(newButton)) {
            newButton.setStyle(skin.get("focused", TextButton.TextButtonStyle.class));
        }

        if (activeScroll != null && newButton.getWidth() > 0f) {
            activeScroll.scrollTo(newButton.getX(), newButton.getY(), newButton.getWidth(), newButton.getHeight());
        }
    }

    private void moveRow(int delta) {
        if (rows.size == 0) return;
        int newRow = (focusedRow + delta + rows.size) % rows.size;
        int newCol = rows.get(newRow)[focusedCol] == null ? 0 : focusedCol;
        if (newRow != focusedRow || newCol != focusedCol) selectSound.play();
        setFocus(newRow, newCol);
    }

    private void moveCol(int delta) {
        if (focusedRow < 0) return;
        int newCol = focusedCol + delta;
        if (newCol < 0 || newCol >= rows.get(focusedRow).length || rows.get(focusedRow)[newCol] == null) return;
        selectSound.play();
        setFocus(focusedRow, newCol);
    }

    private void handleControllerNavigation() {
        Controller controller = Controllers.getCurrent();
        if (controller == null) return;

        GamepadButton[] buttons = GamepadButton.values();
        boolean[] current = new boolean[buttons.length];
        for (int i = 0; i < buttons.length; i++) {
            current[i] = controller.getButton(KeyBindings.rawCode(controller, buttons[i]));
        }

        if (listeningFor != null) {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != GamepadButton.BACK && current[i] && !prevGamepadButtonDown[i]) {
                    cancelListening();
                    break;
                }
            }
        } else if (gamepadListeningFor != null) {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != GamepadButton.BACK && current[i] && !prevGamepadButtonDown[i]) {
                    confirmSound.play();
                    keyBindings.setGamepadButton(gamepadListeningFor, buttons[i]);
                    applyIdleStyle(gamepadListeningButton);
                    gamepadListeningButton.setText(buttons[i].displayName);
                    gamepadListeningFor = null;
                    gamepadListeningButton = null;
                    break;
                }
            }
        } else {
            boolean dpadUp = current[GamepadButton.DPAD_UP.ordinal()];
            boolean dpadDown = current[GamepadButton.DPAD_DOWN.ordinal()];
            boolean dpadLeft = current[GamepadButton.DPAD_LEFT.ordinal()];
            boolean dpadRight = current[GamepadButton.DPAD_RIGHT.ordinal()];
            boolean confirm = current[GamepadButton.A.ordinal()];
            boolean back = current[GamepadButton.B.ordinal()];

            if (dpadUp && !prevGamepadButtonDown[GamepadButton.DPAD_UP.ordinal()]) moveRow(-1);
            if (dpadDown && !prevGamepadButtonDown[GamepadButton.DPAD_DOWN.ordinal()]) moveRow(1);
            if (dpadLeft && !prevGamepadButtonDown[GamepadButton.DPAD_LEFT.ordinal()]) moveCol(-1);
            if (dpadRight && !prevGamepadButtonDown[GamepadButton.DPAD_RIGHT.ordinal()]) moveCol(1);
            if (confirm && !prevGamepadButtonDown[GamepadButton.A.ordinal()]) activateFocused();
            // B doubles as Back alongside the dedicated gamepad Back/Select button Main already
            // wires to handleControllerBackPressed() - the more familiar of the two on most pads.
            if (back && !prevGamepadButtonDown[GamepadButton.B.ordinal()]) triggerBack();
        }

        System.arraycopy(current, 0, prevGamepadButtonDown, 0, current.length);
    }

    private void activateFocused() {
        if (focusedRow < 0) return;
        Runnable activator = rowActivators.get(focusedRow)[focusedCol];
        if (activator != null) {
            confirmSound.play();
            activator.run();
        }
    }

    /** Shared by keyboard Escape, the dedicated gamepad Back/Select button, and the new gamepad B
     *  (see handleKeyDown()/handleControllerBackPressed()/handleControllerNavigation()): cancels an
     *  in-progress key/button capture if one is active, steps back up one page level if one is
     *  open, otherwise requests leaving Options entirely. */
    private void triggerBack() {
        backSound.play();
        if (listeningFor != null) {
            cancelListening();
        } else if (gamepadListeningFor != null) {
            cancelGamepadListening();
        } else if (page == Page.MENU) {
            backRequested = true;
        } else {
            switchPage(Page.MENU);
        }
    }

    private void resetKeyBindingsToDefaults() {
        keyBindings.resetToDefaults();
        for (Map.Entry<Action, TextButton> entry : keyButtons.entrySet()) {
            entry.getValue().setText(Input.Keys.toString(keyBindings.getKey(entry.getKey())));
        }
        for (Map.Entry<Action, TextButton> entry : gamepadBindingButtons.entrySet()) {
            entry.getValue().setText(keyBindings.getGamepadButton(entry.getKey()).displayName);
        }
    }

    private void resetAudioToDefaults() {
        audioSettings.resetToDefaults();
        musicValueLabel.setText(volumeText(audioSettings.getMusicVolume()));
        sfxValueLabel.setText(volumeText(audioSettings.getSfxVolume()));
    }

    public void render(float delta) {
        stage.act(delta);
        handleControllerNavigation();
        stage.getViewport().apply();
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public Stage getStage() {
        return stage;
    }

    public boolean isBackRequested() {
        return backRequested;
    }

    /** Seeds prevGamepadButtonDown from the controller's actual current state - call right before
     *  handing this screen input focus (see Main.transitionToOptions()). Whatever gamepad button
     *  just confirmed opening Options (commonly buttonA, which is also this screen's own confirm
     *  button) is very likely still physically held on the first frame handleControllerNavigation()
     *  polls, and its edge-detection would otherwise misread that same held press as a fresh
     *  confirm and instantly activate whatever's focused - same bug/fix as StartScreen.
     *  enterMenuPhase(). */
    public void syncGamepadState() {
        Controller controller = Controllers.getCurrent();
        GamepadButton[] buttons = GamepadButton.values();
        for (int i = 0; i < buttons.length; i++) {
            prevGamepadButtonDown[i] = controller != null && controller.getButton(KeyBindings.rawCode(controller, buttons[i]));
        }
    }

    /** Gamepad equivalent of the Escape-key handling in {@link #handleKeyDown} - see
     * triggerBack(). */
    public void handleControllerBackPressed() {
        triggerBack();
    }

    public void clearBackRequested() {
        backRequested = false;
        cancelListening();
        cancelGamepadListening();
        switchPage(Page.MENU);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        font.dispose();
        pixel.dispose();
        backSound.dispose();
        confirmSound.dispose();
        selectSound.dispose();
    }
}
