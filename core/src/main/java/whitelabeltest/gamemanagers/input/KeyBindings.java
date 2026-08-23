package whitelabeltest.gamemanagers.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;

public class KeyBindings {

    public enum GamepadButton {
        A("A"), B("B"), X("X"), Y("Y"), L1("LB"), R1("RB"), START("Start"), BACK("Back"),
        DPAD_UP("D-Up"), DPAD_DOWN("D-Down"), DPAD_LEFT("D-Left"), DPAD_RIGHT("D-Right");

        public final String displayName;

        GamepadButton(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum Action {
        MOVE_LEFT("Move Left", Input.Keys.LEFT, null),
        MOVE_RIGHT("Move Right", Input.Keys.RIGHT, null),
        MOVE_UP("Move Up", Input.Keys.UP, null),
        MOVE_DOWN("Move Down", Input.Keys.DOWN, null),
        SHOOT("Shoot", Input.Keys.SPACE, GamepadButton.X),
        BOMB("Bomb", Input.Keys.SHIFT_LEFT, GamepadButton.B),
        WEAPON_SWITCH("Switch Weapon", Input.Keys.X, GamepadButton.R1),
        HYPER_ATTACK("Hyper Attack", Input.Keys.C, GamepadButton.A),
        RESTART("Restart", Input.Keys.R, GamepadButton.START),
        QUIT("Quit", Input.Keys.Q, GamepadButton.BACK);

        public final String label;
        public final int defaultKey;
        public final GamepadButton defaultGamepadButton;

        Action(String label, int defaultKey, GamepadButton defaultGamepadButton) {
            this.label = label;
            this.defaultKey = defaultKey;
            this.defaultGamepadButton = defaultGamepadButton;
        }

        public boolean hasGamepadBinding() {
            return defaultGamepadButton != null;
        }
    }

    private static final String PREFS_NAME = "whitelabeltest-keybindings";
    private static final String GAMEPAD_SUFFIX = "_gamepad";

    private final Preferences prefs;
    private final int[] keys = new int[Action.values().length];
    private final GamepadButton[] gamepadButtons = new GamepadButton[Action.values().length];

    public KeyBindings() {
        prefs = Gdx.app.getPreferences(PREFS_NAME);
        for (Action action : Action.values()) {
            keys[action.ordinal()] = prefs.getInteger(action.name(), action.defaultKey);
            if (action.hasGamepadBinding()) {
                String stored = prefs.getString(action.name() + GAMEPAD_SUFFIX, action.defaultGamepadButton.name());
                gamepadButtons[action.ordinal()] = GamepadButton.valueOf(stored);
            }
        }
    }

    public int getKey(Action action) {
        return keys[action.ordinal()];
    }

    public void setKey(Action action, int keycode) {
        keys[action.ordinal()] = keycode;
        prefs.putInteger(action.name(), keycode);
        prefs.flush();
    }

    public GamepadButton getGamepadButton(Action action) {
        return gamepadButtons[action.ordinal()];
    }

    public void setGamepadButton(Action action, GamepadButton button) {
        gamepadButtons[action.ordinal()] = button;
        prefs.putString(action.name() + GAMEPAD_SUFFIX, button.name());
        prefs.flush();
    }

    public void resetToDefaults() {
        for (Action action : Action.values()) {
            keys[action.ordinal()] = action.defaultKey;
            prefs.putInteger(action.name(), action.defaultKey);
            if (action.hasGamepadBinding()) {
                gamepadButtons[action.ordinal()] = action.defaultGamepadButton;
                prefs.putString(action.name() + GAMEPAD_SUFFIX, action.defaultGamepadButton.name());
            }
        }
        prefs.flush();
    }

    /** Resolves a logical button to this specific controller's raw button code. */
    public static int rawCode(Controller controller, GamepadButton button) {
        ControllerMapping mapping = controller.getMapping();
        switch (button) {
            case A: return mapping.buttonA;
            case B: return mapping.buttonB;
            case X: return mapping.buttonX;
            case Y: return mapping.buttonY;
            case L1: return mapping.buttonL1;
            case R1: return mapping.buttonR1;
            case START: return mapping.buttonStart;
            case BACK: return mapping.buttonBack;
            case DPAD_UP: return mapping.buttonDpadUp;
            case DPAD_DOWN: return mapping.buttonDpadDown;
            case DPAD_LEFT: return mapping.buttonDpadLeft;
            case DPAD_RIGHT: return mapping.buttonDpadRight;
            default: throw new IllegalStateException("Unhandled gamepad button: " + button);
        }
    }
}
