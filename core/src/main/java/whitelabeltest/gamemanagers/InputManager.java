package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.gamemanagers.KeyBindings.Action;

public class InputManager {
    private final KeyBindings keyBindings;
    private final Vector2 moveDirection = new Vector2();
    private boolean isShooting;
    private boolean shootJustPressed;
    private boolean bombJustPressed;
    private boolean weaponSwitchJustPressed;
    private boolean hyperAttackJustPressed;
    private boolean hyperAttackHeld;
    private boolean hyperAttackJustReleased;
    private boolean restartJustPressed;
    private boolean quitJustPressed;
    private boolean debugToggleJustPressed;
    private boolean debugRestartJustPressed;
    private boolean debugMenuToggleJustPressed;
    private boolean debugMenuUpJustPressed;
    private boolean debugMenuDownJustPressed;
    private boolean debugMenuLeftPressed;
    private boolean debugMenuRightPressed;
    private boolean debugMenuLeftJustPressed;
    private boolean debugMenuRightJustPressed;
    private boolean debugMenuConfirmJustPressed;
    private boolean debugMenuDeleteJustPressed;
    private boolean debugMenuNewBookmarkJustPressed;
    private boolean debugMuteJustPressed;

    private boolean moveJustStarted;
    private boolean moveLeftJustStarted;
    private boolean moveRightJustStarted;

    private InputType activeInput = InputType.KEYBOARD;
    private boolean prevBombButton;
    private boolean prevWeaponSwitchButton;
    private boolean prevHyperAttackHeld;
    private boolean prevShootHeld;
    private boolean prevMoving;
    private boolean prevMovingLeft;
    private boolean prevMovingRight;
    // Guards the one-time seedHeldState() call below - see its javadoc.
    private boolean primed = false;

    public InputManager(KeyBindings keyBindings) {
        this.keyBindings = keyBindings;
    }

    public void setActiveInput(InputType type) {
        this.activeInput = type;
    }

