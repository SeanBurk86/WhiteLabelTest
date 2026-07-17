package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Vector2;

public class InputManager {
    private final Vector2 moveDirection = new Vector2();
    private boolean isShooting;
    private boolean bombJustPressed;
    private boolean weaponSwitchJustPressed;
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

    private InputType activeInput = InputType.KEYBOARD;
    private boolean prevBombButton;
    private boolean prevWeaponSwitchButton;

    public void setActiveInput(InputType type) {
        this.activeInput = type;
    }

    public void update() {
        moveDirection.set(0, 0);
        isShooting = false;
        bombJustPressed = false;
        weaponSwitchJustPressed = false;
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

        if (activeInput == InputType.KEYBOARD) {
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) moveDirection.x -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) moveDirection.x += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) moveDirection.y += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) moveDirection.y -= 1;

            isShooting = Gdx.input.isKeyPressed(Input.Keys.SPACE);
            bombJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT);
            weaponSwitchJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.X);
            restartJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.R);
            quitJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.Q);
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

                isShooting |= controller.getButton(controller.getMapping().buttonA);

                boolean bombButton = controller.getButton(controller.getMapping().buttonB);
                if (bombButton && !prevBombButton) bombJustPressed = true;
                prevBombButton = bombButton;

                boolean weaponSwitchButton = controller.getButton(controller.getMapping().buttonX);
                if (weaponSwitchButton && !prevWeaponSwitchButton) weaponSwitchJustPressed = true;
                prevWeaponSwitchButton = weaponSwitchButton;

                if (controller.getButton(controller.getMapping().buttonStart)) restartJustPressed = true;
                if (controller.getButton(controller.getMapping().buttonBack)) quitJustPressed = true;
            }
        }

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

        if (moveDirection.len() > 1.0f) {
            moveDirection.nor();
        }
    }

    public Vector2 getMoveDirection() { return moveDirection; }
    public boolean isShooting() { return isShooting; }
    public boolean isBombJustPressed() { return bombJustPressed; }
    public boolean isWeaponSwitchJustPressed() { return weaponSwitchJustPressed; }
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
}
