package whitelabeltest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import whitelabeltest.gamemanagers.KeyBindings;
import whitelabeltest.gamemanagers.KeyBindings.Action;
import whitelabeltest.gamemanagers.KeyBindings.GamepadButton;

import java.util.EnumMap;
import java.util.Map;

/** Scene2D key/gamepad-rebinding menu, reached from StartScreen via Escape or the gamepad Back
 * button. Built with a small procedurally-generated Skin since the project has no bundled
 * Scene2D skin assets. Fully operable by mouse, keyboard, or gamepad D-pad/A/Back. */
public class OptionsScreen implements Disposable {
    private final Stage stage;
    private final KeyBindings keyBindings;
    private final BitmapFont font;
    private final Texture pixel;
    private final Skin skin;
    private final Map<Action, TextButton> keyButtons = new EnumMap<>(Action.class);
    private final Map<Action, TextButton> gamepadBindingButtons = new EnumMap<>(Action.class);

    // Gamepad-navigable grid, in visual row order: each row is [keyButton, gamepadButton-or-null],
    // with a final one-column row for the reset button (null action). D-pad up/down moves between
    // rows, left/right moves between the Keyboard/Gamepad columns (matching the visual layout),
    // and A activates whichever cell is focused.
    private final Array<TextButton[]> rows = new Array<>();
    private final Array<Action> rowAction = new Array<>();
    private int focusedRow = -1;
    private int focusedCol = 0;
    private final boolean[] prevGamepadButtonDown = new boolean[GamepadButton.values().length];

    private Action listeningFor;
    private TextButton listeningButton;
    private Action gamepadListeningFor;
    private TextButton gamepadListeningButton;
    private boolean backRequested;

    public OptionsScreen(KeyBindings keyBindings, float worldWidth, float worldHeight) {
        this.keyBindings = keyBindings;
        this.stage = new Stage(new ExtendViewport(worldWidth, worldHeight));

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        generator.dispose();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.01171875f);

        Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        skin = buildSkin();

        Table root = buildLayout();
        stage.addActor(root);
        stage.setKeyboardFocus(root);
        setFocus(0, 0);
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

        // Gamepad D-pad selection highlight, distinct from the mouse-hover ("over") and
        // key-capture ("listening") tints so a gamepad user can always see where they are.
        TextButton.TextButtonStyle focusedStyle = new TextButton.TextButtonStyle(buttonStyle);
        focusedStyle.up = base.tint(new Color(0.2f, 0.32f, 0.5f, 1f));
        focusedStyle.fontColor = Color.CYAN;
        skin.add("focused", focusedStyle);

