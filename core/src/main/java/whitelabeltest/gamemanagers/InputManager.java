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
    private boolean restartJustPressed;
    private boolean quitJustPressed;
    private boolean debugToggleJustPressed;

    private InputType activeInput = InputType.KEYBOARD;

    public void setActiveInput(InputType type) {
        this.activeInput = type;
    }

    public void update() {
        moveDirection.set(0, 0);
        isShooting = false;
        bombJustPressed = false;
        restartJustPressed = false;
        quitJustPressed = false;
        debugToggleJustPressed = false;

        if (activeInput == InputType.KEYBOARD) {
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) moveDirection.x -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) moveDirection.x += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) moveDirection.y += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) moveDirection.y -= 1;

            isShooting = Gdx.input.isKeyPressed(Input.Keys.SPACE);
            bombJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT);
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
                isShooting |= controller.getButton(controller.getMapping().buttonR1);

                if (controller.getButton(controller.getMapping().buttonB)) bombJustPressed = true;
                if (controller.getButton(controller.getMapping().buttonStart)) restartJustPressed = true;
                if (controller.getButton(controller.getMapping().buttonBack)) quitJustPressed = true;
            }
        }

        // Debug toggle always available regardless of input mode
        debugToggleJustPressed = Gdx.input.isKeyJustPressed(Input.Keys.F12);

        if (moveDirection.len() > 1.0f) {
            moveDirection.nor();
        }
    }

    public Vector2 getMoveDirection() { return moveDirection; }
    public boolean isShooting() { return isShooting; }
    public boolean isBombJustPressed() { return bombJustPressed; }
    public boolean isRestartJustPressed() { return restartJustPressed; }
    public boolean isQuitJustPressed() { return quitJustPressed; }
    public boolean isDebugToggleJustPressed() { return debugToggleJustPressed; }
}