    /** Primes prevShootHeld/prevHyperAttackHeld/prevMoving/etc. from the actual current
     *  keyboard/gamepad state instead of leaving them at their false default - without this, a key
     *  still physically held down from confirming the PREVIOUS screen (e.g. SPACE, which is both
     *  the start-menu's confirm key and the default SHOOT bind; or gamepad A, both the menu confirm
     *  and the default HYPER_ATTACK button) reads as a fresh press on this InputManager's very
     *  first update() - instantly firing/bombing/etc. from input the player never actually pressed
     *  during gameplay. Same fix as StartScreen.enterMenuPhase()'s gamepad-confirm debounce,
     *  applied here for the analogous carry-over into a freshly-constructed GameController. */
    private void seedHeldState() {
        if (activeInput == InputType.KEYBOARD) {
            prevShootHeld = Gdx.input.isKeyPressed(keyBindings.getKey(Action.SHOOT));
            prevBombButton = Gdx.input.isKeyPressed(keyBindings.getKey(Action.BOMB));
            prevWeaponSwitchButton = Gdx.input.isKeyPressed(keyBindings.getKey(Action.WEAPON_SWITCH));
            prevHyperAttackHeld = Gdx.input.isKeyPressed(keyBindings.getKey(Action.HYPER_ATTACK));
            prevMoving = Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_LEFT))
                || Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_RIGHT))
                || Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_UP))
                || Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_DOWN));
            prevMovingLeft = Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_LEFT));
            prevMovingRight = Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_RIGHT));
        } else if (activeInput == InputType.GAMEPAD) {
            Controller controller = Controllers.getCurrent();
            if (controller != null) {
                prevShootHeld = controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.SHOOT)));
                prevBombButton = controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.BOMB)));
                prevWeaponSwitchButton = controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.WEAPON_SWITCH)));
                prevHyperAttackHeld = controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.HYPER_ATTACK)));
                float axisX = controller.getAxis(controller.getMapping().axisLeftX);
                float axisY = controller.getAxis(controller.getMapping().axisLeftY);
                boolean dpadLeft = controller.getButton(controller.getMapping().buttonDpadLeft);
                boolean dpadRight = controller.getButton(controller.getMapping().buttonDpadRight);
                prevMoving = Math.abs(axisX) > 0.2f || Math.abs(axisY) > 0.2f
                    || dpadLeft || dpadRight
                    || controller.getButton(controller.getMapping().buttonDpadUp)
                    || controller.getButton(controller.getMapping().buttonDpadDown);
                prevMovingLeft = axisX < -0.2f || dpadLeft;
                prevMovingRight = axisX > 0.2f || dpadRight;
            }
        }
    }

    public void update() {
        update(null);
    }

    /** frame != null replays a previously-recorded ReplayFrame instead of polling live
     *  keyboard/gamepad state - see ReplayRecorder/ReplayPlayer. Debug hotkeys are always polled
     *  live regardless (see the bottom of this method), so debug tooling stays reachable while
     *  watching a replay. */
    public void update(ReplayFrame frame) {
        // Live play only (a replay's frame stream is a recorded run and must reproduce exactly, not
        // get perturbed by whatever the watching machine's hardware happens to be doing) - see
        // seedHeldState()'s javadoc for why this needs to run before the first real frame.
        if (!primed) {
            primed = true;
            if (frame == null) seedHeldState();
        }

        moveDirection.set(0, 0);
        isShooting = false;
        shootJustPressed = false;
        bombJustPressed = false;
        weaponSwitchJustPressed = false;
        hyperAttackJustPressed = false;
        hyperAttackHeld = false;
        hyperAttackJustReleased = false;
        restartJustPressed = false;
        quitJustPressed = false;
        debugToggleJustPressed = false;
        debugRestartJustPressed = false;
        debugMenuToggleJustPressed = false;
        debugMenuUpJustPressed = false;
        debugMenuDownJustPressed = false;
        debugMenuLeftPressed = false;
        debugMenuRightPressed = false;
        debugMenuLeftJustPressed = false;
        debugMenuRightJustPressed = false;
        debugMenuConfirmJustPressed = false;
        debugMenuDeleteJustPressed = false;
        debugMenuNewBookmarkJustPressed = false;
        debugMuteJustPressed = false;

        if (frame != null) {
            moveDirection.set(frame.moveX, frame.moveY);
            isShooting = frame.shooting;
            bombJustPressed = frame.bombJustPressed;
            weaponSwitchJustPressed = frame.weaponSwitchJustPressed;
            hyperAttackHeld = frame.hyperAttackHeld;
            restartJustPressed = frame.confirmJustPressed;
        } else {
            if (activeInput == InputType.KEYBOARD) {
                if (Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_LEFT))) moveDirection.x -= 1;
                if (Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_RIGHT))) moveDirection.x += 1;
                if (Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_UP))) moveDirection.y += 1;
                if (Gdx.input.isKeyPressed(keyBindings.getKey(Action.MOVE_DOWN))) moveDirection.y -= 1;

                isShooting = Gdx.input.isKeyPressed(keyBindings.getKey(Action.SHOOT));
                bombJustPressed = Gdx.input.isKeyJustPressed(keyBindings.getKey(Action.BOMB));
                weaponSwitchJustPressed = Gdx.input.isKeyJustPressed(keyBindings.getKey(Action.WEAPON_SWITCH));
                hyperAttackHeld = Gdx.input.isKeyPressed(keyBindings.getKey(Action.HYPER_ATTACK));
                restartJustPressed = Gdx.input.isKeyJustPressed(keyBindings.getKey(Action.RESTART));
                quitJustPressed = Gdx.input.isKeyJustPressed(keyBindings.getKey(Action.QUIT));
            }

            if (activeInput == InputType.GAMEPAD) {
                Controller controller = Controllers.getCurrent();
                if (controller != null) {
                    float axisX = controller.getAxis(controller.getMapping().axisLeftX);
                    float axisY = controller.getAxis(controller.getMapping().axisLeftY);

                    if (Math.abs(axisX) > 0.2f) moveDirection.x += axisX;
                    if (Math.abs(axisY) > 0.2f) moveDirection.y -= axisY;

                    if (controller.getButton(controller.getMapping().buttonDpadLeft)) moveDirection.x -= 1;
                    if (controller.getButton(controller.getMapping().buttonDpadRight)) moveDirection.x += 1;
                    if (controller.getButton(controller.getMapping().buttonDpadUp)) moveDirection.y += 1;
                    if (controller.getButton(controller.getMapping().buttonDpadDown)) moveDirection.y -= 1;

                    isShooting |= controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.SHOOT)));

                    boolean bombButton = controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.BOMB)));
                    if (bombButton && !prevBombButton) bombJustPressed = true;
                    prevBombButton = bombButton;

                    boolean weaponSwitchButton = controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.WEAPON_SWITCH)));
                    if (weaponSwitchButton && !prevWeaponSwitchButton) weaponSwitchJustPressed = true;
                    prevWeaponSwitchButton = weaponSwitchButton;

                    hyperAttackHeld |= controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.HYPER_ATTACK)));

                    if (controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.RESTART)))) restartJustPressed = true;
                    if (controller.getButton(KeyBindings.rawCode(controller, keyBindings.getGamepadButton(Action.QUIT)))) quitJustPressed = true;
                }
            }
        }

        // Held/edge state is derived once here instead of separately per input type, so a hyper
        // attack charge started on one input type is still tracked correctly even if a rebind or
        // input-type switch happens mid-charge - see Player.updateThunderboltCharge.
        hyperAttackJustPressed = hyperAttackHeld && !prevHyperAttackHeld;
        hyperAttackJustReleased = !hyperAttackHeld && prevHyperAttackHeld;
        prevHyperAttackHeld = hyperAttackHeld;
        shootJustPressed = isShooting && !prevShootHeld;
        prevShootHeld = isShooting;

        // Debug toggle/restart/menu always available regardless of input mode
        debugToggleJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.F12);
        debugRestartJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.F9);
        debugMenuToggleJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.F1);
        debugMenuUpJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.UP);
        debugMenuDownJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.DOWN);
        debugMenuLeftPressed = Gdx.input.isKeyPressed(Input.Keys.LEFT);
        debugMenuRightPressed = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        debugMenuLeftJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.LEFT);
        debugMenuRightJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT);
        debugMenuConfirmJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
        debugMenuDeleteJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.FORWARD_DEL);
        debugMenuNewBookmarkJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.N);
        debugMuteJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.M);

        // A replayed frame's moveX/moveY is already final (e.g. a partial analog-stick tilt of
        // length 0.3) - only live-polled input needs renormalizing after combining axes/keys.
        if (frame == null && moveDirection.len() > 1.0f) {
            moveDirection.nor();
        }

        // Same held/edge derivation as shootJustPressed above, but for movement - lets a
        // SpawnScheduler "moved" gate (see GateCue) require a fresh press after the gate engages
        // instead of being trivially satisfied by a direction key already held from earlier,
        // unrestricted play.
        boolean moving = !moveDirection.isZero();
        moveJustStarted = moving && !prevMoving;
        prevMoving = moving;

        // Same idea, but split by X-axis direction - lets a "movedLeft"/"movedRight" gate (see
        // GateCue) require a fresh press specifically in that direction, for a scripted
        // left/right/left micro-dodging drill instead of just "moved at all".
        boolean movingLeft = moveDirection.x < 0f;
        boolean movingRight = moveDirection.x > 0f;
        moveLeftJustStarted = movingLeft && !prevMovingLeft;
        moveRightJustStarted = movingRight && !prevMovingRight;
        prevMovingLeft = movingLeft;
        prevMovingRight = movingRight;
    }

    public Vector2 getMoveDirection() { return moveDirection; }
    public boolean isShooting() { return isShooting; }
    public boolean isShootJustPressed() { return shootJustPressed; }
    public boolean isMoveJustStarted() { return moveJustStarted; }
    public boolean isMoveLeftJustStarted() { return moveLeftJustStarted; }
    public boolean isMoveRightJustStarted() { return moveRightJustStarted; }
    public boolean isBombJustPressed() { return bombJustPressed; }
    public boolean isWeaponSwitchJustPressed() { return weaponSwitchJustPressed; }
    public boolean isHyperAttackJustPressed() { return hyperAttackJustPressed; }
    public boolean isHyperAttackHeld() { return hyperAttackHeld; }
    public boolean isHyperAttackJustReleased() { return hyperAttackJustReleased; }
    public boolean isRestartJustPressed() { return restartJustPressed; }
    public boolean isQuitJustPressed() { return quitJustPressed; }
    public boolean isDebugToggleJustPressed() { return debugToggleJustPressed; }
    public boolean isDebugRestartJustPressed() { return debugRestartJustPressed; }
    public boolean isDebugMenuToggleJustPressed() { return debugMenuToggleJustPressed; }
    public boolean isDebugMenuUpJustPressed() { return debugMenuUpJustPressed; }
    public boolean isDebugMenuDownJustPressed() { return debugMenuDownJustPressed; }
    public boolean isDebugMenuLeftPressed() { return debugMenuLeftPressed; }
    public boolean isDebugMenuRightPressed() { return debugMenuRightPressed; }
    public boolean isDebugMenuLeftJustPressed() { return debugMenuLeftJustPressed; }
    public boolean isDebugMenuRightJustPressed() { return debugMenuRightJustPressed; }
    public boolean isDebugMenuConfirmJustPressed() { return debugMenuConfirmJustPressed; }
    public boolean isDebugMenuDeleteJustPressed() { return debugMenuDeleteJustPressed; }
    public boolean isDebugMenuNewBookmarkJustPressed() { return debugMenuNewBookmarkJustPressed; }
    public boolean isDebugMuteJustPressed() { return debugMuteJustPressed; }
}