        return skin;
    }

    private Table buildLayout() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(0.6f);

        Label title = new Label("OPTIONS", skin, "header");
        root.add(title).colspan(3).padBottom(0.5f).row();

        Label subtitle = new Label("KEY BINDINGS", skin);
        root.add(subtitle).colspan(3).padBottom(0.2f).row();

        root.add();
        root.add(new Label("Keyboard", skin)).width(1.9f);
        root.add(new Label("Gamepad", skin)).width(1.9f).row();

        for (Action action : Action.values()) {
            Label nameLabel = new Label(action.label, skin);
            TextButton keyButton = new TextButton(Input.Keys.toString(keyBindings.getKey(action)), skin);
            keyButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    startListening(action, keyButton);
                }
            });

            root.add(nameLabel).left().width(2.6f).pad(0.06f);
            root.add(keyButton).width(1.9f).height(0.4f).pad(0.06f);
            keyButtons.put(action, keyButton);

            TextButton gamepadButton = null;
            if (action.hasGamepadBinding()) {
                gamepadButton = new TextButton(keyBindings.getGamepadButton(action).displayName, skin);
                TextButton finalGamepadButton = gamepadButton;
                gamepadButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        startGamepadListening(action, finalGamepadButton);
                    }
                });
                root.add(gamepadButton).width(1.9f).height(0.4f).pad(0.06f).row();
                gamepadBindingButtons.put(action, gamepadButton);
            } else {
                root.add().width(1.9f).pad(0.06f).row();
            }
            rows.add(new TextButton[]{keyButton, gamepadButton});
            rowAction.add(action);
        }

        TextButton resetButton = new TextButton("Reset to Defaults", skin);
        resetButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                resetToDefaults();
            }
        });
        root.add(resetButton).colspan(3).padTop(0.4f).height(0.4f).row();
        rows.add(new TextButton[]{resetButton, null});
        rowAction.add(null);

        Label backHint = new Label("Esc/Back - Back   D-Pad - Move   A - Select", skin);
        root.add(backHint).colspan(3).padTop(0.35f);

        root.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleKeyDown(keycode);
            }
        });

        return root;
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
            if (listeningFor != null) {
                cancelListening();
            } else if (gamepadListeningFor != null) {
                cancelGamepadListening();
            } else {
                backRequested = true;
            }
            return true;
        }

        if (listeningFor != null) {
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

        return false;
    }

    /** Restores a button to "focused" or "default" depending on whether it's the gamepad's
     * currently-selected cell, once it's no longer showing the "listening" capture style. */
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
    }

    /** Moves between rows (actions), snapping back to column 0 if the destination row has no
     * Gamepad-column cell (e.g. the movement actions, which have no rebindable button). */
    private void moveRow(int delta) {
        if (rows.size == 0) return;
        int newRow = (focusedRow + delta + rows.size) % rows.size;
        int newCol = rows.get(newRow)[focusedCol] == null ? 0 : focusedCol;
        setFocus(newRow, newCol);
    }

    /** Moves between the Keyboard/Gamepad columns of the current row; a no-op where the target
     * column doesn't exist for this row. */
    private void moveCol(int delta) {
        if (focusedRow < 0) return;
        int newCol = focusedCol + delta;
        if (newCol < 0 || newCol >= rows.get(focusedRow).length || rows.get(focusedRow)[newCol] == null) return;
        setFocus(focusedRow, newCol);
    }

    /** Polls the gamepad each frame so the menu is fully operable without a mouse or keyboard:
     * D-pad up/down moves between rows, left/right moves between the Keyboard/Gamepad columns,
     * and A activates the focused cell (opening key/button capture, or firing reset). While
     * capturing a gamepad button, any button but Back is accepted as the new binding - Back is
     * reserved for cancel/exit and handled by Main via {@link #handleControllerBackPressed()}.
     * While capturing a *keyboard* key, a gamepad button can't satisfy it, so any non-Back press
     * cancels that capture instead of leaving the "Press a key..." prompt stuck. */
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

            if (dpadUp && !prevGamepadButtonDown[GamepadButton.DPAD_UP.ordinal()]) moveRow(-1);
            if (dpadDown && !prevGamepadButtonDown[GamepadButton.DPAD_DOWN.ordinal()]) moveRow(1);
            if (dpadLeft && !prevGamepadButtonDown[GamepadButton.DPAD_LEFT.ordinal()]) moveCol(-1);
            if (dpadRight && !prevGamepadButtonDown[GamepadButton.DPAD_RIGHT.ordinal()]) moveCol(1);
            if (confirm && !prevGamepadButtonDown[GamepadButton.A.ordinal()]) activateFocused();
        }

        System.arraycopy(current, 0, prevGamepadButtonDown, 0, current.length);
    }

    private void activateFocused() {
        if (focusedRow < 0) return;
        Action action = rowAction.get(focusedRow);
        TextButton button = rows.get(focusedRow)[focusedCol];
        if (action == null) {
            resetToDefaults();
        } else if (focusedCol == 1) {
            startGamepadListening(action, button);
        } else {
            startListening(action, button);
        }
    }

    private void resetToDefaults() {
        keyBindings.resetToDefaults();
        for (Map.Entry<Action, TextButton> entry : keyButtons.entrySet()) {
            entry.getValue().setText(Input.Keys.toString(keyBindings.getKey(entry.getKey())));
        }
        for (Map.Entry<Action, TextButton> entry : gamepadBindingButtons.entrySet()) {
            entry.getValue().setText(keyBindings.getGamepadButton(entry.getKey()).displayName);
        }
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

    /** Gamepad equivalent of the Escape-key handling in {@link #handleKeyDown}: cancels an
     * in-progress key/button capture if one is active, otherwise requests leaving the screen. */
    public void handleControllerBackPressed() {
        if (listeningFor != null) {
            cancelListening();
        } else if (gamepadListeningFor != null) {
            cancelGamepadListening();
        } else {
            backRequested = true;
        }
    }

    public void clearBackRequested() {
        backRequested = false;
        cancelListening();
        cancelGamepadListening();
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        font.dispose();
        pixel.dispose();
    }
}
